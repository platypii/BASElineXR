package com.platypii.baselinexr

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.atan2
import kotlin.math.sqrt
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Visible

class MiniMapPanel : SystemBase() {
    private var initialized = false
    private var miniMapEntity: Entity? = null
    private var grabbablePanel: GrabbablePanel? = null

    // Minimap content references
    private var redDot: View? = null
    private var blueDot: View? = null
    private var greenDot: View? = null
    private var minimapImage: ImageView? = null

    // For periodic auto-selection
    private var lastAutoSelectTime = 0L
    private val autoSelectInterval = 10000L // Check every 10 seconds


    override fun execute() {
        val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
        if (!activity.glxfLoaded) return

        if (!initialized) {
            initializeMiniMap(activity)
        }

        if (initialized) {
            grabbablePanel?.setupInteraction()
            grabbablePanel?.updatePosition()
            updateMiniMapPosition()

            // Periodically check if we should auto-select a different minimap
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAutoSelectTime > autoSelectInterval) {
                autoSelectMinimap()
                lastAutoSelectTime = currentTime
            }

            updateRedDotPosition()
            updateBlueDotPosition()
            updateGreenDotPosition()
        }
    }

    private fun initializeMiniMap(activity: BaselineActivity) {
        try {
            // Find the minimap panel from the scene
            val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
            val miniMapNode = composition.tryGetNodeByName("MiniMapPanel")
            if (miniMapNode?.entity != null) {
                miniMapEntity = miniMapNode.entity

                // Position in bottom right (offset for bottom-right positioning)
                val bottomRightOffset = Vector3(-1.8f, -1.8f, 3f)

                // Create grabbable panel with bottom-right positioning
                grabbablePanel = GrabbablePanel(
                    systemManager,
                    miniMapEntity!!,
                    bottomRightOffset
                )

                initialized = true
                Log.i("MiniMapPanel", "Minimap panel initialized")
            } else {
                Log.w("MiniMapPanel", "MiniMapPanel node not found in scene")
            }

        } catch (e: Exception) {
            Log.e("MiniMapPanel", "Failed to initialize minimap panel", e)
        }
    }

    private fun updateMiniMapPosition() {
        if (!initialized || miniMapEntity == null) return

        // Make sure the minimap is visible
        miniMapEntity?.setComponent(Visible(true))
    }

    fun setViews(minimapImage: ImageView?, redDot: View?, blueDot: View?, greenDot: View?) {
        this.minimapImage = minimapImage
        this.redDot = redDot
        this.blueDot = blueDot
        this.greenDot = greenDot

        // Load the current minimap image
        loadCurrentMinimap()
    }

    /**
     * Load the currently configured minimap image
     */
    private fun loadCurrentMinimap() {
        minimapImage?.let { imageView ->
            imageView.setImageResource(VROptions.minimap.drawableResource())
            Log.i("MiniMapPanel", "Loaded minimap: ${VROptions.minimap.drawableResource()}")
        }
    }

    /**
     * Update minimap based on current GPS location (auto-select best minimap)
     */
    fun autoSelectMinimap() {
        val loc = Services.location.lastLoc
        if (loc != null) {
            val currentMinimap = VROptions.minimap
            VROptions.autoSelectMinimap(loc.latitude, loc.longitude)

            // If minimap changed, reload the image
            if (VROptions.minimap != currentMinimap) {
                loadCurrentMinimap()
                Log.i("MiniMapPanel", "Auto-switched minimap to: ${VROptions.minimap.drawableResource()}")
            }
        }
    }

    /**
     * Manually switch to a specific minimap
     */
    fun switchMinimap(newMinimap: MiniMapOptions) {
        VROptions.setMinimap(newMinimap)
        loadCurrentMinimap()
        Log.i("MiniMapPanel", "Manually switched minimap to: ${newMinimap.drawableResource()}")
    }

    private fun translateLatLngToMinimapPixels(lat: Double, lng: Double): Pair<Int, Int>? {
        minimapImage?.let { image ->
            val imageWidth = image.width
            val imageHeight = image.height

            if (imageWidth > 0 && imageHeight > 0) {
                val latMin = VROptions.minimap.latMin()
                val latMax = VROptions.minimap.latMax()
                val lngMin = VROptions.minimap.lngMin()
                val lngMax = VROptions.minimap.lngMax()

                // Calculate position relative to minimap bounds
                val latNormalized = (lat - latMin) / (latMax - latMin)
                val lngNormalized = (lng - lngMin) / (lngMax - lngMin)

                // Clamp to [0, 1] range
                val clampedLat = latNormalized.coerceIn(0.0, 1.0)
                val clampedLng = lngNormalized.coerceIn(0.0, 1.0)

                val xPos = (clampedLng * imageWidth).toInt()
                val yPos = ((1.0 - clampedLat) * imageHeight).toInt() // Invert Y for screen coordinates

                return Pair(xPos, yPos)
            }
        }
        return null
    }

    private fun updateRedDotPosition() {
        val loc = Services.location.lastLoc
        val millisecondsSinceLastFix = Services.location.lastFixDuration()

        // Hide red dot if GPS data is stale (3+ seconds)
        if (millisecondsSinceLastFix >= 3000 || loc == null) {
            redDot?.visibility = View.INVISIBLE
            return
        }

        redDot?.visibility = View.VISIBLE

        val pixelPos = translateLatLngToMinimapPixels(loc.latitude, loc.longitude)
        pixelPos?.let { (xPos, yPos) ->
            redDot?.let { dot ->
                // Update layout params to position the red arrow
                val layoutParams = dot.layoutParams as FrameLayout.LayoutParams
                layoutParams.leftMargin = xPos - (dot.width / 2)
                layoutParams.topMargin = yPos - (dot.height / 2)
                layoutParams.gravity = 0 // Remove center gravity
                dot.layoutParams = layoutParams

                // Calculate and apply rotation based on velocity direction
                // Only rotate if we have significant velocity (> 0.5 m/s to avoid jitter)
                val groundSpeed = sqrt(loc.vN * loc.vN + loc.vE * loc.vE)
                if (groundSpeed > 0.5) {
                    // Calculate bearing in degrees (0° = North, 90° = East)
                    val bearingDegrees = Math.toDegrees(atan2(loc.vE, loc.vN))
                    dot.rotation = bearingDegrees.toFloat()
                } else {
                    // If not moving significantly, keep arrow pointing north
                    dot.rotation = 0f
                }
            }
        }
    }

    private fun updateBlueDotPosition() {
        val destination = VROptions.current.getDestination()

        // Hide blue dot if no destination is set
        if (destination == null) {
            blueDot?.visibility = View.INVISIBLE
            return
        }

        blueDot?.visibility = View.VISIBLE

        val pixelPos = translateLatLngToMinimapPixels(destination.lat, destination.lng)
        pixelPos?.let { (xPos, yPos) ->
            blueDot?.let { dot ->
                // Update layout params to position the blue dot
                val layoutParams = dot.layoutParams as FrameLayout.LayoutParams
                layoutParams.leftMargin = xPos - (dot.width / 2)
                layoutParams.topMargin = yPos - (dot.height / 2)
                layoutParams.gravity = 0 // Remove center gravity
                dot.layoutParams = layoutParams
            }
        }
    }

    private fun updateGreenDotPosition() {
        val target = VROptions.dropzone

        greenDot?.visibility = View.VISIBLE

        val pixelPos = translateLatLngToMinimapPixels(target.lat, target.lng)
        pixelPos?.let { (xPos, yPos) ->
            greenDot?.let { dot ->
                // Update layout params to position the green dot
                val layoutParams = dot.layoutParams as FrameLayout.LayoutParams
                layoutParams.leftMargin = xPos - (dot.width / 2)
                layoutParams.topMargin = yPos - (dot.height / 2)
                layoutParams.gravity = 0 // Remove center gravity
                dot.layoutParams = layoutParams
            }
        }
    }

    fun cleanup() {
        initialized = false
        miniMapEntity = null
        grabbablePanel = null
        redDot = null
        blueDot = null
        greenDot = null
        minimapImage = null
        lastAutoSelectTime = 0L
    }
}
