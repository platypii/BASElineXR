package com.platypii.baselinexr.util

import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Quaternion utility functions for 3D rotations.
 * 
 * Meta Spatial SDK coordinate system:
 * - Y-up (Y axis points up)
 * - +Z forward (North in GPS terms)
 * - +X right (East in GPS terms)
 * 
 * GPS/Flight conventions:
 * - Heading: North=0°, East=90°, South=180°, West=-90° (from atan2(vE, vN))
 * - Pitch: 0° = level, negative = nose down (descending)
 * - Roll: positive = bank right (right wing down)
 * - AOA: positive = nose up relative to velocity vector
 */
object QuaternionUtil {

    /**
     * Create a quaternion from GPS flight attitude.
     * 
     * This takes standard GPS/aviation conventions and converts to Meta SDK quaternion:
     * - headingRad: compass heading in radians (North=0, East=π/2)
     * - pitchRad: pitch angle (0=level, negative=nose down)
     * - rollRad: roll angle (positive=bank right)
     * 
     * Internally handles the coordinate system conversion from GPS to Meta SDK.
     */
    fun fromGpsAttitude(headingRad: Float, pitchRad: Float, rollRad: Float): Quaternion {
        // Convert GPS heading to SDK yaw
        // GPS: atan2(vE, vN) gives North=0, East=π/2
        // SDK: rotation around Y axis where 0 = facing +Z (North)
        // Since both conventions have North=0 when facing +Z, we just negate for rotation direction
        val yaw = -headingRad
        
        // Pitch: GPS negative = nose down, SDK positive rotation around X = nose down
        // So we negate pitch
        val pitch = -pitchRad
        
        // Roll: GPS positive = bank right, SDK positive rotation around Z = ?
        // Need to verify this - for now keep same sign
        val roll = rollRad
        
        return fromEuler(roll, pitch, yaw)
    }
    
    /**
     * Create a rotation quaternion for AOA (angle of attack).
     * AOA rotates the model nose-up relative to the velocity vector.
     * 
     * @param aoaRad: angle of attack in radians (positive = nose up)
     */
    fun fromAoa(aoaRad: Float): Quaternion {
        // AOA is a pitch rotation (around X axis in body frame)
        // Positive AOA = nose up = negative pitch rotation in SDK convention
        return fromEuler(0f, -aoaRad, 0f)
    }
    
    /**
     * Create a model orientation offset quaternion.
     * This corrects for glTF models that aren't authored with +Z forward.
     * 
     * For the fullhead.gltf and wingsuit models that need 180° flip:
     */
    fun modelOffset180(): Quaternion {
        // Rotate 180° around Y (yaw) to flip model forward direction
        return fromEuler(0f, 0f, Math.PI.toFloat())
    }

    fun modelOffset90(): Quaternion {
        // Rotate 180° around Y (yaw) to flip model forward direction
        return fromEuler(0f, 0f, (Math.PI/2).toFloat())
    }

    fun modelOffsetminus90(): Quaternion {
        // Rotate 180° around Y (yaw) to flip model forward direction
        return fromEuler(0f, 0f, (-Math.PI/2).toFloat())
    }

    /**
     * Model offset for wingsuit glTF model (tsimwingsuit.glb).
     * This rotation corrects the model to align with GPS coordinate frame.
     * Only yaw π needed to face forward (other adjustments in attitude calc).
     */
    fun wingsuitModelOffset(): Quaternion {
        // yaw=π to face forward
        return fromEuler(0f, 0f, Math.PI.toFloat())
    }
    
    /**
     * Model offset for canopy glTF model (cp2.glb).
     * Adjust as needed based on canopy model's native orientation.
     */
    fun canopyModelOffset(): Quaternion {
        // Same as wingsuit for now - adjust if canopy model differs
        return fromEuler(0f, Math.PI.toFloat(), Math.PI.toFloat())
    }
    
    /**
     * Model offset for vector/arrow glTF model (arrow.glb).
     * Adjust as needed based on arrow model's native orientation.
     */
    fun vectorModelOffset(): Quaternion {
        // Vectors may need different offset - adjust as needed
        return fromEuler(0f, 0f, 0f) // Identity for now
    }

    /**
     * Compute GPS heading from velocity vector.
     * @param vE: East velocity
     * @param vN: North velocity  
     * @return heading in radians (North=0, East=π/2)
     */
    fun headingFromVelocity(vE: Double, vN: Double): Float {
        return atan2(vE, vN).toFloat()
    }
    
    /**
     * Compute pitch from velocity vector.
     * @param vUp: Up velocity (positive = climbing)
     * @param vE: East velocity
     * @param vN: North velocity
     * @return pitch in radians (negative = nose down/descending)
     */
    fun pitchFromVelocity(vUp: Double, vE: Double, vN: Double): Float {
        val horizontalSpeed = sqrt(vE * vE + vN * vN)
        // Positive vUp = climbing = positive pitch (nose up)
        return atan2(vUp, horizontalSpeed).toFloat()
    }

    /**
     * Create a quaternion from Euler angles (roll, pitch, yaw) in radians.
     * Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
     * 
     * This is the low-level function. Prefer fromGpsAttitude() for flight data.
     */
    fun fromEuler(roll: Float, pitch: Float, yaw: Float): Quaternion {
        // Convert to half angles
        val halfYaw = yaw * 0.5f
        val halfPitch = pitch * 0.5f
        val halfRoll = roll * 0.5f

        val cy = cos(halfYaw)
        val sy = sin(halfYaw)
        val cp = cos(halfPitch)
        val sp = sin(halfPitch)
        val cr = cos(halfRoll)
        val sr = sin(halfRoll)

        // Quaternion multiplication: q_yaw * q_pitch * q_roll
        // Y-up, Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
        val qw = cy * cp * cr + sy * sp * sr
        val qx = cy * sp * cr + sy * cp * sr
        val qy = sy * cp * cr - cy * sp * sr
        val qz = cy * cp * sr - sy * sp * cr

        return Quaternion(qx, qy, qz, qw)
    }

    /**
     * Multiply two quaternions: q1 * q2
     * This applies q1 first, then q2
     */
    fun multiply(q1: Quaternion, q2: Quaternion): Quaternion {
        val w = q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
        val x = q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y
        val y = q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x
        val z = q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w

        return Quaternion(x, y, z, w)
    }

    /**
     * Rotate a Vector3 by a quaternion.
     * Transforms vector from one coordinate frame to another using quaternion rotation.
     */
    fun rotateVector(vector: Vector3, q: Quaternion): Vector3 {
        // Convert vector to quaternion with w=0
        val vecQuat = Quaternion(vector.x, vector.y, vector.z, 0f)

        // Calculate quaternion conjugate (inverse rotation)
        val qConj = Quaternion(-q.x, -q.y, -q.z, q.w)

        // Apply rotation: q * v * q^-1
        val temp = multiply(q, vecQuat)
        val result = multiply(temp, qConj)

        return Vector3(result.x, result.y, result.z)
    }

    /**
     * Rotate a tensor Vector3 by a quaternion.
     * Overload for compatibility with tensor.Vector3 type.
     */
    fun rotateVector(vector: com.platypii.baselinexr.util.tensor.Vector3, q: Quaternion): com.platypii.baselinexr.util.tensor.Vector3 {
        val vecQuat = Quaternion(vector.x.toFloat(), vector.y.toFloat(), vector.z.toFloat(), 0f)
        val qConj = Quaternion(-q.x, -q.y, -q.z, q.w)
        val temp = multiply(q, vecQuat)
        val result = multiply(temp, qConj)

        return com.platypii.baselinexr.util.tensor.Vector3(
            result.x.toDouble(),
            result.y.toDouble(),
            result.z.toDouble()
        )
    }
}
