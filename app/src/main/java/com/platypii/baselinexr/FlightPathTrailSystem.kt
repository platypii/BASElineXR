package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.platypii.baselinexr.location.MockLocationProvider
import com.platypii.baselinexr.measurements.MLocation

class FlightPathTrailSystem(
    private val context: Context,
    private val gpsTransform: GpsToWorldTransform
) : SystemBase() {
    companion object {
        private const val TAG = "FlightPathTrailSystem"
    }
    
    private val trailEntities = mutableListOf<Entity>()
    private var trackData: List<MLocation>? = null
    private var initialized = false
    private var sphereMesh: Mesh? = null
    private var previousOriginDelta = Vector3(0f, 0f, 0f)
    
    fun setSphereMesh(mesh: Mesh) {
        sphereMesh = mesh
    }

    /**
     * Calculate scale factor based on distance from origin.
     * Scales from 0.01 at 0m to 1.0 at 1000m.
     */
    private fun calculateScaleForDistance(distance: Float): Float {
        val minScale = 0.01f
        val maxScale = 1.0f
        val maxDistance = 1000f
        return (minScale + (distance / maxDistance) * (maxScale - minScale)).coerceAtMost(maxScale)
    }

    /**
     * Calculate distance from origin for a given position.
     */
    private fun calculateDistance(position: Vector3): Float {
        return kotlin.math.sqrt(
            position.x * position.x + position.y * position.y + position.z * position.z
        )
    }

    /**
     * Update entity's transform and scale based on position.
     */
    private fun updateEntityTransformAndScale(entity: Entity, position: Vector3, rotation: Quaternion = Quaternion()) {
        val distance = calculateDistance(position)
        val scaleFactor = calculateScaleForDistance(distance)
        entity.setComponent(Transform(Pose(position, rotation)))
        entity.setComponent(Scale(Vector3(scaleFactor, scaleFactor, scaleFactor)))
    }
    
    override fun execute() {
        if (!initialized && sphereMesh != null) {
            loadTrackData()
            trackData?.let {
                if (it.isNotEmpty()) {
                    createTrailPoints()
                    initialized = true
                }
            }
        }
    }
    
    private fun loadTrackData() {
        Log.i(TAG, "Loading track data from csv")
        trackData = MockLocationProvider.loadData(context)
        
        if (trackData.isNullOrEmpty()) {
            Log.e(TAG, "No track data loaded")
            return
        }
        
        val firstLocation = trackData!![0]
        gpsTransform.setOrigin(firstLocation)
        Log.i(TAG, "Loaded ${trackData!!.size} GPS points. Origin set at: " +
                "${firstLocation.latitude}, ${firstLocation.longitude}, ${firstLocation.altitude_gps}")
    }
    
    private fun createTrailPoints() {
        Log.i(TAG, "Creating trail visualization from ${trackData!!.size} points")
        
        // Create a sphere for every point in the track
        for (i in trackData!!.indices) {
            val location = trackData!![i]
            val worldPos = gpsTransform.toWorldCoordinates(location)
            
            // Create a copy of the mesh
            val meshCopy = Mesh(sphereMesh!!.mesh)
            
            // Create entity with mesh component
            val sphereEntity = Entity.create(listOf(meshCopy))
            
            // Update transform and scale using helper function
            updateEntityTransformAndScale(sphereEntity, worldPos)
            
            trailEntities.add(sphereEntity)
            
            // Log first few points for debugging
            if (i < 3) {
                Log.i(TAG, "Sphere $i at GPS(${location.latitude}, " +
                        "${location.longitude}, ${location.altitude_gps})")
            }
        }
        
        Log.i(TAG, "Created ${trailEntities.size} trail spheres")
    }
    
    override fun delete(entity: Entity) {
        cleanup()
        super.delete(entity)
    }

    fun cleanup() {
        // Clean up any entities we created
        trailEntities.forEach { it.destroy() }
        trailEntities.clear()
        trackData = null
        initialized = false
        sphereMesh = null
        previousOriginDelta = Vector3(0f, 0f, 0f)
    }
    
    /**
     * Update the origin based on the latest GPS location.
     * This will shift all trail entities by the delta between the old and new origin.
     */
    fun onLocationUpdate(location: MLocation) {
        if (!initialized) {
            return
        }
        
        // Calculate the new origin delta
        val newOriginDelta = gpsTransform.getOriginDelta()
        
        // Calculate how much to shift the trail entities
        // When origin moves forward, trail needs to move backward (negative shift)
        val shift = Vector3(
            -(newOriginDelta.x - previousOriginDelta.x),
            -(newOriginDelta.y - previousOriginDelta.y),
            -(newOriginDelta.z - previousOriginDelta.z)
        )
        
        // Shift all trail entities
        trailEntities.forEachIndexed { index, entity ->
            val transform = entity.getComponent<Transform>()
            val currentPose = transform.transform
            val currentPos = currentPose.t
            val newPos = Vector3(
                currentPos.x + shift.x,
                currentPos.y + shift.y,
                currentPos.z + shift.z
            )
            
            // Update transform and scale using helper function
            updateEntityTransformAndScale(entity, newPos, currentPose.q)
            
            // Log first entity position for debugging
//            if (index == 0) {
//                Log.i(TAG, "First entity moved from (${currentPos.x}, ${currentPos.y}, ${currentPos.z}) to (${newPos.x}, ${newPos.y}, ${newPos.z})")
//            }
        }
        
        // Update the previous delta for next time
        previousOriginDelta = newOriginDelta
        
//        Log.i(TAG, "Updated origin to: ${location.latitude}, ${location.longitude}, ${location.altitude_gps}")
//        Log.i(TAG, "Origin delta: (${newOriginDelta.x}, ${newOriginDelta.y}, ${newOriginDelta.z})")
//        Log.i(TAG, "Trail shifted by: ${shift.x}, ${shift.y}, ${shift.z}")
    }
}