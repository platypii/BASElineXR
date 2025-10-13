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
// import com.platypii.baselinexr.measurements.MLocation // Unused
import com.platypii.baselinexr.util.HeadPoseUtil
import com.platypii.baselinexr.jarvis.FlightMode
import com.platypii.baselinexr.location.MotionEstimator
import com.platypii.baselinexr.location.KalmanFilter3D
import com.platypii.baselinexr.location.PolarLibrary
import com.platypii.baselinexr.location.SimpleEstimator
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

        // Update driven by 90Hz GpsToWorldTransform predictDelta() calls
        updateModelOrientationAndVisibility()
    }

    private fun initializeModels() {
        // Initial model alignment rotation (to fix model orientation)
        val initialWingsuitRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat(), (-Math.PI/2).toFloat()+Adjustments.yawAdjustment)
        val initialCanopyRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat(), (-Math.PI/2).toFloat()+Adjustments.yawAdjustment)

        // Create wingsuit entity using tsimwingsuit.glb model with initial rotation
        wingsuitEntity = Entity.create(
            Mesh("tsimwingsuit.glb".toUri()),
            Transform(Pose(Vector3(0f), initialWingsuitRotation)),
            Scale(Vector3(WINGSUIT_SCALE)),
            Visible(false) // Start invisible
        )

        // Create canopy entity using cp1.glb model with initial rotation
        canopyEntity = Entity.create(
            Mesh("cp1.glb".toUri()),
            Transform(Pose(Vector3(0f), initialCanopyRotation)),
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

        // Use cached predicted state from 90Hz GpsToWorldTransform updates with interpolated wingsuit parameters
        val (velocity, rollRad) = when (motionEstimator) {
            is KalmanFilter3D -> {
                // Get cached predicted state from last predictDelta() call with 90Hz interpolated roll
                val predictedState = motionEstimator.getCachedPredictedState(System.currentTimeMillis())
                Pair(predictedState.velocity, predictedState.roll.toFloat())
            }
            is SimpleEstimator -> {
                Pair(motionEstimator.v, 0f)
            }
            else -> {
                // Fallback to GPS velocity if not a known estimator type
                val lastUpdate = motionEstimator.getLastUpdate()
                if (lastUpdate != null) {
                    Pair(com.platypii.baselinexr.util.tensor.Vector3(lastUpdate.vE, lastUpdate.climb, lastUpdate.vN), 0f)
                } else {
                    return // No data available
                }
            }
        }

        // Calculate pitch (angle from horizontal)
        val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z).toFloat()
        val pitchRad = atan2(-velocity.y.toFloat(), horizontalSpeed) // Negative because up is positive

        // Calculate flight yaw for +Z forward coordinate system
        // For +Z forward: yaw = atan2(vZ, vX) where vZ=North, vX=East
        val flightYaw = -atan2(velocity.z.toFloat(), velocity.x.toFloat())

        // Calculate AOA from measured KL/KD coefficients using polar data
        val aoaDeg = when (motionEstimator) {
            is KalmanFilter3D -> {
                val predictedState = motionEstimator.getCachedPredictedState(System.currentTimeMillis())
                val lastUpdate = motionEstimator.getLastUpdate()
                if (lastUpdate != null) {
                    PolarLibrary.convertKlKdToAOA(predictedState.kl, predictedState.kd, lastUpdate.altitude_gps,
                        PolarLibrary.AURA_FIVE_POLAR)
                } else {
                    10.0 // Default AOA if no GPS data
                }
            }
            else -> 10.0 // Default AOA for other estimators
        }
        val aoa = aoaDeg * Math.PI / 180 // Convert to radians

        // Create two separate rotations and combine them
        // 1. Model  offset
        val modelRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat()+aoa.toFloat(), (Math.PI).toFloat())

        // 2. Flight attitude
        val attitudeRotation = createQuaternionFromEuler(rollRad, pitchRad, (-flightYaw -Math.PI/2 -  Adjustments.yawAdjustment   ).toFloat())
        // val controlRotation = createQuaternionFromEuler(0f, aoa.toFloat(), 0f)
        // Combine rotations: first apply offset, then attitude

        // val wsRotation = multiplyQuaternions(attitudeRotation, controlRotation)

        val rotation = multiplyQuaternions(modelRotation, attitudeRotation)

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

        Log.d(TAG, "Wingsuit: vE=${velocity.x.toInt()}, vN=${velocity.z.toInt()}, climb=${velocity.y.toInt()}, pitch=${Math.toDegrees(pitchRad.toDouble()).toInt()}°, heading=${Math.toDegrees(flightYaw.toDouble()).toInt()}°, roll=${Math.toDegrees(rollRad.toDouble()).toInt()}°, AOA=${aoaDeg.toInt()}° | Model forward=+Z, left=+X")
    }

    private fun updateCanopyOrientation(motionEstimator: MotionEstimator, position: Vector3, hasNewGpsData: Boolean) {
        val canopyEntity = this.canopyEntity ?: return
        // Use cached predicted state from 90Hz GpsToWorldTransform updates with interpolated wingsuit parameters
        val (velocity, rollRad) = when (motionEstimator) {
            is KalmanFilter3D -> {
                // Get cached predicted state from last predictDelta() call with 90Hz interpolated roll
                val predictedState = motionEstimator.getCachedPredictedState(System.currentTimeMillis())
                Pair(predictedState.velocity, predictedState.roll.toFloat())
            }
            is SimpleEstimator -> {
                Pair(motionEstimator.v, 0f)
            }
            else -> {
                // Fallback to GPS velocity if not a known estimator type
                val lastUpdate = motionEstimator.getLastUpdate()
                if (lastUpdate != null) {
                    Pair(com.platypii.baselinexr.util.tensor.Vector3(lastUpdate.vE, lastUpdate.climb, lastUpdate.vN), 0f)
                } else {
                    return // No data available
                }
            }
        }

        // Calculate pitch (angle from horizontal)
        val horizontalSpeed = sqrt(velocity.x * velocity.x + velocity.z * velocity.z).toFloat()
        val pitchRad = atan2(-velocity.y.toFloat(), horizontalSpeed) // Negative because up is positive

        // Calculate flight yaw for +Z forward coordinate system
        // For +Z forward: yaw = atan2(vZ, vX) where vZ=North, vX=East
        val flightYaw = -atan2(velocity.z.toFloat(), velocity.x.toFloat())

        val aoa = 10*Math.PI/180
        // Create two separate rotations and combine them
        // 1. Model  offset
        val modelRotation = createQuaternionFromEuler(0f, (Math.PI).toFloat()+aoa.toFloat(), (Math.PI).toFloat())

        // 2. Flight attitude
        val attitudeRotation = createQuaternionFromEuler(rollRad, pitchRad, (-flightYaw -Math.PI/2 -  Adjustments.yawAdjustment   ).toFloat())
        // val controlRotation = createQuaternionFromEuler(0f, aoa.toFloat(), 0f)
        // Combine rotations: first apply offset, then attitude

        // val wsRotation = multiplyQuaternions(attitudeRotation, controlRotation)

        val rotation = multiplyQuaternions(modelRotation, attitudeRotation)

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

        //Log.d(TAG, "Canopy: vE=${velocity.x}, vN=${velocity.z}, climb=${velocity.y}, pitch=${Math.toDegrees(pitchRad.toDouble())}°, yaw=${Math.toDegrees(yawRad)}°, roll=${Math.toDegrees(rollRad.toDouble())}°")
    }

    /**
     * Create a quaternion from Euler angles (roll, pitch, yaw) in radians
     * Meta Spatial uses Y-up coordinate system
     * Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
     */
    private fun createQuaternionFromEuler(roll: Float, pitch: Float, yaw: Float): Quaternion {
        // Convert to half angles
        val halfYaw = yaw * 0.5f
        val halfPitch = pitch * 0.5f
        val halfRoll = roll * 0.5f

        val cy = cos(halfYaw)
        val sy = sin(halfYaw)
        val cp = cos(halfPitch)
        val sp = sin(halfPitch)
        val cr = cos(halfRoll)
        val sr = sin(halfRoll)


        // Quaternion multiplication: q_yaw * q_pitch * q_roll
        // Y-up, Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
        val qw = cy * cp * cr + sy * sp * sr
        val qx = cy * sp * cr + sy * cp * sr
        val qy = sy * cp * cr - cy * sp * sr
        val qz = cy * cp * sr - sy * sp * cr

        return Quaternion(qx, qy, qz, qw)
    }

    /**
     * Multiply two quaternions: q1 * q2
     * This applies q1 first, then q2
     */
    private fun multiplyQuaternions(q1: Quaternion, q2: Quaternion): Quaternion {
        val w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
        val x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y
        val y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x
        val z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w

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
