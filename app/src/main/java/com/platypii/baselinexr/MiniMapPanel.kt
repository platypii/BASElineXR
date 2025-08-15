package com.platypii.baselinexr

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

class MiniMapPanel : SystemBase() {
    private var initialized = false
    private var miniMapEntity: Entity? = null
    private var grabbablePanel: GrabbablePanel? = null

    // Minimap content references
    private var redDot: View? = null
    private var minimapImage: ImageView? = null

    // Minimap bounds - should be set based on the actual minimap image coordinates
    val latMin = 47.214
    val latMax = 47.2637
    val lngMin = -123.2033
    val lngMax = -123.0856

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
            updateRedDotPosition()
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
                val bottomRightOffset = Vector3(-1.8f, -2.0f, 3f)

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

    fun setViews(minimapImage: ImageView?, redDot: View?) {
        this.minimapImage = minimapImage
        this.redDot = redDot
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

        // Calculate position relative to minimap bounds
        val latNormalized = (loc.latitude - latMin) / (latMax - latMin)
        val lngNormalized = (loc.longitude - lngMin) / (lngMax - lngMin)

        // Clamp to [0, 1] range
        val clampedLat = latNormalized.coerceIn(0.0, 1.0)
        val clampedLng = lngNormalized.coerceIn(0.0, 1.0)

        minimapImage?.let { image ->
            redDot?.let { dot ->
                // Calculate pixel position within the image
                val imageWidth = image.width
                val imageHeight = image.height

                if (imageWidth > 0 && imageHeight > 0) {
                    val xPos = (clampedLng * imageWidth).toInt()
                    val yPos = ((1.0 - clampedLat) * imageHeight).toInt() // Invert Y for screen coordinates

                    // Update layout params to position the red dot
                    val layoutParams = dot.layoutParams as FrameLayout.LayoutParams
                    layoutParams.leftMargin = xPos - (dot.width / 2)
                    layoutParams.topMargin = yPos - (dot.height / 2)
                    layoutParams.gravity = 0 // Remove center gravity
                    dot.layoutParams = layoutParams
                }
            }
        }
    }

    private fun getHeadPose(): Pose? {
        val head = systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.head ?: return null
        return head.tryGetComponent<Transform>()?.transform
    }

    fun cleanup() {
        initialized = false
        miniMapEntity = null
        grabbablePanel = null
        redDot = null
        minimapImage = null
    }
}
