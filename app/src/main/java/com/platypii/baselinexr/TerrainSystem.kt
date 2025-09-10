package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.util.HeadPoseUtil

class TerrainSystem(
    private val gpsToWorldTransform: GpsToWorldTransform,
    private val context: Context
) : SystemBase() {

    private val terrainTiles = mutableListOf<TerrainTileEntity>()
    private var isInitialized = false
    private var terrainConfig: TerrainConfiguration? = null
    private var isVisible = true

    fun initialize() {
        loadTerrainConfiguration()
    }

    override fun execute() {
        updateTilePositions()
    }

    override fun delete(entity: Entity) {
        super.delete(entity)

        // Find and remove deleted tile
        val tileToRemove = terrainTiles.find { it.highLODEntity == entity || it.lowLODEntity == entity }
        tileToRemove?.let {
            terrainTiles.remove(it)
            it.highLODEntity.destroy()
            it.lowLODEntity?.destroy()
        }
    }

    // Load from terrain json
    private fun loadTerrainConfiguration() {
        val terrainConfig = TerrainConfigLoader.loadConfig(context)
        if (terrainConfig != null) {
            // Store configuration for offset access
            this.terrainConfig = terrainConfig

            // Create entities for each tile
            terrainConfig.tiles.forEach { config ->
                createTerrainTile(config)
            }

            isInitialized = true
        } else {
            Log.e("TerrainSystem", "Failed to load terrain configuration")
        }
    }

    private fun createTerrainTile(config: TerrainTileConfig) {
        if (VROptions.current.shader == VROptions.ShaderType.DEFAULT_SHADER) {
            // For DEFAULT_SHADER: only load high LOD model, do NOT override defaultShaderOverride
            val highLODEntity = Entity.create(
                Mesh(config.model.toUri()),
                Visible(false),
                Transform(Pose())
            )

            terrainTiles.add(TerrainTileEntity(config, highLODEntity, null))
        } else {
            // For LOD_SHADER: use current code with 2 LOD models
            // Create high LOD entity with high LOD shader
            val highLODEntity = Entity.create(
                Mesh(config.model.toUri()),
                Visible(false),
                Transform(Pose())
            )

            val highMesh = highLODEntity.getComponent<Mesh>()
            highMesh.defaultShaderOverride = "data/shaders/terrain_high_lod"
            highLODEntity.setComponent(highMesh)

            // Create low LOD entity - use modelLowLOD if available, otherwise fallback to regular model
            val lowLODModelPath = config.modelLowLOD ?: config.model
            val lowLODEntity = Entity.create(
                Mesh(lowLODModelPath.toUri()),
                Visible(false),
                Transform(Pose())
            )

            val lowMesh = lowLODEntity.getComponent<Mesh>()
            lowMesh.defaultShaderOverride = "data/shaders/terrain_low_lod"
            lowLODEntity.setComponent(lowMesh)

            terrainTiles.add(TerrainTileEntity(config, highLODEntity, lowLODEntity))
        }
    }

    private fun updateTilePositions() {
        if (!isInitialized || terrainTiles.isEmpty() || terrainConfig == null) return
        if (gpsToWorldTransform.initialOrigin == null) return

        val currentTime = System.currentTimeMillis()
        val motionEstimator = Services.location.motionEstimator

        // Get destination position in world coordinates
        val dest = VROptions.current.destination

        // Calculate the offset from terrainOrigin to pointOfInterest using geographic math
        val terrainToPoiOffset = GeoUtils.calculateOffset(terrainConfig!!.pointOfInterest, terrainConfig!!.terrainOrigin)

        // Get head pose for optional room movement correction
        val headPose = HeadPoseUtil.getHeadPose(systemManager)

        terrainTiles.forEach { tile ->
            // Calculate offset from tileOrigin to terrainOrigin using geographic math
            val tileToTerrainOffset = GeoUtils.calculateOffset(terrainConfig!!.terrainOrigin, tile.config.tileOrigin)

            // Apply offsets to destination
            val offsetDest = GeoUtils.applyOffset(dest, terrainToPoiOffset, tileToTerrainOffset)

            var tilePosition = gpsToWorldTransform.toWorldCoordinates(offsetDest.lat, offsetDest.lng, offsetDest.alt, currentTime, motionEstimator)

            // Apply room movement correction, if enabled
            if (VROptions.current.roomMovement && headPose != null) {
                tilePosition -= headPose.t
            }

            // Update entity transform
            // Apply yaw adjustment and tile-specific rotation to the terrain rotation
            val yawDegrees = Adjustments.yawAdjustmentDegrees().toFloat()
            val totalRotation = 180f + yawDegrees + tile.config.rotation
            val transform = Transform(Pose(
                tilePosition,
                Quaternion(0f, totalRotation, 0f)  // Apply yaw adjustment + tile rotation
            ))

            // Update high LOD entity
            tile.highLODEntity.setComponents(listOf(
                transform,
                Visible(isVisible)
            ))

            // Update low LOD entity if it exists
            tile.lowLODEntity?.setComponents(listOf(
                transform,
                Visible(isVisible)
            ))
        }
    }


    fun setVisible(visible: Boolean) {
        isVisible = visible
        terrainTiles.forEach { tile ->
            tile.highLODEntity.setComponent(Visible(visible))
            tile.lowLODEntity?.setComponent(Visible(visible))
        }
    }

    fun cleanup() {
//        terrainTiles.forEach { it.entity.destroy() }
        terrainTiles.clear()
        isInitialized = false
    }
}