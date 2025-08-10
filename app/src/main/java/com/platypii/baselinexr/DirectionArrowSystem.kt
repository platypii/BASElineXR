package com.platypii.baselinexr

import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.measurements.MLocation
import kotlin.math.*

class DirectionArrowSystem(
    private val gpsTransform: GpsToWorldTransform
) : SystemBase() {

    companion object {
        private const val TAG = "DirectionArrowSystem"
        private const val ARROW_SCALE = 0.3f
        private const val ARROW_HEIGHT_OFFSET = -5.0f
        private const val MIN_SPEED_THRESHOLD = 0.9 // m/s minimum speed to show arrow
    }

    private var arrowEntity: Entity? = null
    private var currentLocation: MLocation? = null
    private var initialized = false

    override fun execute() {
        if (!initialized) {
            initializeDirectionArrow()
        }

        updateArrowDirection()
    }

    fun onLocation(location: MLocation) {
        currentLocation = location
        updateArrowDirection()
    }

    private fun initializeDirectionArrow() {
        // Create arrow using custom arrow.glb model
        // The arrow points in the positive Z direction (forward)

        arrowEntity = Entity.create(
            Mesh("arrow.glb".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f))),
            Scale(Vector3(ARROW_SCALE)),
            Visible(false) // Start invisible
        )

        initialized = true
        Log.i(TAG, "DirectionArrowSystem initialized")
    }

    private fun updateArrowDirection() {
        val loc = currentLocation ?: return

        // Check if direction arrow is enabled
        if (!VROptions.current.showDirectionArrow) {
            arrowEntity?.setComponent(Visible(false))
            return
        }

        // Only show arrow if moving fast enough (and not stale)
        val groundSpeed = loc.groundSpeed()
        if (groundSpeed < MIN_SPEED_THRESHOLD || !Services.location.isFresh) {
            arrowEntity?.setComponent(Visible(false))
            return
        }

        // Get head position to place arrow below it
        val headPose = getHeadPose() ?: return
        if (headPose == Pose()) return

        // Position arrow below the head
        val arrowPosition = headPose.t + Vector3(0f, ARROW_HEIGHT_OFFSET, 0f)

        // Calculate rotation based on velocity direction
        var bearingRad = Math.toRadians(loc.bearing() - 90)

        // Apply yaw adjustment to align with world coordinate system
        bearingRad += gpsTransform.yawAdjustment

        // Convert to quaternion rotation around Y-axis
        // Note: Meta Spatial uses Y-up coordinate system
        val yRotation = bearingRad.toFloat() // Negative because we want clockwise rotation for eastward bearing

        // Create rotation quaternion for Y-axis rotation
        val cosHalfAngle = cos(yRotation / 2f)
        val sinHalfAngle = sin(yRotation / 2f)
        val rotation = Quaternion(0f, sinHalfAngle, 0f, cosHalfAngle)

        // Update arrow position and orientation
        arrowEntity?.setComponents(listOf(
            Scale(Vector3(ARROW_SCALE)),
            Transform(Pose(arrowPosition, rotation)),
            Visible(true)
        ))
    }


    private fun getHeadPose(): Pose? {
        try {
            val head = systemManager
                .tryFindSystem<PlayerBodyAttachmentSystem>()
                ?.tryGetLocalPlayerAvatarBody()
                ?.head ?: return null
            return head.tryGetComponent<Transform>()?.transform
        } catch (e: Exception) {
            Log.e(TAG, "Error getting head pose", e)
            return null
        }
    }

    fun cleanup() {
        arrowEntity = null
        initialized = false
        Log.i(TAG, "DirectionArrowSystem cleaned up")
    }
}