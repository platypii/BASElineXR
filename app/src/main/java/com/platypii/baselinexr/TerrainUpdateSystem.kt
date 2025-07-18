package com.platypii.baselinexr

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Transform

class TerrainUpdateSystem(
    private val terrainEntity: Entity,
    private val gpsToWorldTransform: GpsToWorldTransform
) : SystemBase() {
    
    // Center on mushroom
//    private val terrainLat = 46.5901325
//    private val terrainLon = 7.9475298
//    private val terrainAlt = 0.0

    // Center on kpow
    private val terrainLat = 47.265 // 47.239
    private val terrainLon = -123.17 // -123.143
    private val terrainAlt = -1300.0 // above shroom

    override fun execute() {
        // Update terrain position every frame based on current GPS location
        val location = Services.location.lastLoc
        if (location != null) {
            // Transform terrain using gps transform (matching original implementation)
//            var terrainWorldPos = Vector3(-3574f, -3210f, -1690f)
            val currentTime = System.currentTimeMillis()
            val terrainWorldPos = gpsToWorldTransform.toWorldCoordinates(terrainLat, terrainLon, terrainAlt, currentTime)
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