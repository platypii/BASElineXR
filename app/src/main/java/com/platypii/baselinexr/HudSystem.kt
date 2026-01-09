package com.platypii.baselinexr

import android.util.Log
import android.widget.TextView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.location.LocationStatus
import com.platypii.baselinexr.util.Convert
import com.platypii.baselinexr.util.GpsFreshnessColor

class HudSystem : SystemBase() {
    companion object {
        private const val TAG = "BXRINPUT"
        private const val CLICK_DEBOUNCE_MS = 200L  // Ignore clicks within 200ms of each other
    }
    
    private var initialized = false
    private var grabbablePanel: GrabbablePanel? = null
    private var extraControlsVisible = false
    private var inputListenerAdded = false
    private var panelEntity: Entity? = null
    private var lastClickTime = 0L  // For debouncing duplicate click events
    
    // Seekbar drag tracking
    private var isSeekbarDragging = false
    private var seekbarDragStartU = 0f

    // HUD content references
    private var latlngLabel: TextView? = null
    private var speedLabel: TextView? = null

    override fun execute() {
        val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
        if (!activity.glxfLoaded) return

        if (!initialized) {
            initializePanel(activity)
        }

        if (initialized) {
            grabbablePanel?.setupInteraction()
            grabbablePanel?.updatePosition()
            
            // Retry adding input listener if it wasn't added yet
            if (!inputListenerAdded && panelEntity != null) {
                addPanelInputListener(activity, panelEntity!!)
            }
            
            // Update seekbar position during playback
            activity.hudPanelController?.updateSeekBarPosition()
        }

        updateLocation()
    }

    private fun initializePanel(activity: BaselineActivity) {
        val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val panel = composition.tryGetNodeByName("Panel")
        if (panel?.entity != null) {
            val hudOffset = Vector3(-0.2f, 1.4f, 3.6f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, hudOffset)
            panelEntity = panel.entity
            initialized = true
            
            // Ensure Hittable component is set for input detection
            // Default Hittable() enables raycast collision on the panel mesh
            Log.d(TAG, "Setting Hittable component on Panel entity")
            panel.entity.setComponent(com.meta.spatial.toolkit.Hittable())
            
            // Add input listener for button clicks
            if (!inputListenerAdded) {
                addPanelInputListener(activity, panel.entity)
            }
        }
    }
    
    /**
     * Add an InputListener to the HUD Panel entity to handle clicks
     * based on hit position coordinates
     */
    private fun addPanelInputListener(activity: BaselineActivity, entity: Entity) {
        Log.d(TAG, "addPanelInputListener() called for entity: $entity")
        val sceneObjectSystem = systemManager.findSystem<SceneObjectSystem>()
        Log.d(TAG, "SceneObjectSystem: $sceneObjectSystem")
        
        // Try to get SceneObject - it may not be ready yet
        val sceneObjectFuture = sceneObjectSystem.getSceneObject(entity)
        Log.d(TAG, "SceneObject future: $sceneObjectFuture")
        
        if (sceneObjectFuture == null) {
            // SceneObject not ready yet, we'll retry on next frame via execute()
            Log.d(TAG, "SceneObject not ready, will retry...")
            return
        }
        
        sceneObjectFuture.thenAccept { sceneObject ->
            Log.d(TAG, "Got SceneObject: $sceneObject (type: ${sceneObject::class.simpleName})")
            
            // Cast to PanelSceneObject if possible for better panel input handling
            val isPanelSceneObject = sceneObject is com.meta.spatial.runtime.PanelSceneObject
            Log.d(TAG, "Is PanelSceneObject: $isPanelSceneObject, adding InputListener...")
            
            sceneObject.addInputListener(object : InputListener {
                // Hover callbacks for debugging
                override fun onHoverStart(receiver: SceneObject, sourceOfInput: Entity) {
                    Log.d(TAG, "=== HUD PANEL onHoverStart ===")
                }
                
                override fun onHoverStop(receiver: SceneObject, sourceOfInput: Entity) {
                    Log.d(TAG, "=== HUD PANEL onHoverStop ===")
                }
                
                // Use onClick for simple click handling (higher-level than onInput)
                override fun onClick(
                    receiver: SceneObject,
                    hitInfo: HitInfo,
                    sourceOfInput: Entity,
                ) {
                    Log.i(TAG, "=== HUD PANEL onClick ===")
                    handlePanelClick(activity, hitInfo)
                }
                
                // Also keep onInput for debugging and catching all input types
                override fun onInput(
                    receiver: SceneObject,
                    hitInfo: HitInfo,
                    sourceOfInput: Entity,
                    changed: Int,
                    clicked: Int,
                    downTime: Long
                ): Boolean {
                    // Calculate button states
                    val buttonPressed = (changed and clicked) != 0  // Button just went down
                    val buttonReleased = (changed != 0) && (changed and clicked) == 0  // Button just went up
                    val buttonHeld = clicked != 0  // Button is currently held (includes just pressed)
                    
                    // Get panel coordinates for all input handling
                    val panelTransform = panelEntity?.tryGetComponent<com.meta.spatial.toolkit.Transform>()
                    if (panelTransform == null) {
                        if (buttonReleased && isSeekbarDragging) {
                            // End drag even without coordinates
                            Log.i(TAG, "Seekbar drag ended (no transform)")
                            isSeekbarDragging = false
                            activity.runOnUiThread {
                                activity.hudPanelController?.handleSeekbarDragEnd(seekbarDragStartU)
                            }
                        }
                        return false
                    }
                    
                    val localPoint = panelTransform.transform.inverse() * hitInfo.point
                    val panelWidth = 2.0f
                    val panelHeight = 1.5f
                    val u = 1.0f - (localPoint.x + panelWidth / 2) / panelWidth
                    val v = 1.0f - (localPoint.y + panelHeight / 2) / panelHeight
                    
                    val headerThreshold = 0.20f
                    val isPlayControlsPopupVisible = activity.hudPanelController?.isPlayControlsPopupVisible() == true
                    
                    // Handle seekbar drag state machine
                    if (isSeekbarDragging) {
                        if (buttonReleased) {
                            // End drag
                            Log.i(TAG, "Seekbar drag ended at u=$u")
                            isSeekbarDragging = false
                            activity.runOnUiThread {
                                activity.hudPanelController?.handleSeekbarDragEnd(u)
                            }
                            return true
                        } else if (buttonHeld) {
                            // Continue drag - update position
                            activity.runOnUiThread {
                                activity.hudPanelController?.handleSeekbarDragUpdate(u)
                            }
                            return true
                        }
                    }
                    
                    // Only process new button presses below this point
                    if (!buttonPressed) {
                        return false
                    }
                    
                    // Debounce for non-drag clicks
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime < CLICK_DEBOUNCE_MS) {
                        Log.d(TAG, "Ignoring duplicate click (debounce)")
                        return true
                    }
                    lastClickTime = now
                    
                    Log.i(TAG, "=== HUD PANEL CLICK === u=$u, v=$v")
                    
                    // Check for play controls popup seekbar first (before other popup handling)
                    if (isPlayControlsPopupVisible && v > headerThreshold) {
                        // Check if click is in seekbar area
                        val isInSeekbarArea = activity.hudPanelController?.isPointInSeekbar(u, v) == true
                        if (isInSeekbarArea) {
                            Log.i(TAG, "Seekbar drag started at u=$u")
                            isSeekbarDragging = true
                            seekbarDragStartU = u
                            activity.runOnUiThread {
                                activity.hudPanelController?.handleSeekbarDragStart(u)
                            }
                            return true
                        }
                    }
                        
                    // Header area thresholds
                    val exitButtonLeft = 0.86f
                    val recordButtonLeft = 0.72f
                    val playControlsButtonLeft = 0.58f
                    val hudPanelRight = 0.72f
                    
                    // Check for exit button
                    if (u > exitButtonLeft && v < headerThreshold) {
                        Log.i(TAG, "  -> EXIT BUTTON clicked!")
                        activity.runOnUiThread {
                            activity.finish()
                        }
                        return true
                    }
                    
                    // Check for record button
                    if (u > recordButtonLeft && u < exitButtonLeft && v < headerThreshold) {
                        Log.i(TAG, "  -> RECORD BUTTON clicked!")
                        activity.runOnUiThread {
                            activity.screenRecordController?.toggleRecording()
                        }
                        return true
                    }
                    
                    // Check for play controls button
                    val isPlayControlsVisible = activity.hudPanelController?.isPlayControlsButtonVisible() == true
                    if (isPlayControlsVisible && u > playControlsButtonLeft && u < recordButtonLeft && v < headerThreshold) {
                        Log.i(TAG, "  -> PLAY CONTROLS BUTTON clicked!")
                        activity.runOnUiThread {
                            activity.hudPanelController?.togglePlayControlsPopup()
                        }
                        return true
                    }
                    
                    // Check for hudPanel header area
                    val effectiveHudPanelRight = if (isPlayControlsVisible) playControlsButtonLeft else hudPanelRight
                    if (u < effectiveHudPanelRight && v < headerThreshold) {
                        Log.i(TAG, "  -> HUD HEADER clicked - toggling menu")
                        activity.runOnUiThread {
                            activity.hudPanelController?.handleHeaderClick()
                        }
                        return true
                    }
                    
                    // Check for play controls popup (non-seekbar clicks)
                    if (isPlayControlsPopupVisible && v > headerThreshold) {
                        Log.i(TAG, "  -> PLAY CONTROLS POPUP clicked at ($u, $v)")
                        activity.runOnUiThread {
                            activity.hudPanelController?.handlePlayControlsClick(u, v)
                        }
                        return true
                    }
                    
                    // Check for menu area
                    if (v > headerThreshold) {
                        Log.i(TAG, "  -> MENU AREA clicked at ($u, $v)")
                        activity.runOnUiThread {
                            activity.hudPanelController?.handleMenuClick(u, v)
                        }
                        return true
                    }
                    
                    Log.i(TAG, "  -> Click not in known button area")
                    return false
                }
            })
            inputListenerAdded = true
            Log.d(TAG, "Added HUD Panel input listener")
        }
    }


    fun setLabels(latlngLabel: TextView?, speedLabel: TextView?) {
        this.latlngLabel = latlngLabel
        this.speedLabel = speedLabel

        updateLocation()
    }

    private fun updateLocation() {
        val provider = Services.location.dataSource()
        val loc = Services.location.lastLoc
        val refreshRate = Services.location.refreshRate()
        latlngLabel?.setCompoundDrawablesWithIntrinsicBounds(LocationStatus.icon, 0, 0, 0)
        if (loc != null) {
            latlngLabel?.text = provider + " " + loc.toStringSimple() + " (" + VROptions.current.name + ")"
            speedLabel?.text = Convert.speed(loc.groundSpeed()) + "  " + Convert.distance(loc.altitude_gps) + "  " + String.format("%.1f Hz", refreshRate)
        } else {
            latlngLabel?.text = LocationStatus.message
            speedLabel?.text = ""
        }
        val millisecondsSinceLastFix = Services.location.lastFixDuration()
        val color = GpsFreshnessColor.getColorForFreshness(millisecondsSinceLastFix)
        speedLabel?.setTextColor(color)
    }

    fun setExtraControlsVisible(visible: Boolean) {
        // Only apply changes if state is actually changing
        if (extraControlsVisible == visible) return

        extraControlsVisible = visible
        val offset = if (visible) Vector3(0.2f, -1.5f, -2f) else Vector3(-0.2f, 1.5f, 2f)
        grabbablePanel?.moveByOffset(offset)

        // Scale panel uniformly when menus are visible
        val scale = if (visible) Vector3(1.1f, 1.1f, 1.1f) else Vector3(1.0f, 1.0f, 1.0f)
        grabbablePanel?.setMenuScale(scale)

        // Enlarge DirectionArrow when extra controls are visible
        val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
        activity.directionArrowSystem?.setEnlarged(visible)
        
        // Show head model for heading calibration when menu is visible
        activity.headModelSystem?.setEnlarged(visible)
    }

    fun cleanup() {
        grabbablePanel = null
        latlngLabel = null
        speedLabel = null
        initialized = false
    }

    /**
     * Handle a click on the HUD panel based on hit position
     */
    private fun handlePanelClick(activity: BaselineActivity, hitInfo: HitInfo) {
        Log.i(TAG, "  Hit point (world): ${hitInfo.point}")
        
        // Get panel transform to convert world coords to local
        val panelTransform = panelEntity?.tryGetComponent<com.meta.spatial.toolkit.Transform>()
        if (panelTransform != null) {
            // Convert world hit point to local panel coordinates
            val localPoint = panelTransform.transform.inverse() * hitInfo.point
            Log.i(TAG, "  Hit point (local): $localPoint")
            
            // Panel is 2m wide x 1.5m tall (from scene scale)
            // Local coords: x is left-right, y is up-down
            // Note: Panel's local X axis is inverted relative to view (negative X = right side of panel)
            // Normalize to 0-1 range
            val panelWidth = 2.0f
            val panelHeight = 1.5f
            val u = 1.0f - (localPoint.x + panelWidth / 2) / panelWidth  // Invert: 0=left, 1=right (in view coords)
            val v = 1.0f - (localPoint.y + panelHeight / 2) / panelHeight  // 0=top, 1=bottom (invert for view coords)
            
            Log.i(TAG, "  Normalized: u=$u, v=$v")
        
            // Header area is top ~20% of panel (80dp header in ~400dp panel)
            // Exit button is rightmost ~14% (80dp in ~600dp panel)
            // HudPanel (header text) is left ~72% (panel minus 170dp button area)
            val headerThreshold = 0.20f  // Top 20% is header row
            val exitButtonLeft = 0.86f   // Exit button starts at 86% from left
            val hudPanelRight = 0.72f    // HudPanel ends at 72% (170dp button area)
            
            // Check for exit button (right ~14% of width, top header area)
            if (u > exitButtonLeft && v < headerThreshold) {
                Log.i(TAG, "  -> EXIT BUTTON clicked!")
                activity.runOnUiThread {
                    activity.finish()
                }
                return
            }
            
            // Check for heading button (between hudPanel and exit button, ~72%-86% range)
            if (u > hudPanelRight && u < exitButtonLeft && v < headerThreshold) {
                Log.i(TAG, "  -> HEADING BUTTON clicked!")
                activity.runOnUiThread {
                    activity.hudPanelController?.handleHeadingButtonClick()
                }
                return
            }
            
            // Check for hudPanel area (left ~72% of width, top header area)
            if (u < hudPanelRight && v < headerThreshold) {
                Log.i(TAG, "  -> HUD HEADER clicked - toggling menu")
                activity.runOnUiThread {
                    activity.hudPanelController?.handleHeaderClick()
                }
                return
            }
            
            // Check for menu area (when visible, below header)
            if (v > headerThreshold) {
                Log.i(TAG, "  -> MENU AREA clicked at ($u, $v)")
                activity.runOnUiThread {
                    activity.hudPanelController?.handleMenuClick(u, v)
                }
                return
            }
            
            Log.i(TAG, "  -> Click not in known button area")
        } else {
            Log.w(TAG, "Panel transform not found")
        }
    }
}
