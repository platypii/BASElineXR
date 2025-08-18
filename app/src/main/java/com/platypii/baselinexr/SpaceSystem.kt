package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.GLXFInfo
import com.meta.spatial.toolkit.GLXFManager
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.measurements.LatLngAlt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * SpaceSystem manages a space environment with a cubemap of stars that encloses the user.
 * When activated, it shows a large cube with space textures on the inside faces.
 */
class SpaceSystem(
    private val context: Context,
    private val gpsTransform: GpsToWorldTransform,
    private val glXFManager: GLXFManager
) : SystemBase() {

    companion object {
        private const val TAG = "SpaceSystem"
        private const val SPACE_CUBE_SIZE = 1000f // Large enough to enclose user

        // Trench rotation parameters (degrees)
        private const val TRENCH_ROLL = 0f
        private const val TRENCH_PITCH = -33f
        private const val TRENCH_YAW = 70f

        // Apply yaw rotation to X,Z coordinates
        private val trenchYawRadians = Math.toRadians(TRENCH_YAW.toDouble()).toFloat()
        private val cosYaw = cos(trenchYawRadians)
        private val sinYaw = sin(trenchYawRadians)
    }

    private var spaceCubeEntity: Entity? = null
    private var spaceSceneEntity: Entity? = null
    private var spaceComposition: GLXFInfo? = null
    private var isActive = false
    private var visible = false
    private val systemScope = CoroutineScope(Dispatchers.Main)

    /**
     * Creates the space environment with separate spaceCube and trench composition
     */
    fun createSpaceEnvironment(userPosition: Vector3 = Vector3(0f)): Job {
        if (isActive) {
            Log.w(TAG, "Space environment already active")
            return Job()
        }

        Log.i(TAG, "Creating space environment with spaceCube and trench composition")

        // Create the space cube that follows the user
        spaceCubeEntity = Entity.create(
            Mesh("space.glb".toUri(), hittable = MeshCollision.NoCollision),
            Transform(Pose(userPosition)),
            Scale(Vector3(SPACE_CUBE_SIZE, SPACE_CUBE_SIZE, SPACE_CUBE_SIZE)),
            Visible(false) // Start invisible until showSpace() is called
        )

        // Create the world-locked scene entity for trench models
        spaceSceneEntity = Entity.create(
            Transform(Pose(Vector3(0f))),
            Visible(false)
        )

        return systemScope.launch {
            try {
                glXFManager.inflateGLXF(
                    "apk:///scenes/Space.glxf".toUri(),
                    rootEntity = spaceSceneEntity!!,
                    onLoaded = { composition ->
                        spaceComposition = composition
                        isActive = true
                        Log.i(TAG, "Space scene loaded successfully")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Space scene: ${e.message}", e)
            }
        }
    }

    /**
     * Shows the space environment if it exists
     */
    fun showSpace() {
        visible = true
        val spaceCube = spaceCubeEntity
        val sceneEntity = spaceSceneEntity
        if (spaceCube != null && sceneEntity != null && isActive) {
            spaceCube.setComponent(Visible(true))
            updateCompositionVisibility(true)
            Log.i(TAG, "Space environment shown")
        } else {
            // Create space environment if it doesn't exist and show it
            val headPose = getHeadPose()
            val userPosition = headPose?.t ?: Vector3(0f, 0f, 0f)
            createSpaceEnvironment(userPosition)
            // Note: Visibility will be handled in updateSpacePosition after scene is loaded
            Log.i(TAG, "Space environment creation started, will show when loaded")
        }
    }

    /**
     * Hides the space environment
     */
    fun hideSpace() {
        visible = false
        spaceCubeEntity?.setComponent(Visible(false))
        updateCompositionVisibility(false)
        Log.i(TAG, "Space environment hidden")
    }

    /**
     * Updates space environment position relative to user (keeps it centered)
     */
    private fun updateSpacePosition() {
        if (!isActive) return

        val spaceCube = spaceCubeEntity ?: return
        val headPose = getHeadPose() ?: return

        // Keep space cube centered on user's head position
        val transform = Transform(Pose(headPose.t))
        spaceCube.setComponent(transform)

        // Set visibility based on visible flag
        spaceCube.setComponent(Visible(visible))
    }

    private fun updateTrenchPosition() {
        val terrainConfig = TerrainConfigLoader.loadConfig(context) ?: return
        val rootEntity = spaceSceneEntity ?: return

        try {
            // Calculate trench offset from terrain origin to portal location
            val terrainToPortal = GeoUtils.calculateOffset(terrainConfig.pointOfInterest, VROptions.portalLocation)

            // Apply offsets to destination to get trench position in user's reference frame
            val trenchLocation = GeoUtils.applyOffset(VROptions.current.destination, terrainToPortal)

            // Convert to world coordinates
            val currentTime = System.currentTimeMillis()
            val motionEstimator = Services.location.motionEstimator
            val trenchWorldPos = gpsTransform.toWorldCoordinates(
                trenchLocation.lat,
                trenchLocation.lng,
                trenchLocation.alt,
                currentTime,
                motionEstimator
            )

            // Apply terrain rotation (same as terrain system) 
            val yawDegrees = Math.toDegrees(Adjustments.yawAdjustment).toFloat()
            val totalYaw = yawDegrees + TRENCH_YAW
            val rotation = Quaternion(TRENCH_PITCH, totalYaw, TRENCH_ROLL)

            // Position the entire Space composition with world transform and yaw
            val transform = Transform(Pose(trenchWorldPos, rotation))
            rootEntity.setComponent(transform)
            
            // Set visibility on child entities in the composition, not just the root
            updateCompositionVisibility(visible)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating composition position: ${e.message}", e)
        }
    }

    /**
     * Updates visibility for all child entities in the GLXF composition
     * This is necessary because setting Visible on the root entity doesn't affect children
     */
    private fun updateCompositionVisibility(isVisible: Boolean) {
        val composition = spaceComposition ?: return
        
        try {
            // Get all nodes in the composition and update their visibility
            composition.nodes.forEach { node ->
                node.entity.setComponent(Visible(isVisible))
            }
            Log.d(TAG, "Updated composition visibility: $isVisible for ${composition.nodes.size} nodes")
        } catch (e: Exception) {
            Log.w(TAG, "Error updating composition visibility: ${e.message}", e)
        }
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
            spaceSceneEntity?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying space entities: ${e.message}")
        }

        spaceCubeEntity = null
        spaceSceneEntity = null
        spaceComposition = null
        isActive = false
        visible = false
    }

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
        spaceComposition = null
        Log.i(TAG, "SpaceSystem cleaned up")
    }
}
