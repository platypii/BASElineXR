package com.platypii.baselinexr.calibration

import com.platypii.baselinexr.measurements.MMagData
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.sqrt

/**
 * Magnetometer calibration algorithms.
 * Implements both hard iron (center offset) and soft iron (ellipsoid to sphere) calibration.
 */
object MagCalibrator {

    /**
     * Perform hard iron calibration using the min/max method.
     * 
     * This finds the center of the bounding box of all measurements,
     * which approximates the hard iron offset (bias in the magnetometer readings).
     * 
     * @param samples List of magnetometer measurements
     * @return Calibration result with hard iron offsets
     */
    fun calibrateHardIron(samples: List<MMagData>): MagCalibrationResult {
        if (samples.size < 10) {
            throw IllegalArgumentException("Need at least 10 samples for calibration")
        }

        // Find min/max for each axis
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = Float.MIN_VALUE

        for (sample in samples) {
            if (sample.magX < minX) minX = sample.magX
            if (sample.magX > maxX) maxX = sample.magX
            if (sample.magY < minY) minY = sample.magY
            if (sample.magY > maxY) maxY = sample.magY
            if (sample.magZ < minZ) minZ = sample.magZ
            if (sample.magZ > maxZ) maxZ = sample.magZ
        }

        // Hard iron offset = center of the bounding box
        val offsetX = (maxX + minX) / 2f
        val offsetY = (maxY + minY) / 2f
        val offsetZ = (maxZ + minZ) / 2f

        // Range for each axis (diameter of the ellipsoid along each axis)
        val rangeX = maxX - minX
        val rangeY = maxY - minY
        val rangeZ = maxZ - minZ
        val avgRange = (rangeX + rangeY + rangeZ) / 3f

        // Sphericity: how close to a perfect sphere (1.0 = perfect)
        // Based on how similar the three axis ranges are
        val sphericity = if (avgRange > 0) {
            val deviation = abs(rangeX - avgRange) + abs(rangeY - avgRange) + abs(rangeZ - avgRange)
            (1f - deviation / (3f * avgRange)).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Expected field magnitude = average of the half-ranges (radius)
        val magnitude = avgRange / 2f

        // Calculate RMS error from a perfect sphere
        var residualSum = 0.0
        for (sample in samples) {
            val cx = sample.magX - offsetX
            val cy = sample.magY - offsetY
            val cz = sample.magZ - offsetZ
            val radius = sqrt(cx * cx + cy * cy + cz * cz)
            residualSum += (radius - magnitude) * (radius - magnitude)
        }
        val residualRms = sqrt(residualSum / samples.size).toFloat()

        // Quality: based on sphericity and residual
        val residualPercent = if (magnitude > 0) (residualRms / magnitude) * 100f else 0f
        val qualityFromResidual = (1f - (residualPercent / 10f).coerceAtMost(1f)).coerceAtLeast(0f)
        val quality = (sphericity * 100f * qualityFromResidual).coerceIn(0f, 100f)

        return MagCalibrationResult.hardIronOnly(
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            magnitude = magnitude,
            sphericity = sphericity,
            sampleCount = samples.size
        ).copy(
            residualRms = residualRms,
            quality = quality
        )
    }

    /**
     * Perform soft iron calibration using ellipsoid fitting.
     * 
     * Fits an ellipsoid to the measurement data and computes the transformation
     * matrix that converts the ellipsoid to a sphere (correcting for soft iron distortion).
     * 
     * Uses the algebraic ellipsoid fitting method with least squares.
     * 
     * @param samples List of magnetometer measurements
     * @return Calibration result with soft iron matrix
     */
    fun calibrateSoftIron(samples: List<MMagData>): MagCalibrationResult {
        if (samples.size < 10) {
            throw IllegalArgumentException("Need at least 10 samples for ellipsoid fitting")
        }

        val n = samples.size

        // Build design matrix for least squares ellipsoid fit
        // Equation: ax² + by² + cz² + 2fyz + 2gxz + 2hxy + 2px + 2qy + 2rz = 1
        // D2 matrix: [x², y², z², 2yz, 2xz, 2xy, 2x, 2y, 2z]
        // b vector: all ones

        val D2 = Array(n) { FloatArray(9) }
        val b = FloatArray(n) { 1f }

        for (i in 0 until n) {
            val x = samples[i].magX.toDouble()
            val y = samples[i].magY.toDouble()
            val z = samples[i].magZ.toDouble()
            D2[i][0] = (x * x).toFloat()
            D2[i][1] = (y * y).toFloat()
            D2[i][2] = (z * z).toFloat()
            D2[i][3] = (2 * y * z).toFloat()
            D2[i][4] = (2 * x * z).toFloat()
            D2[i][5] = (2 * x * y).toFloat()
            D2[i][6] = (2 * x).toFloat()
            D2[i][7] = (2 * y).toFloat()
            D2[i][8] = (2 * z).toFloat()
        }

        // Solve least squares: (D2^T * D2) * v = D2^T * b
        val D2tD2 = Array(9) { DoubleArray(9) }
        val D2tb = DoubleArray(9)

        // D2^T * D2
        for (i in 0 until 9) {
            for (j in 0 until 9) {
                var sum = 0.0
                for (k in 0 until n) {
                    sum += D2[k][i] * D2[k][j]
                }
                D2tD2[i][j] = sum
            }
        }

        // D2^T * b
        for (i in 0 until 9) {
            var sum = 0.0
            for (k in 0 until n) {
                sum += D2[k][i] * b[k]
            }
            D2tb[i] = sum
        }

        // Add regularization to prevent singular matrix
        for (i in 0 until 9) {
            D2tD2[i][i] += 1e-6
        }

        // Solve using Gaussian elimination
        val v = solveLinearSystem(D2tD2, D2tb)

        // Extract ellipsoid parameters
        // v = [a, b, c, f, g, h, p, q, r]
        val a = v[0]
        val bCoef = v[1]
        val c = v[2]
        val f = v[3]
        val g = v[4]
        val h = v[5]
        val px = v[6]
        val qy = v[7]
        val rz = v[8]

        // Build matrix M = [[a, h, g], [h, b, f], [g, f, c]]
        val M = arrayOf(
            doubleArrayOf(a, h, g),
            doubleArrayOf(h, bCoef, f),
            doubleArrayOf(g, f, c)
        )

        // Vector n = [p, q, r]
        val nVec = doubleArrayOf(px, qy, rz)

        // Compute center: center = -M^(-1) * n
        val Minv = inverse3x3(M)
        val center = DoubleArray(3)
        for (i in 0 until 3) {
            center[i] = -(Minv[i][0] * nVec[0] + Minv[i][1] * nVec[1] + Minv[i][2] * nVec[2])
        }

        // Compute eigenvalues of M for ellipsoid shape
        val eigen = symmetricEigen3x3(M)
        val eigenvalues = eigen.first
        val eigenvectors = eigen.second

        // Calculate k value for semi-axes
        val Mc = DoubleArray(3)
        for (i in 0 until 3) {
            Mc[i] = M[i][0] * center[0] + M[i][1] * center[1] + M[i][2] * center[2]
        }
        val k = center[0] * Mc[0] + center[1] * Mc[1] + center[2] * Mc[2] + 1

        // Compute semi-axes
        val semiAxes = DoubleArray(3)
        val allNegative = eigenvalues.all { it < 0 }
        val allPositive = eigenvalues.all { it > 0 }

        if (allNegative && k < 0 || allPositive && k > 0) {
            for (i in 0 until 3) {
                semiAxes[i] = sqrt(abs(k / eigenvalues[i]))
            }
        } else {
            // Fallback for mixed signs
            for (i in 0 until 3) {
                val absEv = abs(eigenvalues[i])
                val absK = abs(k)
                semiAxes[i] = if (absEv > 0) sqrt(absK / absEv) else 0.0
            }
        }

        // Sphericity
        val validAxes = semiAxes.filter { it > 0 && it.isFinite() }
        val minAxis = validAxes.minOrNull() ?: 0.0
        val maxAxis = validAxes.maxOrNull() ?: 0.0
        val sphericity = if (maxAxis > 0 && minAxis > 0) (minAxis / maxAxis).toFloat() else 0f

        // Build soft iron correction matrix
        // W^(-1) = V * S^(-1) * V^T scaled to preserve magnitude
        val V = eigenvectors
        val Sinv = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            Sinv[i][i] = if (semiAxes[i] > 0 && semiAxes[i].isFinite()) 1.0 / semiAxes[i] else 1.0
        }

        // softIronInverse = V * Sinv * V^T
        val VtSinv = matmul3x3(V, Sinv)
        val Vt = transpose3x3(V)
        val softIronInverseUnnorm = matmul3x3(VtSinv, Vt)

        // Geometric mean of semi-axes as reference magnitude
        val geometricMean = cbrt(semiAxes[0] * semiAxes[1] * semiAxes[2])

        // Calculate scale factor to preserve magnitude
        val testPoint = doubleArrayOf(semiAxes[0], 0.0, 0.0)
        val testCorrected = DoubleArray(3)
        for (i in 0 until 3) {
            testCorrected[i] = softIronInverseUnnorm[i][0] * testPoint[0] +
                               softIronInverseUnnorm[i][1] * testPoint[1] +
                               softIronInverseUnnorm[i][2] * testPoint[2]
        }
        val testMag = sqrt(testCorrected[0] * testCorrected[0] +
                          testCorrected[1] * testCorrected[1] +
                          testCorrected[2] * testCorrected[2])
        val scaleFactor = if (testMag > 0) geometricMean / testMag else 1.0

        // Apply scale and convert to float array (row-major)
        val softIronMatrix = FloatArray(9)
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                softIronMatrix[i * 3 + j] = (softIronInverseUnnorm[i][j] * scaleFactor).toFloat()
            }
        }

        // Compute residual RMS
        var residualSum = 0.0
        for (sample in samples) {
            val shifted = doubleArrayOf(
                sample.magX - center[0],
                sample.magY - center[1],
                sample.magZ - center[2]
            )
            val corrected = DoubleArray(3)
            for (i in 0 until 3) {
                corrected[i] = (softIronInverseUnnorm[i][0] * shifted[0] +
                               softIronInverseUnnorm[i][1] * shifted[1] +
                               softIronInverseUnnorm[i][2] * shifted[2]) * scaleFactor
            }
            val radius = sqrt(corrected[0] * corrected[0] +
                             corrected[1] * corrected[1] +
                             corrected[2] * corrected[2])
            residualSum += (radius - geometricMean) * (radius - geometricMean)
        }
        val residualRms = sqrt(residualSum / n).toFloat()

        // Quality metric
        val residualPercent = (residualRms / geometricMean) * 100
        val qualityFromResidual = (1.0 - (residualPercent / 10.0).coerceAtMost(1.0)).coerceAtLeast(0.0)
        val quality = (sphericity * 100f * qualityFromResidual).toFloat().coerceIn(0f, 100f)

        return MagCalibrationResult(
            offsetX = center[0].toFloat(),
            offsetY = center[1].toFloat(),
            offsetZ = center[2].toFloat(),
            softIronMatrix = softIronMatrix,
            semiAxes = floatArrayOf(semiAxes[0].toFloat(), semiAxes[1].toFloat(), semiAxes[2].toFloat()),
            referenceMagnitude = geometricMean.toFloat(),
            sphericity = sphericity,
            residualRms = residualRms,
            sampleCount = n,
            quality = quality,
            calibrationType = "soft_iron"
        )
    }

    // ========================================================================
    // Matrix Math Helpers
    // ========================================================================

    private fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = A.size
        val aug = Array(n) { i -> A[i].copyOf() + b[i] }

        // Forward elimination with partial pivoting
        for (col in 0 until n) {
            var maxRow = col
            var maxVal = abs(aug[col][col])
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > maxVal) {
                    maxVal = abs(aug[row][col])
                    maxRow = row
                }
            }

            // Swap rows
            val temp = aug[col]
            aug[col] = aug[maxRow]
            aug[maxRow] = temp

            if (abs(aug[col][col]) < 1e-10) {
                throw IllegalStateException("Matrix is singular")
            }

            // Eliminate column
            for (row in col + 1 until n) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col..n) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }

        // Back substitution
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var sum = aug[i][n]
            for (j in i + 1 until n) {
                sum -= aug[i][j] * x[j]
            }
            x[i] = sum / aug[i][i]
        }

        return x
    }

    private fun det3x3(M: Array<DoubleArray>): Double {
        return M[0][0] * (M[1][1] * M[2][2] - M[1][2] * M[2][1]) -
               M[0][1] * (M[1][0] * M[2][2] - M[1][2] * M[2][0]) +
               M[0][2] * (M[1][0] * M[2][1] - M[1][1] * M[2][0])
    }

    private fun inverse3x3(M: Array<DoubleArray>): Array<DoubleArray> {
        val det = det3x3(M)
        if (abs(det) < 1e-10) {
            throw IllegalStateException("Matrix is singular, cannot invert")
        }

        val inv = Array(3) { DoubleArray(3) }
        inv[0][0] = (M[1][1] * M[2][2] - M[1][2] * M[2][1]) / det
        inv[0][1] = (M[0][2] * M[2][1] - M[0][1] * M[2][2]) / det
        inv[0][2] = (M[0][1] * M[1][2] - M[0][2] * M[1][1]) / det
        inv[1][0] = (M[1][2] * M[2][0] - M[1][0] * M[2][2]) / det
        inv[1][1] = (M[0][0] * M[2][2] - M[0][2] * M[2][0]) / det
        inv[1][2] = (M[0][2] * M[1][0] - M[0][0] * M[1][2]) / det
        inv[2][0] = (M[1][0] * M[2][1] - M[1][1] * M[2][0]) / det
        inv[2][1] = (M[0][1] * M[2][0] - M[0][0] * M[2][1]) / det
        inv[2][2] = (M[0][0] * M[1][1] - M[0][1] * M[1][0]) / det

        return inv
    }

    private fun matmul3x3(A: Array<DoubleArray>, B: Array<DoubleArray>): Array<DoubleArray> {
        val C = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                var sum = 0.0
                for (k in 0 until 3) {
                    sum += A[i][k] * B[k][j]
                }
                C[i][j] = sum
            }
        }
        return C
    }

    private fun transpose3x3(A: Array<DoubleArray>): Array<DoubleArray> {
        val T = Array(3) { DoubleArray(3) }
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                T[j][i] = A[i][j]
            }
        }
        return T
    }

    /**
     * Compute eigenvalues and eigenvectors of a 3x3 symmetric matrix using Jacobi iteration.
     * Returns Pair(eigenvalues, eigenvectors as column matrix)
     */
    private fun symmetricEigen3x3(A: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
        val maxIter = 100
        val tol = 1e-10

        // Copy matrix
        val D = Array(3) { i -> A[i].copyOf() }
        val V = Array(3) { i -> DoubleArray(3) { j -> if (i == j) 1.0 else 0.0 } }

        for (iter in 0 until maxIter) {
            // Find largest off-diagonal element
            var maxVal = 0.0
            var p = 0
            var q = 1
            for (i in 0 until 3) {
                for (j in i + 1 until 3) {
                    if (abs(D[i][j]) > maxVal) {
                        maxVal = abs(D[i][j])
                        p = i
                        q = j
                    }
                }
            }

            if (maxVal < tol) break

            // Compute rotation angle
            val theta = (D[q][q] - D[p][p]) / (2 * D[p][q])
            val t = if (theta >= 0) 1.0 / (theta + sqrt(theta * theta + 1))
                    else -1.0 / (-theta + sqrt(theta * theta + 1))
            val cos = 1.0 / sqrt(t * t + 1)
            val sin = t * cos

            // Apply rotation to D
            val Dpp = D[p][p]
            val Dqq = D[q][q]
            val Dpq = D[p][q]

            D[p][p] = cos * cos * Dpp - 2 * sin * cos * Dpq + sin * sin * Dqq
            D[q][q] = sin * sin * Dpp + 2 * sin * cos * Dpq + cos * cos * Dqq
            D[p][q] = 0.0
            D[q][p] = 0.0

            for (i in 0 until 3) {
                if (i != p && i != q) {
                    val Dip = D[i][p]
                    val Diq = D[i][q]
                    D[i][p] = cos * Dip - sin * Diq
                    D[p][i] = D[i][p]
                    D[i][q] = sin * Dip + cos * Diq
                    D[q][i] = D[i][q]
                }
            }

            // Apply rotation to V
            for (i in 0 until 3) {
                val Vip = V[i][p]
                val Viq = V[i][q]
                V[i][p] = cos * Vip - sin * Viq
                V[i][q] = sin * Vip + cos * Viq
            }
        }

        // Extract eigenvalues (diagonal) and sort by descending value
        val eigenvalues = doubleArrayOf(D[0][0], D[1][1], D[2][2])
        val eigenvectors = Array(3) { i -> DoubleArray(3) { j -> V[j][i] } } // Transpose: columns become rows

        // Sort indices by eigenvalue (descending)
        val indices = intArrayOf(0, 1, 2)
        for (i in 0 until 2) {
            for (j in i + 1 until 3) {
                if (eigenvalues[indices[j]] > eigenvalues[indices[i]]) {
                    val temp = indices[i]
                    indices[i] = indices[j]
                    indices[j] = temp
                }
            }
        }

        val sortedEigenvalues = DoubleArray(3) { eigenvalues[indices[it]] }
        val sortedEigenvectors = Array(3) { i -> DoubleArray(3) { j -> V[j][indices[i]] } }

        // Return as column matrix
        val columnEigenvectors = Array(3) { i -> DoubleArray(3) { j -> sortedEigenvectors[j][i] } }

        return Pair(sortedEigenvalues, columnEigenvectors)
    }
}
