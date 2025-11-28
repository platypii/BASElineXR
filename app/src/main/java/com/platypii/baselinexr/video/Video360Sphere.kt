package com.platypii.baselinexr.video

import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform

/**
 * Creates a sphere entity for rendering 360° equirectangular video
 * Uses the existing sphere.gltf model scaled appropriately
 */
class Video360Sphere {
    
    /**
     * Create sphere entity for 360° video rendering
     * @param videoMaterial Material with video texture
     * @return Entity configured for 360° video display
     */
    fun createSphereEntity(videoMaterial: Material): Entity {
        // Use existing sphere model, scaled large enough to enclose the scene
        // Scale of 500 gives a 500-unit radius sphere
        val sphereScale = 500f
        
        return Entity.create(
            Mesh("sphere.gltf".toUri()),
            Transform(Pose(Vector3(0f, 0f, 0f))),
            Scale(Vector3(sphereScale, sphereScale, sphereScale)),
            videoMaterial
        )
    }
    
    companion object {
        private const val TAG = "Video360Sphere"
    }
}
