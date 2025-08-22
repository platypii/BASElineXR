package com.platypii.baselinexr

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ButtonBits
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.toolkit.Controller
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.SceneObjectSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.getAbsoluteTransform

class GrabbablePanel(
    private val systemManager: com.meta.spatial.core.SystemManager,
    private val panelEntity: Entity,
    initialOffset: Vector3 = Vector3(-0.2f, 1.4f, 3.6f)
) {
    private var inputListenerAdded = false
    private var panelOffset = initialOffset
    private var isDragging = false
    private var dragController: Entity? = null
    private var dragLocalOffset: Vector3? = null
    private var dragDistance: Float = 0f

    fun setupInteraction() {
        if (inputListenerAdded) return
        
        val sceneObjectSystem = systemManager.findSystem<SceneObjectSystem>()
        val systemObject = sceneObjectSystem.getSceneObject(panelEntity) ?: return

        systemObject.thenAccept { sceneObject ->
            inputListenerAdded = true
            sceneObject.addInputListener(object : InputListener {
                override fun onInput(
                    receiver: SceneObject,
                    hitInfo: HitInfo,
                    sourceOfInput: Entity,
                    changed: Int,
                    clicked: Int,
                    downTime: Long
                ): Boolean {
                    val triggerPressed = changed and clicked and (ButtonBits.AllButtonClickMask)
                    val triggerReleased = changed and clicked.inv() and (ButtonBits.AllButtonClickMask)

                    if (triggerPressed != 0 && !isDragging) {
                        isDragging = true
                        dragController = sourceOfInput
                        val panelTransform = getAbsoluteTransform(panelEntity)
                        dragLocalOffset = panelTransform.inverse() * hitInfo.point
                        dragDistance = hitInfo.distance
                        return true
                    }

                    if (triggerReleased != 0 && isDragging && sourceOfInput == dragController) {
                        isDragging = false
                        dragController = null
                        dragLocalOffset = null
                        return true
                    }

                    return false
                }
            })
        }
    }

    fun updatePosition() {
        if (isDragging && dragController != null && dragLocalOffset != null) {
            val controller = dragController!!.tryGetComponent<Controller>()
            if (controller == null) return
            val controllerTransform = dragController!!.tryGetComponent<Transform>()
            if (controllerTransform == null) return

            // Check if trigger is still pressed
            if ((controller.buttonState and (ButtonBits.ButtonTriggerL or ButtonBits.ButtonTriggerR)) == 0) {
                isDragging = false
                // Store the new offset relative to head
                val headPose = getHeadPose()
                if (headPose != null) {
                        val panelTransform = panelEntity.tryGetComponent<Transform>()
                        if (panelTransform != null) {
                            val relativeToHead = headPose.inverse() * panelTransform.transform
                            panelOffset = relativeToHead.t
                        }
                }
                dragController = null
                dragLocalOffset = null
                return
            }

            // Calculate new position based on controller
            val newPosition = controllerTransform.transform * Vector3(0f, 0f, dragDistance)
            val targetPose = Pose()
            targetPose.t = newPosition - dragLocalOffset!!

            // Make panel face the head
            val headPose = getHeadPose() ?: return

            val toHead = headPose.t - targetPose.t
            val forwardVec = toHead.normalize()
            val up = headPose.q * Vector3(0f, 1f, 0f)
            targetPose.q = Quaternion.lookRotation(forwardVec, up)

            panelEntity.setComponent(Transform(targetPose))

            // Update stored offset relative to head for when dragging stops
            val relativeToHead = headPose.inverse() * targetPose
            panelOffset = relativeToHead.t

        } else {
            // Not dragging, follow head with stored offset
            val headPose = getHeadPose() ?: return
            if (headPose == Pose()) return

            // Calculate the forward and right vectors from the head's rotation
            val forward = headPose.q * Vector3(0f, 0f, 1f)
            val right = headPose.q * Vector3(1f, 0f, 0f)
            val up = headPose.q * Vector3(0f, 1f, 0f)

            val targetPose = Pose()

            // Position the panel relative to the user's view
            targetPose.t = headPose.t + (forward * panelOffset.z) + (right * panelOffset.x) + (up * panelOffset.y)

            // Calculate the direction from the panel to the head position
            val toHead = headPose.t - targetPose.t
            val forwardVec = toHead.normalize()
            val rotation = Quaternion.lookRotation(forwardVec, up)
            targetPose.q = rotation

            // Update panel transform directly
            panelEntity.setComponent(Transform(targetPose))
        }
    }

    private fun getHeadPose(): Pose? {
        val head = systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.head ?: return null
        return head.tryGetComponent<Transform>()?.transform
    }
}