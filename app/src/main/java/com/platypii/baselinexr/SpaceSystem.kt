package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.measurements.LatLngAlt
import kotlin.math.cos
import kotlin.math.sin

/**
 * SpaceSystem manages a space environment with a cubemap of stars that encloses the user.
 * When activated, it shows a large cube with space textures on the inside faces.
 */
class SpaceSystem(
    private val context: Context,
    private val gpsTransform: GpsToWorldTransform
) : SystemBase() {

    companion object {
        private const val TAG = "SpaceSystem"
        private const val SPACE_CUBE_SIZE = 1000f // Large enough to enclose user

        // Trench rotation parameters (degrees)
        private const val TRENCH_ROLL = 0f
        private const val TRENCH_PITCH = 32f
        private const val TRENCH_YAW = 15f
        private val TRENCH_OFFSET = Vector3(20f, -61f, 100f)
    }

    private var spaceCubeEntity: Entity? = null
    private var trenchEntity: Entity? = null
    private var isActive = false

    /**
     * Creates the space environment with a cubemap around the user
     */
    fun createSpaceEnvironment(userPosition: Vector3 = Vector3(0f)) {
        if (isActive) {
            Log.w(TAG, "Space environment already active")
            return
        }

        Log.i(TAG, "Creating space cubemap environment")

        // Create a large cube centered on the user position
        // The cube should have inward-facing normals to show textures on the inside
        // Start invisible until showSpace() is called
        spaceCubeEntity = Entity.create(
            Mesh("space.glb".toUri(), hittable = MeshCollision.NoCollision),
            Transform(Pose(userPosition)),
            Scale(Vector3(SPACE_CUBE_SIZE, SPACE_CUBE_SIZE, SPACE_CUBE_SIZE)),
            Visible(false)
        )

        // Create trench model at portal location (world-locked)
        trenchEntity = Entity.create(
            Mesh("trench.glb".toUri(), hittable = MeshCollision.NoCollision),
            Transform(Pose(Vector3(0f))), // Will be updated in updateTrenchPosition
            Scale(Vector3(10f)),
            Visible(false)
        )

        isActive = true
        Log.i(TAG, "Space environment created")
    }

    /**
     * Shows the space environment if it exists
     */
    fun showSpace() {
        val entity = spaceCubeEntity
        if (entity != null) {
            entity.setComponent(Visible(true))
            trenchEntity?.setComponent(Visible(true))
            Log.i(TAG, "Space environment shown")
        } else {
            // Create space environment if it doesn't exist and show it
            val headPose = getHeadPose()
            val userPosition = headPose?.t ?: Vector3(0f, 0f, 0f)
            createSpaceEnvironment(userPosition)
            // After creation, make it visible
            spaceCubeEntity?.setComponent(Visible(true))
            trenchEntity?.setComponent(Visible(true))
            Log.i(TAG, "Space environment created and shown")
        }
    }

    /**
     * Hides the space environment
     */
    fun hideSpace() {
        spaceCubeEntity?.setComponent(Visible(false))
        trenchEntity?.setComponent(Visible(false))
        Log.i(TAG, "Space environment hidden")
    }

    /**
     * Updates space environment position relative to user (keeps it centered)
     */
    fun updateSpacePosition() {
        if (!isActive) return

        val entity = spaceCubeEntity ?: return
        val headPose = getHeadPose() ?: return

        // Keep space cube centered on user's head position
        val transform = Transform(Pose(headPose.t))
        entity.setComponent(transform)

        // Update trench position (world-locked at portal location)
        updateTrenchPosition()
    }

    private fun updateTrenchPosition() {
        val entity = trenchEntity ?: return

        // Get current GPS location for coordinate transformation
        val currentLocation = Services.location.lastLoc ?: return

        val terrainConfig = TerrainConfigLoader.loadConfig(context) ?: return

        // Calculate trench offset from terrain origin to portal location
        val terrainToTrench = GeoUtils.calculateOffset(terrainConfig.pointOfInterest, VROptions.portalLocation)

        // Apply offsets to destination to get trench position in user's reference frame
        val offsetDest = GeoUtils.applyOffset(VROptions.current.destination, terrainToTrench)
        val trenchLocation = LatLngAlt(offsetDest.lat, offsetDest.lng, offsetDest.alt)

        // Convert to world coordinates
        val currentTime = System.currentTimeMillis()
        val motionEstimator = Services.location.motionEstimator
        val trenchWorldPos = gpsTransform.toWorldCoordinates(
            trenchLocation.lat,
            trenchLocation.lng,
            trenchLocation.alt,
            currentTime,
            motionEstimator
        ).add(TRENCH_OFFSET)

        // Apply trench rotation (only roll for now)
        val rotation = Quaternion(TRENCH_PITCH, TRENCH_YAW, TRENCH_ROLL)

        val transform = Transform(Pose(trenchWorldPos, rotation))
        entity.setComponent(transform)
    }

    override fun execute() {
        // Update space cube position to follow user if active
        if (isActive) {
            updateSpacePosition()
            updateTrenchPosition()
        }
    }

    /**
     * Removes the space environment and cleans up
     */
    fun destroySpaceEnvironment() {
        if (!isActive) {
            return
        }

        Log.i(TAG, "Destroying space environment")

        try {
            spaceCubeEntity?.destroy()
            trenchEntity?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying space entities: ${e.message}")
        }

        spaceCubeEntity = null
        trenchEntity = null
        isActive = false
    }

    /**
     * Returns true if the space environment is currently active
     */
    fun isSpaceActive(): Boolean = isActive

    private fun getHeadPose(): Pose? {
        try {
            val head = systemManager
                .tryFindSystem<PlayerBodyAttachmentSystem>()
                ?.tryGetLocalPlayerAvatarBody()
                ?.head ?: return null
            return head.tryGetComponent<Transform>()?.transform
        } catch (e: Exception) {
            Log.e(TAG, "Error getting head pose", e)
            return null
        }
    }

    fun cleanup() {
        destroySpaceEnvironment()
        trenchEntity = null
        Log.i(TAG, "SpaceSystem cleaned up")
    }
}