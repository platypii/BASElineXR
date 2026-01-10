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
        
        // Head offset in model's local coordinate space (before rotation)
        // These values position the head on the wingsuit model's head
        // X: left/right, Y: up/down, Z: forward/back
        private const val HEAD_OFFSET_X = -0.7f //backwards
        private const val HEAD_OFFSET_Y = 0.0f   // up
        private const val HEAD_OFFSET_Z = -0.0f    // left
        
        // Calibration mode settings (when heading menu is open)
        // Adjust these to position head relative to enlarged compass
        private const val CALIBRATION_X = 0.0f   // Left/Right (positive = right)
        private const val CALIBRATION_Y = -3.5f  // Up/Down (negative = down)
        private const val CALIBRATION_Z = 1.5f   // Forward/Back (negative = toward user)
        private const val CALIBRATION_SCALE = 0.10f
    }

    // Single combined head entity (HMD + helmet + FlySight)
    private var headEntity: Entity? = null
    
    private var initialized = false
    private var enabled = true
    private var isEnlarged = false  // True when heading menu is open (calibration mode)

    override fun execute() {
        if (!initialized) {
            initializeEntities()
        }
        
        // When enlarged (heading menu open), show head for calibration
        if (isEnlarged) {
            updateHeadOrientationCalibration()
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
     * Set enlarged mode (called when heading menu opens/closes).
     * When enlarged, shows head model for heading calibration.
     */
    fun setEnlarged(enlarged: Boolean) {
        isEnlarged = enlarged
        if (!enlarged) {
            headEntity?.setComponent(Visible(false))
        }
        Log.i(TAG, "Head calibration mode ${if (enlarged) "enabled" else "disabled"}")
    }
    
    /**
     * Update head orientation for calibration mode.
     * Shows head above the enlarged compass using HMD rotation.
     * Used during heading menu to help align magnetic north with GPS north.
     */
    private fun updateHeadOrientationCalibration() {
        val headEntity = this.headEntity ?: return
        
        // Get head pose for room position (consistent with compass positioning)
        val headPose = HeadPoseUtil.getHeadPose(systemManager)
        if (headPose == null || headPose == Pose()) {
            headEntity.setComponent(Visible(false))
            return
        }
        
        // Position relative to head pose (room position), offset by calibration constants
        val position = headPose.t + Vector3(CALIBRATION_X, CALIBRATION_Y, CALIBRATION_Z)
        
        // Use HMD rotation directly for calibration
        val rotation = headPose.q
        
        // Update head entity
        headEntity.setComponents(
            Transform(Pose(position, rotation)),
            Scale(Vector3(CALIBRATION_SCALE)),
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
