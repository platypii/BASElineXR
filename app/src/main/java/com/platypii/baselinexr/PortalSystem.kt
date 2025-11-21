package com.platypii.baselinexr

import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.platypii.baselinexr.measurements.LatLngAlt
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

/**
 * Portal system that detects when the user flies through a portal and transports them to space.
 * The portal is a ring-shaped 3D object placed in the world that the user can fly through.
 */
class PortalSystem(
    private val gpsTransform: GpsToWorldTransform,
    private val activity: BaselineActivity
) : SystemBase() {

    companion object {
        private const val TAG = "PortalSystem"
        private const val PORTAL_SCALE = 2.0f
        private const val TRIGGER_RADIUS = 4.0f // Radius for collision detection
        private const val PRELOAD_RADIUS = 100.0f // Distance to preload space environment
        private const val SPACE_DURATION_MS = 4200L // Duration to stay in space (milliseconds)

        // Portal orientation
        private const val PORTAL_ORIENTATION_YAW = 75.0 // degrees yaw
    }

    private var portalEntity: Entity? = null
    private var isInSpace = false
    private var lastHeadPosition: Vector3? = null
    private var initialized = false
    private val spaceSystem = SpaceSystem(gpsTransform, activity)
    private var spaceStartTime: Long = 0
    private var spaceEnvironmentPreloaded = false

    override fun execute() {
        if (!initialized) {
            initializePortal()
            // Register the space system with the system manager
            systemManager.registerSystem(spaceSystem)
        }

        updatePortalPosition()
        checkPortalCollision()
        preloadSpaceEnvironmentIfNear()

        // Check if we need to return from space after specified duration
        if (isInSpace && System.currentTimeMillis() - spaceStartTime >= SPACE_DURATION_MS) {
            exitSpace()
            isInSpace = false
        }
    }

    private fun initializePortal() {
        // Create portal entity using portal.gltf model
        // The portal allows users to fly through and transition to space
        portalEntity = Entity.create(
            Mesh("portal.gltf".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f))),
            Scale(Vector3(PORTAL_SCALE, PORTAL_SCALE, PORTAL_SCALE)),
            Visible(false)
        )

        initialized = true
        Log.i(TAG, "Portal system initialized")
    }

    private fun updatePortalPosition() {
        val portalEntity = this.portalEntity ?: return

        // Check if portal location is defined (show portal if and only if location is defined)
        if (VROptions.current.portalLocation == null) {
            portalEntity.setComponent(Visible(false))
            return
        }

        // Get current GPS location for coordinate transformation
        val currentLocation = Services.location.lastLoc
        if (currentLocation == null) {
            portalEntity.setComponent(Visible(false))
            return
        }

        val terrainConfig = TerrainConfigLoader.loadConfig(activity)
        if (terrainConfig == null) {
            return
        }

        // Calculate portal offset from terrain origin to point of interest, similar to TerrainSystem
        val terrainToPortal = GeoUtils.calculateOffset(terrainConfig.pointOfInterest, VROptions.current.portalLocation)

        // Apply offsets to destination to get portal position in user's reference frame
        val offsetDest = GeoUtils.applyOffset(VROptions.current.destination, terrainToPortal)
        val portalLocation = LatLngAlt(offsetDest.lat, offsetDest.lng, offsetDest.alt)

        // Convert to world coordinates
        val currentTime = System.currentTimeMillis()
        val motionEstimator = Services.location.motionEstimator
        val portalWorldPos = gpsTransform.toWorldCoordinates(
            portalLocation.lat,
            portalLocation.lng,
            portalLocation.alt,
            currentTime,
            motionEstimator,
            false
        )

        // Apply yaw adjustment and portal orientation to align with world coordinate system
        val yawDegrees = Adjustments.yawAdjustmentDegrees().toFloat() + PORTAL_ORIENTATION_YAW.toFloat()
        val transform = Transform(Pose(
            portalWorldPos,
            Quaternion(0f, yawDegrees, 0f)
        ))

        portalEntity.setComponents(listOf(
            transform,
            Visible(true)
        ))
    }

    private fun preloadSpaceEnvironmentIfNear() {
        if (spaceEnvironmentPreloaded || VROptions.current.portalLocation == null) {
            return
        }

        val portalEntity = this.portalEntity ?: return

        // Get portal position
        val portalTransform = portalEntity.getComponent<Transform>()
        val portalPosition = portalTransform.transform.t

        // Calculate distance between head and portal center
        val distance = portalPosition.length()

        // Preload space environment when within 100m
        if (distance <= PRELOAD_RADIUS) {
            Log.i(TAG, "Preloading space environment - distance: ${distance}m")
            spaceSystem.createSpaceEnvironment()
            spaceEnvironmentPreloaded = true
        }
    }

    private fun checkPortalCollision() {
        val portalEntity = this.portalEntity ?: return

        // Don't check collision if portal location is not defined
        if (VROptions.current.portalLocation == null) {
            return
        }

        // Get portal position
        val portalTransform = portalEntity.getComponent<Transform>()
        val portalPosition = portalTransform.transform.t
        val headPosition = Vector3(0f) // Assume head is at origin

        // Calculate distance between head and portal center
        val distance = (headPosition - portalPosition).length()

        // Check if user is within trigger radius
        if (distance <= TRIGGER_RADIUS) {
            // Check if user actually passed through the portal (not just near it)
            if (!isInSpace && gpsTransform.initialOrigin != null) {
                triggerPortalTransition()
            }
        }

        lastHeadPosition = headPosition
    }

    private fun triggerPortalTransition() {
        Log.i(TAG, "Portal triggered! Transitioning to space...")

        if (!isInSpace) {
            // Transition TO space
            enterSpace()
            isInSpace = true
            spaceStartTime = System.currentTimeMillis()
        }
        // Note: We don't handle manual exit anymore, the 3-second timer will handle it
    }

    private fun enterSpace() {
        // Hide the terrain system
        activity.terrainSystem?.let { terrainSystem ->
            // Set all terrain tiles to invisible
            terrainSystem.setVisible(false)
        }

        // Show the space system cubemap (already preloaded when within 100m)
        // This will also handle setting the space lighting environment
        spaceSystem.showSpace()

        Log.i(TAG, "Entered space environment")
    }

    private fun exitSpace() {
        // Hide the space system
        // This will also handle restoring the normal lighting environment
        spaceSystem.hideSpace()

        // Show the terrain system again
//        activity.terrainSystem?.let { terrainSystem ->
//            // Set all terrain tiles back to visible
//            terrainSystem.setVisible(true)
//        }

        Log.i(TAG, "Exited space, returned to normal environment")
    }


    fun cleanup() {
        // Clean up space system
        spaceSystem.cleanup()

//        portalEntity?.destroy()
        portalEntity = null
        initialized = false
        isInSpace = false
        lastHeadPosition = null
        spaceEnvironmentPreloaded = false
        Log.i(TAG, "Portal system cleaned up")
    }
}