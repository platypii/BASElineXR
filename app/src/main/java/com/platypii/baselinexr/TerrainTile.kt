package com.platypii.baselinexr

import com.meta.spatial.core.Entity
import com.platypii.baselinexr.measurements.LatLngAlt

data class TerrainTileConfig(
    val model: String,    // Path to GLB file relative to assets folder
    val lat: Double,      // Latitude of tile center/origin
    val lng: Double,      // Longitude of tile center/origin
    val alt: Double,      // Altitude offset in meters
    val gridX: Int = 0,   // Grid position X (multiples of 1000m)
    val gridZ: Int = 0    // Grid position Z (multiples of 1000m)
)

data class TerrainConfiguration(
    val tiles: List<TerrainTileConfig>,
    val pointOfInterest: LatLngAlt
)

data class TerrainTileEntity(
    val config: TerrainTileConfig,
    val entity: Entity
)
