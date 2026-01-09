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
import com.platypii.baselinexr.location.KalmanFilter3D
import com.platypii.baselinexr.location.PolarLibrary
import com.platypii.baselinexr.location.SimpleEstimator
import com.platypii.baselinexr.util.FlightAttitude
import com.platypii.baselinexr.util.HeadPoseUtil
import com.platypii.baselinexr.util.QuaternionUtil

/**
 * System to visualize HMD head rotation on the 3D wingsuit/canopy models.
 * 
 * This draws a combined head model (HMD + helmet + FlySight) at the model's head position.
 * The model position and rotation are provided by WingsuitCanopySystem via updateFromWingsuit().
 * 
 * Used for developing sensor fusion and calibration between HMD and GPS magnetometer.
 */
class HeadModelSystem : SystemBase() {

    companion object {
        private const val TAG = "HeadModelSystem"
        
        // Full head model scale (relative to model scale)
        private const val HEAD_SCALE = 0.10f
        
        // Wingsuit model scale (same as WingsuitCanopySystem)
        private const val WINGSUIT_SCALE = 0.5f
        
        // Head offset in model's local coordinate space (before rotation)
        // These values position the head on the wingsuit model's head
        // X: left/right, Y: up/down, Z: forward/back
        private const val HEAD_OFFSET_X = -0.7f //backwards
        private const val HEAD_OFFSET_Y = 0.0f   // up
        private const val HEAD_OFFSET_Z = -0.0f    // left
        
        // Model position offset (same as WingsuitCanopySystem)
        private const val MODEL_HEIGHT_OFFSET = -3.0f
    }

    // Single combined head entity (HMD + helmet + FlySight)
    private var headEntity: Entity? = null
    
    private var initialized = false
    private var enabled = true

    override fun execute() {
        if (!initialized) {
            initializeEntities()
        }
        
        if (enabled) {
            //updateHeadOrientation()
        }
    }

    private fun initializeEntities() {
        // Create combined head entity (HMD + helmet + FlySight all in one model)
        headEntity = Entity.create(
            Mesh("fullheadneck.gltf".toUri()),
            Transform(Pose(Vector3(0f), Quaternion())),
            Scale(Vector3(HEAD_SCALE)),
            Visible(false)
        )
        
        initialized = true
        Log.i(TAG, "HeadModelSystem initialized with fullheadneck.gltf attachment at neck")
    }

    /**
     * Independent head orientation update - calculates rotation from GPS/kalman data.
     * Uses clean GPS conventions: heading (North=0), pitch (negative=down), roll (positive=right).
     */
    private fun updateHeadOrientation() {
        val headEntity = this.headEntity ?: return
        
        if (!VROptions.current.showWingsuitCanopy) {
            headEntity.setComponent(Visible(false))
            return
        }
        
        // Get current time and motion estimator
        val currentTime = System.currentTimeMillis()
        val motionEstimator = Services.location.motionEstimator
        if (motionEstimator == null) {
            headEntity.setComponent(Visible(false))
            return
        }
        
        // Get velocity and roll from motion estimator
        val (velocity, rollRad) = when (motionEstimator) {
            is KalmanFilter3D -> {
                val predictedState = motionEstimator.getCachedPredictedState(currentTime)
                Pair(predictedState.velocity.plus(predictedState.windVelocity), predictedState.rollwind.toFloat())
            }
            is SimpleEstimator -> {
                Pair(motionEstimator.v, 0f)
            }
            else -> {
                val lastUpdate = motionEstimator.lastUpdate
                if (lastUpdate != null) {
                    Pair(com.platypii.baselinexr.util.tensor.Vector3(lastUpdate.vE, lastUpdate.climb, lastUpdate.vN), 0f)
                } else {
                    headEntity.setComponent(Visible(false))
                    return
                }
            }
        }
        
        // Extract velocity components in GPS conventions
        // velocity.x = East, velocity.y = Up, velocity.z = North
        val vE = velocity.x
        val vUp = velocity.y
        val vN = velocity.z
        
        // Calculate GPS heading (North=0, East=π/2)
        val headingRad = QuaternionUtil.headingFromVelocity(vE, vN)
        
        // Calculate pitch (negative = descending/nose down)
        val pitchRad = QuaternionUtil.pitchFromVelocity(vUp, vE, vN)
        
        // Calculate AOA from KL/KD coefficients
        val aoaDeg = when (motionEstimator) {
            is KalmanFilter3D -> {
                val predictedState = motionEstimator.getCachedPredictedState(currentTime)
                val lastUpdate = motionEstimator.lastUpdate
                if (lastUpdate != null) {
                    PolarLibrary.convertKlKdToAOA(predictedState.kl, predictedState.kd, lastUpdate.altitude_gps,
                        PolarLibrary.AURA_FIVE_POLAR)
                } else {
                    10.0
                }
            }
            else -> 10.0
        }
        val aoaRad = Math.toRadians(aoaDeg).toFloat()
        
        // Build rotation from GPS attitude
        // 1. Flight attitude from velocity (heading, pitch, roll)
        val attitudeRotation = QuaternionUtil.fromGpsAttitude(headingRad, pitchRad, rollRad)
        
        // 2. AOA rotation (composed on top of flight attitude)
        val aoaRotation = QuaternionUtil.fromAoa(aoaRad)
        
        // 3. Model offset if glTF model isn't +Z forward (may need adjustment)
        val modelOffset = QuaternionUtil.modelOffsetminus90()
        
        // Combine: model offset * AOA * attitude
        // AOA is applied in body frame (after attitude), model offset corrects glTF orientation
        val rotation = QuaternionUtil.multiply(aoaRotation, attitudeRotation)

        
        // Calculate position and scale (same as WingsuitCanopySystem)
        val scale = WINGSUIT_SCALE
        val position = Vector3(0f, MODEL_HEIGHT_OFFSET, 0f)
        
        // Calculate head offset in local coordinates
        val localHeadOffset = Vector3(
            HEAD_OFFSET_X * scale,
            HEAD_OFFSET_Y * scale,
            HEAD_OFFSET_Z * scale
        )
        
        // Rotate the local offset by the model's rotation to get world offset
        val worldHeadOffset = QuaternionUtil.rotateVector(localHeadOffset, rotation)
        
        // Final head position = model position + rotated offset
        val headPosition = position + worldHeadOffset
        
        // Get HMD rotation for the head
        val headPose = HeadPoseUtil.getHeadPose(systemManager)
        val hmdRotation = headPose?.q ?: rotation // Fall back to model rotation if no HMD
        
        // Update head entity
        headEntity.setComponents(
            Transform(Pose(headPosition, hmdRotation)),
            Scale(Vector3(HEAD_SCALE * scale)),
            Visible(true)
        )
    }
    
    /**
     * Update head model position and rotation from WingsuitCanopySystem.
     * Receives shared FlightAttitude data and builds head-specific rotation.
     * 
     * @param modelPosition The wingsuit model's world position
     * @param flightAttitude Shared flight data (heading, pitch, roll, AOA)
     * @param modelScale The wingsuit model's scale factor
     */
    fun updateFromWingsuit(modelPosition: Vector3, flightAttitude: FlightAttitude, modelScale: Float) {
        val headEntity = this.headEntity ?: return
        
        if (!enabled || !VROptions.current.showWingsuitCanopy) {
            headEntity.setComponent(Visible(false))
            return
        }
        
        // === HEAD-SPECIFIC ROTATION ===
        // Use shared flight data with head model adjustments
        // Head model uses same adjustments as wingsuit for positioning
        val attitudeRotation = QuaternionUtil.fromGpsAttitude(
            (flightAttitude.headingRad + flightAttitude.yawAdjustment),
            flightAttitude.pitchRad,
            flightAttitude.rollRad
        )
        val aoaRotation = QuaternionUtil.fromAoa(flightAttitude.aoaRad)
        val modelOffset = QuaternionUtil.modelOffset180()
        
        // Combine: model offset * AOA * attitude
        val rotation = QuaternionUtil.multiply(modelOffset,
            QuaternionUtil.multiply(aoaRotation, attitudeRotation))
        
        // Get HMD rotation for the head display (optional - fall back to calculated rotation)
        val headPose = HeadPoseUtil.getHeadPose(systemManager)
        val hmdRotation = if (headPose != null && headPose != Pose()) {
            headPose.q
        } else {
            rotation // Fall back to body rotation if no HMD
        }
        
        // Calculate head offset in local coordinates
        val localHeadOffset = Vector3(
            HEAD_OFFSET_X * modelScale,
            HEAD_OFFSET_Y * modelScale,
            HEAD_OFFSET_Z * modelScale
        )

        // Rotate the local offset by the model's rotation to get world offset
        val worldHeadOffset = QuaternionUtil.rotateVector(localHeadOffset, rotation)

        // Final head position = model position + rotated offset
        val headFinalPosition = modelPosition + worldHeadOffset
        
        // Update head entity
        headEntity.setComponents(
            Transform(Pose(headFinalPosition, hmdRotation)),
            Scale(Vector3(HEAD_SCALE * modelScale)),
            Visible(true)
        )
    }
    
    /**
     * Hide the head model (called when wingsuit is hidden)
     */
    fun hide() {
        headEntity?.setComponent(Visible(false))
    }

    /**
     * Enable or disable head visualization
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            headEntity?.setComponent(Visible(false))
        }
        Log.i(TAG, "Head visualization ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if head visualization is enabled
     */
    fun isEnabled(): Boolean = enabled

    fun cleanup() {
        headEntity = null
        initialized = false
        Log.i(TAG, "HeadModelSystem cleaned up")
    }
}
