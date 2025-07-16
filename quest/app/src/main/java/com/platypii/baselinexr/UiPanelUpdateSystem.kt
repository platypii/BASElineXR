package com.platypii.baselinexr

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Transform

class UiPanelUpdateSystem : SystemBase() {
  private var initialized = false
  private var panelEntity: Entity? = null
  private val panelOffset = Vector3(-0.2f, 0.25f, 4f)

  override fun execute() {
    val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
    if (!activity.glxfLoaded) return

    if (!initialized) {
      initializePanel(activity)
    }

    updatePanelPosition()
  }

  private fun initializePanel(activity: BaselineActivity) {
    val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
    val panel = composition.tryGetNodeByName("Panel")
    if (panel?.entity != null) {
      panelEntity = panel.entity
      initialized = true
    }
  }

  private fun updatePanelPosition() {
    if (!initialized || panelEntity == null) return

    val head = getHmd() ?: return
    val headTransform = head.tryGetComponent<Transform>() ?: return
    val headPose = headTransform.transform
    if (headPose == Pose()) return

    // Calculate the forward and right vectors from the head's rotation
    val forward = headPose.q * Vector3(0f, 0f, 1f)
    val right = headPose.q * Vector3(1f, 0f, 0f)
    val up = headPose.q * Vector3(0f, 1f, 0f)

    val targetPose = Pose()

    // Position the panel in bottom left relative to the user's view
    targetPose.t = headPose.t + (forward * panelOffset.z) + (right * panelOffset.x) + (up * panelOffset.y)

    // Use look rotation to create the rotation quaternion
    // Calculate the direction from the panel to the head position
    val toHead = headPose.t - targetPose.t
    val forwardVec = toHead.normalize()
    // Use the head's up vector to maintain consistent orientation
    // Use look rotation to create the rotation quaternion
    val rotation = Quaternion.lookRotation(forwardVec, up)
    targetPose.q = rotation

    // Update panel transform directly
    panelEntity?.setComponent(Transform(targetPose))
  }

  private fun getHmd(): Entity? {
    return systemManager
      .tryFindSystem<PlayerBodyAttachmentSystem>()
      ?.tryGetLocalPlayerAvatarBody()
      ?.head
  }
}
