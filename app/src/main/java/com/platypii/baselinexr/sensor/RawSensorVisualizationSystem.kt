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
        
        // Position relative to user (same as AHRS viz)
        private const val VIZ_FORWARD = 1.0f    // meters in front
        private const val VIZ_HEIGHT = -0.5f    // below eye level
    }

    private var initialized = false
    
    // Entity references
    private var deviceEntity: Entity? = null
    private var accelArrowEntity: Entity? = null
    private var magArrowEntity: Entity? = null
    
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
        deviceEntity = null
        accelArrowEntity = null
        magArrowEntity = null
        
        initialized = false
    }
}
