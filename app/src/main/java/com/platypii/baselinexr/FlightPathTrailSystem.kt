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

class FlightPathTrailSystem(private val context: Context) : SystemBase() {
    companion object {
        private const val TAG = "FlightPathTrailSystem"
    }
    
    private val gpsTransform = GpsToWorldTransform()
    private val trailEntities = mutableListOf<Entity>()
    private var trackData: List<MLocation>? = null
    private var initialized = false
    private var sphereMesh: Mesh? = null
    private var previousOriginDelta = Vector3(0f, 0f, 0f)
    
    fun setSphereMesh(mesh: Mesh) {
        sphereMesh = mesh
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
        Log.i(TAG, "Loading track data from eiger.csv")
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
            
            // Create pose with position and default rotation
            val pose = Pose(worldPos, Quaternion())
            val transform = Transform(pose)
            val scale = Scale(Vector3(0.1f, 0.1f, 0.1f))
            
            // Create entity with mesh, transform, and scale components
            val sphereEntity = Entity.create(listOf(meshCopy, transform, scale))
            
            trailEntities.add(sphereEntity)
            
            // Log first few points for debugging
            if (i < 5) {
                Log.i(TAG, "Sphere $i at GPS(${location.latitude}, " +
                        "${location.longitude}, ${location.altitude_gps})")
            }
        }
        
        Log.i(TAG, "Created ${trailEntities.size} trail spheres")
    }
    
    override fun delete(entity: Entity) {
        // Clean up any entities we created
        trailEntities.forEach { it.destroy() }
        trailEntities.clear()
        super.delete(entity)
    }
    
    /**
     * Update the origin based on the latest GPS location.
     * This will shift all trail entities by the delta between the old and new origin.
     */
    fun onLocationUpdate(location: MLocation) {
        if (!initialized) {
            return
        }
        
        // Update the origin to the latest GPS location
        gpsTransform.setOrigin(location)
        
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
            
            // Create new pose with updated position
            val newPose = Pose(newPos, currentPose.q)
            
            // IMPORTANT: Set the component back on the entity for changes to take effect
            entity.setComponent(Transform(newPose))
            
            // Log first entity position for debugging
            if (index == 0) {
                Log.i(TAG, "First entity moved from (${currentPos.x}, ${currentPos.y}, ${currentPos.z}) to (${newPos.x}, ${newPos.y}, ${newPos.z})")
            }
        }
        
        // Update the previous delta for next time
        previousOriginDelta = newOriginDelta
        
//        Log.i(TAG, "Updated origin to: ${location.latitude}, ${location.longitude}, ${location.altitude_gps}")
//        Log.i(TAG, "Origin delta: (${newOriginDelta.x}, ${newOriginDelta.y}, ${newOriginDelta.z})")
//        Log.i(TAG, "Trail shifted by: ${shift.x}, ${shift.y}, ${shift.z}")
    }
}