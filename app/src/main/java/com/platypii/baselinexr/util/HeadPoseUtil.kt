package com.platypii.baselinexr.util

import android.util.Log
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SystemManager
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform

object HeadPoseUtil {
    private const val TAG = "HeadPoseUtil"

    fun getHeadPose(systemManager: SystemManager): Pose? {
        return try {
            val head = systemManager
                .tryFindSystem<PlayerBodyAttachmentSystem>()
                ?.tryGetLocalPlayerAvatarBody()
                ?.head ?: return null
            val headPose = head.tryGetComponent<Transform>()?.transform
            if (headPose == null || headPose == Pose()) {
                null
            } else {
                headPose
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting head pose: ${e.message}")
            null
        }
    }
}