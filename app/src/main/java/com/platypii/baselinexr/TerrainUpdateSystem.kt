package com.platypii.baselinexr

import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Transform

class TerrainUpdateSystem(
    private val terrainEntity: Entity,
    private val gpsToWorldTransform: GpsToWorldTransform
) : SystemBase() {
    
    // Terrain origin coordinates from the original implementation
    private val terrainLon = 7.9475298
    private val terrainLat = 46.5901325
    private val terrainAlt = 0.0
    
    override fun execute() {
        // Update terrain position every frame based on current GPS location
        val location = Services.location.lastLoc
        if (location != null) {
            // Transform terrain using gps transform (matching original implementation)
            var terrainWorldPos = Vector3(-3574f, -3210f, -1690f)
            val currentTime = System.currentTimeMillis()
            terrainWorldPos = gpsToWorldTransform.toWorldCoordinates(terrainLat, terrainLon, terrainAlt, currentTime)
            terrainWorldPos.x -= 18
            terrainWorldPos.y += 5
            terrainWorldPos.z -= 3022
            
            val terrainTransform = Transform(Pose(
                terrainWorldPos,
                Quaternion(0f, 180f, 0f)
            ))
            
            terrainEntity.setComponent(terrainTransform)
        }
    }
}