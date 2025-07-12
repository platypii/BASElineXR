package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Transform
import com.platypii.baselinexr.location.GpsToWorldTransform
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
            
            // Create entity with mesh and transform components
            val sphereEntity = Entity.create(listOf(meshCopy, transform))
            
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
}