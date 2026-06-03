package com.platypii.baselinexr.sensor

import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clean math utility for converting FlySight 2 sensor data to Meta world frame.
 * 
 * Coordinate Systems:
 * - NWU World Frame (Fusion library): X=North, Y=West, Z=Up
 * - Meta World Frame: X=East, Y=Up, Z=South
 * 
 * Sensor Body Frames:
 * - Accel/Gyro: Same as device body frame
 * - Mag: X and Z axes negated relative to device body frame
 * 
 * Conversion Pipeline:
 * 1. Sensor body → Device body (mag needs X,Z negation)
 * 2. Device body × Quaternion → NWU world
 * 3. NWU world → Meta world
 * 4. (Optional) Apply BASElineXR heading adjustment
 */
object SensorMath {
    
    /**
     * Convert a vector from device body frame to Meta world frame using the fusion quaternion.
     * 
     * @param deviceBodyVec Vector in device body frame (e.g., acceleration)
     * @param fusionQuat Quaternion from sensor fusion (NWU frame)
     * @return Vector in Meta world frame
     */
    fun deviceBodyToMetaWorld(deviceBodyVec: Vector3, fusionQuat: Quaternion): Vector3 {
        // Step 1: Rotate device body vector by fusion quaternion -> NWU world frame
        val nwuWorld = rotateVectorByQuaternion(deviceBodyVec, fusionQuat)
        
        // Step 2: Convert NWU world -> Meta world
        return nwuToMeta(nwuWorld)
    }
    
    /**
     * Convert magnetometer sensor data to device body frame.
     * Mag sensor has X and Z axes negated relative to device body.
     */
    fun magSensorToDeviceBody(magSensorVec: Vector3): Vector3 {
        return Vector3(-magSensorVec.x, magSensorVec.y, -magSensorVec.z)
    }
    
    /**
     * Convert a quaternion from NWU frame to Meta frame.
     * 
     * NWU (Fusion): X=North, Y=West, Z=Up
     * Meta: X=East, Y=Up, Z=South
     * 
     * Axis mapping:
     * - NWU X (North) → Meta -Z
     * - NWU Y (West) → Meta -X
     * - NWU Z (Up) → Meta Y
     * 
     * For quaternion (x, y, z, w), imaginary components transform like vectors.
     */
    fun quaternionNwuToMeta(nwuQuat: Quaternion): Quaternion {
        // NWU (x,y,z) -> Meta (-y, z, -x)
        return Quaternion(-nwuQuat.y, nwuQuat.z, -nwuQuat.x, nwuQuat.w)
    }
    
    /**
     * Convert a vector from NWU world frame to Meta world frame.
     * 
     * NWU (Fusion): X=North, Y=West, Z=Up
     * Meta: X=East, Y=Up, Z=South
     * 
     * Transform:
     * - meta_x = -nwu_y (West → -East)
     * - meta_y = nwu_z (Up → Up)
     * - meta_z = -nwu_x (North → -South)
     */
    fun nwuToMeta(nwuVec: Vector3): Vector3 {
        return Vector3(-nwuVec.y, nwuVec.z, -nwuVec.x)
    }
    
    /**
     * Apply a yaw rotation around the Y axis (up) in Meta frame.
     * Used for BASElineXR heading adjustment.
     * 
     * @param metaVec Vector in Meta world frame
     * @param yawRadians Yaw angle in radians (positive = counterclockwise from above)
     * @return Rotated vector in Meta world frame
     */
    fun applyYawRotation(metaVec: Vector3, yawRadians: Float): Vector3 {
        val cosY = cos(yawRadians)
        val sinY = sin(yawRadians)
        return Vector3(
            metaVec.x * cosY + metaVec.z * sinY,
            metaVec.y,
            -metaVec.x * sinY + metaVec.z * cosY
        )
    }
    
    /**
     * Create a yaw rotation quaternion around the Y axis (up) in Meta frame.
     * 
     * @param yawRadians Yaw angle in radians
     * @return Quaternion representing the yaw rotation
     */
    fun yawQuaternion(yawRadians: Float): Quaternion {
        val halfAngle = yawRadians / 2f
        return Quaternion(0f, sin(halfAngle), 0f, cos(halfAngle))
    }
    
    /**
     * Multiply two quaternions: result = a * b
     * Represents applying rotation b first, then rotation a.
     */
    fun multiplyQuaternions(a: Quaternion, b: Quaternion): Quaternion {
        return Quaternion(
            a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x,
            a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w,
            a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z
        )
    }
    
    /**
     * Quaternion inverse (conjugate for unit quaternions).
     * For a unit quaternion q = (x, y, z, w), inverse = (-x, -y, -z, w)
     */
    fun inverseQuaternion(q: Quaternion): Quaternion {
        return Quaternion(-q.x, -q.y, -q.z, q.w)
    }
    
    /**
     * Convert Euler angles (degrees) to quaternion.
     * Order: Yaw (Y) → Pitch (X) → Roll (Z), intrinsic.
     * 
     * @param yawDeg   rotation around Y axis (up), positive = turn left
     * @param pitchDeg rotation around X axis (right), positive = nose up
     * @param rollDeg  rotation around Z axis (forward), positive = roll right
     */
    fun eulerToQuaternion(yawDeg: Float, pitchDeg: Float, rollDeg: Float): Quaternion {
        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
        
        val cy = kotlin.math.cos(yaw * 0.5f)
        val sy = kotlin.math.sin(yaw * 0.5f)
        val cp = kotlin.math.cos(pitch * 0.5f)
        val sp = kotlin.math.sin(pitch * 0.5f)
        val cr = kotlin.math.cos(roll * 0.5f)
        val sr = kotlin.math.sin(roll * 0.5f)
        
        return Quaternion(
            cr * sp * cy + sr * cp * sy,  // x
            cr * cp * sy - sr * sp * cy,  // y
            sr * cp * cy - cr * sp * sy,  // z
            cr * cp * cy + sr * sp * sy   // w
        )
    }
    
    /**
     * Rotate a vector by a quaternion.
     * v' = q * v * q^-1
     */
    fun rotateVectorByQuaternion(v: Vector3, q: Quaternion): Vector3 {
        // Quaternion multiplication: q * v * q^-1
        // where v is treated as quaternion (0, vx, vy, vz)
        val qx = q.x
        val qy = q.y
        val qz = q.z
        val qw = q.w
        
        // Optimized rotation formula
        val tx = 2f * (qy * v.z - qz * v.y)
        val ty = 2f * (qz * v.x - qx * v.z)
        val tz = 2f * (qx * v.y - qy * v.x)
        
        return Vector3(
            v.x + qw * tx + qy * tz - qz * ty,
            v.y + qw * ty + qz * tx - qx * tz,
            v.z + qw * tz + qx * ty - qy * tx
        )
    }
    
    /**
     * Calculate the magnitude of a vector.
     */
    fun magnitude(v: Vector3): Float {
        return kotlin.math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z)
    }
    
    /**
     * Normalize a vector to unit length.
     */
    fun normalize(v: Vector3): Vector3 {
        val mag = magnitude(v)
        return if (mag > 0.0001f) {
            Vector3(v.x / mag, v.y / mag, v.z / mag)
        } else {
            Vector3(0f, 0f, 0f)
        }
    }
    
    /**
     * Calculate rotation quaternion to point an arrow (default pointing +Z) toward a direction.
     * Arrow models (sensor_arrow_accel.glb, sensor_arrow_mag.glb) point along +Z at identity rotation.
     *
     * @param direction Target direction vector (should be normalized)
     * @return Quaternion that rotates +Z to point along direction
     */
    fun arrowRotationFromDirection(direction: Vector3): Quaternion {
        // Arrow model points along +Z by default
        // We need to rotate +Z to align with direction
        val forward = Vector3(0f, 0f, 1f)

        // Handle edge cases
        val dot = forward.x * direction.x + forward.y * direction.y + forward.z * direction.z

        if (dot > 0.9999f) {
            // Already aligned with +Z
            return Quaternion(0f, 0f, 0f, 1f)
        }
        if (dot < -0.9999f) {
            // Opposite direction - rotate 180° around Y axis
            return Quaternion(0f, 1f, 0f, 0f)
        }
        
        // Cross product gives rotation axis
        val axis = Vector3(
            forward.y * direction.z - forward.z * direction.y,
            forward.z * direction.x - forward.x * direction.z,
            forward.x * direction.y - forward.y * direction.x
        )
        val axisNorm = normalize(axis)
        
        // Rotation angle from dot product
        val angle = kotlin.math.acos(dot.coerceIn(-1f, 1f))
        val halfAngle = angle / 2f
        val sinHalf = sin(halfAngle)
        
        return Quaternion(
            axisNorm.x * sinHalf,
            axisNorm.y * sinHalf,
            axisNorm.z * sinHalf,
            cos(halfAngle)
        )
    }
}
