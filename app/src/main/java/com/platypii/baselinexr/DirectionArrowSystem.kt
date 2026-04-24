package com.platypii.baselinexr

import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.util.HeadPoseUtil
import kotlin.math.*

class DirectionArrowSystem : SystemBase() {

    companion object {
        private const val TAG = "DirectionArrowSystem"
        private const val ARROW_SCALE = 0.1f
        private const val ARROW_HEIGHT_OFFSET = -5.0f
        private const val COMPASS_SCALE = 0.12f
        private const val MIN_SPEED_THRESHOLD = 0.9 // m/s minimum speed to show arrow
        private const val ENLARGEMENT_FACTOR = 10f
    }

    private var arrowEntity: Entity? = null
    private var northEntity: Entity? = null
    private var initialized = false
    private var isEnlarged = false

    override fun execute() {
        if (!initialized) {
            initializeDirectionArrow()
        }

        updateArrowDirection()
    }

    private fun initializeDirectionArrow() {
        // Create arrow using custom arrow.glb model
        // The arrow points in the positive Z direction (forward)
        arrowEntity = Entity.create(
            Mesh("arrow.glb".toUri()),
            Transform(Pose(Vector3(0f))),
            Scale(Vector3(ARROW_SCALE)),
            Visible(false) // Start invisible
        )

        // Create north indicator using compass model
        northEntity = Entity.create(
            Mesh("compass.gltf".toUri()),
            Transform(Pose(Vector3(0f))),
            Scale(Vector3(COMPASS_SCALE)),
            Visible(false) // Start invisible
        )

        initialized = true
        Log.i(TAG, "DirectionArrowSystem initialized")
    }

    private fun updateArrowDirection() {
        // Check if direction arrow is enabled
        if (!VROptions.current.showDirectionArrow) {
            arrowEntity?.setComponent(Visible(false))
            northEntity?.setComponent(Visible(false))
            return
        }

        val loc = Services.location.lastLoc

        // Get head position to place indicator below it
        val headPose = HeadPoseUtil.getHeadPose(systemManager) ?: return
        if (headPose == Pose()) return

        // Position indicator below the head
        val indicatorPosition = headPose.t + Vector3(0f, ARROW_HEIGHT_OFFSET, 0f)

        // Check if moving fast enough (and not stale)
        if (loc == null || loc.groundSpeed() < MIN_SPEED_THRESHOLD || !Services.location.isFresh) {
            // Show north indicator instead of direction arrow
            arrowEntity?.setComponent(Visible(false))

            // Calculate rotation to point north
            var northBearingRad = Math.toRadians(-90.0) // North is 0 degrees, but we adjust for model orientation

            // Apply yaw adjustment to align with world coordinate system
            northBearingRad += Adjustments.yawAdjustment

            val yRotation = northBearingRad.toFloat()
            val cosHalfAngle = cos(yRotation / 2f)
            val sinHalfAngle = sin(yRotation / 2f)
            val northRotation = Quaternion(0f, sinHalfAngle, 0f, cosHalfAngle)

            // Use enlarged scale if enabled
            val compassScale = if (isEnlarged) COMPASS_SCALE * ENLARGEMENT_FACTOR else COMPASS_SCALE

            northEntity?.setComponents(listOf(
                Scale(Vector3(compassScale)),
                Transform(Pose(indicatorPosition, northRotation)),
                Visible(true)
            ))
            return
        } else {

            // Show direction arrow when moving fast enough
            northEntity?.setComponent(Visible(false))

            // Calculate rotation based on velocity direction
            var bearingRad = Math.toRadians(loc.bearing() - 90)

            // Apply yaw adjustment to align with world coordinate system
            bearingRad += Adjustments.yawAdjustment

            // Convert to quaternion rotation around Y-axis
            // Note: Meta Spatial uses Y-up coordinate system
            val yRotation = bearingRad.toFloat()

            // Create rotation quaternion for Y-axis rotation
            val cosHalfAngle = cos(yRotation / 2f)
            val sinHalfAngle = sin(yRotation / 2f)
            val rotation = Quaternion(0f, sinHalfAngle, 0f, cosHalfAngle)

            // Use enlarged scale if enabled
            val arrowScale = if (isEnlarged) ARROW_SCALE * ENLARGEMENT_FACTOR else ARROW_SCALE

            // Update arrow position and orientation
            arrowEntity?.setComponents(
                listOf(
                    Scale(Vector3(arrowScale)),
                    Transform(Pose(indicatorPosition, rotation)),
                    Visible(true)
                )
            )
        }
    }

    fun setEnlarged(enlarged: Boolean) {
        isEnlarged = enlarged
    }

    fun cleanup() {
        arrowEntity = null
        northEntity = null
        initialized = false
        Log.i(TAG, "DirectionArrowSystem cleaned up")
    }
}