package com.platypii.baselinexr

import android.util.Log
import android.widget.TextView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.location.LatLng

class TargetPanel(private val gpsTransform: GpsToWorldTransform) : SystemBase() {
    private var initialized = false
    private var targetPanelEntity: Entity? = null
    private var distanceTextView: TextView? = null
    
    override fun execute() {
        val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
        if (!activity.glxfLoaded) return
        
        if (!initialized) {
            initializeTargetPanel(activity)
        }
        
        if (initialized) {
            updateTargetPanelPosition()
        }
    }
    
    private fun initializeTargetPanel(activity: BaselineActivity) {
        val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val targetPanel = composition.tryGetNodeByName("TargetPanel")
        if (targetPanel?.entity != null) {
            targetPanelEntity = targetPanel.entity
            initialized = true
            Log.i("TargetPanel", "Target panel initialized")
        } else {
            Log.w("TargetPanel", "TargetPanel node not found in scene")
        }
    }
    
    private fun updateTargetPanelPosition() {
        if (!initialized || targetPanelEntity == null) return

        // Check if target panel is enabled
        if (!VROptions.current.showTarget) {
            targetPanelEntity?.setComponent(Visible(false))
            return
        }

        val headPose = getHeadPose() ?: return
        if (headPose == Pose()) return
        if (gpsTransform.initialOrigin == null) return

        // Convert target GPS coordinates to world space
        val currentTime = System.currentTimeMillis()
        val dest = VROptions.target
        val targetWorldPos = gpsTransform.toWorldCoordinates(
            dest.lat, dest.lng, dest.alt, currentTime, Services.location.motionEstimator
        )
        
        // Calculate direction from camera to target
        val directionToTarget = if (VROptions.current.roomMovement)
                (targetWorldPos - headPose.t).normalize() else targetWorldPos.normalize()
        
        // Position panel 4 meters from camera in direction of target
        val panelDistance = 4.0f
        val panelPosition = headPose.t + (directionToTarget * panelDistance)
        
        // Create target pose
        val targetPose = Pose()
        targetPose.t = panelPosition
        
        // Make panel face the camera
        val toHead = headPose.t - panelPosition
        val forwardVec = toHead.normalize()
        val up = headPose.q * Vector3(0f, 1f, 0f)
        targetPose.q = Quaternion.lookRotation(forwardVec, up)
        // Calculate distance to target and update TextView
        updateDistanceDisplay()
        
        // Update panel transform and make it visible
        targetPanelEntity?.setComponents(listOf(
            Transform(targetPose),
            Visible(true)
        ))
    }

    fun setLabel(distanceLabel: TextView?) {
        this.distanceTextView = distanceLabel
        updateDistanceDisplay()
    }

    private fun updateDistanceDisplay() {
        val currentLocation = Services.location.lastLoc
        val millisecondsSinceLastFix = Services.location.lastFixDuration()

        // Hide distance text if GPS data is stale (3+ seconds)
        if (millisecondsSinceLastFix >= 3000) {
            distanceTextView?.text = ""
            return
        }

        if (currentLocation != null && distanceTextView != null) {
            try {
                val target = VROptions.target
                val targetLatLng = LatLng(target.lat, target.lng)
                
                // Calculate distance in meters
                val distanceMeters = currentLocation.distanceTo(targetLatLng)
                
                // Convert to miles (meters * 0.000621371192)
                val distanceMiles = distanceMeters * 0.000621371192
                
                // Format and update TextView
                val distanceText = String.format("%.1f mi", distanceMiles)
                distanceTextView?.text = distanceText
            } catch (e: Exception) {
                Log.w("TargetPanel", "Error calculating distance: ${e.message}")
                distanceTextView?.text = "-- mi"
            }
        } else {
            distanceTextView?.text = "-- mi"
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
        targetPanelEntity = null
        distanceTextView = null
    }
}
