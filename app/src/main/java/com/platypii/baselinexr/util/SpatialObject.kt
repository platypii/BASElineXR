package com.platypii.baselinexr.util

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Three.js-style wrapper for Meta Spatial entities, providing easy parent-child relationships.
 * 
 * Similar to THREE.Object3D, this class allows you to:
 * - Create hierarchies of objects
 * - Set local transforms (position, rotation) relative to parent
 * - Have children automatically follow parent transformations
 * - Apply model offset rotations to correct native model orientation
 * 
 * Usage:
 * ```
 * // Create a parent (like device model)
 * val device = SpatialObject.createFromMesh("flysight.gltf")
 * device.setWorldPosition(Vector3(0f, 1f, -1f))
 * device.setLocalRotation(deviceQuaternion)
 * 
 * // Create child arrows that follow the device
 * val accelArrow = SpatialObject.createFromMesh("arrow.glb")
 * device.add(accelArrow)
 * accelArrow.setLocalRotation(arrowDirection)  // Local to device
 * 
 * // Update the hierarchy
 * device.updateWorldTransform()  // Propagates to children
 * ```
 * 
 * The world transform is computed as:
 *   worldTransform = parent.worldTransform * localTransform * baseRotation
 * 
 * Where baseRotation is an optional model offset to correct native orientation.
 */
class SpatialObject private constructor(
    val entity: Entity,
    private val baseRotation: Quaternion = IDENTITY_QUAT
) {
    companion object {
        private const val TAG = "SpatialObject"
        
        val IDENTITY_QUAT = Quaternion(0f, 0f, 0f, 1f)
        val IDENTITY_POS = Vector3(0f, 0f, 0f)
        
        /**
         * Create a SpatialObject from a mesh file.
         * 
         * @param meshPath Path to the mesh file (e.g., "arrow.glb")
         * @param baseRotation Optional rotation to correct model's native orientation
         * @param scale Initial scale
         */
        fun createFromMesh(
            meshPath: String,
            baseRotation: Quaternion = IDENTITY_QUAT,
            scale: Float = 1f
        ): SpatialObject {
            val entity = Entity.create(
                Mesh(meshPath.toUri()),
                Transform(Pose(IDENTITY_POS, IDENTITY_QUAT)),
                Scale(Vector3(scale)),
                Visible(false)
            )
            val obj = SpatialObject(entity, baseRotation)
            obj.localScale = scale  // Preserve initial scale for updateWorldTransform
            return obj
        }
        
        /**
         * Create a SpatialObject from a mesh URI.
         */
        fun createFromMesh(
            meshUri: Uri,
            baseRotation: Quaternion = IDENTITY_QUAT,
            scale: Float = 1f
        ): SpatialObject {
            val entity = Entity.create(
                Mesh(meshUri),
                Transform(Pose(IDENTITY_POS, IDENTITY_QUAT)),
                Scale(Vector3(scale)),
                Visible(false)
            )
            val obj = SpatialObject(entity, baseRotation)
            obj.localScale = scale  // Preserve initial scale for updateWorldTransform
            return obj
        }
        
        /**
         * Create a SpatialObject wrapping an existing entity.
         */
        fun wrap(entity: Entity, baseRotation: Quaternion = IDENTITY_QUAT): SpatialObject {
            return SpatialObject(entity, baseRotation)
        }
        
        /**
         * Create a quaternion that rotates to point in the given direction.
         * Arrow models are assumed to point in +Z direction by default.
         */
        fun lookRotation(direction: Vector3, up: Vector3 = Vector3(0f, 1f, 0f)): Quaternion {
            val mag = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
            if (mag < 0.0001f) return IDENTITY_QUAT
            
            val forward = Vector3(direction.x / mag, direction.y / mag, direction.z / mag)
            
            // Gram-Schmidt orthogonalization
            val dot = up.x * forward.x + up.y * forward.y + up.z * forward.z
            var right = Vector3(
                up.y * forward.z - up.z * forward.y,
                up.z * forward.x - up.x * forward.z,
                up.x * forward.y - up.y * forward.x
            )
            val rightMag = sqrt(right.x * right.x + right.y * right.y + right.z * right.z)
            if (rightMag < 0.0001f) {
                // Direction is parallel to up, use different up vector
                right = Vector3(1f, 0f, 0f)
            } else {
                right = Vector3(right.x / rightMag, right.y / rightMag, right.z / rightMag)
            }
            
            val newUp = Vector3(
                forward.y * right.z - forward.z * right.y,
                forward.z * right.x - forward.x * right.z,
                forward.x * right.y - forward.y * right.x
            )
            
            // Build rotation matrix and convert to quaternion
            // Matrix columns are: right, newUp, forward
            val m00 = right.x; val m01 = newUp.x; val m02 = forward.x
            val m10 = right.y; val m11 = newUp.y; val m12 = forward.y
            val m20 = right.z; val m21 = newUp.z; val m22 = forward.z
            
            val trace = m00 + m11 + m22
            return if (trace > 0) {
                val s = 0.5f / sqrt(trace + 1.0f)
                Quaternion(
                    (m21 - m12) * s,
                    (m02 - m20) * s,
                    (m10 - m01) * s,
                    0.25f / s
                )
            } else if (m00 > m11 && m00 > m22) {
                val s = 2.0f * sqrt(1.0f + m00 - m11 - m22)
                Quaternion(
                    0.25f * s,
                    (m01 + m10) / s,
                    (m02 + m20) / s,
                    (m21 - m12) / s
                )
            } else if (m11 > m22) {
                val s = 2.0f * sqrt(1.0f + m11 - m00 - m22)
                Quaternion(
                    (m01 + m10) / s,
                    0.25f * s,
                    (m12 + m21) / s,
                    (m02 - m20) / s
                )
            } else {
                val s = 2.0f * sqrt(1.0f + m22 - m00 - m11)
                Quaternion(
                    (m02 + m20) / s,
                    (m12 + m21) / s,
                    0.25f * s,
                    (m10 - m01) / s
                )
            }
        }
        
        /**
         * Multiply two quaternions: result = q1 * q2
         * Represents rotation q2 followed by q1.
         */
        fun multiplyQuaternion(q1: Quaternion, q2: Quaternion): Quaternion {
            return Quaternion(
                q1.w * q2.x + q1.x * q2.w + q1.y * q2.z - q1.z * q2.y,
                q1.w * q2.y - q1.x * q2.z + q1.y * q2.w + q1.z * q2.x,
                q1.w * q2.z + q1.x * q2.y - q1.y * q2.x + q1.z * q2.w,
                q1.w * q2.w - q1.x * q2.x - q1.y * q2.y - q1.z * q2.z
            )
        }
        
        /**
         * Rotate a vector by a quaternion.
         */
        fun rotateVector(v: Vector3, q: Quaternion): Vector3 {
            val qx = q.x; val qy = q.y; val qz = q.z; val qw = q.w
            
            val tx = qw * v.x + qy * v.z - qz * v.y
            val ty = qw * v.y + qz * v.x - qx * v.z
            val tz = qw * v.z + qx * v.y - qy * v.x
            val tw = -qx * v.x - qy * v.y - qz * v.z
            
            return Vector3(
                tw * -qx + tx * qw + ty * -qz - tz * -qy,
                tw * -qy + ty * qw + tz * -qx - tx * -qz,
                tw * -qz + tz * qw + tx * -qy - ty * -qx
            )
        }
    }
    
    // Parent-child relationship
    private var parent: SpatialObject? = null
    private val children = mutableListOf<SpatialObject>()
    
    // Local transform (relative to parent)
    private var localPosition = IDENTITY_POS
    private var localRotation = IDENTITY_QUAT
    private var localScale = 1f
    
    // Cached world transform
    private var worldPosition = IDENTITY_POS
    private var worldRotation = IDENTITY_QUAT
    private var dirty = true
    
    // Visibility
    private var visible = false
    
    /**
     * Add a child SpatialObject. The child will follow this object's transform.
     */
    fun add(child: SpatialObject) {
        if (child.parent != null) {
            child.parent?.remove(child)
        }
        child.parent = this
        children.add(child)
        child.markDirty()
    }
    
    /**
     * Remove a child SpatialObject.
     */
    fun remove(child: SpatialObject) {
        if (children.remove(child)) {
            child.parent = null
            child.markDirty()
        }
    }
    
    /**
     * Set local position relative to parent.
     */
    fun setLocalPosition(position: Vector3) {
        localPosition = position
        markDirty()
    }
    
    /**
     * Set local rotation relative to parent.
     */
    fun setLocalRotation(rotation: Quaternion) {
        localRotation = rotation
        markDirty()
    }
    
    /**
     * Set local scale.
     */
    fun setLocalScale(scale: Float) {
        localScale = scale
    }
    
    /**
     * Set world position directly (for root objects).
     */
    fun setWorldPosition(position: Vector3) {
        if (parent == null) {
            localPosition = position
            worldPosition = position
            dirty = false
        } else {
            // Would need to compute local from world - simplified for now
            localPosition = position
            markDirty()
        }
    }
    
    /**
     * Set world rotation directly (for root objects).
     */
    fun setWorldRotation(rotation: Quaternion) {
        if (parent == null) {
            localRotation = rotation
            worldRotation = rotation
            dirty = false
        } else {
            localRotation = rotation
            markDirty()
        }
    }
    
    /**
     * Set visibility of this object and optionally cascade to children.
     */
    fun setVisible(isVisible: Boolean, cascade: Boolean = true) {
        visible = isVisible
        entity.setComponent(Visible(isVisible))
        if (cascade) {
            children.forEach { it.setVisible(isVisible, cascade) }
        }
    }
    
    /**
     * Get current world position.
     */
    fun getWorldPosition(): Vector3 {
        if (dirty) updateWorldTransformInternal()
        return worldPosition
    }
    
    /**
     * Get current world rotation.
     */
    fun getWorldRotation(): Quaternion {
        if (dirty) updateWorldTransformInternal()
        return worldRotation
    }
    
    /**
     * Mark this object and all descendants as needing transform update.
     */
    private fun markDirty() {
        dirty = true
        children.forEach { it.markDirty() }
    }
    
    /**
     * Update the world transform and apply to the entity.
     * Call this on the root object after setting all transforms.
     */
    fun updateWorldTransform() {
        updateWorldTransformInternal()
        children.forEach { it.updateWorldTransform() }
    }
    
    private fun updateWorldTransformInternal() {
        if (!dirty && parent == null) return
        
        if (parent == null) {
            // Root object: local = world
            worldPosition = localPosition
            // Apply base rotation after local rotation
            worldRotation = multiplyQuaternion(localRotation, baseRotation)
        } else {
            // Child object: transform relative to parent
            val parentPos = parent!!.getWorldPosition()
            val parentRot = parent!!.getWorldRotation()
            
            // Rotate local position by parent rotation and add to parent position
            val rotatedLocalPos = rotateVector(localPosition, parentRot)
            worldPosition = Vector3(
                parentPos.x + rotatedLocalPos.x,
                parentPos.y + rotatedLocalPos.y,
                parentPos.z + rotatedLocalPos.z
            )
            
            // Compose rotations: parent * local * base
            val combinedLocal = multiplyQuaternion(localRotation, baseRotation)
            worldRotation = multiplyQuaternion(parentRot, combinedLocal)
        }
        
        // Apply to entity
        entity.setComponents(listOf(
            Transform(Pose(worldPosition, worldRotation)),
            Scale(Vector3(localScale)),
            Visible(visible)
        ))
        
        dirty = false
    }
    
    /**
     * Destroy this object and all children.
     */
    fun destroy() {
        children.forEach { it.destroy() }
        children.clear()
        parent?.remove(this)
        // Entity cleanup is handled by Meta Spatial garbage collection
    }
}

/**
 * Factory for creating common AHRS visualization objects.
 */
object AhrsObjectFactory {
    
    // Arrow model base rotation to correct for native orientation
    // If arrow.glb points in +Z by default but we want it to point in a specific direction,
    // we set the local rotation. The baseRotation here corrects any model-specific issues.
    private val ARROW_BASE_ROTATION = Quaternion(0f, 0f, 0f, 1f)  // Identity unless model needs correction
    
    /**
     * Create a FlySight device model.
     * 
     * The flysightspatial.gltf model is pre-rotated 180° around Y so that
     * at NWU identity, the model's front faces Meta +Z (toward user/South).
     */
    fun createDeviceModel(scale: Float = 0.3f): SpatialObject {
        // Model offset rotation (0, π, π) for flysight model
        // This corrects the model's native orientation to match the expected body frame
        val modelOffset = createQuaternionFromEuler(0f, Math.PI.toFloat(), Math.PI.toFloat())
        return SpatialObject.createFromMesh("flysightspatial.gltf", modelOffset, scale)
    }
    
    /**
     * Create a sensor vector arrow.
     * 
     * @param meshPath Path to the arrow mesh (e.g., "vectoro.glb" for orange)
     * @param scale Arrow scale
     */
    fun createVectorArrow(meshPath: String, scale: Float = 0.15f): SpatialObject {
        // Arrow model offset - same as device model for consistency
        val modelOffset = createQuaternionFromEuler(0f, Math.PI.toFloat(), Math.PI.toFloat())
        return SpatialObject.createFromMesh(meshPath, modelOffset, scale)
    }
    
    /**
     * Create a quaternion from Euler angles (roll, pitch, yaw) in radians.
     * Order: Y(yaw) * X(pitch) * Z(roll) - intrinsic rotations
     */
    fun createQuaternionFromEuler(roll: Float, pitch: Float, yaw: Float): Quaternion {
        val halfYaw = yaw * 0.5f
        val halfPitch = pitch * 0.5f
        val halfRoll = roll * 0.5f

        val cy = cos(halfYaw)
        val sy = sin(halfYaw)
        val cp = cos(halfPitch)
        val sp = sin(halfPitch)
        val cr = cos(halfRoll)
        val sr = sin(halfRoll)

        val qw = cy * cp * cr + sy * sp * sr
        val qx = cy * sp * cr + sy * cp * sr
        val qy = sy * cp * cr - cy * sp * sr
        val qz = cy * cp * sr - sy * sp * cr

        return Quaternion(qx, qy, qz, qw)
    }
    
    /**
     * Calculate the rotation quaternion to point an arrow in a given direction.
     * 
     * @param direction The direction vector in Meta Spatial coordinates
     * @return Quaternion that when combined with model offset points arrow in direction
     */
    fun calculateArrowRotation(direction: Vector3): Quaternion {
        val mag = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        if (mag < 0.001f) return SpatialObject.IDENTITY_QUAT
        
        val dx = direction.x / mag
        val dy = direction.y / mag
        val dz = direction.z / mag
        
        // Calculate pitch (angle from horizontal)
        val horizontalSpeed = sqrt(dx * dx + dz * dz)
        val pitchRad = kotlin.math.atan2(-dy, horizontalSpeed)
        
        // Calculate yaw for +Z forward
        val flightYaw = -kotlin.math.atan2(dz, dx)
        
        // Attitude rotation with -π/2 yaw offset
        return createQuaternionFromEuler(0f, pitchRad, (-flightYaw - Math.PI.toFloat() / 2f))
    }
}
