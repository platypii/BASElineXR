package com.platypii.baselinexr

import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

class TerrainSystem(
    private val gpsToWorldTransform: GpsToWorldTransform
) : SystemBase() {

    private var terrainEntity: Entity? = null

    // Center on kpow
    private val terrainLat = 47.265
    private val terrainLon = -123.17
    private val terrainAlt = -1300.0

    fun initialize() {
        // Load terrain GLTF directly
        terrainEntity = Entity.create(
            Mesh("eiger_terrain_0.5m.glb".toUri()),
            Visible(true)
        )
    }

    override fun execute() {
        terrainEntity?.let { entity ->
            // Update terrain position every frame based on current GPS location
            val location = Services.location.lastLoc
            if (location != null) {
                val currentTime = System.currentTimeMillis()
                val terrainWorldPos = gpsToWorldTransform.toWorldCoordinates(terrainLat, terrainLon, terrainAlt, currentTime)
                terrainWorldPos.x -= 18
                terrainWorldPos.y += 5
                terrainWorldPos.z -= 3022
                val terrainTransform = Transform(Pose(
                    terrainWorldPos,
                    Quaternion(0f, 180f, 0f)
                ))
                
                entity.setComponent(terrainTransform)
            }
        }
    }

    override fun delete(entity: Entity) {
        super.delete(entity)
        // If the terrain entity is being deleted, clean up our reference
        if (entity == terrainEntity) {
            terrainEntity?.destroy()
            terrainEntity = null
        }
    }
}