package com.platypii.baselinexr.ahrs

import kotlin.math.*

/**
 * FusionAhrs - Attitude and Heading Reference System (AHRS) Algorithm
 *
 * Port of x-io Technologies Fusion library by Seb Madgwick.
 * Implements the Chapter 7 AHRS algorithm with:
 * - Gradient descent quaternion optimization
 * - Accelerometer rejection (ignores accel when |a| != 1g)
 * - Magnetometer rejection (ignores mag when heading error too large)
 * - Recovery trigger mechanism
 * - Gain ramping during initialization
 *
 * Coordinate convention: NWU (North-West-Up)
 */
class FusionAhrs(settings: FusionAhrsSettings = FusionAhrsSettings()) {

    /**
     * AHRS settings
     */
    data class FusionAhrsSettings(
        /** Algorithm gain - higher = faster convergence, more noise */
        val gain: Float = 0.5f,
        /** Gyroscope range in deg/s (0 = unlimited) */
        val gyroscopeRange: Float = 2000f,
        /** Acceleration rejection threshold in degrees (0 = disabled) */
        val accelerationRejection: Float = 10f,
        /** Magnetic rejection threshold in degrees (0 = disabled) */
        val magneticRejection: Float = 10f,
        /** Recovery trigger period in seconds (0 = disabled) */
        val recoveryTriggerPeriod: Float = 5.0f
    )

    /**
     * Internal states for debugging/display
     */
    data class InternalStates(
        val accelerationError: Float,
        val accelerometerIgnored: Boolean,
        val accelerationRecoveryTrigger: Float,
        val magneticError: Float,
        val magnetometerIgnored: Boolean,
        val magneticRecoveryTrigger: Float
    )

    /**
     * AHRS flags
     */
    data class Flags(
        val initialising: Boolean,
        val angularRateRecovery: Boolean,
        val accelerationRecovery: Boolean,
        val magneticRecovery: Boolean
    )

    companion object {
        private const val DEG_TO_RAD = (PI / 180.0).toFloat()
        private const val RAD_TO_DEG = (180.0 / PI).toFloat()

        /** Initial gain used during initialization */
        private const val INITIAL_GAIN = 10.0f

        /** Initialization period in seconds */
        private const val INITIALISATION_PERIOD = 3.0f
    }

    // Settings (converted for internal use)
    private var settingsGain: Float = settings.gain
    private var settingsGyroscopeRange: Float = 
        if (settings.gyroscopeRange == 0f) Float.MAX_VALUE else 0.98f * settings.gyroscopeRange
    private var settingsAccelerationRejection: Float = 
        if (settings.accelerationRejection == 0f) Float.MAX_VALUE 
        else (0.5f * sin(settings.accelerationRejection * DEG_TO_RAD)).pow(2)
    private var settingsMagneticRejection: Float = 
        if (settings.magneticRejection == 0f) Float.MAX_VALUE 
        else (0.5f * sin(settings.magneticRejection * DEG_TO_RAD)).pow(2)
    private var settingsRecoveryTriggerPeriod: Float = settings.recoveryTriggerPeriod

    // Quaternion (w, x, y, z)
    private var qw: Float = 1f
    private var qx: Float = 0f
    private var qy: Float = 0f
    private var qz: Float = 0f

    // Last accelerometer reading (for linear acceleration calculation)
    private var accelX: Float = 0f
    private var accelY: Float = 0f
    private var accelZ: Float = 0f

    // Initialization state
    private var initialising: Boolean = true
    private var reinitialising: Boolean = false
    private var rampedGain: Float = INITIAL_GAIN
    private var rampedGainStep: Float = (INITIAL_GAIN - settingsGain) / INITIALISATION_PERIOD
    private var angularRateRecovery: Boolean = false

    // Feedback vectors (for error calculation)
    private var halfAccelFeedbackX: Float = 0f
    private var halfAccelFeedbackY: Float = 0f
    private var halfAccelFeedbackZ: Float = 0f
    private var halfMagFeedbackX: Float = 0f
    private var halfMagFeedbackY: Float = 0f
    private var halfMagFeedbackZ: Float = 0f

    // Rejection state
    private var accelerometerIgnored: Boolean = false
    private var accelerationRecoveryTrigger: Float = 0f
    private var accelerationRecoveryTimeout: Float = settingsRecoveryTriggerPeriod
    private var magnetometerIgnored: Boolean = false
    private var magneticRecoveryTrigger: Float = 0f
    private var magneticRecoveryTimeout: Float = settingsRecoveryTriggerPeriod

    /**
     * Apply new settings
     */
    fun setSettings(settings: FusionAhrsSettings) {
        settingsGain = settings.gain
        settingsGyroscopeRange = 
            if (settings.gyroscopeRange == 0f) Float.MAX_VALUE else 0.98f * settings.gyroscopeRange
        settingsAccelerationRejection = 
            if (settings.accelerationRejection == 0f) Float.MAX_VALUE 
            else (0.5f * sin(settings.accelerationRejection * DEG_TO_RAD)).pow(2)
        settingsMagneticRejection = 
            if (settings.magneticRejection == 0f) Float.MAX_VALUE 
            else (0.5f * sin(settings.magneticRejection * DEG_TO_RAD)).pow(2)
        settingsRecoveryTriggerPeriod = settings.recoveryTriggerPeriod
        
        accelerationRecoveryTimeout = settingsRecoveryTriggerPeriod
        magneticRecoveryTimeout = settingsRecoveryTriggerPeriod

        // Disable rejection if gain is zero or no recovery period
        if (settings.gain == 0f || settings.recoveryTriggerPeriod == 0f) {
            settingsAccelerationRejection = Float.MAX_VALUE
            settingsMagneticRejection = Float.MAX_VALUE
        }

        if (!initialising) {
            rampedGain = settingsGain
        }
        rampedGainStep = (INITIAL_GAIN - settingsGain) / INITIALISATION_PERIOD
    }

    /**
     * Reset the AHRS algorithm
     */
    fun reset() {
        qw = 1f
        qx = 0f
        qy = 0f
        qz = 0f
        accelX = 0f
        accelY = 0f
        accelZ = 0f
        initialising = true
        reinitialising = false
        rampedGain = INITIAL_GAIN
        angularRateRecovery = false
        halfAccelFeedbackX = 0f
        halfAccelFeedbackY = 0f
        halfAccelFeedbackZ = 0f
        halfMagFeedbackX = 0f
        halfMagFeedbackY = 0f
        halfMagFeedbackZ = 0f
        accelerometerIgnored = false
        accelerationRecoveryTrigger = 0f
        accelerationRecoveryTimeout = settingsRecoveryTriggerPeriod
        magnetometerIgnored = false
        magneticRecoveryTrigger = 0f
        magneticRecoveryTimeout = settingsRecoveryTriggerPeriod
    }

    /**
     * Update with gyroscope, accelerometer, and magnetometer
     *
     * @param gx Gyroscope X in deg/s
     * @param gy Gyroscope Y in deg/s
     * @param gz Gyroscope Z in deg/s
     * @param ax Accelerometer X in g
     * @param ay Accelerometer Y in g
     * @param az Accelerometer Z in g
     * @param mx Magnetometer X (calibrated, any units)
     * @param my Magnetometer Y
     * @param mz Magnetometer Z
     * @param deltaTime Delta time in seconds
     */
    fun update(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float,
        mx: Float, my: Float, mz: Float,
        deltaTime: Float
    ) {
        // Store accelerometer for linear acceleration
        accelX = ax
        accelY = ay
        accelZ = az

        // Reinitialize if gyroscope range exceeded
        if (abs(gx) > settingsGyroscopeRange || 
            abs(gy) > settingsGyroscopeRange || 
            abs(gz) > settingsGyroscopeRange) {
            // Keep current quaternion but reset initialization
            val saveQw = qw; val saveQx = qx; val saveQy = qy; val saveQz = qz
            reset()
            qw = saveQw; qx = saveQx; qy = saveQy; qz = saveQz
            angularRateRecovery = true
        }

        // Ramp down gain during initialization
        if (initialising) {
            rampedGain -= rampedGainStep * deltaTime
            if (rampedGain < settingsGain || settingsGain == 0f) {
                rampedGain = settingsGain
                initialising = false
                angularRateRecovery = false
            }
        }

        // Calculate direction of gravity indicated by algorithm (NWU convention)
        // Third column of transposed rotation matrix scaled by 0.5
        val halfGravityX = qx * qz - qw * qy
        val halfGravityY = qy * qz + qw * qx
        val halfGravityZ = qw * qw - 0.5f + qz * qz

        // Calculate accelerometer feedback
        var halfAccelFbX = 0f
        var halfAccelFbY = 0f
        var halfAccelFbZ = 0f
        accelerometerIgnored = true

        if (ax != 0f || ay != 0f || az != 0f) {
            // Normalize accelerometer
            val accelNorm = sqrt(ax * ax + ay * ay + az * az)
            val accelNormX = ax / accelNorm
            val accelNormY = ay / accelNorm
            val accelNormZ = az / accelNorm

            // Calculate feedback (cross product of sensor and reference)
            val fbX = accelNormY * halfGravityZ - accelNormZ * halfGravityY
            val fbY = accelNormZ * halfGravityX - accelNormX * halfGravityZ
            val fbZ = accelNormX * halfGravityY - accelNormY * halfGravityX

            // Check if error > 90 degrees (dot product negative)
            val dot = accelNormX * halfGravityX + accelNormY * halfGravityY + accelNormZ * halfGravityZ
            if (dot < 0f) {
                // Normalize feedback if error > 90 degrees
                val fbNorm = sqrt(fbX * fbX + fbY * fbY + fbZ * fbZ)
                if (fbNorm > 0f) {
                    halfAccelFeedbackX = fbX / fbNorm
                    halfAccelFeedbackY = fbY / fbNorm
                    halfAccelFeedbackZ = fbZ / fbNorm
                }
            } else {
                halfAccelFeedbackX = fbX
                halfAccelFeedbackY = fbY
                halfAccelFeedbackZ = fbZ
            }

            // Calculate feedback norm squared for rejection test
            val feedbackNormSq = halfAccelFeedbackX * halfAccelFeedbackX + 
                                 halfAccelFeedbackY * halfAccelFeedbackY + 
                                 halfAccelFeedbackZ * halfAccelFeedbackZ

            // Don't ignore accelerometer if error below threshold
            // Time-based: decrement by 9*dt when good, increment by 1*dt when bad
            // This maintains the 9:1 ratio regardless of sample rate
            if (initialising || feedbackNormSq <= settingsAccelerationRejection) {
                accelerometerIgnored = false
                accelerationRecoveryTrigger -= 9f * deltaTime
            } else {
                accelerationRecoveryTrigger += deltaTime
            }

            // Don't ignore during acceleration recovery
            if (accelerationRecoveryTrigger > accelerationRecoveryTimeout) {
                accelerationRecoveryTimeout = 0f
                accelerometerIgnored = false
                reinitialising = true  // Trigger reinitialization from sensors
            } else {
                accelerationRecoveryTimeout = settingsRecoveryTriggerPeriod
            }
            accelerationRecoveryTrigger = accelerationRecoveryTrigger.coerceIn(0f, settingsRecoveryTriggerPeriod)

            // Apply accelerometer feedback
            if (!accelerometerIgnored) {
                halfAccelFbX = halfAccelFeedbackX
                halfAccelFbY = halfAccelFeedbackY
                halfAccelFbZ = halfAccelFeedbackZ
            }
        }

        // Calculate magnetometer feedback
        var halfMagFbX = 0f
        var halfMagFbY = 0f
        var halfMagFbZ = 0f
        magnetometerIgnored = true

        if (mx != 0f || my != 0f || mz != 0f) {
            // Calculate direction of magnetic field indicated by algorithm (NWU)
            // Second column of transposed rotation matrix scaled by 0.5
            val halfMagneticX = qx * qy + qw * qz
            val halfMagneticY = qw * qw - 0.5f + qy * qy
            val halfMagneticZ = qy * qz - qw * qx

            // Cross product of half gravity with magnetometer
            val crossX = halfGravityY * mz - halfGravityZ * my
            val crossY = halfGravityZ * mx - halfGravityX * mz
            val crossZ = halfGravityX * my - halfGravityY * mx

            // Normalize
            val crossNorm = sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
            if (crossNorm > 0f) {
                val crossNormX = crossX / crossNorm
                val crossNormY = crossY / crossNorm
                val crossNormZ = crossZ / crossNorm

                // Calculate feedback (cross product of sensor and reference)
                val fbX = crossNormY * halfMagneticZ - crossNormZ * halfMagneticY
                val fbY = crossNormZ * halfMagneticX - crossNormX * halfMagneticZ
                val fbZ = crossNormX * halfMagneticY - crossNormY * halfMagneticX

                // Check if error > 90 degrees
                val dot = crossNormX * halfMagneticX + crossNormY * halfMagneticY + crossNormZ * halfMagneticZ
                if (dot < 0f) {
                    val fbNorm = sqrt(fbX * fbX + fbY * fbY + fbZ * fbZ)
                    if (fbNorm > 0f) {
                        halfMagFeedbackX = fbX / fbNorm
                        halfMagFeedbackY = fbY / fbNorm
                        halfMagFeedbackZ = fbZ / fbNorm
                    }
                } else {
                    halfMagFeedbackX = fbX
                    halfMagFeedbackY = fbY
                    halfMagFeedbackZ = fbZ
                }

                val feedbackNormSq = halfMagFeedbackX * halfMagFeedbackX + 
                                     halfMagFeedbackY * halfMagFeedbackY + 
                                     halfMagFeedbackZ * halfMagFeedbackZ

                // Don't ignore magnetometer if error below threshold
                // Time-based: decrement by 9*dt when good, increment by 1*dt when bad
                // This maintains the 9:1 ratio regardless of sample rate
                if (initialising || feedbackNormSq <= settingsMagneticRejection) {
                    magnetometerIgnored = false
                    magneticRecoveryTrigger -= 9f * deltaTime
                } else {
                    magneticRecoveryTrigger += deltaTime
                }

                // Don't ignore during magnetic recovery
                if (magneticRecoveryTrigger > magneticRecoveryTimeout) {
                    magneticRecoveryTimeout = 0f
                    magnetometerIgnored = false
                    reinitialising = true  // Trigger reinitialization from sensors
                } else {
                    magneticRecoveryTimeout = settingsRecoveryTriggerPeriod
                }
                magneticRecoveryTrigger = magneticRecoveryTrigger.coerceIn(0f, settingsRecoveryTriggerPeriod)

                // Apply magnetometer feedback
                if (!magnetometerIgnored) {
                    halfMagFbX = halfMagFeedbackX
                    halfMagFbY = halfMagFeedbackY
                    halfMagFbZ = halfMagFeedbackZ
                }
            }
        }

        // Convert gyroscope to radians per second scaled by 0.5
        val halfGyroX = gx * DEG_TO_RAD * 0.5f
        val halfGyroY = gy * DEG_TO_RAD * 0.5f
        val halfGyroZ = gz * DEG_TO_RAD * 0.5f

        // Apply feedback to gyroscope
        val adjustedHalfGyroX = halfGyroX + (halfAccelFbX + halfMagFbX) * rampedGain
        val adjustedHalfGyroY = halfGyroY + (halfAccelFbY + halfMagFbY) * rampedGain
        val adjustedHalfGyroZ = halfGyroZ + (halfAccelFbZ + halfMagFbZ) * rampedGain

        // Integrate rate of change of quaternion
        // q_dot = 0.5 * q * omega
        qw += (-qx * adjustedHalfGyroX - qy * adjustedHalfGyroY - qz * adjustedHalfGyroZ) * deltaTime
        qx += (qw * adjustedHalfGyroX + qy * adjustedHalfGyroZ - qz * adjustedHalfGyroY) * deltaTime
        qy += (qw * adjustedHalfGyroY - qx * adjustedHalfGyroZ + qz * adjustedHalfGyroX) * deltaTime
        qz += (qw * adjustedHalfGyroZ + qx * adjustedHalfGyroY - qy * adjustedHalfGyroX) * deltaTime

        // Normalize quaternion
        val norm = sqrt(qw * qw + qx * qx + qy * qy + qz * qz)
        if (norm > 0f) {
            qw /= norm
            qx /= norm
            qy /= norm
            qz /= norm
        }

        // Handle reinitialization from sensors (after recovery trigger)
        if (reinitialising) {
            reinitialiseFromSensors(ax, ay, az, mx, my, mz)
            reinitialising = false
            
            // Reset recovery counters
            accelerationRecoveryTrigger = 0f
            magneticRecoveryTrigger = 0f
            accelerationRecoveryTimeout = settingsRecoveryTriggerPeriod
            magneticRecoveryTimeout = settingsRecoveryTriggerPeriod
        }
    }

    /**
     * Update with gyroscope and accelerometer only (no magnetometer)
     */
    fun updateNoMagnetometer(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float,
        deltaTime: Float
    ) {
        update(gx, gy, gz, ax, ay, az, 0f, 0f, 0f, deltaTime)

        // Zero heading during initialization
        if (initialising) {
            setHeading(0f)
        }
    }

    /**
     * Get quaternion as array [w, x, y, z]
     */
    fun getQuaternion(): FloatArray = floatArrayOf(qw, qx, qy, qz)

    /**
     * Set quaternion directly
     */
    fun setQuaternion(w: Float, x: Float, y: Float, z: Float) {
        qw = w
        qx = x
        qy = y
        qz = z
    }

    /**
     * Reinitialize quaternion from accelerometer and magnetometer using TRIAD method.
     * Called during recovery when sensors have been rejected for too long.
     * 
     * @param ax Accelerometer X in g (NWU frame)
     * @param ay Accelerometer Y in g
     * @param az Accelerometer Z in g
     * @param mx Magnetometer X (NWU frame)
     * @param my Magnetometer Y
     * @param mz Magnetometer Z
     */
    private fun reinitialiseFromSensors(
        ax: Float, ay: Float, az: Float,
        mx: Float, my: Float, mz: Float
    ) {
        // Normalize accelerometer (points up in NWU when stationary)
        val aNorm = sqrt(ax * ax + ay * ay + az * az)
        if (aNorm < 0.1f) return  // Invalid accelerometer
        val ux = ax / aNorm
        val uy = ay / aNorm
        val uz = az / aNorm

        // If no valid magnetometer, just set from accelerometer with heading = 0
        val mNorm = sqrt(mx * mx + my * my + mz * mz)
        if (mNorm < 0.01f) {
            // Compute pitch and roll from accelerometer only
            val pitch = asin((-ux).coerceIn(-1f, 1f))
            val roll = atan2(uy, uz)
            
            // Convert to quaternion with heading = 0
            val cp = cos(pitch / 2f)
            val sp = sin(pitch / 2f)
            val cr = cos(roll / 2f)
            val sr = sin(roll / 2f)
            
            qw = cp * cr
            qx = cp * sr
            qy = sp * cr
            qz = -sp * sr
            return
        }

        // Normalize magnetometer
        val magX = mx / mNorm
        val magY = my / mNorm
        val magZ = mz / mNorm

        // TRIAD method: compute orthonormal frame from accel (up) and mag
        // East = mag × up (cross product)
        var ex = magY * uz - magZ * uy
        var ey = magZ * ux - magX * uz
        var ez = magX * uy - magY * ux

        val eNorm = sqrt(ex * ex + ey * ey + ez * ez)
        if (eNorm < 0.01f) return  // Mag parallel to gravity - can't determine heading
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

        // Normalize quaternion
        val qNorm = sqrt(qw * qw + qx * qx + qy * qy + qz * qz)
        if (qNorm > 0f) {
            qw /= qNorm
            qx /= qNorm
            qy /= qNorm
            qz /= qNorm
        }

        // Restart gain ramping after reinitialization
        initialising = true
        rampedGain = INITIAL_GAIN
    }

    /**
     * Get direction of gravity as unit vector
     */
    fun getGravity(): FloatArray {
        // Third column of transposed rotation matrix (NWU)
        return floatArrayOf(
            2f * (qx * qz - qw * qy),
            2f * (qy * qz + qw * qx),
            2f * (qw * qw - 0.5f + qz * qz)
        )
    }

    /**
     * Get linear acceleration (gravity removed) in body frame, in g
     */
    fun getLinearAcceleration(): FloatArray {
        val gravity = getGravity()
        // NWU convention: subtract gravity
        return floatArrayOf(
            accelX - gravity[0],
            accelY - gravity[1],
            accelZ - gravity[2]
        )
    }

    /**
     * Get Earth-frame acceleration (gravity removed) in g
     */
    fun getEarthAcceleration(): FloatArray {
        // Rotate accelerometer to Earth frame
        val earthAccelX = 2f * ((qw * qw - 0.5f + qx * qx) * accelX + (qx * qy - qw * qz) * accelY + (qx * qz + qw * qy) * accelZ)
        val earthAccelY = 2f * ((qx * qy + qw * qz) * accelX + (qw * qw - 0.5f + qy * qy) * accelY + (qy * qz - qw * qx) * accelZ)
        val earthAccelZ = 2f * ((qx * qz - qw * qy) * accelX + (qy * qz + qw * qx) * accelY + (qw * qw - 0.5f + qz * qz) * accelZ)

        // Remove gravity (NWU: subtract 1 from Z)
        return floatArrayOf(earthAccelX, earthAccelY, earthAccelZ - 1f)
    }

    /**
     * Get internal states for display
     */
    fun getInternalStates(): InternalStates {
        val halfAccelNorm = sqrt(
            halfAccelFeedbackX * halfAccelFeedbackX +
            halfAccelFeedbackY * halfAccelFeedbackY +
            halfAccelFeedbackZ * halfAccelFeedbackZ
        )
        val halfMagNorm = sqrt(
            halfMagFeedbackX * halfMagFeedbackX +
            halfMagFeedbackY * halfMagFeedbackY +
            halfMagFeedbackZ * halfMagFeedbackZ
        )

        // Calculate error angles
        val accelError = asin((2f * halfAccelNorm).coerceIn(-1f, 1f)) * RAD_TO_DEG
        val magError = asin((2f * halfMagNorm).coerceIn(-1f, 1f)) * RAD_TO_DEG

        val accelRecoveryTriggerNorm = if (settingsRecoveryTriggerPeriod == 0f) 0f 
            else accelerationRecoveryTrigger / settingsRecoveryTriggerPeriod
        val magRecoveryTriggerNorm = if (settingsRecoveryTriggerPeriod == 0f) 0f 
            else magneticRecoveryTrigger / settingsRecoveryTriggerPeriod

        return InternalStates(
            accelerationError = accelError,
            accelerometerIgnored = accelerometerIgnored,
            accelerationRecoveryTrigger = accelRecoveryTriggerNorm,
            magneticError = magError,
            magnetometerIgnored = magnetometerIgnored,
            magneticRecoveryTrigger = magRecoveryTriggerNorm
        )
    }

    /**
     * Get flags
     */
    fun getFlags(): Flags {
        return Flags(
            initialising = initialising,
            angularRateRecovery = angularRateRecovery,
            accelerationRecovery = accelerationRecoveryTrigger > accelerationRecoveryTimeout,
            magneticRecovery = magneticRecoveryTrigger > magneticRecoveryTimeout
        )
    }

    /**
     * Get current ramped gain (for display during initialization)
     */
    fun getRampedGain(): Float = rampedGain

    /**
     * Check if still initializing
     */
    fun isInitialising(): Boolean = initialising

    /**
     * Set heading (yaw) to specified value in degrees
     */
    fun setHeading(heading: Float) {
        val yaw = atan2(qw * qz + qx * qy, 0.5f - qy * qy - qz * qz)
        val halfYawMinusHeading = 0.5f * (yaw - heading * DEG_TO_RAD)
        val rotW = cos(halfYawMinusHeading)
        val rotZ = -sin(halfYawMinusHeading)

        // Quaternion product: rotation * quaternion
        val newQw = rotW * qw - rotZ * qz
        val newQx = rotW * qx - rotZ * qy
        val newQy = rotW * qy + rotZ * qx
        val newQz = rotW * qz + rotZ * qw

        qw = newQw
        qx = newQx
        qy = newQy
        qz = newQz
    }

    /**
     * Get Euler angles in degrees [heading, pitch, roll]
     * Heading: 0 = North, increases clockwise (East = 90°)
     * Pitch: positive = nose up
     * Roll: positive = right side down
     */
    fun getEulerAngles(): FloatArray {
        // Heading (yaw) - rotation around Z axis
        val heading = atan2(qw * qz + qx * qy, 0.5f - qy * qy - qz * qz) * RAD_TO_DEG

        // Pitch - rotation around Y axis
        val sinPitch = 2f * (qw * qy - qz * qx)
        val pitch = asin(sinPitch.coerceIn(-1f, 1f)) * RAD_TO_DEG

        // Roll - rotation around X axis
        val roll = atan2(qw * qx + qy * qz, 0.5f - qx * qx - qy * qy) * RAD_TO_DEG

        return floatArrayOf(heading, pitch, roll)
    }
}
