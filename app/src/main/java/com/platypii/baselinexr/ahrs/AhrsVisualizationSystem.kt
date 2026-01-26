package com.platypii.baselinexr.ahrs

import android.util.Log
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.platypii.baselinexr.Adjustments
import com.platypii.baselinexr.HudOptions
import com.platypii.baselinexr.util.AhrsObjectFactory
import com.platypii.baselinexr.util.HeadPoseUtil
import com.platypii.baselinexr.util.SpatialObject
import kotlin.math.*

/**
 * 3D Visualization System for AHRS orientation and sensor vectors.
 * 
 * Displays a FlySight device model rotating according to AHRS output,
 * with sensor vectors (accel, mag, gravity) in world frame.
 * 
 * Coordinate Systems:
 * - Body frame: FlySight body axes (X=West, Y=Up, Z=North relative to device)
 * - NWU frame: Algorithm frame (X=North, Y=West, Z=Up)
 * - Meta Spatial: VR frame (X=East, Y=Up, Z=South toward user)
 * 
 * Transform Pipeline (Body -> NWU -> Meta):
 * - bodyToNWU: [bz, bx, by] (in FusionAhrsAdapter)
 * - nwuWorldToMeta: [-ny, nz, -nx]
 * 
 * Device Rotation:
 * - Uses euler-based rotation with createQuaternionFromEuler()
 * - Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
 * - Model offset rotation: (roll=0, pitch=π, yaw=π) baked into baseRotation
 * - Attitude rotation: (roll, pitch, -heading - π/2)
 * - The -π/2 yaw offset converts NWU heading (0=North) to Meta forward (-Z=North)
 * 
 * Sensor Vector Pipeline:
 * 1. Get sensor data in NWU body frame from FusionAhrsAdapter
 * 2. Rotate by AHRS quaternion -> NWU world frame
 * 3. Transform NWU world -> Meta world via nwuWorldToMeta()
 * 
 * Expected Results (device at rest, level):
 * - Acceleration: Points UP (reactive force from gravity) - Meta +Y
 * - Magnetometer: Points toward magnetic North and DOWN (~60° inclination)
 * - Gravity: Points UP (AHRS estimate) - Meta +Y
 * - North indicator: Fixed at Meta -Z
 */
class AhrsVisualizationSystem : SystemBase() {

    companion object {
        private const val TAG = "AhrsVisualizationSystem"
        
        // Model scaling
        private const val DEVICE_SCALE = 0.2f   // Smaller device
        private const val ARROW_SCALE = 0.15f
        
        // Position relative to user
        private const val VIZ_FORWARD = 1.0f   // meters in front
        private const val VIZ_HEIGHT = -0.5f   // below eye level
        
        // Vector arrow scales (larger for visibility)
        private const val ACCEL_SCALE = 0.4f     // Orange - measured acceleration
        private const val GRAVITY_SCALE = 0.4f   // Blue - estimated gravity
        private const val MAG_SCALE = 1.0f       // Purple - magnetic field
        private const val GYRO_SCALE = 0.02f     // Per deg/s rotation indicator
    }

    private var initialized = false
    
    // =====================================================================
    // SpatialObject-based entity hierarchy (Three.js-style parenting)
    // =====================================================================
    
    // Root visualization group - follows user's head position
    private var vizRoot: SpatialObject? = null
    
    // Device model - child of vizRoot, rotates with AHRS orientation
    private var deviceObject: SpatialObject? = null
    
    // Sensor vector arrows - world frame vectors (not parented to device)
    private var accelArrow: SpatialObject? = null     // Orange - acceleration in world frame
    private var magArrow: SpatialObject? = null       // Purple - magnetic field in world frame
    private var gravityArrow: SpatialObject? = null   // Blue - gravity (always up)
    private var northIndicator: SpatialObject? = null // Blue - north direction
    
    // Reference to AHRS system for getting orientation and sensor data
    private var ahrsSystem: AhrsSystem? = null
    
    // Fixed visualization position (updated each frame to stay in front of user)
    private var vizPosition = Vector3(0f, 0f, -VIZ_FORWARD)
    private var frameCount = 0

    override fun execute() {
        if (!initialized) {
            initialize()
        }
        
        if (HudOptions.showAhrs && initialized) {
            updateVisualization()
            
            // Debug logging every 100 frames
            frameCount++
            if (frameCount % 100 == 0) {
                Log.d(TAG, "AHRS viz update: pos=$vizPosition, showAhrs=${HudOptions.showAhrs}")
            }
        }
    }

    private fun initialize() {
        Log.i(TAG, "Initializing AHRS visualization with SpatialObject hierarchy")
        
        // =====================================================================
        // Create entity hierarchy using SpatialObject (Three.js-style parenting)
        // 
        // Hierarchy:
        //   vizRoot (invisible, follows head position)
        //     └── deviceObject (FlySight model, rotates with AHRS)
        //   
        //   accelArrow (world frame, at vizRoot position)
        //   magArrow (world frame, at vizRoot position)
        //   gravityArrow (world frame, at vizRoot position)
        //   northIndicator (world frame, at vizRoot position)
        // =====================================================================
        
        // Create device model with proper model offset rotation
        deviceObject = AhrsObjectFactory.createDeviceModel(DEVICE_SCALE)
        Log.i(TAG, "Created FlySight device entity using SpatialObject")
        
        // Create sensor vector arrows (world frame - positioned at device but rotated independently)
        accelArrow = AhrsObjectFactory.createVectorArrow("vectoro.glb", ACCEL_SCALE)
        magArrow = AhrsObjectFactory.createVectorArrow("vectorp.glb", MAG_SCALE)
        gravityArrow = AhrsObjectFactory.createVectorArrow("vectorb.glb", GRAVITY_SCALE)
        northIndicator = AhrsObjectFactory.createVectorArrow("vectorb.glb", 0.1f)
        
        initialized = true
        Log.i(TAG, "AHRS Visualization initialized with SpatialObject hierarchy")
    }

    private fun updateVisualization() {
        // Try to find AhrsSystem if we don't have it yet
        if (ahrsSystem == null) {
            ahrsSystem = systemManager.tryFindSystem<AhrsSystem>()
        }
        
        val ahrs = ahrsSystem
        val device = deviceObject ?: return
        
        // Get head position and place visualization in front of user
        val headPose = HeadPoseUtil.getHeadPose(systemManager)
        if (headPose != null && headPose != Pose()) {
            vizPosition = headPose.t + Vector3(0f, VIZ_HEIGHT, VIZ_FORWARD)
        } else {
            vizPosition = Vector3(0f, 1.0f, -VIZ_FORWARD)
        }
        
        if (ahrs == null) {
            // Can't update without AHRS, just position at default
            device.setWorldPosition(vizPosition)
            device.setVisible(HudOptions.showAhrs)
            device.updateWorldTransform()
            return
        }
        
        val adapter = ahrs.getAdapter()
        
        // Get AHRS orientation quaternion (NWU frame)
        val rawDeviceRotation = ahrs.predict(System.currentTimeMillis())
        
        // Apply yaw adjustment to align with BASElineXR compass heading
        // Yaw rotation is around Y axis in Meta Spatial (Y-up)
        val yawAdj = -Adjustments.yawAdjustment
        val yawAdjQuat = Quaternion(0f, sin(yawAdj / 2f), 0f, cos(yawAdj / 2f))
        val deviceRotation = multiplyQuaternions(yawAdjQuat, rawDeviceRotation)
        
        // Get euler angles from AHRS [heading, pitch, roll] in degrees
        val euler = adapter.getEulerAngles()
        val headingDeg = euler[0]  // Yaw in NWU (0 = North, 90 = West)
        val pitchDeg = euler[1]    // Pitch in NWU
        val rollDeg = euler[2]     // Roll in NWU
        
        // Convert to radians
        val headingRad = (headingDeg * Math.PI / 180.0).toFloat()
        val pitchRad = (pitchDeg * Math.PI / 180.0).toFloat()
        val rollRad = (rollDeg * Math.PI / 180.0).toFloat()
        
        // =====================================================================
        // Device Model Rotation using SpatialObject
        // The baseRotation in AhrsObjectFactory.createDeviceModel() handles model offset.
        // We only need to set the attitude rotation here.
        // =====================================================================
        
        // Attitude rotation with yaw offset
        // NWU heading: 0 = North. Subtract yawAdjustment to align with BASElineXR compass.
        // TEMPORARY: Set pitch/roll to 0 to test heading rotation direction only
        val attitudeRotation = AhrsObjectFactory.createQuaternionFromEuler(
            -rollRad,  // rollRad - temporarily 0 to test heading
            -pitchRad,  // pitchRad - temporarily 0 to test heading
            (-headingRad - Adjustments.yawAdjustment)
        )
        
        // Update device using SpatialObject - base rotation is applied automatically
        device.setWorldPosition(vizPosition)
        device.setLocalRotation(attitudeRotation)
        device.setVisible(true)
        device.updateWorldTransform()
        
        // Get sensor data in NWU frame for correct quaternion rotation
        // The AHRS quaternion rotates NWU-frame vectors to NWU-world frame
        val accel = adapter.getAccelNWU()    // NWU frame, in g
        val mag = adapter.getMagNWU()        // NWU frame, in gauss
        val gravity = adapter.getGravityNWU() // NWU frame, in g (AHRS estimate)
        
        // =====================================================================
        // Sensor Vector Pipeline:
        // 1. Sensor data already in NWU body frame (X=North, Y=West, Z=Up)
        // 2. Rotate by deviceRotation quaternion (includes yaw adjustment)
        // 3. This ensures vectors rotate with the device
        // =====================================================================
        
        // Update accel vector - use deviceRotation quaternion
        updateSensorArrow(accelArrow, accel, deviceRotation, ACCEL_SCALE, true)
        
        // Update mag vector
        if (adapter.isMagValid()) {
            updateSensorArrow(magArrow, mag, deviceRotation, MAG_SCALE, true)
        } else {
            magArrow?.setVisible(false)
        }
        
        // Update gravity vector
        updateSensorArrow(gravityArrow, gravity, deviceRotation, GRAVITY_SCALE, true)
        
        // Update north indicator - fixed direction in world frame
        // TEMPORARY: Identity rotation to verify arrow GLB orientation
        // In Meta Spatial, North = -Z direction (since Z points South toward user)
        val northDir = Vector3(0f, 0f, -1f)  // North in Meta = -Z
        // updateWorldArrow(northIndicator, northDir, 0.1f)
        northIndicator?.setWorldPosition(vizPosition)
        northIndicator?.setLocalRotation(Quaternion(0f, 0f, 0f, 1f))
        northIndicator?.setLocalScale(0.1f)
        northIndicator?.setVisible(true)
        northIndicator?.updateWorldTransform()
    }
    
    /**
     * Update a sensor vector arrow using the coordinate transform pipeline.
     * Input is in NWU body frame, rotated to NWU world, then to Meta world.
     * 
     * @param arrow The SpatialObject arrow to update
     * @param sensorData Sensor data in NWU body frame [X=North, Y=West, Z=Up]
     * @param deviceRotation AHRS quaternion that rotates NWU body -> NWU world
     * @param baseScale Base scale for the arrow
     * @param visible Whether arrow should be visible
     */
    private fun updateSensorArrow(
        arrow: SpatialObject?,
        sensorData: FloatArray,
        deviceRotation: Quaternion,
        baseScale: Float,
        visible: Boolean
    ) {
        arrow ?: return
        
        val mag = sqrt(sensorData[0] * sensorData[0] + sensorData[1] * sensorData[1] + sensorData[2] * sensorData[2])
        if (mag < 0.01f) {
            arrow.setVisible(false)
            return
        }
        
        // Simpler approach: do rotation in NWU frame, then convert result to Meta
        
        // Step 1: Normalize NWU body frame vector
        val nwuBodyVec = Vector3(sensorData[0] / mag, sensorData[1] / mag, sensorData[2] / mag)
        
        // Step 2: Rotate by AHRS quaternion -> NWU World frame
        val nwuWorld = rotateVectorByQuaternion(nwuBodyVec, deviceRotation)
        
        // Step 3: NWU World -> Meta World (final coordinate transform)
        val metaWorld = nwuWorldToMeta(nwuWorld)
        
        // Calculate arrow rotation to point in direction
        val arrowRotation = AhrsObjectFactory.calculateArrowRotation(metaWorld)
        
        arrow.setWorldPosition(vizPosition)
        arrow.setLocalRotation(arrowRotation)
        arrow.setLocalScale(mag * baseScale)
        arrow.setVisible(visible)
        arrow.updateWorldTransform()
    }
    
    /**
     * Update a world-frame arrow (like north indicator) that doesn't need sensor transforms.
     */
    private fun updateWorldArrow(arrow: SpatialObject?, direction: Vector3, scale: Float) {
        arrow ?: return
        
        val arrowRotation = AhrsObjectFactory.calculateArrowRotation(direction)
        
        arrow.setWorldPosition(vizPosition)
        arrow.setLocalRotation(arrowRotation)
        arrow.setLocalScale(scale)
        arrow.setVisible(true)
        arrow.updateWorldTransform()
    }

    // =====================================================================
    // Coordinate Transform Functions
    // =====================================================================

    /**
     * Transform NWU World frame directly to Meta Spatial World frame.
     * 
     * NWU World: X=North, Y=West, Z=Up
     * Meta World: X=East, Y=Up, Z=South (toward user)
     * 
     * Derivation (via BASElineXR World as intermediate):
     *   BASElineXR: X=East, Y=Up, Z=North
     *   
     *   NWU -> BASElineXR:
     *     Baseline_X (East) = -NWU_Y (West negated)
     *     Baseline_Y (Up) = NWU_Z (Up)
     *     Baseline_Z (North) = NWU_X (North)
     *   
     *   BASElineXR -> Meta:
     *     Meta_X = Baseline_X
     *     Meta_Y = Baseline_Y
     *     Meta_Z = -Baseline_Z
     *   
     * Combined NWU -> Meta:
     *   Meta_X (East) = -NWU_Y (West negated)
     *   Meta_Y (Up) = NWU_Z (Up)
     *   Meta_Z (South) = -NWU_X (North negated)
     */
    private fun nwuWorldToMeta(nwu: Vector3): Vector3 {
        return Vector3(-nwu.y, nwu.z, -nwu.x)
    }
    
    /**
     * Convert a quaternion from NWU frame to Meta Spatial frame.
     * 
     * The quaternion's vector part (x,y,z) represents the rotation axis,
     * so it needs the same coordinate transform as vectors:
     *   Meta_X = -NWU_Y
     *   Meta_Y = NWU_Z
     *   Meta_Z = -NWU_X
     * 
     * The scalar part (w) is unchanged.
     */
    private fun nwuQuatToMeta(q: Quaternion): Quaternion {
        return Quaternion(-q.y, q.z, -q.x, q.w)
    }

    /**
     * Rotate a vector by a quaternion: q * v * q^-1
     */
    private fun rotateVectorByQuaternion(v: Vector3, q: Quaternion): Vector3 {
        val qx = q.x; val qy = q.y; val qz = q.z; val qw = q.w
        
        val tx = qw * v.x + qy * v.z - qz * v.y
        val ty = qw * v.y + qz * v.x - qx * v.z
        val tz = qw * v.z + qx * v.y - qy * v.x
        val tw = -qx * v.x - qy * v.y - qz * v.z
        
        return Vector3(
            tw * -qx + tx * qw + ty * -qz - tz * -qy,
            tw * -qy + ty * qw + tz * -qx - tx * -qz,
            tw * -qz + tz * qw + tx * -qy - ty * -qx
        )
    }

    /**
     * Update visibility of all entities
     */
    fun updateVisibility() {
        val visible = HudOptions.showAhrs
        Log.i(TAG, "updateVisibility called: visible=$visible, initialized=$initialized")
        
        if (!initialized) {
            // Try to initialize now if we haven't yet
            initialize()
        }
        
        // Update SpatialObject visibility
        deviceObject?.setVisible(visible)
        accelArrow?.setVisible(visible)
        magArrow?.setVisible(visible)
        gravityArrow?.setVisible(visible)
        northIndicator?.setVisible(visible)
        
        if (!visible) {
            Log.d(TAG, "AHRS visualization hidden")
        }
    }
    
    /**
     * Multiply two quaternions: q1 * q2
     * This applies q2 first, then q1 (standard quaternion multiplication order)
     */
    private fun multiplyQuaternions(q1: Quaternion, q2: Quaternion): Quaternion {
        val w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
        val x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y
        val y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x
        val z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w
        return Quaternion(x, y, z, w)
    }

    override fun destroy() {
        deviceObject?.destroy()
        accelArrow?.destroy()
        magArrow?.destroy()
        gravityArrow?.destroy()
        northIndicator?.destroy()
        
        deviceObject = null
        accelArrow = null
        magArrow = null
        gravityArrow = null
        northIndicator = null
        vizRoot = null
        ahrsSystem = null
        initialized = false
        super.destroy()
    }
}
