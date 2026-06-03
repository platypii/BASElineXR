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
 * Displays FlySight 2 sensor data in two side-by-side systems:
 *
 * System A — Device-centric (FlySight fusion quaternion, ENU body → ENU world → Meta):
 *   - Device model oriented by fusion quaternion (empirical model correction baked in)
 *   - Accel arrow: chip = ENU body frame, no remap
 *   - Mag arrow:   chip bottom of PCB, negate X and Z to get ENU body
 *
 * System B — Head-centric (Quest headset pose + mounting offset):
 *   - Head model driven by headPose.q directly (already in Meta space)
 *   - Mounting offset: Quaternion(0,1,0,0) = 180° around Meta Y (device on back of helmet)
 *   - Accel arrow: chip = sensor body frame in mounted orientation, no remap
 *   - Mag arrow:   negate X and Z (chip East,Up,South → sensor body West,Up,North)
 *
 * See docs/Coordinate-Systems.md § Rendering Strategy for full derivation.
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
    // Device on back of helmet = 180° around Meta Y (Up): flips X (East→West) and Z (South→North)
    // See docs/Coordinate-Systems.md § Rendering Strategy, System B
    private val mountingOffset = Quaternion(0f, 1f, 0f, 0f)
    
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
        updateAccelArrow(imu)//, adjustedQuat)
        
        // Update magnetometer vector
        val mag = latestMag
        if (mag != null) {
            updateMagArrow(mag, imu)
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
            
            // Head-anchored accel: chip = sensor body frame in mounted orientation—no remap
            val accelVec = Vector3(imu.accelX, imu.accelY, imu.accelZ)
            val worldAccel = SensorMath.rotateVectorByQuaternion(accelVec, sensorToWorld)
            val accelMag = SensorMath.magnitude(worldAccel)
            // Negate direction and use -X forward (same convention as device accel arrow)
            val adx = -worldAccel.x / accelMag; val ady = -worldAccel.y / accelMag; val adz = -worldAccel.z / accelMag
            val adot = -adx
            val accelRot: Quaternion
            if (adot > 0.9999f) {
                accelRot = Quaternion(0f, 0f, 0f, 1f)
            } else if (adot < -0.9999f) {
                accelRot = Quaternion(0f, 1f, 0f, 0f)
            } else {
                val axisY = adz; val axisZ = -ady
                val axisLen = kotlin.math.sqrt((axisY * axisY + axisZ * axisZ).toDouble()).toFloat()
                val angle = kotlin.math.acos(adot.coerceIn(-1f, 1f).toDouble()).toFloat()
                val half = angle / 2f; val s = kotlin.math.sin(half.toDouble()).toFloat()
                accelRot = Quaternion(0f, axisY / axisLen * s, axisZ / axisLen * s,
                    kotlin.math.cos(half.toDouble()).toFloat())
            }
            headAccelArrowEntity?.setComponents(listOf(
                Transform(Pose(headPosition, accelRot)),
                Scale(Vector3(accelMag * HEAD_ACCEL_SCALE)),
                Visible(true)
            ))
            
            // Headset-anchored mag arrow
            val mag = latestMag
            if (mag != null) {
                // Head-anchored mag: chip (X=East, Y=Up, Z=South in mounted) → sensor body (W, Up, N): negate X and Z
                val magVec = Vector3(-mag.magX, mag.magY, -mag.magZ)
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
    
    private fun updateAccelArrow(imu: MImuData) {
        // All math is inline — no SDK quaternion wrapper used for calculations.
        // Accel chip: X=East, Y=North, Z=Up (ENU body frame). accelZ ≈ 1g when flat.
        val ax = imu.accelX
        val ay = imu.accelY
        val az = imu.accelZ

        // Rotate ENU body → ENU world: v' = q * v * q^-1
        // Using raw floats directly to avoid any SDK Quaternion constructor ambiguity.
        val qx = imu.qx; val qy = imu.qy; val qz = imu.qz; val qw = imu.qw
        val tx = 2f * (qy * az - qz * ay)
        val ty = 2f * (qz * ax - qx * az)
        val tz = 2f * (qx * ay - qy * ax)
        val wx = ax + qw * tx + qy * tz - qz * ty  // East in ENU world
        val wy = ay + qw * ty + qz * tx - qx * tz  // North in ENU world
        val wz = az + qw * tz + qx * ty - qy * tx  // Up in ENU world

        // ENU world → Meta world: (E, N, U) → (E, U, -N)
        // Meta: X=East, Y=Up, Z=South
        var mx = wx
        var my = wz
        var mz = -wy

        // Apply yaw adjustment (rotation around Meta Y=Up axis)
        val yaw = Adjustments.yawAdjustment
        val cosY = kotlin.math.cos(yaw.toDouble()).toFloat()
        val sinY = kotlin.math.sin(yaw.toDouble()).toFloat()
        val mx2 = mx * cosY + mz * sinY
        val mz2 = -mx * sinY + mz * cosY
        mx = mx2; mz = mz2

        // Normalize
        val mag = kotlin.math.sqrt((mx * mx + my * my + mz * mz).toDouble()).toFloat()
        if (mag < 0.001f) return
        val dx = -mx / mag; val dy = -my / mag; val dz = -mz / mag

        // Compute rotation quaternion: rotate arrow's default -X axis to point at (dx, dy, dz)
        // Arrow model points along -X (West) at identity — confirmed from Meta Spatial editor screenshot.
        // forward = (-1, 0, 0)
        val dot = -dx  // dot(forward=(-1,0,0), direction=(dx,dy,dz))
        val arrowRotation: Quaternion
        if (dot > 0.9999f) {
            arrowRotation = Quaternion(0f, 0f, 0f, 1f)
        } else if (dot < -0.9999f) {
            arrowRotation = Quaternion(0f, 1f, 0f, 0f)  // 180° around Y
        } else {
            // axis = (-1,0,0) × (dx,dy,dz) = (0*dz-0*dy, 0*dx-(-1)*dz, (-1)*dy-0*dx) = (0, dz, -dy)
            val axisY = dz; val axisZ = -dy
            val axisLen = kotlin.math.sqrt((axisY * axisY + axisZ * axisZ).toDouble()).toFloat()
            val angle = kotlin.math.acos(dot.coerceIn(-1f, 1f).toDouble()).toFloat()
            val half = angle / 2f
            val s = kotlin.math.sin(half.toDouble()).toFloat()
            arrowRotation = Quaternion(
                0f,
                axisY / axisLen * s,
                axisZ / axisLen * s,
                kotlin.math.cos(half.toDouble()).toFloat()
            )
        }

        val scale = kotlin.math.sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat() * ACCEL_SCALE
        accelArrowEntity?.setComponents(listOf(
            Transform(Pose(vizPosition, arrowRotation)),
            Scale(Vector3(scale)),
            Visible(true)
        ))
    }
    
    private fun updateMagArrow(mag: MMagData, imu: MImuData) {
        // Mag chip is on bottom of PCB: X and Z negated vs accel chip
        // chip (X=West, Y=North, Z=Down) → ENU body (X=East, Y=North, Z=Up): negate X and Z
        val enuBodyMag = Vector3(-mag.magX, mag.magY, -mag.magZ)

        // Step 2: Apply hard iron calibration (values in ENU body frame)
        val cal = Services.deviceMagCal
        val calibratedMag = if (cal != null) {
            Vector3(
                enuBodyMag.x - cal.hardIronX,
                enuBodyMag.y - cal.hardIronY,
                enuBodyMag.z - cal.hardIronZ
            )
        } else enuBodyMag

        // Step 3: Rotate ENU body → ENU world using empirical quaternion (same as device model)
        val empiricalQuat = Quaternion(-imu.qx, imu.qz, imu.qy, imu.qw)
        val enuMag = SensorMath.rotateVectorByQuaternion(calibratedMag, empiricalQuat)

        // Step 4: ENU world → Meta Spatial world: (E, N, U) → (E, U, -N)
        val metaMag = Vector3(enuMag.x, enuMag.z, -enuMag.y)

        // Step 5: Apply heading adjustment
        val yawQuat = SensorMath.yawQuaternion(Adjustments.yawAdjustment)
        val worldMag = SensorMath.rotateVectorByQuaternion(metaMag, yawQuat)

        val direction = SensorMath.normalize(worldMag)
        val arrowRotation = SensorMath.arrowRotationFromDirection(direction)
        val scale = SensorMath.magnitude(enuBodyMag) * MAG_SCALE

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
    
}
