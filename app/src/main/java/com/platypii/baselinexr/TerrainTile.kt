package com.platypii.baselinexr

import com.meta.spatial.core.Entity
import com.platypii.baselinexr.measurements.LatLngAlt

data class TerrainTileConfig(
    val model: String,         // Path to GLB file relative to assets folder
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
    val entity: Entity
)
