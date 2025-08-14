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
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible

class TerrainSystem(
    private val gpsToWorldTransform: GpsToWorldTransform,
    private val context: Context
) : SystemBase() {

    private val terrainTiles = mutableListOf<TerrainTileEntity>()
    private var isInitialized = false
    private var terrainConfig: TerrainConfiguration? = null

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
            val jsonString = context.assets.open(VROptions.current.terrainModel)
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
        if (VROptions.current.shader != null) {
            val mesh = entity.getComponent<Mesh>()
            mesh.defaultShaderOverride = VROptions.current.shader
            entity.setComponent(mesh)
        }

        terrainTiles.add(TerrainTileEntity(config, entity))
    }

    private fun updateTilePositions() {
        if (!isInitialized || terrainTiles.isEmpty()) return

        val currentTime = System.currentTimeMillis()

        // We need the lat/lng of where to put the model corner translated to world space
        val motionEstimator = Services.location.motionEstimator
        val dest = VROptions.current.destination
        // Pass motion estimator to toWorldCoordinates for better prediction
        val referencePos = gpsToWorldTransform.toWorldCoordinates(dest.lat, dest.lng, dest.alt, currentTime, motionEstimator)

        // Get head pose for optional room movement translation
        val headPose = getHeadPose()

        terrainTiles.forEach { tile ->
            var worldPos = Vector3(
                referencePos.x + (tile.config.gridX * 1000f),
                referencePos.y,
                referencePos.z + (tile.config.gridZ * 1000f)
            )

            // Apply room movement translation if enabled
            if (VROptions.current.roomMovement && headPose != null) {
                worldPos -= headPose.t
            }

            // Update entity transform
            // Apply yaw adjustment to the terrain rotation
            val yawDegrees = Math.toDegrees(Adjustments.yawAdjustment).toFloat()
            val transform = Transform(Pose(
                worldPos,
                Quaternion(0f, 180f + yawDegrees, 0f)  // Apply yaw adjustment
            ))

            tile.entity.setComponent(transform)
        }
    }

    private fun getHeadPose(): Pose? {
        val head = systemManager
            .tryFindSystem<PlayerBodyAttachmentSystem>()
            ?.tryGetLocalPlayerAvatarBody()
            ?.head ?: return null
        return head.tryGetComponent<Transform>()?.transform
    }

    fun cleanup() {
//        terrainTiles.forEach { it.entity.destroy() }
        terrainTiles.clear()
        isInitialized = false
    }
}