package com.platypii.baselinexr.ahrs

import kotlin.math.*

/**
 * FusionAhrsAdapter - Adapter layer for FusionAhrs
 *
 * Handles:
 * - Coordinate transforms (sensor → body → NWU)
 * - Calibration application (gyro bias, accel offset, mag cal, soft iron)
 * - Runtime gyro bias estimation
 * - Axis remapping for different sensor orientations
 *
 * Coordinate System Pipeline:
 * 1. SENSOR FRAME (raw data from IMU/Mag chips)
 * 2. DEVICE BODY FRAME (FlySight 2 convention: X=West, Y=Up, Z=North)
 * 3. NWU ALGORITHM FRAME (Fusion convention: X=North, Y=West, Z=Up)
 *
 * INPUT PATH: Sensor → (axis remap) → Body → (bodyToNWU) → FusionAhrs.update()
 * OUTPUT PATH: FusionAhrs outputs → (nwuToBody for body-frame quantities) → Display
 */
class FusionAhrsAdapter(
    settings: AdapterSettings = AdapterSettings()
) {
    /**
     * Axis mapping configuration
     * Maps sensor axis to body axis with optional sign flip
     */
    data class AxisMapping(
        val axis: Int,      // 0=X, 1=Y, 2=Z from sensor
        val sign: Float     // +1 or -1
    )

    data class AxisRemap(
        val x: AxisMapping = AxisMapping(0, 1f),  // Body X = which sensor axis
        val y: AxisMapping = AxisMapping(1, 1f),  // Body Y = which sensor axis
        val z: AxisMapping = AxisMapping(2, 1f)   // Body Z = which sensor axis
    )

    /**
     * Magnetometer calibration (hard iron + scale)
     */
    data class MagCalibration(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val offsetZ: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val scaleZ: Float = 1f
    )

    /**
     * IMU calibration (gyro bias + accel offset)
     */
    data class IMUCalibration(
        val gyroBiasX: Float = 0f,  // deg/s
        val gyroBiasY: Float = 0f,
        val gyroBiasZ: Float = 0f,
        val accelOffsetX: Float = 0f,  // g
        val accelOffsetY: Float = 0f,
        val accelOffsetZ: Float = 0f
    )

    /**
     * Adapter settings
     */
    data class AdapterSettings(
        val ahrsSettings: FusionAhrs.FusionAhrsSettings = FusionAhrs.FusionAhrsSettings(),
        val biasSettings: FusionBias.FusionBiasSettings = FusionBias.FusionBiasSettings(),
        val enableBiasEstimation: Boolean = true
    )

    companion object {
        private const val DEG_TO_RAD = (PI / 180.0).toFloat()
        private const val RAD_TO_DEG = (180.0 / PI).toFloat()

        // Default calibration values from screenshots
        val DEFAULT_MAG_CALIBRATION = MagCalibration(
            offsetX = -0.3707f,
            offsetY = -0.1334f,
            offsetZ = -0.4150f,
            scaleX = 1f,
            scaleY = 1f,
            scaleZ = 1f
        )

        val DEFAULT_IMU_CALIBRATION = IMUCalibration(
            gyroBiasX = -0.2537f,
            gyroBiasY = -0.0681f,
            gyroBiasZ = -0.0823f,
            accelOffsetX = 0.008565f,
            accelOffsetY = -0.01207f,
            accelOffsetZ = 0.007986f
        )

        // IMU axis remap: identity (sensor frame = body frame)
        val DEFAULT_IMU_AXIS_REMAP = AxisRemap()

        // Mag axis remap: X→-X, Y→Y, Z→-Z (mag is on bottom of board)
        val DEFAULT_MAG_AXIS_REMAP = AxisRemap(
            x = AxisMapping(0, -1f),  // Body X = -Sensor X
            y = AxisMapping(1, 1f),   // Body Y = +Sensor Y
            z = AxisMapping(2, -1f)   // Body Z = -Sensor Z
        )
    }

    // Core components
    private val ahrs = FusionAhrs(settings.ahrsSettings)
    private val bias = FusionBias(settings.biasSettings)
    private var enableBiasEstimation = settings.enableBiasEstimation

    // Calibration
    private var magCal = DEFAULT_MAG_CALIBRATION
    private var imuCal = DEFAULT_IMU_CALIBRATION
    private var imuAxisRemap = DEFAULT_IMU_AXIS_REMAP
    private var magAxisRemap = DEFAULT_MAG_AXIS_REMAP

    // Soft iron matrix (3x3, identity by default)
    private var softIronMatrix: FloatArray? = null

    // Accel scale matrix (3x3, identity by default)
    private var accelScaleMatrix: FloatArray? = null

    // Last magnetometer (for async update - mag arrives at different rate than IMU)
    private var lastMagNwuX = 0f
    private var lastMagNwuY = 0f
    private var lastMagNwuZ = 0f
    private var lastMagBodyX = 0f
    private var lastMagBodyY = 0f
    private var lastMagBodyZ = 0f
    private var magValid = false

    // Last accelerometer in NWU (for compass heading calculation)
    private var lastAccelNwuX = 0f
    private var lastAccelNwuY = 0f
    private var lastAccelNwuZ = 0f

    // Last accelerometer in body frame (for visualization)
    private var lastAccelBodyX = 0f
    private var lastAccelBodyY = 0f
    private var lastAccelBodyZ = 0f

    // Last gyroscope in body frame (for visualization)
    private var lastGyroBodyX = 0f
    private var lastGyroBodyY = 0f
    private var lastGyroBodyZ = 0f

    // =========================================================================
    // Configuration
    // =========================================================================

    fun updateAhrsSettings(settings: FusionAhrs.FusionAhrsSettings) {
        ahrs.setSettings(settings)
    }

    fun updateBiasSettings(settings: FusionBias.FusionBiasSettings) {
        bias.applySettings(settings)
    }

    fun setEnableBiasEstimation(enable: Boolean) {
        enableBiasEstimation = enable
        if (!enable) bias.reset()
    }

    fun setMagCalibration(cal: MagCalibration) {
        magCal = cal
    }

    fun getMagCalibration(): MagCalibration = magCal

    fun setIMUCalibration(cal: IMUCalibration) {
        imuCal = cal
    }

    fun getIMUCalibration(): IMUCalibration = imuCal

    fun setIMUAxisRemap(remap: AxisRemap) {
        imuAxisRemap = remap
    }

    fun setMagAxisRemap(remap: AxisRemap) {
        magAxisRemap = remap
    }

    fun setSoftIronMatrix(matrix: FloatArray?) {
        softIronMatrix = matrix?.copyOf()
    }
    
    fun getSoftIronMatrix(): FloatArray? = softIronMatrix?.copyOf()

    fun setAccelScaleMatrix(matrix: FloatArray?) {
        accelScaleMatrix = matrix?.copyOf()
    }

    // =========================================================================
    // Reset
    // =========================================================================

    fun reset() {
        ahrs.reset()
        bias.reset()
        magValid = false
    }

    // =========================================================================
    // Coordinate Transforms
    // =========================================================================

    /**
     * Apply axis remap: sensor frame → body frame
     */
    private fun applyAxisRemap(x: Float, y: Float, z: Float, remap: AxisRemap): FloatArray {
        val input = floatArrayOf(x, y, z)
        return floatArrayOf(
            input[remap.x.axis] * remap.x.sign,
            input[remap.y.axis] * remap.y.sign,
            input[remap.z.axis] * remap.z.sign
        )
    }

    /**
     * Body frame → NWU algorithm frame transform
     *
     * FlySight Body Frame:    NWU Algorithm Frame:
     *   X = West                X = North
     *   Y = Up                  Y = West
     *   Z = North               Z = Up
     *
     * Transform: NWU = [Body_Z, Body_X, Body_Y]
     */
    private fun bodyToNWU(bx: Float, by: Float, bz: Float): FloatArray {
        return floatArrayOf(bz, bx, by)  // NWU_X=Body_Z, NWU_Y=Body_X, NWU_Z=Body_Y
    }

    /**
     * NWU algorithm frame → Body frame transform (inverse of bodyToNWU)
     *
     * Transform: Body = [NWU_Y, NWU_Z, NWU_X]
     */
    private fun nwuToBody(nx: Float, ny: Float, nz: Float): FloatArray {
        return floatArrayOf(ny, nz, nx)  // Body_X=NWU_Y, Body_Y=NWU_Z, Body_Z=NWU_X
    }

    /**
     * Apply 3x3 matrix to vector (row-major order)
     */
    private fun applyMatrix3x3(matrix: FloatArray, x: Float, y: Float, z: Float): FloatArray {
        return floatArrayOf(
            matrix[0] * x + matrix[1] * y + matrix[2] * z,
            matrix[3] * x + matrix[4] * y + matrix[5] * z,
            matrix[6] * x + matrix[7] * y + matrix[8] * z
        )
    }

    // =========================================================================
    // Update Methods
    // =========================================================================

    /**
     * Update magnetometer (stored for next IMU update)
     * Called when MAG samples arrive (typically lower rate than IMU)
     *
     * @param mx Raw magnetometer X (gauss)
     * @param my Raw magnetometer Y
     * @param mz Raw magnetometer Z
     */
    fun updateMag(mx: Float, my: Float, mz: Float) {
        // 1. Apply hard iron offset
        var calX = mx - magCal.offsetX
        var calY = my - magCal.offsetY
        var calZ = mz - magCal.offsetZ

        // 2. Apply scale factors
        calX *= magCal.scaleX
        calY *= magCal.scaleY
        calZ *= magCal.scaleZ

        // 3. Apply soft iron matrix if present
        softIronMatrix?.let { matrix ->
            val result = applyMatrix3x3(matrix, calX, calY, calZ)
            calX = result[0]
            calY = result[1]
            calZ = result[2]
        }

        // 4. Apply axis remap: sensor → body frame
        val body = applyAxisRemap(calX, calY, calZ, magAxisRemap)
        lastMagBodyX = body[0]
        lastMagBodyY = body[1]
        lastMagBodyZ = body[2]

        // 5. Transform to NWU
        val nwu = bodyToNWU(body[0], body[1], body[2])
        lastMagNwuX = nwu[0]
        lastMagNwuY = nwu[1]
        lastMagNwuZ = nwu[2]

        magValid = true
    }

    /**
     * Update with IMU data (uses stored magnetometer)
     *
     * @param dt Time step in seconds
     * @param wx Gyro X in deg/s (raw sensor)
     * @param wy Gyro Y in deg/s
     * @param wz Gyro Z in deg/s
     * @param ax Accel X in g (raw sensor)
     * @param ay Accel Y in g
     * @param az Accel Z in g
     */
    fun updateIMU(dt: Float, wx: Float, wy: Float, wz: Float, ax: Float, ay: Float, az: Float) {
        // 1. Apply gyro bias (in sensor frame)
        var gx = wx - imuCal.gyroBiasX
        var gy = wy - imuCal.gyroBiasY
        var gz = wz - imuCal.gyroBiasZ

        // 2. Apply runtime bias estimation if enabled
        if (enableBiasEstimation) {
            val corrected = bias.update(gx, gy, gz, dt)
            gx = corrected[0]
            gy = corrected[1]
            gz = corrected[2]
        }

        // 3. Apply accel offset
        var accelX = ax - imuCal.accelOffsetX
        var accelY = ay - imuCal.accelOffsetY
        var accelZ = az - imuCal.accelOffsetZ

        // 4. Apply accel scale matrix if present
        accelScaleMatrix?.let { matrix ->
            val result = applyMatrix3x3(matrix, accelX, accelY, accelZ)
            accelX = result[0]
            accelY = result[1]
            accelZ = result[2]
        }

        // 5. Apply axis remap: sensor → body frame
        val gyroBody = applyAxisRemap(gx, gy, gz, imuAxisRemap)
        val accelBody = applyAxisRemap(accelX, accelY, accelZ, imuAxisRemap)

        // Store body frame values for visualization
        lastGyroBodyX = gyroBody[0]
        lastGyroBodyY = gyroBody[1]
        lastGyroBodyZ = gyroBody[2]
        lastAccelBodyX = accelBody[0]
        lastAccelBodyY = accelBody[1]
        lastAccelBodyZ = accelBody[2]

        // 6. Transform to NWU
        val gyroNwu = bodyToNWU(gyroBody[0], gyroBody[1], gyroBody[2])
        val accelNwu = bodyToNWU(accelBody[0], accelBody[1], accelBody[2])

        // Store for compass heading
        lastAccelNwuX = accelNwu[0]
        lastAccelNwuY = accelNwu[1]
        lastAccelNwuZ = accelNwu[2]

        // 7. Update AHRS
        if (magValid) {
            ahrs.update(
                gyroNwu[0], gyroNwu[1], gyroNwu[2],
                accelNwu[0], accelNwu[1], accelNwu[2],
                lastMagNwuX, lastMagNwuY, lastMagNwuZ,
                dt
            )
        } else {
            ahrs.updateNoMagnetometer(
                gyroNwu[0], gyroNwu[1], gyroNwu[2],
                accelNwu[0], accelNwu[1], accelNwu[2],
                dt
            )
        }
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Initialize from accelerometer and magnetometer using TRIAD method
     */
    fun initFromAccelMag(ax: Float, ay: Float, az: Float, mx: Float, my: Float, mz: Float) {
        // Apply calibration and transforms to get NWU values
        updateMag(mx, my, mz)

        var accelX = ax - imuCal.accelOffsetX
        var accelY = ay - imuCal.accelOffsetY
        var accelZ = az - imuCal.accelOffsetZ

        accelScaleMatrix?.let { matrix ->
            val result = applyMatrix3x3(matrix, accelX, accelY, accelZ)
            accelX = result[0]
            accelY = result[1]
            accelZ = result[2]
        }

        val accelBody = applyAxisRemap(accelX, accelY, accelZ, imuAxisRemap)
        val accelNwu = bodyToNWU(accelBody[0], accelBody[1], accelBody[2])

        // Compute initial quaternion using TRIAD
        val q = computeInitialQuaternion(
            accelNwu[0], accelNwu[1], accelNwu[2],
            lastMagNwuX, lastMagNwuY, lastMagNwuZ
        )
        if (q != null) {
            ahrs.setQuaternion(q[0], q[1], q[2], q[3])
        }
    }

    /**
     * Initialize from accelerometer only (6-DOF, heading = 0)
     */
    fun initFromAccelOnly(ax: Float, ay: Float, az: Float) {
        var accelX = ax - imuCal.accelOffsetX
        var accelY = ay - imuCal.accelOffsetY
        var accelZ = az - imuCal.accelOffsetZ

        accelScaleMatrix?.let { matrix ->
            val result = applyMatrix3x3(matrix, accelX, accelY, accelZ)
            accelX = result[0]
            accelY = result[1]
            accelZ = result[2]
        }

        val accelBody = applyAxisRemap(accelX, accelY, accelZ, imuAxisRemap)
        val accelNwu = bodyToNWU(accelBody[0], accelBody[1], accelBody[2])

        // Normalize
        val norm = sqrt(accelNwu[0] * accelNwu[0] + accelNwu[1] * accelNwu[1] + accelNwu[2] * accelNwu[2])
        if (norm < 0.1f) return

        val ax2 = accelNwu[0] / norm
        val ay2 = accelNwu[1] / norm
        val az2 = accelNwu[2] / norm

        // Compute pitch and roll from accelerometer
        val pitch = asin(-ax2)
        val roll = atan2(ay2, az2)

        // Convert to quaternion with heading = 0
        val cy = 1f  // cos(0/2)
        val sy = 0f  // sin(0/2)
        val cp = cos(pitch / 2f)
        val sp = sin(pitch / 2f)
        val cr = cos(roll / 2f)
        val sr = sin(roll / 2f)

        ahrs.setQuaternion(
            cy * cp * cr + sy * sp * sr,
            cy * cp * sr - sy * sp * cr,
            cy * sp * cr + sy * cp * sr,
            sy * cp * cr - cy * sp * sr
        )
    }

    /**
     * Compute initial quaternion using TRIAD method
     * Returns [w, x, y, z] or null if invalid
     */
    private fun computeInitialQuaternion(
        ax: Float, ay: Float, az: Float,
        mx: Float, my: Float, mz: Float
    ): FloatArray? {
        // Normalize accelerometer
        val aNorm = sqrt(ax * ax + ay * ay + az * az)
        if (aNorm < 0.1f) return null
        val ux = ax / aNorm
        val uy = ay / aNorm
        val uz = az / aNorm

        // Normalize magnetometer
        val mNorm = sqrt(mx * mx + my * my + mz * mz)
        if (mNorm < 0.01f) return null
        val magX = mx / mNorm
        val magY = my / mNorm
        val magZ = mz / mNorm

        // East = mag × up (cross product)
        var ex = magY * uz - magZ * uy
        var ey = magZ * ux - magX * uz
        var ez = magX * uy - magY * ux

        val eNorm = sqrt(ex * ex + ey * ey + ez * ez)
        if (eNorm < 0.01f) return null
        ex /= eNorm
        ey /= eNorm
        ez /= eNorm

        // North = up × east
        val nx = uy * ez - uz * ey
        val ny = uz * ex - ux * ez
        val nz = ux * ey - uy * ex

        // Build rotation matrix (columns are North, West, Up in NWU)
        // For NWU: West = -East
        val r00 = nx; val r01 = -ex; val r02 = ux
        val r10 = ny; val r11 = -ey; val r12 = uy
        val r20 = nz; val r21 = -ez; val r22 = uz

        // Convert rotation matrix to quaternion
        val trace = r00 + r11 + r22
        val qw: Float; val qx: Float; val qy: Float; val qz: Float

        if (trace > 0) {
            val s = sqrt(trace + 1f) * 2f
            qw = 0.25f * s
            qx = (r21 - r12) / s
            qy = (r02 - r20) / s
            qz = (r10 - r01) / s
        } else if (r00 > r11 && r00 > r22) {
            val s = sqrt(1f + r00 - r11 - r22) * 2f
            qw = (r21 - r12) / s
            qx = 0.25f * s
            qy = (r01 + r10) / s
            qz = (r02 + r20) / s
        } else if (r11 > r22) {
            val s = sqrt(1f + r11 - r00 - r22) * 2f
            qw = (r02 - r20) / s
            qx = (r01 + r10) / s
            qy = 0.25f * s
            qz = (r12 + r21) / s
        } else {
            val s = sqrt(1f + r22 - r00 - r11) * 2f
            qw = (r10 - r01) / s
            qx = (r02 + r20) / s
            qy = (r12 + r21) / s
            qz = 0.25f * s
        }

        return floatArrayOf(qw, qx, qy, qz)
    }

    // =========================================================================
    // Output Methods
    // =========================================================================

    /**
     * Get quaternion [w, x, y, z]
     */
    fun getQuaternion(): FloatArray = ahrs.getQuaternion()

    /**
     * Get Euler angles [heading, pitch, roll] in degrees
     */
    fun getEulerAngles(): FloatArray = ahrs.getEulerAngles()

    /**
     * Get heading in degrees (0-360)
     */
    fun getHeading(): Float {
        val euler = ahrs.getEulerAngles()
        var heading = euler[0]
        if (heading < 0) heading += 360f
        return heading
    }

    /**
     * Get calibrated magnetometer in body frame
     */
    fun getCalibratedMag(): FloatArray = floatArrayOf(lastMagBodyX, lastMagBodyY, lastMagBodyZ)

    /**
     * Get calibrated accelerometer in body frame (in g)
     */
    fun getCalibratedAccel(): FloatArray = floatArrayOf(lastAccelBodyX, lastAccelBodyY, lastAccelBodyZ)

    /**
     * Get calibrated gyroscope in body frame (in deg/s, with runtime bias removed)
     */
    fun getCalibratedGyro(): FloatArray = floatArrayOf(lastGyroBodyX, lastGyroBodyY, lastGyroBodyZ)

    fun isMagValid(): Boolean = magValid

    /**
     * Get gravity vector in body frame
     */
    fun getGravityVector(): FloatArray {
        val gravityNwu = ahrs.getGravity()
        return nwuToBody(gravityNwu[0], gravityNwu[1], gravityNwu[2])
    }

    /**
     * Get linear acceleration in body frame (gravity removed)
     */
    fun getLinearAcceleration(): FloatArray {
        val linAccelNwu = ahrs.getLinearAcceleration()
        return nwuToBody(linAccelNwu[0], linAccelNwu[1], linAccelNwu[2])
    }

    /**
     * Get Earth-frame acceleration in NWU (gravity removed)
     */
    fun getEarthAcceleration(): FloatArray = ahrs.getEarthAcceleration()

    /**
     * Get internal AHRS states for display
     */
    fun getInternalStates(): FusionAhrs.InternalStates = ahrs.getInternalStates()

    /**
     * Get AHRS flags
     */
    fun getFlags(): FusionAhrs.Flags = ahrs.getFlags()

    /**
     * Get bias estimation state
     */
    fun getBiasState(): FusionBias.BiasState = bias.getBiasState()

    /**
     * Check if AHRS is still initializing (ramping gain)
     */
    fun isInitialising(): Boolean = ahrs.isInitialising()

    /**
     * Get current ramped gain
     */
    fun getRampedGain(): Float = ahrs.getRampedGain()

    // =========================================================================
    // NWU-frame sensor accessors for visualization
    // =========================================================================

    /**
     * Get accelerometer in NWU frame (for visualization)
     * This is the sensor measurement, not rotated to world frame.
     */
    fun getAccelNWU(): FloatArray {
        return bodyToNWU(lastAccelBodyX, lastAccelBodyY, lastAccelBodyZ)
    }

    /**
     * Get magnetometer in NWU frame (for visualization)
     */
    fun getMagNWU(): FloatArray {
        return bodyToNWU(lastMagBodyX, lastMagBodyY, lastMagBodyZ)
    }

    /**
     * Get gravity vector in NWU frame (from AHRS estimate)
     * Unlike body-frame gravity, this is already in NWU coordinates.
     */
    fun getGravityNWU(): FloatArray {
        return ahrs.getGravity()
    }
}
