package com.platypii.baselinexr

import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.GLXFInfo
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * SpaceSystem manages a space environment with a cubemap of stars that encloses the user.
 * When activated, it shows a large cube with space textures on the inside faces.
 */
class SpaceSystem(
    private val gpsTransform: GpsToWorldTransform,
    private val activity: BaselineActivity
) : SystemBase() {

    companion object {
        private const val TAG = "SpaceSystem"
        private const val SPACE_CUBE_SIZE = 1000f // Large enough to enclose user

        // Trench rotation parameters (degrees)
        private const val TRENCH_ROLL = 0f
        private const val TRENCH_PITCH = -33f
        private const val TRENCH_YAW = 69f
        private const val TRENCH_HEIGHT_OFFSET = 0.0

        // Space lighting constants
        val SPACE_AMBIENT_COLOR = Vector3(0.8f)
        val SPACE_SUN_COLOR = Vector3(1.2f, 1.2f, 1.8f)
        val SPACE_SUN_DIRECTION = Vector3(1f, -2f, -1f).normalize()
        const val SPACE_ENVIRONMENT_INTENSITY = 0.01f
    }

    private var spaceCubeEntity: Entity? = null
    private var spaceSceneEntity: Entity? = null
    private var spaceComposition: GLXFInfo? = null
    private var isActive = false
    private var visible = false
    private val systemScope = CoroutineScope(Dispatchers.Main)

    // Movement tracking
    private var movementStartTime: Long = 0
    private var isMoving = false
    private var fighterEntity: Entity? = null
    private var lasersEntity: Entity? = null
    private val fighterInitialPosition = Vector3(0f, 0f, -200f)

    // Movement speeds (m/s)
    private val fighterSpeed = 50f
    private val lasersSpeed = 400f

    // Laser firing cycle (2 seconds)
    private val laserFireCycleTime = 700L // milliseconds
    private var lastLaserFireTime: Long = 0

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
                activity.glXFManager.inflateGLXF(
                    "apk:///scenes/Space.glxf".toUri(),
                    rootEntity = spaceSceneEntity!!,
                    onLoaded = { composition ->
                        spaceComposition = composition
                        isActive = true
                        findChildEntities(composition)
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

        // Set space lighting environment
        activity.scene.setLightingEnvironment(
            ambientColor = SPACE_AMBIENT_COLOR,
            sunColor = SPACE_SUN_COLOR,
            sunDirection = SPACE_SUN_DIRECTION,
            environmentIntensity = SPACE_ENVIRONMENT_INTENSITY
        )

        val spaceCube = spaceCubeEntity
        val sceneEntity = spaceSceneEntity
        if (spaceCube != null && sceneEntity != null && isActive) {
            spaceCube.setComponent(Visible(true))
            updateCompositionVisibility(true)
            startMovement()
            Log.i(TAG, "Space environment shown with movement started")
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
        stopMovement()
        spaceCubeEntity?.setComponent(Visible(false))
        updateCompositionVisibility(false)

        // Restore normal lighting environment
        activity.scene.setLightingEnvironment(
            ambientColor = VROptions.AMBIENT_COLOR,
            sunColor = VROptions.SUN_COLOR,
            sunDirection = VROptions.SUN_DIRECTION,
            environmentIntensity = VROptions.ENVIRONMENT_INTENSITY
        )
        activity.scene.updateIBLEnvironment("environment.env")

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
        val terrainConfig = TerrainConfigLoader.loadConfig(activity) ?: return
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
                trenchLocation.alt + TRENCH_HEIGHT_OFFSET,
                currentTime,
                motionEstimator
            )

            // Apply terrain rotation (same as terrain system) 
            val yawDegrees = Adjustments.yawAdjustmentDegrees().toFloat()
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
            updateMovement()
        }
    }

    /**
     * Finds and stores references to fighter and lasers entities from the composition
     */
    private fun findChildEntities(composition: GLXFInfo) {
        try {
            fighterEntity = composition.tryGetNodeByName("fighter")?.entity
            lasersEntity = composition.tryGetNodeByName("lasers")?.entity

            if (fighterEntity != null) {
                Log.d(TAG, "Found fighter entity")
            }
            if (lasersEntity != null) {
                Log.d(TAG, "Found lasers entity")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding child entities: ${e.message}", e)
        }
    }

    /**
     * Starts movement for the fighter and lasers
     */
    private fun startMovement() {
        if (!isMoving) {
            movementStartTime = System.currentTimeMillis()
            lastLaserFireTime = movementStartTime
            isMoving = true
            Log.i(TAG, "Movement started")
        }
    }

    /**
     * Stops movement and resets positions
     */
    private fun stopMovement() {
        if (isMoving) {
            isMoving = false
            resetChildPositions()
            Log.i(TAG, "Movement stopped")
        }
    }

    /**
     * Updates positions of moving entities each frame
     */
    private fun updateMovement() {
        if (!isMoving) return

        val currentTime = System.currentTimeMillis()
        val elapsedSeconds = (currentTime - movementStartTime) / 1000f

        // Update fighter position (moving along Z-axis)
        val fighterCurrentZ = fighterInitialPosition.z + (fighterSpeed * elapsedSeconds)
        fighterEntity?.let { entity ->
            try {
                val newPosition = Vector3(fighterInitialPosition.x, fighterInitialPosition.y, fighterCurrentZ)
                val currentTransform = entity.tryGetComponent<Transform>()
                val currentPose = currentTransform?.transform ?: Pose(fighterInitialPosition)
                val newPose = Pose(newPosition, currentPose.q)
                entity.setComponent(Transform(newPose))
            } catch (e: Exception) {
                Log.w(TAG, "Error updating fighter position: ${e.message}")
            }
        }

        // Update lasers position with firing cycle
        lasersEntity?.let { entity ->
            try {
                // Check if 2 seconds have passed since last fire
                if (currentTime - lastLaserFireTime >= laserFireCycleTime) {
                    // Reset lasers to fighter position and restart fire cycle
                    lastLaserFireTime = currentTime
                    Log.d(TAG, "Lasers firing cycle reset")
                }

                // Calculate time since last fire for laser movement
                val timeSinceLastFire = (currentTime - lastLaserFireTime) / 1000f
                val laserZ = fighterCurrentZ + 32f + (lasersSpeed * timeSinceLastFire) // in front

                val newPosition = Vector3(fighterInitialPosition.x, fighterInitialPosition.y + 1.5f, laserZ) // shift up a bit
                val currentTransform = entity.tryGetComponent<Transform>()
                val currentPose = currentTransform?.transform ?: Pose(newPosition)
                val newPose = Pose(newPosition, currentPose.q)
                entity.setComponent(Transform(newPose))
            } catch (e: Exception) {
                Log.w(TAG, "Error updating lasers position: ${e.message}")
            }
        }
    }

    /**
     * Resets child entities to their initial positions
     */
    private fun resetChildPositions() {
        fighterEntity?.let { entity ->
            try {
                val currentTransform = entity.tryGetComponent<Transform>()
                val currentPose = currentTransform?.transform ?: Pose(fighterInitialPosition)
                val resetPose = Pose(fighterInitialPosition, currentPose.q)
                entity.setComponent(Transform(resetPose))
            } catch (e: Exception) {
                Log.w(TAG, "Error resetting fighter position: ${e.message}")
            }
        }

        lasersEntity?.let { entity ->
            try {
                val currentTransform = entity.tryGetComponent<Transform>()
                val currentPose = currentTransform?.transform ?: Pose(fighterInitialPosition)
                val resetPose = Pose(fighterInitialPosition, currentPose.q)
                entity.setComponent(Transform(resetPose))
            } catch (e: Exception) {
                Log.w(TAG, "Error resetting lasers position: ${e.message}")
            }
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
        fighterEntity = null
        lasersEntity = null
        isActive = false
        visible = false
        isMoving = false
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
