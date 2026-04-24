package com.platypii.baselinexr

import com.meta.spatial.core.Entity
import com.platypii.baselinexr.measurements.LatLngAlt

data class TerrainTileConfig(
    val model: String,         // Path to GLB file relative to assets folder (high LOD)
    val modelLowLOD: String?,  // Path to low LOD GLB file (optional)
    val lodSwitchDistance: Float = 100f, // Distance in meters to switch to low LOD
    val tileOrigin: LatLngAlt, // Origin point (lat, lng, alt) of tile corner
    val rotation: Float = 0f   // Rotation in degrees to apply to this tile
)

data class TerrainConfiguration(
    val tiles: List<TerrainTileConfig>,
    val terrainOrigin: LatLngAlt,
    val pointOfInterest: LatLngAlt
)

data class TerrainTileEntity(
    val config: TerrainTileConfig,
    val highLODEntity: Entity,     // High detail model entity
    val lowLODEntity: Entity?      // Low detail model entity (optional, only used for LOD_SHADER)
)
