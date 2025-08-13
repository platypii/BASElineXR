package com.platypii.baselinexr

import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Manages the space environment with a star field around the user.
 * Creates a simple star field using small spheres positioned randomly around the user.
 */
class SpaceEnvironment {
    
    companion object {
        private const val TAG = "SpaceEnvironment"
        private const val NUM_STARS = 200
        private const val STAR_DISTANCE = 200.0f // Distance from user
        private const val STAR_SCALE_MIN = 0.01f
        private const val STAR_SCALE_MAX = 0.05f
    }
    
    private val starEntities = mutableListOf<Entity>()
    private var isActive = false
    
    /**
     * Creates the space environment with stars around the user
     */
    fun createStarField(userPosition: Vector3 = Vector3(0f, 0f, 0f)) {
        if (isActive) {
            Log.w(TAG, "Star field already active")
            return
        }
        
        Log.i(TAG, "Creating space star field with $NUM_STARS stars")
        
        // Generate random stars in a sphere around the user
        repeat(NUM_STARS) {
            createStar(userPosition)
        }
        
        isActive = true
    }
    
    private fun createStar(centerPosition: Vector3) {
        // Generate random spherical coordinates
        val theta = Random.nextFloat() * 2 * Math.PI.toFloat() // Azimuth
        val phi = Random.nextFloat() * Math.PI.toFloat() // Inclination
        val radius = STAR_DISTANCE + Random.nextFloat() * STAR_DISTANCE * 0.5f // Vary distance slightly
        
        // Convert to cartesian coordinates
        val x = centerPosition.x + radius * sin(phi) * cos(theta)
        val y = centerPosition.y + radius * cos(phi)
        val z = centerPosition.z + radius * sin(phi) * sin(theta)
        
        val starPosition = Vector3(x, y, z)
        
        // Random star size
        val scale = STAR_SCALE_MIN + Random.nextFloat() * (STAR_SCALE_MAX - STAR_SCALE_MIN)
        
        // Create star entity using sphere mesh
        val starEntity = Entity.create(
            Mesh("sphere.gltf".toUri(), hittable = MeshCollision.NoCollision),
            Transform(Pose(starPosition)),
            Scale(Vector3(scale, scale, scale)),
            Visible(true)
        )
        
        starEntities.add(starEntity)
    }
    
    /**
     * Updates star positions relative to user (if needed for movement)
     */
    fun updateStarField(userPosition: Vector3) {
        // For now, stars are static. Could implement movement here if needed.
        // This could be used to create parallax effects or keep stars relative to user
    }
    
    /**
     * Removes all stars and cleans up the star field
     */
    fun destroyStarField() {
        if (!isActive) {
            return
        }
        
        Log.i(TAG, "Destroying star field")
        
        starEntities.forEach { entity ->
            try {
                entity.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying star entity: ${e.message}")
            }
        }
        
        starEntities.clear()
        isActive = false
    }
    
    /**
     * Returns true if the space environment is currently active
     */
    fun isSpaceActive(): Boolean = isActive
    
    /**
     * Gets the number of active stars
     */
    fun getStarCount(): Int = starEntities.size
}