package com.platypii.baselinexr.calibration

/**
 * Result of magnetometer calibration containing both hard iron and soft iron corrections.
 * 
 * The magnetic field distortion model:
 *   m_measured = A * m_true + b
 * 
 * Where A is soft iron (3x3) and b is hard iron offset (3x1).
 * 
 * Calibration transforms:
 *   m_corrected = W * (m_measured - V)
 * 
 * Where V is hard iron offset and W is soft iron correction matrix.
 */
data class MagCalibrationResult(
    /** Hard iron offset X (gauss) - center of ellipsoid */
    val offsetX: Float,
    /** Hard iron offset Y (gauss) - center of ellipsoid */
    val offsetY: Float,
    /** Hard iron offset Z (gauss) - center of ellipsoid */
    val offsetZ: Float,

    /** Soft iron correction matrix (3x3) - transforms ellipsoid to sphere */
    val softIronMatrix: FloatArray,

    /** Semi-axes lengths from ellipsoid fit (a, b, c) */
    val semiAxes: FloatArray,

    /** Expected field magnitude after calibration (gauss) */
    val referenceMagnitude: Float,

    /** Sphericity metric (0-1, where 1 = perfect sphere) */
    val sphericity: Float,

    /** RMS residual error after calibration */
    val residualRms: Float,

    /** Number of samples used for calibration */
    val sampleCount: Int,

    /** Quality score (0-100%) based on sphericity and residual */
    val quality: Float,

    /** Calibration type: "hard_iron" or "soft_iron" */
    val calibrationType: String,

    /** Timestamp when calibration was performed */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Apply calibration to a raw magnetometer reading.
     * For hard iron only: corrected = raw - offset
     * For soft iron: corrected = softIronMatrix * (raw - offset)
     */
    fun apply(rawX: Float, rawY: Float, rawZ: Float): FloatArray {
        val shiftedX = rawX - offsetX
        val shiftedY = rawY - offsetY
        val shiftedZ = rawZ - offsetZ

        return if (calibrationType == "hard_iron") {
            floatArrayOf(shiftedX, shiftedY, shiftedZ)
        } else {
            // Apply soft iron correction: W * shifted
            floatArrayOf(
                softIronMatrix[0] * shiftedX + softIronMatrix[1] * shiftedY + softIronMatrix[2] * shiftedZ,
                softIronMatrix[3] * shiftedX + softIronMatrix[4] * shiftedY + softIronMatrix[5] * shiftedZ,
                softIronMatrix[6] * shiftedX + softIronMatrix[7] * shiftedY + softIronMatrix[8] * shiftedZ
            )
        }
    }

    /**
     * Get the magnitude of a calibrated reading.
     */
    fun getCalibratedMagnitude(rawX: Float, rawY: Float, rawZ: Float): Float {
        val corrected = apply(rawX, rawY, rawZ)
        return kotlin.math.sqrt(
            corrected[0] * corrected[0] +
            corrected[1] * corrected[1] +
            corrected[2] * corrected[2]
        )
    }

    /**
     * Format the soft iron matrix for display (3x3 grid).
     */
    fun formatSoftIronMatrix(): String {
        return String.format(
            "%+.4f  %+.4f  %+.4f\n%+.4f  %+.4f  %+.4f\n%+.4f  %+.4f  %+.4f",
            softIronMatrix[0], softIronMatrix[1], softIronMatrix[2],
            softIronMatrix[3], softIronMatrix[4], softIronMatrix[5],
            softIronMatrix[6], softIronMatrix[7], softIronMatrix[8]
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MagCalibrationResult) return false
        return offsetX == other.offsetX &&
               offsetY == other.offsetY &&
               offsetZ == other.offsetZ &&
               softIronMatrix.contentEquals(other.softIronMatrix) &&
               calibrationType == other.calibrationType
    }

    override fun hashCode(): Int {
        var result = offsetX.hashCode()
        result = 31 * result + offsetY.hashCode()
        result = 31 * result + offsetZ.hashCode()
        result = 31 * result + softIronMatrix.contentHashCode()
        result = 31 * result + calibrationType.hashCode()
        return result
    }

    companion object {
        /** Identity matrix for hard iron only calibration */
        val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )

        /** Create a hard iron only calibration result */
        fun hardIronOnly(
            offsetX: Float,
            offsetY: Float,
            offsetZ: Float,
            magnitude: Float,
            sphericity: Float,
            sampleCount: Int
        ): MagCalibrationResult {
            return MagCalibrationResult(
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                softIronMatrix = IDENTITY_MATRIX.copyOf(),
                semiAxes = floatArrayOf(magnitude, magnitude, magnitude),
                referenceMagnitude = magnitude,
                sphericity = sphericity,
                residualRms = 0f,
                sampleCount = sampleCount,
                quality = sphericity * 100f,
                calibrationType = "hard_iron"
            )
        }
    }
}
