# FlySight Sensor Fusion - Android/Quest Porting Guide

## Overview

This guide covers porting the FlySight Sensor Fusion system from TypeScript to Kotlin for the Meta Quest VR headset. The VR headset serves as the sensor fusion hub, receiving data from the FlySight device via Bluetooth and providing real-time 3D visualization.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Porting Strategy](#porting-strategy)
3. [File-by-File Mapping](#file-by-file-mapping)
4. [Phase 1: Core Math](#phase-1-core-math)
5. [Phase 2: Madgwick AHRS](#phase-2-madgwick-ahrs)
6. [Phase 3: CSV Parsers](#phase-3-csv-parsers)
7. [Phase 4: GPS Integration](#phase-4-gps-integration)
8. [Phase 5: Kalman Filter](#phase-5-kalman-filter)
9. [Phase 6: Mag Calibration](#phase-6-mag-calibration)
10. [Replay vs Live Mode](#replay-vs-live-mode)
11. [Bluetooth Integration](#bluetooth-integration)
12. [Meta Spatial SDK Integration](#meta-spatial-sdk-integration)
13. [Performance Considerations](#performance-considerations)

---

## Architecture Overview

### Why VR Headset as Fusion Hub

1. **Processing Power** - Quest 3 has Snapdragon XR2, capable of running Kalman filters + sphere fitting
2. **Visualization** - Real-time 3D visualization of calibration spheres, orientation, flight path
3. **Low Latency to Display** - Fused orientation goes directly to the rendering pipeline
4. **Two IMU Sources** - Headset IMU (high quality) + FlySight IMU (with GPS) for cross-validation
5. **Natural Replay/Live Toggle** - Same codebase, different data source

### System Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                         Quest VR Headset                                │
│                                                                         │
│  ┌─────────────┐                      ┌─────────────────────────────┐  │
│  │   Madgwick  │ ───── q, a_body ───► │      Aero Kalman Filter     │  │
│  │    AHRS     │                      │                             │  │
│  │             │ ◄── g_corrected ──── │  State: [vel, accel]_world  │  │
│  │             │ ◄── beta_adjust ──── │                             │  │
│  └──────▲──────┘                      └──────────────▲──────────────┘  │
│         │                                            │                  │
│         │                                            │                  │
│   ┌─────┴─────┐                              ┌───────┴───────┐         │
│   │  IMU Data │                              │   GPS Data    │         │
│   │ (200 Hz)  │                              │   (20 Hz)     │         │
│   └───────────┘                              └───────────────┘         │
│         │                                            │                  │
│         └──────────────┬─────────────────────────────┘                  │
│                        │                                                │
│              ┌─────────┴─────────┐                                      │
│              │  Bluetooth Stream │  ◄──── FlySight Device               │
│              │    or CSV Files   │                                      │
│              └───────────────────┘                                      │
│                        │                                                │
│              ┌─────────┴─────────┐                                      │
│              │ Mag Calibration   │                                      │
│              │    Watchdog       │                                      │
│              └───────────────────┘                                      │
│                        │                                                │
│              ┌─────────┴─────────┐                                      │
│              │  Meta Spatial SDK │                                      │
│              │   3D Rendering    │                                      │
│              └───────────────────┘                                      │
└────────────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
┌─────────┐     ┌─────────┐     ┌─────────────┐     ┌──────────┐
│   IMU   │────►│ Madgwick│────►│ Aero Kalman │◄────│   GPS    │
│ 200 Hz  │     │  AHRS   │     │   Filter    │     │  20 Hz   │
└─────────┘     └────▲────┘     └──────┬──────┘     └──────────┘
                     │                 │
                     │    g_corrected  │
                     │    beta_adjust  │
                     └─────────────────┘
                     │
              ┌──────┴──────┐
              │ Mag Watchdog│
              │   + Sphere  │
              │    Fitter   │
              └─────────────┘
```

---

## Porting Strategy

### Recommended Port Order

1. **Core math first** - `Quaternion`, `Vector3`, matrix ops
2. **Madgwick AHRS** - Get basic orientation working
3. **CSV parsers** - For replay mode testing
4. **Visualization** - See orientation in VR
5. **Bluetooth streaming** - Live mode
6. **Kalman filter** - GPS-aided fusion
7. **Mag calibration** - Online sphere fitting

### Key Difference: Batch vs Streaming

In TypeScript, data is processed in batches (entire files at once). In Android, data streams in real-time:

```kotlin
// TypeScript style (batch)
val allData = parseCSV(file)
val results = processAll(allData)
displayResults(results)

// Android style (streaming)
bluetoothStream.collect { packet ->
    fusionEngine.update(packet)
    spatialEntity.rotation = fusionEngine.orientation
}
```

**Important:** Ensure all filters (Kalman, SG) are **incremental** - updating state with each sample rather than reprocessing.

---

## File-by-File Mapping

| TypeScript File | Kotlin Class | Priority | Notes |
|-----------------|--------------|----------|-------|
| `FusionAhrs.ts` | `MadgwickAHRS.kt` | HIGH | Core algorithm, nearly 1:1 translation |
| `types.ts` | `SensorDataModels.kt` | HIGH | Data classes for IMU, MAG |
| `gpsTypes.ts` | `GpsDataModels.kt` | HIGH | Data classes with `@Parcelize` for IPC |
| `csvParser.ts` | `SensorCsvParser.kt` | HIGH | Parse SENSOR.CSV files |
| `trackParser.ts` | `TrackCsvParser.kt` | HIGH | Parse TRACK.CSV files |
| `timestampSync.ts` | `TimestampSynchronizer.kt` | HIGH | Critical - Bluetooth adds latency |
| `mathUtils.ts` | `MathUtils.kt` | MEDIUM | Linear regression, derivatives |
| `sgFilter.ts` | `SavitzkyGolayFilter.kt` | MEDIUM | Keep coefficients as static arrays |
| `sgCoefficients.ts` | `SGCoefficients.kt` | MEDIUM | Static coefficient tables |
| `gpsIntegration.ts` | `GpsIntegration.kt` | MEDIUM | GPS to NWU conversion |
| `ellipsoidFit.ts` | `EllipsoidFitter.kt` | LOW | Use EJML or Apache Commons Math |
| `calibrationManager.ts` | `CalibrationManager.kt` | LOW | Coordinate calibration flow |

---

## Phase 1: Core Math

### Quaternion Class

**Source:** `FusionAhrs.ts` (quaternion operations embedded)

```kotlin
package com.flysight.fusion.math

import kotlin.math.sqrt

/**
 * Quaternion for 3D rotation representation
 * Convention: [w, x, y, z] where w is scalar part
 */
data class Quaternion(
    var w: Float = 1f,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    companion object {
        fun identity() = Quaternion(1f, 0f, 0f, 0f)
    }
    
    /**
     * Normalize to unit quaternion
     */
    fun normalize(): Quaternion {
        val norm = sqrt(w * w + x * x + y * y + z * z)
        if (norm > 0.0001f) {
            w /= norm
            x /= norm
            y /= norm
            z /= norm
        }
        return this
    }
    
    /**
     * Conjugate (inverse for unit quaternions)
     */
    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)
    
    /**
     * Quaternion multiplication: this * other
     */
    operator fun times(other: Quaternion): Quaternion {
        return Quaternion(
            w = w * other.w - x * other.x - y * other.y - z * other.z,
            x = w * other.x + x * other.w + y * other.z - z * other.y,
            y = w * other.y - x * other.z + y * other.w + z * other.x,
            z = w * other.z + x * other.y - y * other.x + z * other.w
        )
    }
    
    /**
     * Rotate a vector by this quaternion
     * v' = q * v * q^-1
     */
    fun rotateVector(v: Vector3): Vector3 {
        val qv = Quaternion(0f, v.x, v.y, v.z)
        val result = this * qv * this.conjugate()
        return Vector3(result.x, result.y, result.z)
    }
    
    /**
     * Convert to rotation matrix (3x3)
     */
    fun toRotationMatrix(): FloatArray {
        val m = FloatArray(9)
        val xx = x * x; val xy = x * y; val xz = x * z; val xw = x * w
        val yy = y * y; val yz = y * z; val yw = y * w
        val zz = z * z; val zw = z * w
        
        m[0] = 1 - 2 * (yy + zz)
        m[1] = 2 * (xy - zw)
        m[2] = 2 * (xz + yw)
        m[3] = 2 * (xy + zw)
        m[4] = 1 - 2 * (xx + zz)
        m[5] = 2 * (yz - xw)
        m[6] = 2 * (xz - yw)
        m[7] = 2 * (yz + xw)
        m[8] = 1 - 2 * (xx + yy)
        
        return m
    }
    
    fun copy(): Quaternion = Quaternion(w, x, y, z)
}
```

### Vector3 Class

```kotlin
package com.flysight.fusion.math

import kotlin.math.sqrt

/**
 * 3D Vector for positions, velocities, accelerations
 */
data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    companion object {
        fun zero() = Vector3(0f, 0f, 0f)
        fun up() = Vector3(0f, 0f, 1f)  // NWU: Up is +Z
        fun north() = Vector3(1f, 0f, 0f)
        fun west() = Vector3(0f, 1f, 0f)
    }
    
    fun magnitude(): Float = sqrt(x * x + y * y + z * z)
    
    fun normalize(): Vector3 {
        val mag = magnitude()
        if (mag > 0.0001f) {
            x /= mag
            y /= mag
            z /= mag
        }
        return this
    }
    
    fun normalized(): Vector3 = copy().normalize()
    
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)
    
    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z
    
    fun cross(other: Vector3): Vector3 = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    
    fun copy(): Vector3 = Vector3(x, y, z)
    
    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)
}
```

### MathUtils

**Source:** `mathUtils.ts`

```kotlin
package com.flysight.fusion.math

/**
 * Math utilities for signal processing
 */
object MathUtils {
    
    /**
     * Linear least squares slope fitting
     * Calculates the slope of y vs x using linear regression
     */
    fun <T> getSlope(
        points: List<T>,
        getX: (T) -> Float,
        getY: (T) -> Float
    ): Float {
        if (points.size < 2) return 0f
        
        var sumx = 0f
        var sumy = 0f
        var sumxx = 0f
        var sumxy = 0f
        
        for (point in points) {
            val x = getX(point)
            val y = getY(point)
            sumx += x
            sumy += y
            sumxx += x * x
            sumxy += x * y
        }
        
        val n = points.size.toFloat()
        val denominator = sumxx - sumx * sumx / n
        
        if (kotlin.math.abs(denominator) < 1e-12f) {
            return 0f
        }
        
        return (sumxy - sumx * sumy / n) / denominator
    }
    
    /**
     * Calculate derivative at each point using sliding window linear regression
     */
    fun <T> calculateDerivative(
        data: List<T>,
        windowSize: Int,
        getTime: (T) -> Float,
        getValue: (T) -> Float
    ): FloatArray {
        if (data.isEmpty()) return floatArrayOf()
        if (data.size == 1) return floatArrayOf(0f)
        
        val halfWindow = windowSize / 2
        val result = FloatArray(data.size)
        
        for (i in data.indices) {
            var startIdx = i - halfWindow
            var endIdx = i + halfWindow
            
            // Handle edges by shifting window
            if (startIdx < 0) {
                startIdx = 0
                endIdx = minOf(windowSize - 1, data.size - 1)
            }
            if (endIdx >= data.size) {
                endIdx = data.size - 1
                startIdx = maxOf(0, endIdx - windowSize + 1)
            }
            
            val window = data.subList(startIdx, endIdx + 1)
            result[i] = getSlope(window, getTime, getValue)
        }
        
        return result
    }
}
```

---

## Phase 2: Madgwick AHRS

### MadgwickAHRS Class

**Source:** `FusionAhrs.ts`

This is the core orientation estimation algorithm. It fuses gyroscope, accelerometer, and magnetometer data.

```kotlin
package com.flysight.fusion.ahrs

import com.flysight.fusion.math.Quaternion
import com.flysight.fusion.math.Vector3
import kotlin.math.sqrt

/**
 * Madgwick AHRS Algorithm
 * 
 * Provides orientation estimation from IMU data using gradient descent optimization.
 * 
 * Coordinate System: NWU (North-West-Up)
 * - X = North
 * - Y = West  
 * - Z = Up
 */
class MadgwickAHRS(
    /** Algorithm gain - higher = faster convergence, more noise */
    var beta: Float = 0.1f,
    
    /** Sampling period in seconds */
    var samplePeriod: Float = 0.01f
) {
    /** Current orientation quaternion */
    val quaternion: Quaternion = Quaternion.identity()
    
    /** Reference magnetic field direction (will be updated on first valid reading) */
    private var magneticReference: Vector3 = Vector3(1f, 0f, 0f)
    private var magneticReferenceSet: Boolean = false
    
    /**
     * Update orientation with gyroscope and accelerometer data
     * 
     * @param gyro Angular velocity in rad/s [x, y, z] in sensor frame
     * @param accel Acceleration in m/s² [x, y, z] in sensor frame (gravity + inertial)
     */
    fun updateIMU(gyro: Vector3, accel: Vector3) {
        val q = quaternion
        
        // Normalize accelerometer
        val aNorm = accel.magnitude()
        if (aNorm < 0.001f) {
            // Just integrate gyro if no valid accel
            integrateGyro(gyro)
            return
        }
        val ax = accel.x / aNorm
        val ay = accel.y / aNorm
        val az = accel.z / aNorm
        
        // Gradient descent step
        // Objective: align accelerometer with gravity (0, 0, 1) in world frame
        val _2q0 = 2f * q.w
        val _2q1 = 2f * q.x
        val _2q2 = 2f * q.y
        val _2q3 = 2f * q.z
        val _4q0 = 4f * q.w
        val _4q1 = 4f * q.x
        val _4q2 = 4f * q.y
        val _8q1 = 8f * q.x
        val _8q2 = 8f * q.y
        val q0q0 = q.w * q.w
        val q1q1 = q.x * q.x
        val q2q2 = q.y * q.y
        val q3q3 = q.z * q.z
        
        // Gradient
        var s0 = _4q0 * q2q2 + _2q2 * ax + _4q0 * q1q1 - _2q1 * ay
        var s1 = _4q1 * q3q3 - _2q3 * ax + 4f * q0q0 * q.x - _2q0 * ay - _4q1 + _8q1 * q1q1 + _8q1 * q2q2 + _4q1 * az
        var s2 = 4f * q0q0 * q.y + _2q0 * ax + _4q2 * q3q3 - _2q3 * ay - _4q2 + _8q2 * q1q1 + _8q2 * q2q2 + _4q2 * az
        var s3 = 4f * q1q1 * q.z - _2q1 * ax + 4f * q2q2 * q.z - _2q2 * ay
        
        // Normalize gradient
        val sNorm = sqrt(s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3)
        if (sNorm > 0.001f) {
            s0 /= sNorm
            s1 /= sNorm
            s2 /= sNorm
            s3 /= sNorm
        }
        
        // Compute rate of change from gyro
        val qDot0 = 0.5f * (-q.x * gyro.x - q.y * gyro.y - q.z * gyro.z)
        val qDot1 = 0.5f * (q.w * gyro.x + q.y * gyro.z - q.z * gyro.y)
        val qDot2 = 0.5f * (q.w * gyro.y - q.x * gyro.z + q.z * gyro.x)
        val qDot3 = 0.5f * (q.w * gyro.z + q.x * gyro.y - q.y * gyro.x)
        
        // Apply feedback
        q.w += (qDot0 - beta * s0) * samplePeriod
        q.x += (qDot1 - beta * s1) * samplePeriod
        q.y += (qDot2 - beta * s2) * samplePeriod
        q.z += (qDot3 - beta * s3) * samplePeriod
        
        q.normalize()
    }
    
    /**
     * Update orientation with gyroscope, accelerometer, and magnetometer data
     * 
     * @param gyro Angular velocity in rad/s [x, y, z]
     * @param accel Acceleration in m/s² [x, y, z]
     * @param mag Magnetic field [x, y, z] (units don't matter, will be normalized)
     */
    fun updateMARG(gyro: Vector3, accel: Vector3, mag: Vector3) {
        val q = quaternion
        
        // Normalize accelerometer
        val aNorm = accel.magnitude()
        if (aNorm < 0.001f) {
            integrateGyro(gyro)
            return
        }
        val ax = accel.x / aNorm
        val ay = accel.y / aNorm
        val az = accel.z / aNorm
        
        // Normalize magnetometer
        val mNorm = mag.magnitude()
        if (mNorm < 0.001f) {
            updateIMU(gyro, accel)
            return
        }
        var mx = mag.x / mNorm
        var my = mag.y / mNorm
        var mz = mag.z / mNorm
        
        // Auxiliary variables
        val _2q0mx = 2f * q.w * mx
        val _2q0my = 2f * q.w * my
        val _2q0mz = 2f * q.w * mz
        val _2q1mx = 2f * q.x * mx
        val _2q0 = 2f * q.w
        val _2q1 = 2f * q.x
        val _2q2 = 2f * q.y
        val _2q3 = 2f * q.z
        val _2q0q2 = 2f * q.w * q.y
        val _2q2q3 = 2f * q.y * q.z
        val q0q0 = q.w * q.w
        val q0q1 = q.w * q.x
        val q0q2 = q.w * q.y
        val q0q3 = q.w * q.z
        val q1q1 = q.x * q.x
        val q1q2 = q.x * q.y
        val q1q3 = q.x * q.z
        val q2q2 = q.y * q.y
        val q2q3 = q.y * q.z
        val q3q3 = q.z * q.z
        
        // Reference direction of Earth's magnetic field
        var hx = mx * q0q0 - _2q0my * q.z + _2q0mz * q.y + mx * q1q1 + _2q1 * my * q.y + _2q1 * mz * q.z - mx * q2q2 - mx * q3q3
        var hy = _2q0mx * q.z + my * q0q0 - _2q0mz * q.x + _2q1mx * q.y - my * q1q1 + my * q2q2 + _2q2 * mz * q.z - my * q3q3
        val _2bx = sqrt(hx * hx + hy * hy)
        val _2bz = -_2q0mx * q.y + _2q0my * q.x + mz * q0q0 + _2q1mx * q.z - mz * q1q1 + _2q2 * my * q.z - mz * q2q2 + mz * q3q3
        val _4bx = 2f * _2bx
        val _4bz = 2f * _2bz
        
        // Gradient descent - accel + mag objectives
        var s0 = -_2q2 * (2f * q1q3 - _2q0q2 - ax) + _2q1 * (2f * q0q1 + _2q2q3 - ay) - _2bz * q.y * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (-_2bx * q.z + _2bz * q.x) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + _2bx * q.y * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz)
        var s1 = _2q3 * (2f * q1q3 - _2q0q2 - ax) + _2q0 * (2f * q0q1 + _2q2q3 - ay) - 4f * q.x * (1f - 2f * q1q1 - 2f * q2q2 - az) + _2bz * q.z * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (_2bx * q.y + _2bz * q.w) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + (_2bx * q.z - _4bz * q.x) * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz)
        var s2 = -_2q0 * (2f * q1q3 - _2q0q2 - ax) + _2q3 * (2f * q0q1 + _2q2q3 - ay) - 4f * q.y * (1f - 2f * q1q1 - 2f * q2q2 - az) + (-_4bx * q.y - _2bz * q.w) * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (_2bx * q.x + _2bz * q.z) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + (_2bx * q.w - _4bz * q.y) * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz)
        var s3 = _2q1 * (2f * q1q3 - _2q0q2 - ax) + _2q2 * (2f * q0q1 + _2q2q3 - ay) + (-_4bx * q.z + _2bz * q.x) * (_2bx * (0.5f - q2q2 - q3q3) + _2bz * (q1q3 - q0q2) - mx) + (-_2bx * q.w + _2bz * q.y) * (_2bx * (q1q2 - q0q3) + _2bz * (q0q1 + q2q3) - my) + _2bx * q.x * (_2bx * (q0q2 + q1q3) + _2bz * (0.5f - q1q1 - q2q2) - mz)
        
        // Normalize
        val sNorm = sqrt(s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3)
        if (sNorm > 0.001f) {
            s0 /= sNorm
            s1 /= sNorm
            s2 /= sNorm
            s3 /= sNorm
        }
        
        // Gyro rate of change
        val qDot0 = 0.5f * (-q.x * gyro.x - q.y * gyro.y - q.z * gyro.z)
        val qDot1 = 0.5f * (q.w * gyro.x + q.y * gyro.z - q.z * gyro.y)
        val qDot2 = 0.5f * (q.w * gyro.y - q.x * gyro.z + q.z * gyro.x)
        val qDot3 = 0.5f * (q.w * gyro.z + q.x * gyro.y - q.y * gyro.x)
        
        // Apply feedback
        q.w += (qDot0 - beta * s0) * samplePeriod
        q.x += (qDot1 - beta * s1) * samplePeriod
        q.y += (qDot2 - beta * s2) * samplePeriod
        q.z += (qDot3 - beta * s3) * samplePeriod
        
        q.normalize()
    }
    
    /**
     * Update with corrected gravity reference (for GPS-aided fusion)
     * 
     * @param gyro Angular velocity
     * @param accel Raw accelerometer reading
     * @param expectedAccel Expected accelerometer reading (gravity - inertial) in sensor frame
     */
    fun updateWithCorrectedGravity(gyro: Vector3, accel: Vector3, expectedAccel: Vector3) {
        // Use expectedAccel instead of assuming pure gravity
        // This allows the filter to work during dynamic maneuvers
        updateIMU(gyro, expectedAccel)
    }
    
    private fun integrateGyro(gyro: Vector3) {
        val q = quaternion
        val qDot0 = 0.5f * (-q.x * gyro.x - q.y * gyro.y - q.z * gyro.z)
        val qDot1 = 0.5f * (q.w * gyro.x + q.y * gyro.z - q.z * gyro.y)
        val qDot2 = 0.5f * (q.w * gyro.y - q.x * gyro.z + q.z * gyro.x)
        val qDot3 = 0.5f * (q.w * gyro.z + q.x * gyro.y - q.y * gyro.x)
        
        q.w += qDot0 * samplePeriod
        q.x += qDot1 * samplePeriod
        q.y += qDot2 * samplePeriod
        q.z += qDot3 * samplePeriod
        q.normalize()
    }
    
    /**
     * Reset to identity orientation
     */
    fun reset() {
        quaternion.w = 1f
        quaternion.x = 0f
        quaternion.y = 0f
        quaternion.z = 0f
    }
}
```

---

## Phase 3: CSV Parsers

### Data Models

**Source:** `types.ts`, `gpsTypes.ts`

```kotlin
package com.flysight.fusion.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Raw IMU data from SENSOR.CSV
 */
@Parcelize
data class IMUData(
    val timestamp: Double,    // Sensor time (seconds since boot)
    val accelX: Float,        // m/s²
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,         // rad/s
    val gyroY: Float,
    val gyroZ: Float
) : Parcelable

/**
 * Raw magnetometer data from SENSOR.CSV
 */
@Parcelize
data class MagData(
    val timestamp: Double,
    val magX: Float,          // Raw units (will be calibrated)
    val magY: Float,
    val magZ: Float
) : Parcelable

/**
 * GNSS data from TRACK.CSV
 */
@Parcelize
data class GNSSData(
    val isoTime: String,
    val timestamp: Long,           // Unix epoch milliseconds
    var sensorTimestamp: Double?,  // Aligned to sensor time (filled by sync)
    val lat: Double,
    val lon: Double,
    val hMSL: Float,               // Altitude (m)
    val velN: Float,               // Velocity North (m/s)
    val velE: Float,               // Velocity East (m/s)
    val velD: Float,               // Velocity Down (m/s)
    val hAcc: Float,               // Horizontal accuracy (m)
    val vAcc: Float,               // Vertical accuracy (m)
    val sAcc: Float,               // Speed accuracy (m/s)
    val numSV: Int                 // Number of satellites
) : Parcelable

/**
 * TIME sync record from SENSOR.CSV
 */
@Parcelize
data class TimeData(
    val timestamp: Double,   // Sensor time
    val tow: Double,         // GPS Time of Week (seconds)
    val week: Int            // GPS week number
) : Parcelable

/**
 * Complete sensor dataset from SENSOR.CSV
 */
data class SensorDataset(
    val imuData: List<IMUData>,
    val magData: List<MagData>,
    val timeData: List<TimeData>,
    val firmwareVersion: String?,
    val deviceId: String?,
    val sessionId: String?
)

/**
 * Complete track dataset from TRACK.CSV
 */
data class TrackDataset(
    val gnssData: List<GNSSData>,
    val firmwareVersion: String?,
    val deviceId: String?,
    val sessionId: String?
)
```

### Sensor CSV Parser

**Source:** `csvParser.ts`

```kotlin
package com.flysight.fusion.parsers

import com.flysight.fusion.data.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Parser for FlySight SENSOR.CSV files
 * 
 * File format:
 * - Header rows start with $
 * - $UNIT row defines columns
 * - $IMU rows contain accelerometer + gyroscope data
 * - $MAG rows contain magnetometer data
 * - $TIME rows contain GPS time sync data
 */
object SensorCsvParser {
    
    private const val DEG_TO_RAD = Math.PI.toFloat() / 180f
    
    fun parse(inputStream: InputStream): SensorDataset {
        val reader = BufferedReader(InputStreamReader(inputStream))
        
        val imuData = mutableListOf<IMUData>()
        val magData = mutableListOf<MagData>()
        val timeData = mutableListOf<TimeData>()
        
        var firmwareVersion: String? = null
        var deviceId: String? = null
        var sessionId: String? = null
        
        // Column indices (determined from $UNIT row)
        var imuColumns: IMUColumnIndices? = null
        var magColumns: MagColumnIndices? = null
        
        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                when {
                    trimmed.startsWith("\$FLYS,") -> {
                        // Firmware version
                        val parts = trimmed.split(",")
                        if (parts.size >= 2) firmwareVersion = parts[1]
                    }
                    
                    trimmed.startsWith("\$VAR,") -> {
                        // Variable (device ID, session ID, etc.)
                        val parts = trimmed.split(",")
                        if (parts.size >= 3) {
                            when (parts[1]) {
                                "DEVICE_ID" -> deviceId = parts[2]
                                "SESSION_ID" -> sessionId = parts[2]
                            }
                        }
                    }
                    
                    trimmed.startsWith("\$UNIT,IMU,") -> {
                        imuColumns = parseIMUUnitRow(trimmed)
                    }
                    
                    trimmed.startsWith("\$UNIT,MAG,") -> {
                        magColumns = parseMagUnitRow(trimmed)
                    }
                    
                    trimmed.startsWith("\$IMU,") -> {
                        imuColumns?.let { cols ->
                            parseIMURow(trimmed, cols)?.let { imuData.add(it) }
                        }
                    }
                    
                    trimmed.startsWith("\$MAG,") -> {
                        magColumns?.let { cols ->
                            parseMagRow(trimmed, cols)?.let { magData.add(it) }
                        }
                    }
                    
                    trimmed.startsWith("\$TIME,") -> {
                        parseTimeRow(trimmed)?.let { timeData.add(it) }
                    }
                }
            }
        }
        
        return SensorDataset(
            imuData = imuData,
            magData = magData,
            timeData = timeData,
            firmwareVersion = firmwareVersion,
            deviceId = deviceId,
            sessionId = sessionId
        )
    }
    
    private data class IMUColumnIndices(
        val time: Int,
        val ax: Int, val ay: Int, val az: Int,
        val wx: Int, val wy: Int, val wz: Int,
        val accelScale: Float,  // Convert to m/s²
        val gyroScale: Float    // Convert to rad/s
    )
    
    private data class MagColumnIndices(
        val time: Int,
        val mx: Int, val my: Int, val mz: Int
    )
    
    private fun parseIMUUnitRow(line: String): IMUColumnIndices? {
        val parts = line.split(",")
        // Expected: $UNIT,IMU,time,wx,wy,wz,ax,ay,az
        // Find column indices by name
        
        var timeIdx = -1
        var axIdx = -1; var ayIdx = -1; var azIdx = -1
        var wxIdx = -1; var wyIdx = -1; var wzIdx = -1
        var accelScale = 1f
        var gyroScale = 1f
        
        for (i in 2 until parts.size) {
            when (parts[i].lowercase()) {
                "time" -> timeIdx = i - 2
                "ax" -> axIdx = i - 2
                "ay" -> ayIdx = i - 2
                "az" -> azIdx = i - 2
                "wx" -> wxIdx = i - 2
                "wy" -> wyIdx = i - 2
                "wz" -> wzIdx = i - 2
            }
        }
        
        // Determine units from subsequent $UNIT row or assume defaults
        // FlySight 2: accel in g, gyro in deg/s
        accelScale = 9.80665f  // g to m/s²
        gyroScale = DEG_TO_RAD // deg/s to rad/s
        
        if (timeIdx < 0 || axIdx < 0) return null
        
        return IMUColumnIndices(
            time = timeIdx,
            ax = axIdx, ay = ayIdx, az = azIdx,
            wx = wxIdx, wy = wyIdx, wz = wzIdx,
            accelScale = accelScale,
            gyroScale = gyroScale
        )
    }
    
    private fun parseMagUnitRow(line: String): MagColumnIndices? {
        val parts = line.split(",")
        
        var timeIdx = -1
        var mxIdx = -1; var myIdx = -1; var mzIdx = -1
        
        for (i in 2 until parts.size) {
            when (parts[i].lowercase()) {
                "time" -> timeIdx = i - 2
                "mx" -> mxIdx = i - 2
                "my" -> myIdx = i - 2
                "mz" -> mzIdx = i - 2
            }
        }
        
        if (timeIdx < 0 || mxIdx < 0) return null
        
        return MagColumnIndices(time = timeIdx, mx = mxIdx, my = myIdx, mz = mzIdx)
    }
    
    private fun parseIMURow(line: String, cols: IMUColumnIndices): IMUData? {
        val parts = line.split(",")
        if (parts.size < 8) return null
        
        return try {
            IMUData(
                timestamp = parts[1 + cols.time].toDouble(),
                accelX = parts[1 + cols.ax].toFloat() * cols.accelScale,
                accelY = parts[1 + cols.ay].toFloat() * cols.accelScale,
                accelZ = parts[1 + cols.az].toFloat() * cols.accelScale,
                gyroX = parts[1 + cols.wx].toFloat() * cols.gyroScale,
                gyroY = parts[1 + cols.wy].toFloat() * cols.gyroScale,
                gyroZ = parts[1 + cols.wz].toFloat() * cols.gyroScale
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseMagRow(line: String, cols: MagColumnIndices): MagData? {
        val parts = line.split(",")
        if (parts.size < 5) return null
        
        return try {
            MagData(
                timestamp = parts[1 + cols.time].toDouble(),
                magX = parts[1 + cols.mx].toFloat(),
                magY = parts[1 + cols.my].toFloat(),
                magZ = parts[1 + cols.mz].toFloat()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseTimeRow(line: String): TimeData? {
        // Format: $TIME,sensorTime,tow,week
        val parts = line.split(",")
        if (parts.size < 4) return null
        
        return try {
            TimeData(
                timestamp = parts[1].toDouble(),
                tow = parts[2].toDouble(),
                week = parts[3].toInt()
            )
        } catch (e: Exception) {
            null
        }
    }
}
```

### Track CSV Parser

**Source:** `trackParser.ts`

```kotlin
package com.flysight.fusion.parsers

import com.flysight.fusion.data.GNSSData
import com.flysight.fusion.data.TrackDataset
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * Parser for FlySight TRACK.CSV files containing GPS data
 */
object TrackCsvParser {
    
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    fun parse(inputStream: InputStream): TrackDataset {
        val reader = BufferedReader(InputStreamReader(inputStream))
        
        val gnssData = mutableListOf<GNSSData>()
        var firmwareVersion: String? = null
        var deviceId: String? = null
        var sessionId: String? = null
        
        reader.useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                when {
                    trimmed.startsWith("\$FLYS,") -> {
                        val parts = trimmed.split(",")
                        if (parts.size >= 2) firmwareVersion = parts[1]
                    }
                    
                    trimmed.startsWith("\$VAR,") -> {
                        val parts = trimmed.split(",")
                        if (parts.size >= 3) {
                            when (parts[1]) {
                                "DEVICE_ID" -> deviceId = parts[2]
                                "SESSION_ID" -> sessionId = parts[2]
                            }
                        }
                    }
                    
                    trimmed.startsWith("\$GNSS,") -> {
                        parseGNSSRow(trimmed)?.let { gnssData.add(it) }
                    }
                }
            }
        }
        
        return TrackDataset(
            gnssData = gnssData,
            firmwareVersion = firmwareVersion,
            deviceId = deviceId,
            sessionId = sessionId
        )
    }
    
    private fun parseGNSSRow(line: String): GNSSData? {
        // Format: $GNSS,time,lat,lon,hMSL,velN,velE,velD,hAcc,vAcc,sAcc,numSV
        val parts = line.split(",")
        if (parts.size < 12) return null
        
        return try {
            val isoTime = parts[1]
            val timestamp = parseISOTimestamp(isoTime)
            
            GNSSData(
                isoTime = isoTime,
                timestamp = timestamp,
                sensorTimestamp = null,  // Filled by timestamp sync
                lat = parts[2].toDouble(),
                lon = parts[3].toDouble(),
                hMSL = parts[4].toFloat(),
                velN = parts[5].toFloat(),
                velE = parts[6].toFloat(),
                velD = parts[7].toFloat(),
                hAcc = parts[8].toFloat(),
                vAcc = parts[9].toFloat(),
                sAcc = parts[10].toFloat(),
                numSV = parts[11].toInt()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun parseISOTimestamp(isoString: String): Long {
        return try {
            isoFormat.parse(isoString)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
```

### Timestamp Synchronizer

**Source:** `timestampSync.ts`

```kotlin
package com.flysight.fusion.sync

import com.flysight.fusion.data.GNSSData
import com.flysight.fusion.data.TimeData
import com.flysight.fusion.data.TrackDataset

/**
 * Synchronizes timestamps between SENSOR.CSV and TRACK.CSV
 * 
 * Uses $TIME entries to link sensor time (seconds since boot) to GPS time (week + TOW)
 */
object TimestampSynchronizer {
    
    // GPS epoch: January 6, 1980 00:00:00 UTC
    private const val GPS_EPOCH_MS = 315964800000L
    private const val SECONDS_PER_WEEK = 604800L
    private const val MS_PER_SECOND = 1000L
    
    data class SyncResult(
        val success: Boolean,
        val errorMessage: String? = null,
        val sensorToGpsOffsetMs: Long = 0,
        val overlapStartSensorTime: Double = 0.0,
        val overlapEndSensorTime: Double = 0.0,
        val matchedGpsCount: Int = 0
    )
    
    /**
     * Convert GPS week and time-of-week to Unix epoch milliseconds
     */
    fun gpsWeekTowToUnixMs(week: Int, tow: Double): Long {
        val gpsMs = (week * SECONDS_PER_WEEK + tow.toLong()) * MS_PER_SECOND
        return GPS_EPOCH_MS + gpsMs
    }
    
    /**
     * Compute time synchronization between sensor and GPS data
     */
    fun computeSync(
        timeEntries: List<TimeData>,
        trackDataset: TrackDataset
    ): SyncResult {
        if (timeEntries.isEmpty()) {
            return SyncResult(
                success = false,
                errorMessage = "No \$TIME entries found in SENSOR.CSV"
            )
        }
        
        if (trackDataset.gnssData.isEmpty()) {
            return SyncResult(
                success = false,
                errorMessage = "No GNSS data found in TRACK.CSV"
            )
        }
        
        // Use first $TIME entry to establish offset
        val firstTime = timeEntries[0]
        val gpsEpochMs = gpsWeekTowToUnixMs(firstTime.week, firstTime.tow)
        val sensorTimeMs = (firstTime.timestamp * MS_PER_SECOND).toLong()
        val offsetMs = gpsEpochMs - sensorTimeMs
        
        // Get GPS time range in sensor time
        val gpsTimestamps = trackDataset.gnssData.map { it.timestamp }
        val gpsStartMs = gpsTimestamps.minOrNull() ?: 0L
        val gpsEndMs = gpsTimestamps.maxOrNull() ?: 0L
        
        val gpsStartSensorTime = (gpsStartMs - offsetMs) / MS_PER_SECOND.toDouble()
        val gpsEndSensorTime = (gpsEndMs - offsetMs) / MS_PER_SECOND.toDouble()
        
        return SyncResult(
            success = true,
            sensorToGpsOffsetMs = offsetMs,
            overlapStartSensorTime = gpsStartSensorTime,
            overlapEndSensorTime = gpsEndSensorTime,
            matchedGpsCount = trackDataset.gnssData.size
        )
    }
    
    /**
     * Apply time sync to GPS data, setting sensorTimestamp on each entry
     */
    fun applySync(
        gnssData: List<GNSSData>,
        syncResult: SyncResult
    ) {
        if (!syncResult.success) return
        
        for (gnss in gnssData) {
            gnss.sensorTimestamp = (gnss.timestamp - syncResult.sensorToGpsOffsetMs) / 
                                   MS_PER_SECOND.toDouble()
        }
    }
}
```

---

## Phase 4: GPS Integration

### GPS to NWU Conversion

**Source:** `gpsIntegration.ts`

```kotlin
package com.flysight.fusion.gps

import com.flysight.fusion.data.GNSSData
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * GPS data point converted to NWU frame with local position
 */
data class GPSIntegrationPoint(
    val sensorTime: Double,
    
    // Raw velocity in NWU frame (m/s)
    val velNorth: Float,
    val velWest: Float,
    val velUp: Float,
    
    // Smoothed velocity in NWU frame (m/s)
    var smoothVelNorth: Float = 0f,
    var smoothVelWest: Float = 0f,
    var smoothVelUp: Float = 0f,
    
    // Position in local NWU frame (meters from origin)
    val posNorth: Float,
    val posWest: Float,
    val posUp: Float,
    
    // Derived values
    val horizontalSpeed: Float,
    val horizontalDistance: Float,
    var smoothHorizontalSpeed: Float = 0f,
    
    // Acceleration from smoothed velocity
    var accelNorth: Float = 0f,
    var accelWest: Float = 0f,
    var accelUp: Float = 0f,
    
    // Original GPS data
    val lat: Double,
    val lon: Double,
    val hMSL: Float,
    val hAcc: Float,
    val vAcc: Float,
    val numSV: Int
)

/**
 * Converts GPS data to NWU integration format
 */
object GpsIntegration {
    
    private const val EARTH_RADIUS_M = 6371000.0
    
    /**
     * Convert lat/lon to local North/East position (meters)
     */
    private fun latLonToLocalNE(
        lat: Double,
        lon: Double,
        originLat: Double,
        originLon: Double
    ): Pair<Float, Float> {
        val originLatRad = Math.toRadians(originLat)
        val dLat = Math.toRadians(lat - originLat)
        val dLon = Math.toRadians(lon - originLon)
        
        val north = (dLat * EARTH_RADIUS_M).toFloat()
        val east = (dLon * EARTH_RADIUS_M * cos(originLatRad)).toFloat()
        
        return Pair(north, east)
    }
    
    /**
     * Convert GPS data to integration-compatible format
     * Converts from NED (GPS native) to NWU (integration frame)
     */
    fun convertToIntegration(
        gnssData: List<GNSSData>,
        startSensorTime: Double
    ): List<GPSIntegrationPoint>? {
        // Filter to only entries with valid sensor timestamps
        val validData = gnssData.filter { it.sensorTimestamp != null }
            .sortedBy { it.sensorTimestamp }
        
        if (validData.isEmpty()) return null
        
        // Find origin (first GPS point at or after start time)
        val originIndex = validData.indexOfFirst { it.sensorTimestamp!! >= startSensorTime }
            .takeIf { it >= 0 } ?: 0
        
        val origin = validData[originIndex]
        val originLat = origin.lat
        val originLon = origin.lon
        val originAlt = origin.hMSL
        
        return validData.map { gnss ->
            val sensorTime = gnss.sensorTimestamp!!
            
            // Convert lat/lon to local NE
            val (north, east) = latLonToLocalNE(gnss.lat, gnss.lon, originLat, originLon)
            
            // Convert NED velocity to NWU
            val velNorth = gnss.velN
            val velWest = -gnss.velE   // East -> West (negate)
            val velUp = -gnss.velD     // Down -> Up (negate)
            
            // Convert position to NWU
            val posNorth = north
            val posWest = -east        // East -> West (negate)
            val posUp = gnss.hMSL - originAlt
            
            // Derived values
            val horizontalSpeed = sqrt(velNorth * velNorth + velWest * velWest)
            val horizontalDistance = sqrt(posNorth * posNorth + posWest * posWest)
            
            GPSIntegrationPoint(
                sensorTime = sensorTime,
                velNorth = velNorth,
                velWest = velWest,
                velUp = velUp,
                posNorth = posNorth,
                posWest = posWest,
                posUp = posUp,
                horizontalSpeed = horizontalSpeed,
                horizontalDistance = horizontalDistance,
                lat = gnss.lat,
                lon = gnss.lon,
                hMSL = gnss.hMSL,
                hAcc = gnss.hAcc,
                vAcc = gnss.vAcc,
                numSV = gnss.numSV
            )
        }
    }
}
```

---

## Phase 5: Kalman Filter

### Aero Kalman Filter Design

**State Vector (6D):**
```
x = [v_N, v_W, v_U, a_N, a_W, a_U]
```

**Process Model:**
```
v_new = v + a * dt
a_new = a + w_a  (random walk with process noise)
```

**Observations:**

| Source | Observation | Rate | Noise |
|--------|------------|------|-------|
| GPS | v_N, v_W, v_U directly | 20 Hz | Low (sAcc from GPS) |
| IMU + q | a_world = q ⊗ a_body ⊗ q* | 200 Hz | Medium |

```kotlin
package com.flysight.fusion.kalman

import com.flysight.fusion.math.Quaternion
import com.flysight.fusion.math.Vector3

/**
 * GPS-Aero Kalman Filter
 * 
 * Fuses GPS velocity observations with IMU acceleration
 * to produce smooth, low-latency velocity and acceleration estimates.
 */
class AeroKalmanFilter {
    
    // State: [velN, velW, velU, accelN, accelW, accelU]
    private val state = FloatArray(6)
    
    // State covariance (6x6)
    private val P = FloatArray(36)
    
    // Process noise
    var processNoiseVel = 0.1f    // Velocity process noise
    var processNoiseAccel = 1.0f  // Acceleration process noise
    
    // Measurement noise
    var gpsVelNoise = 0.5f        // GPS velocity noise (from sAcc)
    var imuAccelNoise = 2.0f      // IMU acceleration noise
    
    init {
        reset()
    }
    
    fun reset() {
        state.fill(0f)
        P.fill(0f)
        // Initialize diagonal of P with high uncertainty
        for (i in 0 until 6) {
            P[i * 6 + i] = 100f
        }
    }
    
    /**
     * Predict step - called at IMU rate (200 Hz)
     */
    fun predict(dt: Float) {
        // State prediction: v = v + a*dt, a = a
        state[0] += state[3] * dt  // velN
        state[1] += state[4] * dt  // velW
        state[2] += state[5] * dt  // velU
        
        // Covariance prediction (simplified)
        // P = F*P*F' + Q
        // For now, just add process noise to diagonal
        P[0] += processNoiseVel * dt
        P[7] += processNoiseVel * dt
        P[14] += processNoiseVel * dt
        P[21] += processNoiseAccel * dt
        P[28] += processNoiseAccel * dt
        P[35] += processNoiseAccel * dt
    }
    
    /**
     * Update with GPS velocity observation
     */
    fun updateGPS(velN: Float, velW: Float, velU: Float, sAcc: Float) {
        val R = sAcc * sAcc  // Measurement noise from GPS accuracy
        
        // Simple scalar updates for each velocity component
        updateScalar(0, velN, R)
        updateScalar(1, velW, R)
        updateScalar(2, velU, R)
    }
    
    /**
     * Update with IMU acceleration (transformed to world frame)
     */
    fun updateIMU(accelWorld: Vector3, q: Quaternion) {
        val R = imuAccelNoise * imuAccelNoise
        
        updateScalar(3, accelWorld.x, R)  // accelN
        updateScalar(4, accelWorld.y, R)  // accelW
        updateScalar(5, accelWorld.z, R)  // accelU
    }
    
    private fun updateScalar(stateIdx: Int, measurement: Float, R: Float) {
        val Pii = P[stateIdx * 6 + stateIdx]
        val K = Pii / (Pii + R)  // Kalman gain
        
        val innovation = measurement - state[stateIdx]
        state[stateIdx] += K * innovation
        P[stateIdx * 6 + stateIdx] = (1 - K) * Pii
    }
    
    // Getters
    fun getVelocity(): Vector3 = Vector3(state[0], state[1], state[2])
    fun getAcceleration(): Vector3 = Vector3(state[3], state[4], state[5])
}
```

---

## Phase 6: Mag Calibration

### Online Sphere Fitting

**Strategy:**
- Maintain orientation bins (discretized by quaternion)
- Keep best sample per bin (for orthogonality)
- Periodically fit sphere to diverse samples

```kotlin
package com.flysight.fusion.calibration

import com.flysight.fusion.math.Quaternion
import com.flysight.fusion.math.Vector3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Online magnetometer calibration using orthogonal sphere fitting
 */
class MagCalibrationWatchdog(
    private val numBins: Int = 26  // 6 faces + 12 edges + 8 corners
) {
    // Current hard iron estimate
    var hardIron = Vector3.zero()
        private set
    
    // Current field magnitude
    var fieldMagnitude = 50f  // Default ~50 µT
        private set
    
    // Orientation bins for sample collection
    private val bins = Array(numBins) { MagSample(0f, 0f, 0f, 0f) }
    private val binFilled = BooleanArray(numBins)
    
    // Interference detection
    private var lastMagnitude = 0f
    private var interferenceLevel = 0f
    
    data class MagSample(
        val mx: Float,
        val my: Float,
        val mz: Float,
        val residual: Float
    )
    
    /**
     * Process new magnetometer sample
     * 
     * @param mag Raw magnetometer reading
     * @param q Current orientation quaternion
     * @param isCleanField True if in clean-field conditions (e.g., freefall)
     * @return True if calibration was updated
     */
    fun processSample(
        mag: Vector3,
        q: Quaternion,
        isCleanField: Boolean
    ): Boolean {
        // Check for interference
        val magnitude = mag.magnitude()
        val expectedMag = fieldMagnitude
        val deviation = abs(magnitude - expectedMag) / expectedMag
        
        interferenceLevel = 0.9f * interferenceLevel + 0.1f * deviation
        lastMagnitude = magnitude
        
        // Only collect samples in clean field
        if (!isCleanField || interferenceLevel > 0.2f) {
            return false
        }
        
        // Determine which bin this sample belongs to
        val binIndex = orientationToBin(q)
        
        // Compute residual from current sphere
        val corrected = Vector3(
            mag.x - hardIron.x,
            mag.y - hardIron.y,
            mag.z - hardIron.z
        )
        val residual = abs(corrected.magnitude() - fieldMagnitude)
        
        // Update bin if this sample is better
        if (!binFilled[binIndex] || residual < bins[binIndex].residual) {
            bins[binIndex] = MagSample(mag.x, mag.y, mag.z, residual)
            binFilled[binIndex] = true
        }
        
        // Check if we have enough diverse samples
        val filledCount = binFilled.count { it }
        if (filledCount >= 6) {
            return tryUpdateCalibration()
        }
        
        return false
    }
    
    private fun orientationToBin(q: Quaternion): Int {
        // Map quaternion to one of numBins discrete orientations
        // Use the body-frame down vector direction
        
        val downWorld = Vector3(0f, 0f, -1f)
        val downBody = q.conjugate().rotateVector(downWorld)
        
        // Quantize to 26 directions (6 faces + 12 edges + 8 corners of cube)
        val ax = if (downBody.x > 0.4f) 1 else if (downBody.x < -0.4f) -1 else 0
        val ay = if (downBody.y > 0.4f) 1 else if (downBody.y < -0.4f) -1 else 0
        val az = if (downBody.z > 0.4f) 1 else if (downBody.z < -0.4f) -1 else 0
        
        // Convert to bin index
        return (ax + 1) * 9 + (ay + 1) * 3 + (az + 1)
    }
    
    private fun tryUpdateCalibration(): Boolean {
        // Collect filled samples
        val samples = bins.filterIndexed { i, _ -> binFilled[i] }
        if (samples.size < 6) return false
        
        // Fit sphere using linear least squares
        // (mx - cx)² + (my - cy)² + (mz - cz)² = r²
        // Rearranged: 2*cx*mx + 2*cy*my + 2*cz*mz + d = mx² + my² + mz²
        // where d = r² - cx² - cy² - cz²
        
        // Build A matrix and b vector
        val n = samples.size
        val A = Array(n) { FloatArray(4) }
        val b = FloatArray(n)
        
        for (i in samples.indices) {
            val s = samples[i]
            A[i][0] = 2 * s.mx
            A[i][1] = 2 * s.my
            A[i][2] = 2 * s.mz
            A[i][3] = 1f
            b[i] = s.mx * s.mx + s.my * s.my + s.mz * s.mz
        }
        
        // Solve A'Ax = A'b using simple matrix math
        val result = solveLeastSquares(A, b) ?: return false
        
        val cx = result[0]
        val cy = result[1]
        val cz = result[2]
        val d = result[3]
        val rSquared = d + cx * cx + cy * cy + cz * cz
        
        if (rSquared <= 0) return false
        
        hardIron = Vector3(cx, cy, cz)
        fieldMagnitude = sqrt(rSquared)
        
        // Clear bins for next calibration cycle
        binFilled.fill(false)
        
        return true
    }
    
    private fun solveLeastSquares(A: Array<FloatArray>, b: FloatArray): FloatArray? {
        // Compute A'A (4x4) and A'b (4x1)
        val ATA = FloatArray(16)
        val ATb = FloatArray(4)
        
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                var sum = 0f
                for (k in A.indices) {
                    sum += A[k][i] * A[k][j]
                }
                ATA[i * 4 + j] = sum
            }
            var sum = 0f
            for (k in A.indices) {
                sum += A[k][i] * b[k]
            }
            ATb[i] = sum
        }
        
        // Solve using Gaussian elimination (4x4 is small enough)
        return solveLinear4x4(ATA, ATb)
    }
    
    private fun solveLinear4x4(A: FloatArray, b: FloatArray): FloatArray? {
        // Simple 4x4 Gaussian elimination with partial pivoting
        val aug = Array(4) { i -> FloatArray(5).also { 
            for (j in 0 until 4) it[j] = A[i * 4 + j]
            it[4] = b[i]
        }}
        
        for (col in 0 until 4) {
            // Find pivot
            var maxRow = col
            for (row in col + 1 until 4) {
                if (abs(aug[row][col]) > abs(aug[maxRow][col])) {
                    maxRow = row
                }
            }
            val tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp
            
            if (abs(aug[col][col]) < 1e-10f) return null
            
            // Eliminate
            for (row in col + 1 until 4) {
                val factor = aug[row][col] / aug[col][col]
                for (j in col until 5) {
                    aug[row][j] -= factor * aug[col][j]
                }
            }
        }
        
        // Back substitution
        val x = FloatArray(4)
        for (i in 3 downTo 0) {
            x[i] = aug[i][4]
            for (j in i + 1 until 4) {
                x[i] -= aug[i][j] * x[j]
            }
            x[i] /= aug[i][i]
        }
        
        return x
    }
    
    /**
     * Check if magnetometer seems to be experiencing interference
     */
    fun isInterferenceDetected(): Boolean = interferenceLevel > 0.2f
    
    /**
     * Get current calibration quality (0-1)
     */
    fun getCalibrationQuality(): Float {
        val filledCount = binFilled.count { it }
        return (filledCount.toFloat() / numBins).coerceIn(0f, 1f)
    }
}
```

---

## Replay vs Live Mode

```kotlin
package com.flysight.fusion

sealed class DataSource {
    data class Replay(
        val sensorFile: java.io.File,
        val trackFile: java.io.File
    ) : DataSource()
    
    data class Live(
        val bluetoothDevice: android.bluetooth.BluetoothDevice
    ) : DataSource()
}

class FusionEngine(
    private val source: DataSource,
    private val onOrientationUpdate: (Quaternion) -> Unit,
    private val onPositionUpdate: (Vector3) -> Unit
) {
    private val ahrs = MadgwickAHRS()
    private val kalman = AeroKalmanFilter()
    private val magCalibration = MagCalibrationWatchdog()
    
    fun start() {
        when (source) {
            is DataSource.Replay -> startReplayMode(source)
            is DataSource.Live -> startBluetoothMode(source)
        }
    }
    
    private fun startReplayMode(source: DataSource.Replay) {
        // Parse files
        val sensorData = SensorCsvParser.parse(source.sensorFile.inputStream())
        val trackData = TrackCsvParser.parse(source.trackFile.inputStream())
        
        // Synchronize timestamps
        val syncResult = TimestampSynchronizer.computeSync(
            sensorData.timeData,
            trackData
        )
        TimestampSynchronizer.applySync(trackData.gnssData, syncResult)
        
        // Process data (can be at accelerated speed)
        processDataset(sensorData, trackData)
    }
    
    private fun startBluetoothMode(source: DataSource.Live) {
        // Connect and stream - see Bluetooth Integration section
    }
    
    private fun processIMU(imu: IMUData, mag: MagData?) {
        val gyro = Vector3(imu.gyroX, imu.gyroY, imu.gyroZ)
        val accel = Vector3(imu.accelX, imu.accelY, imu.accelZ)
        
        if (mag != null) {
            val magVec = Vector3(mag.magX, mag.magY, mag.magZ)
            ahrs.updateMARG(gyro, accel, magVec)
        } else {
            ahrs.updateIMU(gyro, accel)
        }
        
        onOrientationUpdate(ahrs.quaternion.copy())
    }
}
```

---

## Bluetooth Integration

```kotlin
package com.flysight.fusion.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Bluetooth LE connection to FlySight device
 */
class FlySightBleConnection(
    private val device: BluetoothDevice
) {
    private val _dataFlow = MutableSharedFlow<SensorPacket>(
        extraBufferCapacity = 100
    )
    val dataFlow: Flow<SensorPacket> = _dataFlow
    
    // Clock synchronization
    private var clockOffset: Long = 0
    private val offsetSamples = ArrayDeque<Long>(20)
    
    data class SensorPacket(
        val flysightTimestamp: Long,  // Device time (µs)
        val receiveTimeNanos: Long,   // Android time
        val imu: IMUData?,
        val mag: MagData?,
        val gps: GNSSData?
    )
    
    fun connect(context: android.content.Context) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: android.bluetooth.BluetoothGattCharacteristic
            ) {
                val receiveTime = System.nanoTime()
                val packet = parsePacket(characteristic.value, receiveTime)
                
                // Update clock offset estimate
                updateClockSync(packet)
                
                _dataFlow.tryEmit(packet)
            }
        })
    }
    
    private fun parsePacket(data: ByteArray, receiveTime: Long): SensorPacket {
        // Parse FlySight binary protocol
        // TODO: Implement based on actual protocol
        return SensorPacket(
            flysightTimestamp = 0,
            receiveTimeNanos = receiveTime,
            imu = null,
            mag = null,
            gps = null
        )
    }
    
    private fun updateClockSync(packet: SensorPacket) {
        // Estimate clock offset using GPS time as reference
        if (packet.gps != null) {
            val gpsTimeNanos = packet.gps.timestamp * 1_000_000  // ms to ns
            val offset = packet.receiveTimeNanos - gpsTimeNanos
            
            offsetSamples.addLast(offset)
            if (offsetSamples.size > 20) offsetSamples.removeFirst()
            
            clockOffset = offsetSamples.sorted()[offsetSamples.size / 2]  // Median
        }
    }
    
    /**
     * Convert FlySight timestamp to Android time
     */
    fun toAndroidTime(flysightTimestamp: Long): Long {
        return flysightTimestamp + clockOffset
    }
}
```

---

## Meta Spatial SDK Integration

```kotlin
package com.flysight.fusion.spatial

import com.flysight.fusion.math.Quaternion
import com.flysight.fusion.math.Vector3
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion as SpatialQuaternion
import com.meta.spatial.core.Vector3 as SpatialVector3

/**
 * Bridge between fusion engine and Meta Spatial SDK
 */
class SpatialVisualization(
    private val deviceEntity: Entity,
    private val trajectoryEntity: Entity?,
    private val calibrationSphereEntity: Entity?
) {
    /**
     * Update device orientation from fusion quaternion
     */
    fun updateDeviceOrientation(q: Quaternion) {
        val spatialQuat = SpatialQuaternion(q.x, q.y, q.z, q.w)
        deviceEntity.setComponent(Pose(rotation = spatialQuat))
    }
    
    /**
     * Update trajectory visualization
     */
    fun updateTrajectory(position: Vector3) {
        trajectoryEntity?.let { entity ->
            val spatialPos = SpatialVector3(position.x, position.y, position.z)
            // Add point to trajectory mesh
            // Implementation depends on how trajectory is rendered
        }
    }
    
    /**
     * Visualize magnetometer calibration sphere
     */
    fun updateCalibrationSphere(
        hardIron: Vector3,
        fieldMagnitude: Float,
        samples: List<Vector3>
    ) {
        calibrationSphereEntity?.let { entity ->
            // Update sphere center and radius
            // Render sample points on sphere surface
            // Color-code by residual
        }
    }
}
```

---

## Performance Considerations

### Use Primitive Arrays

```kotlin
// BAD - boxing overhead
val data: List<Float> = listOf(1f, 2f, 3f)

// GOOD - no boxing
val data = floatArrayOf(1f, 2f, 3f)
```

### Pre-allocate Buffers

```kotlin
class MadgwickAHRS {
    // Pre-allocate work arrays
    private val tempQuat = Quaternion()
    private val tempVec = Vector3()
    
    fun update(...) {
        // Reuse tempQuat instead of allocating new
    }
}
```

### Thread Architecture

```
Bluetooth Thread → Ring Buffer → Fusion Thread → Render Thread
                                      ↓
                               Spatial SDK
```

Use `RingBuffer` or `ArrayBlockingQueue` for thread-safe data passing.

### Consider NDK for Hot Paths

If Madgwick update runs at 500+ Hz and causes GC pressure, consider:
- Moving to C++ via JNI
- Using `@JvmInline value class` for small math types
- Object pooling for frequently allocated types

---

## Summary

This porting guide covers the complete FlySight sensor fusion system for Android/Quest:

1. **Core Math** - Quaternion, Vector3, MathUtils
2. **Madgwick AHRS** - Orientation from IMU+Mag
3. **CSV Parsers** - SENSOR.CSV and TRACK.CSV
4. **Timestamp Sync** - Align sensor and GPS time
5. **GPS Integration** - Convert to NWU frame
6. **Kalman Filter** - GPS-aided acceleration estimation
7. **Mag Calibration** - Online sphere fitting
8. **Bluetooth** - Live streaming from FlySight
9. **Spatial SDK** - 3D visualization

Port in order of priority, testing each phase before moving to the next. The replay mode (CSV files) provides a safe environment for testing before enabling live Bluetooth streaming.
