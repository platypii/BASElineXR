package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.google.gson.Gson
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.location.Geo
import com.platypii.baselinexr.measurements.LatLngAlt

class TerrainSystem(
    private val gpsToWorldTransform: GpsToWorldTransform,
    private val context: Context
) : SystemBase() {

    private val terrainTiles = mutableListOf<TerrainTileEntity>()
    private var isInitialized = false
    private var terrainConfig: TerrainConfiguration? = null

//    private val kpow = LatLngAlt(47.2375, -123.166, -900.0) // eiger to lake
    private val kpow = LatLngAlt(47.22, -123.225, -250.0) // eiger to prison
//    private val kpow = LatLngAlt(47.226, -123.17, -500.0) // hangar adjusted for eiger
//    private val kpow = LatLngAlt(47.22966825, -123.16380949, 0.0) // kpow tile origin
//    private val kpow = LatLngAlt(47.22966825, -123.16380949, 1500.0) // kpow tile origin + 4500ft
    private val eiger = LatLngAlt(46.56314640, 7.94727628, 0.0)
//    private val capitolHill = LatLngAlt(47.5967, -122.3818, -3790.0) // top of eiger
    private val capitolHill = LatLngAlt(47.59, -122.36, -2100.0) // foot of eiger
    private val origins = arrayOf(kpow, eiger, capitolHill)

    fun initialize() {
        loadTerrainConfiguration()
    }

    override fun execute() {
        updateTilePositions()
    }

    override fun delete(entity: Entity) {
        super.delete(entity)

        // Find and remove deleted tile
        val tileToRemove = terrainTiles.find { it.entity == entity }
        tileToRemove?.let {
            terrainTiles.remove(it)
            it.entity.destroy()
        }
    }

    // Load from terrain json
    private fun loadTerrainConfiguration() {
        try {
            // Load JSON from assets
            val jsonString = context.assets.open(VROptions.terrainModel)
                .bufferedReader()
                .use { it.readText() }

            // Parse JSON using Gson
            val gson = Gson()
            val terrainConfig = gson.fromJson(
                jsonString,
                TerrainConfiguration::class.java
            )

            // Store configuration for offset access
            this.terrainConfig = terrainConfig

            // Create entities for each tile
            terrainConfig.tiles.forEach { config ->
                createTerrainTile(config)
            }

            isInitialized = true
        } catch (e: Exception) {
            Log.e("TerrainSystem", "Failed to load terrain configuration", e)
        }
    }

    private fun createTerrainTile(config: TerrainTileConfig) {
        val entity = Entity.create(
            Mesh(config.model.toUri()),
            Visible(true),
            Transform(Pose())  // Initial transform, updated in execute()
        )

        // Also modify the mesh to use custom transparency shader
        val mesh = entity.getComponent<Mesh>()
//        mesh.defaultShaderOverride = "data/shaders/terrain_transparent"
        mesh.defaultShaderOverride = "data/shaders/terrain_bubble"
        entity.setComponent(mesh)

        terrainTiles.add(TerrainTileEntity(config, entity))
    }

    private fun updateTilePositions() {
        if (!isInitialized || terrainTiles.isEmpty()) return

        val location = Services.location.lastLoc ?: return
        val currentTime = System.currentTimeMillis()

        // Find nearest origin
        val motionEstimator = Services.location.motionEstimator
        val referencePos = origins.minByOrNull { Geo.distance(it.lat, it.lng, location.latitude, location.longitude) }?.let {
            // Pass motion estimator to toWorldCoordinates for better prediction
            gpsToWorldTransform.toWorldCoordinates(it.lat, it.lng, it.alt, currentTime, motionEstimator)
        } ?: return

        val config = terrainConfig ?: return

        terrainTiles.forEach { tile ->
            val worldPos = Vector3(
                referencePos.x + (tile.config.gridX * 1000f) + config.offsetX.toFloat(),
                referencePos.y + config.offsetY.toFloat(),
                referencePos.z + (tile.config.gridZ * 1000f) + config.offsetZ.toFloat()
            )

            // Update entity transform
            // Apply yaw adjustment to the terrain rotation
            val yawDegrees = Math.toDegrees(gpsToWorldTransform.yawAdjustment).toFloat()
            val transform = Transform(Pose(
                worldPos,
                Quaternion(0f, 180f + yawDegrees, 0f)  // Apply yaw adjustment
            ))

            tile.entity.setComponent(transform)
        }
    }

    fun cleanup() {
//        terrainTiles.forEach { it.entity.destroy() }
        terrainTiles.clear()
        isInitialized = false
    }
}