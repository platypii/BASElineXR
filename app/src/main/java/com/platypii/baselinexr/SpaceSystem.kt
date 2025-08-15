package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

/**
 * SpaceSystem manages a space environment with a cubemap of stars that encloses the user.
 * When activated, it shows a large cube with space textures on the inside faces.
 */
class SpaceSystem(private val context: Context) : SystemBase() {
    
    companion object {
        private const val TAG = "SpaceSystem"
        private const val SPACE_CUBE_SIZE = 1000.0f // Large enough to enclose user
    }
    
    private var spaceCubeEntity: Entity? = null
    private var isActive = false
    
    /**
     * Creates the space environment with a cubemap around the user
     */
    fun createSpaceEnvironment(userPosition: Vector3 = Vector3(0f, 0f, 0f)) {
        if (isActive) {
            Log.w(TAG, "Space environment already active")
            return
        }
        
        Log.i(TAG, "Creating space cubemap environment")
        
        // Create a large cube centered on the user position
        // The cube should have inward-facing normals to show textures on the inside
        // Start invisible until showSpace() is called
        spaceCubeEntity = Entity.create(
            Mesh("space.glb".toUri(), hittable = MeshCollision.NoCollision),
            Transform(Pose(userPosition)),
            Scale(Vector3(SPACE_CUBE_SIZE, SPACE_CUBE_SIZE, SPACE_CUBE_SIZE)),
            Visible(false)
        )
        
        isActive = true
        Log.i(TAG, "Space environment created")
    }
    
    /**
     * Shows the space environment if it exists
     */
    fun showSpace() {
        val entity = spaceCubeEntity
        if (entity != null) {
            entity.setComponent(Visible(true))
            Log.i(TAG, "Space environment shown")
        } else {
            // Create space environment if it doesn't exist and show it
            val headPose = getHeadPose()
            val userPosition = headPose?.t ?: Vector3(0f, 0f, 0f)
            createSpaceEnvironment(userPosition)
            // After creation, make it visible
            spaceCubeEntity?.setComponent(Visible(true))
            Log.i(TAG, "Space environment created and shown")
        }
    }
    
    /**
     * Hides the space environment
     */
    fun hideSpace() {
        spaceCubeEntity?.setComponent(Visible(false))
        Log.i(TAG, "Space environment hidden")
    }
    
    /**
     * Updates space environment position relative to user (keeps it centered)
     */
    fun updateSpacePosition() {
        if (!isActive) return
        
        val entity = spaceCubeEntity ?: return
        val headPose = getHeadPose() ?: return
        
        // Keep space cube centered on user's head position
        val transform = Transform(Pose(headPose.t))
        entity.setComponent(transform)
    }
    
    override fun execute() {
        // Update space cube position to follow user if active
        if (isActive) {
            updateSpacePosition()
        }
    }
    
    /**
     * Removes the space environment and cleans up
     */
    fun destroySpaceEnvironment() {
        if (!isActive) {
            return
        }
        
        Log.i(TAG, "Destroying space environment")
        
        try {
            spaceCubeEntity?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying space cube entity: ${e.message}")
        }
        
        spaceCubeEntity = null
        isActive = false
    }
    
    /**
     * Returns true if the space environment is currently active
     */
    fun isSpaceActive(): Boolean = isActive
    
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
        destroySpaceEnvironment()
        Log.i(TAG, "SpaceSystem cleaned up")
    }
}