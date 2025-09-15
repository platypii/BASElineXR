package com.platypii.baselinexr

import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.util.HeadPoseUtil
import com.platypii.baselinexr.jarvis.FlightMode
import com.platypii.baselinexr.location.MotionEstimator
import kotlin.math.*

class WingsuitCanopySystem : SystemBase() {

    companion object {
        private const val TAG = "WingsuitCanopySystem"
        private const val WINGSUIT_SCALE = 0.05f
        private const val CANOPY_SCALE = 0.08f
        private const val MODEL_HEIGHT_OFFSET = -3.0f
        private const val MIN_SPEED_THRESHOLD = 2.0 // m/s minimum speed to show models
        private const val ENLARGEMENT_FACTOR = 5f
    }

    private var wingsuitEntity: Entity? = null
    private var canopyEntity: Entity? = null
    private var initialized = false
    private var isEnlarged = false
    private var lastGpsTime: Long = 0 // Track when GPS data was last updated

    override fun execute() {
        if (!initialized) {
            initializeModels()
        }

        updateModelOrientationAndVisibility()
    }

    private fun initializeModels() {
        // Create wingsuit entity using tsimwingsuit.glb model
        wingsuitEntity = Entity.create(
            Mesh("tsimwingsuit.glb".toUri()),
            Transform(Pose(Vector3(0f))),
            Scale(Vector3(WINGSUIT_SCALE)),
            Visible(false) // Start invisible
        )

        // Create canopy entity using cp1.glb model
        canopyEntity = Entity.create(
            Mesh("cp1.glb".toUri()),
            Transform(Pose(Vector3(0f))),
            Scale(Vector3(CANOPY_SCALE)),
            Visible(false) // Start invisible
        )

        initialized = true
        Log.i(TAG, "WingsuitCanopySystem initialized")
    }

    private fun updateModelOrientationAndVisibility() {
        // Check if wingsuit/canopy models are enabled
        if (!VROptions.current.showWingsuitCanopy) {
            wingsuitEntity?.setComponent(Visible(false))
            canopyEntity?.setComponent(Visible(false))
            return
        }

        val motionEstimator = Services.location.motionEstimator
        val lastUpdate = motionEstimator.getLastUpdate()

        // Get head position to place models near it
        val headPose = HeadPoseUtil.getHeadPose(systemManager) ?: return
        if (headPose == Pose()) return

        // Position models below and in front of the head
        val modelPosition = headPose.t + Vector3(0f, MODEL_HEIGHT_OFFSET, 0f)

        // Check if we have fresh GPS data and location is valid
        if (lastUpdate == null || !Services.location.isFresh) {
            // Hide both models when no GPS data
            wingsuitEntity?.setComponent(Visible(false))
            canopyEntity?.setComponent(Visible(false))
            return
        }

        // Check if GPS data has been updated since last frame
        val hasNewGpsData = lastUpdate.millis != lastGpsTime
        if (hasNewGpsData) {
            lastGpsTime = lastUpdate.millis
        }

        // Check if moving fast enough
        if (lastUpdate.groundSpeed() < MIN_SPEED_THRESHOLD) {
            // Hide both models when not moving
            wingsuitEntity?.setComponent(Visible(false))
            canopyEntity?.setComponent(Visible(false))
            return
        }

        // Determine which model to show based on flight mode
        val flightMode = Services.flightComputer.flightMode
        val isCanopyMode = flightMode == FlightMode.MODE_CANOPY
        
        if (isCanopyMode) {
            // Show canopy, hide wingsuit
            wingsuitEntity?.setComponent(Visible(false))
            updateCanopyOrientation(motionEstimator, modelPosition, hasNewGpsData)
        } else {
            // Show wingsuit, hide canopy (for all other flight modes)
            canopyEntity?.setComponent(Visible(false))
            updateWingsuitOrientation(motionEstimator, modelPosition, hasNewGpsData)
        }
    }

    private fun updateWingsuitOrientation(motionEstimator: MotionEstimator, position: Vector3, hasNewGpsData: Boolean) {
        val wingsuitEntity = this.wingsuitEntity ?: return

        // Only update orientation when we have new GPS data
        if (!hasNewGpsData) {
            // Just update position, keep existing orientation
            wingsuitEntity.setComponent(Transform(Pose(position, wingsuitEntity.getComponent<Transform>().transform.q)))
            return
        }

        // Get velocity from motion estimator (ENU coordinates: East, North, Up)
        // Cast to SimpleEstimator to access velocity properties
        val velocity = when (motionEstimator) {
            is com.platypii.baselinexr.location.SimpleEstimator -> motionEstimator.v
            else -> {
                // Fallback to GPS velocity if not SimpleEstimator
                val lastUpdate = motionEstimator.getLastUpdate()
                com.platypii.baselinexr.util.tensor.Vector3(lastUpdate.vE, lastUpdate.climb, lastUpdate.vN)
            }
        }

        // Get acceleration from motion estimator for roll calculation
        val acceleration = when (motionEstimator) {
            is com.platypii.baselinexr.location.SimpleEstimator -> motionEstimator.a
            else -> com.platypii.baselinexr.util.tensor.Vector3() // Zero acceleration fallback
        }
        
        // Calculate pitch (angle from horizontal)
        val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z).toFloat()
        val pitchRad = atan2(-velocity.y.toFloat(), horizontalSpeed) // Negative because up is positive
        
        // Calculate yaw (heading direction) from velocity
        var yawRad = atan2(velocity.x.toFloat(), velocity.z.toFloat()) // East, North
        
        // Apply yaw adjustment to align with world coordinate system
        yawRad += Adjustments.yawAdjustment

        // Calculate roll from lateral acceleration
        // Roll angle is approximately atan2(lateral_accel, gravity)
        // For wingsuit flight, use the acceleration perpendicular to velocity direction
        var rollRad = 0f
        if (horizontalSpeed > 1.0f) { // Only calculate roll if moving significantly
            // Calculate lateral acceleration (perpendicular to velocity direction)
            val velocityDirection = Vector3(velocity.x.toFloat(), 0f, velocity.z.toFloat()).normalize()
            val lateralDirection = Vector3(-velocityDirection.z, 0f, velocityDirection.x) // Perpendicular to velocity
            val lateralAccel = acceleration.x.toFloat() * lateralDirection.x + acceleration.z.toFloat() * lateralDirection.z
            
            // Roll angle from lateral acceleration (assuming 9.8 m/s² gravity)
            val gravity = 9.8f
            rollRad = atan2(lateralAccel, gravity)
            
            // Limit roll angle to reasonable values for wingsuit flight
            rollRad = rollRad.coerceIn(-PI.toFloat() / 3, PI.toFloat() / 3) // Max ±60 degrees
        }
        
        // Create quaternion from Euler angles (roll, pitch, yaw)
        val rotation = createQuaternionFromEuler(rollRad, pitchRad, yawRad)

        // Use enlarged scale if enabled
        val scale = if (isEnlarged) WINGSUIT_SCALE * ENLARGEMENT_FACTOR else WINGSUIT_SCALE

        // Update wingsuit position and orientation
        wingsuitEntity.setComponents(
            listOf(
                Scale(Vector3(scale)),
                Transform(Pose(position, rotation)),
                Visible(true)
            )
        )

        Log.d(TAG, "Wingsuit: vE=${velocity.x.toInt()}, vN=${velocity.z.toInt()}, climb=${velocity.y.toInt()}, pitch=${Math.toDegrees(pitchRad.toDouble()).toInt()}°, yaw=${Math.toDegrees(yawRad.toDouble()).toInt()}°, roll=${Math.toDegrees(rollRad.toDouble()).toInt()}°")
    }

    private fun updateCanopyOrientation(motionEstimator: MotionEstimator, position: Vector3, hasNewGpsData: Boolean) {
        val canopyEntity = this.canopyEntity ?: return

        // Only update orientation when we have new GPS data
        if (!hasNewGpsData) {
            // Just update position, keep existing orientation
            canopyEntity.setComponent(Transform(Pose(position, canopyEntity.getComponent<Transform>().transform.q)))
            return
        }

        // Get velocity from motion estimator (ENU coordinates: East, North, Up)
        val velocity = when (motionEstimator) {
            is com.platypii.baselinexr.location.SimpleEstimator -> motionEstimator.v
            else -> {
                // Fallback to GPS velocity if not SimpleEstimator
                val lastUpdate = motionEstimator.getLastUpdate()
                com.platypii.baselinexr.util.tensor.Vector3(lastUpdate.vE, lastUpdate.climb, lastUpdate.vN)
            }
        }

        // Get acceleration from motion estimator for roll calculation
        val acceleration = when (motionEstimator) {
            is com.platypii.baselinexr.location.SimpleEstimator -> motionEstimator.a
            else -> com.platypii.baselinexr.util.tensor.Vector3() // Zero acceleration fallback
        }
        
        // Canopy orientation is primarily based on wind direction and descent
        // Calculate gentle pitch for canopy (less steep than wingsuit)
        val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z).toFloat()
        val pitchRad = atan2(-velocity.y.toFloat(), horizontalSpeed) * 0.3f // Reduce pitch angle for canopy
        
        // Calculate yaw based on movement direction
        var yawRad = atan2(velocity.x.toFloat(), velocity.z.toFloat()) // East, North
        yawRad += Adjustments.yawAdjustment

        // Calculate roll from lateral acceleration (less aggressive than wingsuit)
        var rollRad = 0f
        if (horizontalSpeed > 1.0f) { // Only calculate roll if moving significantly
            // Calculate lateral acceleration (perpendicular to velocity direction)
            val velocityDirection = Vector3(velocity.x.toFloat(), 0f, velocity.z.toFloat()).normalize()
            val lateralDirection = Vector3(-velocityDirection.z, 0f, velocityDirection.x) // Perpendicular to velocity
            val lateralAccel = acceleration.x.toFloat() * lateralDirection.x + acceleration.z.toFloat() * lateralDirection.z
            
            // Roll angle from lateral acceleration (reduced for canopy)
            val gravity = 9.8f
            rollRad = atan2(lateralAccel, gravity) * 0.5f // Reduce roll for canopy
            
            // Limit roll angle to smaller values for canopy
            rollRad = rollRad.coerceIn(-PI.toFloat() / 6, PI.toFloat() / 6) // Max ±30 degrees
        }
        
        // Create quaternion from Euler angles
        val rotation = createQuaternionFromEuler(rollRad, pitchRad, yawRad)

        // Use enlarged scale if enabled
        val scale = if (isEnlarged) CANOPY_SCALE * ENLARGEMENT_FACTOR else CANOPY_SCALE

        // Update canopy position and orientation
        canopyEntity.setComponents(
            listOf(
                Scale(Vector3(scale)),
                Transform(Pose(position, rotation)),
                Visible(true)
            )
        )

        Log.d(TAG, "Canopy: vE=${velocity.x.toInt()}, vN=${velocity.z.toInt()}, climb=${velocity.y.toInt()}, pitch=${Math.toDegrees(pitchRad.toDouble()).toInt()}°, yaw=${Math.toDegrees(yawRad.toDouble()).toInt()}°, roll=${Math.toDegrees(rollRad.toDouble()).toInt()}°")
    }

    /**
     * Create a quaternion from Euler angles (roll, pitch, yaw) in radians
     * Order: ZYX (yaw, pitch, roll)
     */
    private fun createQuaternionFromEuler(roll: Float, pitch: Float, yaw: Float): Quaternion {
        val cy = cos(yaw * 0.5f)
        val sy = sin(yaw * 0.5f)
        val cp = cos(pitch * 0.5f)
        val sp = sin(pitch * 0.5f)
        val cr = cos(roll * 0.5f)
        val sr = sin(roll * 0.5f)

        val w = cr * cp * cy + sr * sp * sy
        val x = sr * cp * cy - cr * sp * sy
        val y = cr * sp * cy + sr * cp * sy
        val z = cr * cp * sy - sr * sp * cy

        return Quaternion(x, y, z, w)
    }

    fun setEnlarged(enlarged: Boolean) {
        isEnlarged = enlarged
    }

    fun cleanup() {
        wingsuitEntity = null
        canopyEntity = null
        initialized = false
        Log.i(TAG, "WingsuitCanopySystem cleaned up")
    }
}
