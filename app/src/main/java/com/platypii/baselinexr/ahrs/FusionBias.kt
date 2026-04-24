package com.platypii.baselinexr.ahrs

import kotlin.math.sqrt

/**
 * FusionBias - Runtime Gyroscope Bias Estimation
 *
 * Estimates gyroscope bias during periods when the sensor is stationary.
 * Uses a low-pass filter to track slowly-varying bias while the sensor
 * is still (gyroscope magnitude below threshold for timeout period).
 *
 * Based on x-io Fusion library bias correction approach.
 */
class FusionBias(settings: FusionBiasSettings = FusionBiasSettings()) {

    data class FusionBiasSettings(
        /** Gyroscope magnitude threshold for stationary detection (deg/s) */
        val stationaryThreshold: Float = DEFAULT_STATIONARY_THRESHOLD_DEG,
        /** Time sensor must be stationary before bias update begins (seconds) */
        val stationaryTimeout: Float = DEFAULT_STATIONARY_TIMEOUT,
        /** Low-pass filter coefficient (0-1, lower = slower tracking) */
        val lpfCoefficient: Float = DEFAULT_LPF_COEFFICIENT
    )

    companion object {
        /** Default threshold in deg/s for detecting stationary state */
        private const val DEFAULT_STATIONARY_THRESHOLD_DEG = 3.0f

        /** Default timeout in seconds before bias update starts */
        private const val DEFAULT_STATIONARY_TIMEOUT = 1.0f

        /** Default low-pass filter coefficient */
        private const val DEFAULT_LPF_COEFFICIENT = 0.0001f

        private const val DEG_TO_RAD = Math.PI.toFloat() / 180f
        private const val RAD_TO_DEG = 180f / Math.PI.toFloat()
    }

    private var settings: FusionBiasSettings = settings

    /** Accumulated stationary time */
    private var stationaryTimer: Float = 0f

    /** Current bias estimate (deg/s) */
    private var biasX: Float = 0f
    private var biasY: Float = 0f
    private var biasZ: Float = 0f

    /** Whether currently updating bias */
    private var isCalibrating: Boolean = false

    /** Last gyro magnitude for debugging (deg/s) */
    private var lastMagnitude: Float = 0f

    /**
     * Apply new settings
     */
    fun applySettings(newSettings: FusionBiasSettings) {
        settings = newSettings
    }

    /**
     * Reset bias estimate to zero
     */
    fun reset() {
        stationaryTimer = 0f
        biasX = 0f
        biasY = 0f
        biasZ = 0f
        isCalibrating = false
        lastMagnitude = 0f
    }

    /**
     * Update bias estimate and return corrected gyroscope
     *
     * @param gyroX Raw gyroscope X in deg/s
     * @param gyroY Raw gyroscope Y in deg/s
     * @param gyroZ Raw gyroscope Z in deg/s
     * @param deltaTime Time step in seconds
     * @return Bias-corrected gyroscope [x, y, z] in deg/s
     */
    fun update(gyroX: Float, gyroY: Float, gyroZ: Float, deltaTime: Float): FloatArray {
        // Calculate corrected gyroscope (subtract current bias)
        val correctedX = gyroX - biasX
        val correctedY = gyroY - biasY
        val correctedZ = gyroZ - biasZ

        // Check if stationary (use corrected values)
        val magnitude = sqrt(
            correctedX * correctedX +
            correctedY * correctedY +
            correctedZ * correctedZ
        )
        lastMagnitude = magnitude

        if (magnitude < settings.stationaryThreshold) {
            // Accumulate stationary time
            stationaryTimer += deltaTime

            // Start bias update after timeout
            if (stationaryTimer >= settings.stationaryTimeout) {
                isCalibrating = true

                // Low-pass filter update: bias = bias + k * (gyro - bias)
                // Which simplifies to: bias += k * corrected
                val k = settings.lpfCoefficient
                biasX += k * correctedX
                biasY += k * correctedY
                biasZ += k * correctedZ
            }
        } else {
            // Not stationary - reset timer
            stationaryTimer = 0f
            isCalibrating = false
        }

        return floatArrayOf(correctedX, correctedY, correctedZ)
    }

    /**
     * Get current bias estimate (deg/s)
     */
    fun getBias(): FloatArray = floatArrayOf(biasX, biasY, biasZ)

    /**
     * Set bias directly (for loading saved calibration)
     */
    fun setBias(x: Float, y: Float, z: Float) {
        biasX = x
        biasY = y
        biasZ = z
    }

    /**
     * Check if currently calibrating (updating bias)
     */
    fun isCurrentlyCalibrating(): Boolean = isCalibrating

    /**
     * Get time spent stationary (seconds)
     */
    fun getStationaryTime(): Float = stationaryTimer

    /**
     * Get progress toward calibration start (0 to 1)
     */
    fun getCalibrationProgress(): Float = 
        minOf(1f, stationaryTimer / settings.stationaryTimeout)

    /**
     * Get last gyro magnitude in deg/s for display
     */
    fun getGyroMagnitudeDegrees(): Float = lastMagnitude

    /**
     * Get stationary threshold in deg/s for display
     */
    fun getStationaryThresholdDegrees(): Float = settings.stationaryThreshold

    /**
     * Get bias state for UI display
     */
    fun getBiasState(): BiasState = BiasState(
        biasX = biasX,
        biasY = biasY,
        biasZ = biasZ,
        isCalibrating = isCalibrating,
        progress = getCalibrationProgress(),
        gyroMagnitude = lastMagnitude,
        stationaryThreshold = settings.stationaryThreshold
    )

    data class BiasState(
        val biasX: Float,
        val biasY: Float,
        val biasZ: Float,
        val isCalibrating: Boolean,
        val progress: Float,
        val gyroMagnitude: Float,
        val stationaryThreshold: Float
    )
}
