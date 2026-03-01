package com.platypii.baselinexr.sensor

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import androidx.core.net.toUri
import com.platypii.baselinexr.Adjustments
import com.platypii.baselinexr.HudOptions
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.measurements.MImuData
import com.platypii.baselinexr.measurements.MMagData
import com.platypii.baselinexr.util.HeadPoseUtil
import com.platypii.baselinexr.util.PubSub

/**
 * Raw Sensor Visualization System
 * 
 * Displays FlySight 2 sensor data directly using the hardware fusion quaternion,
 * bypassing the AHRS filter. This provides a "ground truth" view of raw sensor output.
 * 
 * Displays:
 * - Device model oriented by raw fusion quaternion
 * - Acceleration vector (orange arrow)
 * - Magnetometer vector (purple arrow)
 * 
 * Coordinate Conversions (using SensorMath):
 * - Accel: Device body -> NWU world (via quaternion) -> Meta world
 * - Mag: Sensor body (X,Z negated) -> Device body -> NWU world -> Meta world
 */
class RawSensorVisualizationSystem : SystemBase() {

    companion object {
        private const val TAG = "RawSensorViz"
        
        // Model scaling
        private const val DEVICE_SCALE = 0.2f
        private const val ACCEL_SCALE = 0.4f
        private const val MAG_SCALE = 1.0f
        private const val HEAD_SCALE = 0.10f
        
        // Headset-anchored arrow scaling (larger to extend past head model)
        private const val HEAD_ACCEL_SCALE = 0.8f
        private const val HEAD_MAG_SCALE = 2.0f
        
        // Position relative to user (same as AHRS viz)
        private const val VIZ_FORWARD = 1.0f    // meters in front
        private const val VIZ_HEIGHT = -0.5f    // below eye level
        
        // Head model offset from device model (meters)
        private const val HEAD_OFFSET_X = 0.4f   // to the right of device
    }

    private var initialized = false
    
    // Entity references
    private var deviceEntity: Entity? = null
    private var accelArrowEntity: Entity? = null
    private var magArrowEntity: Entity? = null
    private var headEntity: Entity? = null
    private var headAccelArrowEntity: Entity? = null
    private var headMagArrowEntity: Entity? = null
    
    // Mounting offset: sensor body frame → headset frame
    // Manually tuned to match physical helmet mounting geometry.
    // Adjust yaw/pitch/roll in degrees until vectors emerge correctly.
    private val MOUNT_YAW_DEG = 0f      // rotation around Y (up)
    private val MOUNT_PITCH_DEG = 180f   // rotation around X (right)
    private val MOUNT_ROLL_DEG = 0f      // rotation around Z (forward)
    private var mountingOffset = SensorMath.eulerToQuaternion(MOUNT_YAW_DEG, MOUNT_PITCH_DEG, MOUNT_ROLL_DEG)
    
    // Axis remap for raw sensor vectors before rotation.
    // Each value is a signed axis index: 1=X, 2=Y, 3=Z, negative=negate.
    // Example: (1, 2, 3) = identity, (-1, 3, 2) = negate X, swap Y↔Z
    // Applied to both accel and mag vectors before mountingOffset rotation.
    private val REMAP_X = 2   // which raw axis maps to output X
    private val REMAP_Y = 3   // which raw axis maps to output Y
    private val REMAP_Z = -1   // which raw axis maps to output Z
    
    // Latest sensor data
    private var latestImu: MImuData? = null
    private var latestMag: MMagData? = null
    
    // Sensor subscribers
    private var imuSubscriber: PubSub.Subscriber<MImuData>? = null
    private var magSubscriber: PubSub.Subscriber<MMagData>? = null
    
    // Visualization position
    private var vizPosition = Vector3(0f, 0f, VIZ_FORWARD)

    override fun execute() {
        if (!initialized) {
            initialize()
        }
        
        if (HudOptions.showRawSensor && initialized) {
            updateVisualization()
        } else if (initialized) {
            // Hide when disabled
            deviceEntity?.setComponent(Visible(false))
            accelArrowEntity?.setComponent(Visible(false))
            magArrowEntity?.setComponent(Visible(false))
            headEntity?.setComponent(Visible(false))
            headAccelArrowEntity?.setComponent(Visible(false))
            headMagArrowEntity?.setComponent(Visible(false))
        }
    }

    private fun initialize() {
        Log.i(TAG, "Initializing Raw Sensor Visualization")
        
        // Create device model - simple entity without complex base rotations
        deviceEntity = Entity.create(
            Mesh("sensor_device.glb".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))),
            Scale(Vector3(DEVICE_SCALE)),
            Visible(false)
        )
        
        // Create acceleration arrow (orange)
        accelArrowEntity = Entity.create(
            Mesh("sensor_arrow_accel.glb".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))),
            Scale(Vector3(ACCEL_SCALE)),
            Visible(false)
        )
        
        // Create magnetometer arrow (purple)
        magArrowEntity = Entity.create(
            Mesh("sensor_arrow_mag.glb".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))),
            Scale(Vector3(MAG_SCALE)),
            Visible(false)
        )
        
        // Create head model (HMD + helmet + FlySight)
        // Driven by VR headset quaternion directly (already in Meta space)
        // Placed next to device model for side-by-side quaternion comparison
        headEntity = Entity.create(
            Mesh("fullheadneck.gltf".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))),
            Scale(Vector3(HEAD_SCALE)),
            Visible(false)
        )
        
        // Headset-anchored sensor arrows (no fusion — uses headPose.q + mounting offset)
        // These are ground truth vectors for validating calibration
        headAccelArrowEntity = Entity.create(
            Mesh("sensor_arrow_accel.glb".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))),
            Scale(Vector3(HEAD_ACCEL_SCALE)),
            Visible(false)
        )
        headMagArrowEntity = Entity.create(
            Mesh("sensor_arrow_mag.glb".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f), Quaternion(0f, 0f, 0f, 1f))),
            Scale(Vector3(HEAD_MAG_SCALE)),
            Visible(false)
        )
        
        // Subscribe to sensor updates
        subscribeToSensors()
        
        initialized = true
        Log.i(TAG, "Raw Sensor Visualization initialized")
    }
    
    private fun subscribeToSensors() {
        // Subscribe to IMU updates
        imuSubscriber = PubSub.Subscriber { imu ->
            latestImu = imu
        }
        Services.sensor?.imuUpdates?.subscribe(imuSubscriber!!)
        
        // Subscribe to Mag updates
        magSubscriber = PubSub.Subscriber { mag ->
            latestMag = mag
        }
        Services.sensor?.magUpdates?.subscribe(magSubscriber!!)
        
        Log.i(TAG, "Subscribed to sensor updates")
    }

    private fun updateVisualization() {
        val imu = latestImu
        if (imu == null || !imu.hasQuaternion()) {
            // No quaternion data available
            return
        }
        
        // Get head position and place visualization in front of user
        val headPose = HeadPoseUtil.getHeadPose(systemManager)
        if (headPose != null && headPose != Pose()) {
            vizPosition = headPose.t + Vector3(0f, VIZ_HEIGHT, VIZ_FORWARD)
        } else {
            vizPosition = Vector3(0f, 1.0f, VIZ_FORWARD)
        }
        
        // Get raw fusion quaternion from IMU (NWU frame)
        // Cycle axes: X=Y, Y=Z, Z=X
        // val rawQuat = Quaternion(-imu.qx, imu.qz, imu.qy, imu.qw)
        
        // Get raw fusion quaternion from IMU
        // Original empirical mapping that works with sensor_device.glb
        val rawQuat = Quaternion(-imu.qx, imu.qz, imu.qy, imu.qw)
        
        // Apply BASElineXR heading adjustment
        val yawAdjustQuat = SensorMath.yawQuaternion(Adjustments.yawAdjustment)
        val adjustedQuat = SensorMath.multiplyQuaternions(yawAdjustQuat, rawQuat)
        
        // Update device model
        deviceEntity?.setComponents(listOf(
            Transform(Pose(vizPosition, adjustedQuat)),
            Scale(Vector3(DEVICE_SCALE)),
            Visible(true)
        ))
        
        // Update acceleration vector
        updateAccelArrow(imu, adjustedQuat)
        
        // Update magnetometer vector
        val mag = latestMag
        if (mag != null) {
            updateMagArrow(mag, adjustedQuat)
        } else {
            magArrowEntity?.setComponent(Visible(false))
        }
        
        // Update head model with raw HMD quaternion (already in Meta space)
        // Side-by-side comparison: device model shows FlySight fusion,
        // head model shows VR headset orientation — both on same helmet
        if (headPose != null && headPose != Pose()) {
            val headPosition = vizPosition + Vector3(HEAD_OFFSET_X, 0f, 0f)
            headEntity?.setComponents(listOf(
                Transform(Pose(headPosition, headPose.q)),
                Scale(Vector3(HEAD_SCALE)),
                Visible(true)
            ))
            
            // Headset-anchored sensor vectors (ground truth — no fusion)
            // Rotate raw sensor data by: headPose.q * mountingOffset
            // This transforms sensor-frame vectors into Meta world space
            // using the headset's tracking instead of FlySight's AHRS
            val sensorToWorld = SensorMath.multiplyQuaternions(headPose.q, mountingOffset)
            
            // Headset-anchored accel arrow
            val accelVec = remapAxes(Vector3(imu.accelX, imu.accelY, imu.accelZ))
            val worldAccel = SensorMath.rotateVectorByQuaternion(accelVec, sensorToWorld)
            val accelDir = SensorMath.normalize(worldAccel)
            val accelRot = SensorMath.arrowRotationFromDirection(accelDir)
            val accelMag = SensorMath.magnitude(worldAccel) * HEAD_ACCEL_SCALE
            headAccelArrowEntity?.setComponents(listOf(
                Transform(Pose(headPosition, accelRot)),
                Scale(Vector3(accelMag)),
                Visible(true)
            ))
            
            // Headset-anchored mag arrow
            val mag = latestMag
            if (mag != null) {
                val magVec = remapAxes(Vector3(mag.magX, mag.magY, mag.magZ))
                val worldMag = SensorMath.rotateVectorByQuaternion(magVec, sensorToWorld)
                val magDir = SensorMath.normalize(worldMag)
                val magRot = SensorMath.arrowRotationFromDirection(magDir)
                val magMagn = SensorMath.magnitude(worldMag) * HEAD_MAG_SCALE
                headMagArrowEntity?.setComponents(listOf(
                    Transform(Pose(headPosition, magRot)),
                    Scale(Vector3(magMagn)),
                    Visible(true)
                ))
            } else {
                headMagArrowEntity?.setComponent(Visible(false))
            }
        } else {
            headEntity?.setComponent(Visible(false))
            headAccelArrowEntity?.setComponent(Visible(false))
            headMagArrowEntity?.setComponent(Visible(false))
        }
    }
    
    private fun updateAccelArrow(imu: MImuData, quat: Quaternion) {
        val accel = Vector3(imu.accelX, imu.accelY, imu.accelZ)
        
        // Rotate by the same quaternion used for the device model
        val worldAccel = SensorMath.rotateVectorByQuaternion(accel, quat)
        
        // Calculate arrow rotation to point in direction of acceleration
        val direction = SensorMath.normalize(worldAccel)
        val arrowRotation = SensorMath.arrowRotationFromDirection(direction)
        
        // Scale based on magnitude
        val magnitude = SensorMath.magnitude(worldAccel)
        val scale = magnitude * ACCEL_SCALE
        
        accelArrowEntity?.setComponents(listOf(
            Transform(Pose(vizPosition, arrowRotation)),
            Scale(Vector3(scale)),
            Visible(true)
        ))
    }
    
    private fun updateMagArrow(mag: MMagData, quat: Quaternion) {
        val magVec = Vector3(mag.magX, mag.magY, mag.magZ)
        
        // Rotate by the same quaternion used for the device model
        val worldMag = SensorMath.rotateVectorByQuaternion(magVec, quat)
        
        // Calculate arrow rotation to point in direction of mag field
        val direction = SensorMath.normalize(worldMag)
        val arrowRotation = SensorMath.arrowRotationFromDirection(direction)
        
        // Scale based on magnitude
        val magnitude = SensorMath.magnitude(worldMag)
        val scale = magnitude * MAG_SCALE
        
        magArrowEntity?.setComponents(listOf(
            Transform(Pose(vizPosition, arrowRotation)),
            Scale(Vector3(scale)),
            Visible(true)
        ))
    }
    
    /**
     * Remap raw sensor vector axes using REMAP_X/Y/Z config.
     * Signed axis index: 1=X, 2=Y, 3=Z, negative=negate.
     */
    private fun remapAxes(v: Vector3): Vector3 {
        fun pick(axis: Int): Float {
            val value = when (kotlin.math.abs(axis)) {
                1 -> v.x
                2 -> v.y
                3 -> v.z
                else -> 0f
            }
            return if (axis < 0) -value else value
        }
        return Vector3(pick(REMAP_X), pick(REMAP_Y), pick(REMAP_Z))
    }
    
    fun cleanup() {
        Log.i(TAG, "Cleaning up Raw Sensor Visualization")
        
        // Unsubscribe from sensors
        imuSubscriber?.let { Services.sensor?.imuUpdates?.unsubscribe(it) }
        magSubscriber?.let { Services.sensor?.magUpdates?.unsubscribe(it) }
        imuSubscriber = null
        magSubscriber = null
        
        // Destroy entities
        deviceEntity?.destroy()
        accelArrowEntity?.destroy()
        magArrowEntity?.destroy()
        headEntity?.destroy()
        headAccelArrowEntity?.destroy()
        headMagArrowEntity?.destroy()
        deviceEntity = null
        accelArrowEntity = null
        magArrowEntity = null
        headEntity = null
        headAccelArrowEntity = null
        headMagArrowEntity = null
        
        initialized = false
    }
    
    /**
     * Set mounting offset quaternion: sensor body frame → headset frame.
     * Manually tuned to match the physical mounting on the helmet.
     */
    fun setMountingOffset(offset: Quaternion) {
        mountingOffset = offset
        Log.i(TAG, "Mounting offset set: (${offset.x}, ${offset.y}, ${offset.z}, ${offset.w})")
    }
}
