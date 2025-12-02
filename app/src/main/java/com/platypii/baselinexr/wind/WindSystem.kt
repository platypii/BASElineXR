package com.platypii.baselinexr.wind

import android.util.Log
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.jarvis.FlightMode
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.util.tensor.Vector3

/**
 * Central wind system that manages wind estimations and provides wind data to other systems
 * during flight. Coordinates between multiple wind estimation sources and selects the best
 * available wind estimate based on altitude and flight mode.
 */
class WindSystem {
    // Wind input mode
    enum class WindMode { ESTIMATION, NO_WIND, SAVED }
    private var windMode: WindMode = WindMode.ESTIMATION

    // Saved wind system (CSV/interpolated)
    private var savedWindSystem: SavedWindSystem? = null

    fun setWindMode(mode: WindMode) {
        windMode = mode
        clearCache()
        Log.d(TAG, "Wind mode set to $mode")
    }

    fun setSavedWindSystem(system: SavedWindSystem) {
        savedWindSystem = system
        Log.d(TAG, "SavedWindSystem set")
    }

    fun getSavedWindSystem(): SavedWindSystem? {
        return savedWindSystem
    }
    companion object {
        private const val TAG = "WindSystem"

        // Singleton instance
        @Volatile
        private var INSTANCE: WindSystem? = null

        fun getInstance(): WindSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WindSystem().also { INSTANCE = it }
            }
        }
    }

    // Wind estimation sources
    private var windLayerManager: WindLayerManager? = null
    private var windEstimationSystem: WindEstimationSystem? = null

    // Wind system enabled/disabled state
    private var isEnabled: Boolean = true

    // Current wind estimate cache
    private var cachedWindEstimate: WindEstimate? = null
    private var cacheAltitude: Double = Double.NaN
    private var cacheTimestamp: Long = 0
    private val cacheValidityMs = 5000 // Cache valid for 5 seconds

    /**
     * Wind estimate with 3D components and metadata
     */
    data class WindEstimate(
        val windE: Double,          // East component (m/s)
        val windN: Double,          // North component (m/s)
        val windD: Double,          // Down component (m/s)
        val magnitude: Double,      // Total wind speed (m/s)
        val direction: Double,      // Wind direction (degrees, 0=North, 90=East)
        val confidence: Double,     // R-squared or confidence metric
        val source: WindSource,     // Source of the estimate
        val layerName: String?,     // Layer name if from saved layer
        val altitude: Double,       // Altitude for this estimate (m)
        val timestamp: Long         // When estimate was calculated
    ) {
        /**
         * Get wind vector as Vector3 (East, Down, North in ENU coordinates)
         */
        fun getWindVector(): Vector3 {
            return Vector3(windE, -windD, windN) // ENU: x=East, y=Up (negative Down), z=North
        }

        /**
         * Get horizontal wind vector (East, North only)
         */
        fun getHorizontalWindVector(): Vector3 {
            return Vector3(windE, 0.0, windN)
        }

        /**
         * Get wind speed in MPH
         */
        fun getMagnitudeMph(): Double {
            return magnitude * 2.23694
        }

        /**
         * Get formatted string representation
         */
        fun getDisplayString(): String {
            return String.format("Wind: %.1f MPH @ %.0f° (%s)", getMagnitudeMph(), direction, source.displayName)
        }
    }

    /**
     * Sources of wind estimation data
     */
    enum class WindSource(val displayName: String) {
        NO_WIND("No Wind"),
        LIVE_GPS("Live GPS"),
        LIVE_SUSTAINED("Live Sustained"),
        SAVED_GPS_LAYER("Saved GPS Layer"),
        SAVED_SUSTAINED_LAYER("Saved Sustained Layer"),
        INTERPOLATED("Interpolated"),
        EXTRAPOLATED("Extrapolated")
    }

    /**
     * Flight modes that affect wind estimation selection
     */


    /**
     * Set the wind layer manager source
     */
    fun setWindLayerManager(manager: WindLayerManager) {
        this.windLayerManager = manager
        Log.d(TAG, "Wind layer manager set")
    }

    /**
     * Set the wind estimation system source
     */
    fun setWindEstimationSystem(system: WindEstimationSystem) {
        this.windEstimationSystem = system
        Log.d(TAG, "Wind estimation system set")
    }

    /**
     * Get wind estimate for the specified altitude and flight mode
     */
    fun getWindAtAltitude(altitude: Double, flightMode: Int = FlightMode.MODE_WINGSUIT): WindEstimate {
        // If wind system is disabled or mode is NO_WIND, return zero wind
        if (!isEnabled || windMode == WindMode.NO_WIND) {
            return WindEstimate(
                windE = 0.0,
                windN = 0.0,
                windD = 0.0,
                magnitude = 0.0,
                direction = 0.0,
                confidence = 0.0,
                source = WindSource.NO_WIND,
                layerName = null,
                altitude = altitude,
                timestamp = System.currentTimeMillis()
            )
        }

        // Saved wind mode
        if (windMode == WindMode.SAVED) {
            savedWindSystem?.let { saved ->
                val kf = saved.getWindAtAltitude(altitude)
                if (kf != null) {
                    val rad = Math.toRadians(kf.direction)
                    val windE = kf.windspeed * Math.sin(rad)
                    val windN = kf.windspeed * Math.cos(rad)
                    val windD = kf.windspeed * Math.sin(Math.toRadians(kf.inclination))
                    return WindEstimate(
                        windE = windE,
                        windN = windN,
                        windD = windD,
                        magnitude = kf.windspeed,
                        direction = kf.direction,
                        confidence = 1.0,
                        source = WindSource.INTERPOLATED,
                        layerName = "SavedWindCSV",
                        altitude = altitude,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
            // Fallback: no CSV data
            return WindEstimate(
                windE = 0.0,
                windN = 0.0,
                windD = 0.0,
                magnitude = 0.0,
                direction = 0.0,
                confidence = 0.0,
                source = WindSource.NO_WIND,
                layerName = null,
                altitude = altitude,
                timestamp = System.currentTimeMillis()
            )
        }

        // Check cache validity
        val currentTime = System.currentTimeMillis()
        if (cachedWindEstimate != null &&
            Math.abs(altitude - cacheAltitude) < 10.0 && // Within 10m altitude
            currentTime - cacheTimestamp < cacheValidityMs) {
            return cachedWindEstimate!!
        }

        val windEstimate = calculateWindAtAltitude(altitude, flightMode)

        // Update cache
        cachedWindEstimate = windEstimate
        cacheAltitude = altitude
        cacheTimestamp = currentTime

        Log.d(TAG, String.format("Wind at %.0fm: %.1f MPH @ %.0f° (%s)",
            altitude, windEstimate.getMagnitudeMph(), windEstimate.direction, windEstimate.source.displayName))

        return windEstimate
    }

    /**
     * Get current wind estimate based on current GPS location
     */
    fun getCurrentWind(flightMode: Int = FlightMode.MODE_WINGSUIT): WindEstimate {
        val currentAltitude = getCurrentAltitude()
        return getWindAtAltitude(currentAltitude, flightMode)
    }



    /**
     * Calculate wind estimate for specific altitude and flight mode
     */
    private fun calculateWindAtAltitude(altitude: Double, flightMode: Int): WindEstimate {
        val timestamp = System.currentTimeMillis()

        // Special handling for airplane mode - use live data if available
        if (flightMode == FlightMode.MODE_PLANE) {
            windEstimationSystem?.let { system ->
                // Try live GPS wind first
                system.getCurrentGpsWindEstimation()?.let { gpsWind ->
                    return WindEstimate(
                        windE = gpsWind.windSpeedE,
                        windN = gpsWind.windSpeedN,
                        windD = 0.0, // Assume horizontal wind for now
                        magnitude = gpsWind.windMagnitude,
                        direction = gpsWind.windDirection,
                        confidence = gpsWind.confidence,
                        source = WindSource.LIVE_GPS,
                        layerName = null,
                        altitude = altitude,
                        timestamp = timestamp
                    )
                }

                // Try live sustained wind if GPS not available
                system.getCurrentSustainedWindEstimation()?.let { sustainedWind ->
                    return WindEstimate(
                        windE = sustainedWind.windSpeedE,
                        windN = sustainedWind.windSpeedN,
                        windD = 0.0, // Assume horizontal wind for now
                        magnitude = sustainedWind.windMagnitude,
                        direction = sustainedWind.windDirection,
                        confidence = sustainedWind.confidence,
                        source = WindSource.LIVE_SUSTAINED,
                        layerName = null,
                        altitude = altitude,
                        timestamp = timestamp
                    )
                }
            }
        }

        // Check saved layers for wind estimation
        windLayerManager?.let { manager ->
            val layers = manager.getLayers()

            if (layers.isNotEmpty()) {
                // Sort layers by altitude (highest first)
                val sortedLayers = layers.sortedByDescending { it.maxAltitude }

                // Find layer that contains this altitude
                for (layer in sortedLayers) {
                    if (altitude >= layer.minAltitude && altitude <= layer.maxAltitude) {
                        return getWindFromLayer(layer, altitude, timestamp)
                    }
                }

                // If altitude is above all layers, use highest layer
                if (altitude > sortedLayers.first().maxAltitude) {
                    val highestLayer = sortedLayers.first()
                    val estimate = getWindFromLayer(highestLayer, altitude, timestamp)
                    return estimate.copy(source = WindSource.EXTRAPOLATED)
                }

                // If altitude is below all layers, use lowest layer
                if (altitude < sortedLayers.last().minAltitude) {
                    val lowestLayer = sortedLayers.last()
                    val estimate = getWindFromLayer(lowestLayer, altitude, timestamp)
                    return estimate.copy(source = WindSource.EXTRAPOLATED)
                }
            }
        }

        // No wind data available - return zero wind
        return WindEstimate(
            windE = 0.0,
            windN = 0.0,
            windD = 0.0,
            magnitude = 0.0,
            direction = 0.0,
            confidence = 0.0,
            source = WindSource.NO_WIND,
            layerName = null,
            altitude = altitude,
            timestamp = timestamp
        )
    }

    /**
     * Extract wind estimate from a saved layer
     */
    private fun getWindFromLayer(layer: WindLayer, altitude: Double, timestamp: Long): WindEstimate {
        // Prefer GPS wind estimate, fall back to sustained
        layer.gpsWindEstimation?.let { gpsWind ->
            return WindEstimate(
                windE = gpsWind.windSpeedE,
                windN = gpsWind.windSpeedN,
                windD = 0.0, // Assume horizontal wind for now
                magnitude = gpsWind.windMagnitude,
                direction = gpsWind.windDirection,
                confidence = gpsWind.confidence,
                source = WindSource.SAVED_GPS_LAYER,
                layerName = layer.name,
                altitude = altitude,
                timestamp = timestamp
            )
        }

        layer.sustainedWindEstimation?.let { sustainedWind ->
            return WindEstimate(
                windE = sustainedWind.windSpeedE,
                windN = sustainedWind.windSpeedN,
                windD = 0.0, // Assume horizontal wind for now
                magnitude = sustainedWind.windMagnitude,
                direction = sustainedWind.windDirection,
                confidence = sustainedWind.confidence,
                source = WindSource.SAVED_SUSTAINED_LAYER,
                layerName = layer.name,
                altitude = altitude,
                timestamp = timestamp
            )
        }

        // Layer has no wind estimation
        return WindEstimate(
            windE = 0.0,
            windN = 0.0,
            windD = 0.0,
            magnitude = 0.0,
            direction = 0.0,
            confidence = 0.0,
            source = WindSource.NO_WIND,
            layerName = layer.name,
            altitude = altitude,
            timestamp = timestamp
        )
    }

    /**
     * Get current altitude from GPS
     */
    private fun getCurrentAltitude(): Double {
        return Services.location?.lastLoc?.altitude_gps ?: 0.0
    }

    /**
     * Get wind layers information for debugging
     */
    fun getWindLayersInfo(): String {
        val manager = windLayerManager ?: return "No wind layer manager"
        val layers = manager.getLayers()

        if (layers.isEmpty()) {
            return "No wind layers available"
        }

        val sb = StringBuilder()
        sb.append("Available wind layers:\n")

        for (layer in layers.sortedByDescending { it.maxAltitude }) {
            sb.append(String.format("  %s: %.0f-%.0fm", layer.name, layer.minAltitude, layer.maxAltitude))

            when {
                layer.gpsWindEstimation != null -> {
                    val wind = layer.gpsWindEstimation
                    sb.append(String.format(" - GPS: %.1f MPH @ %.0f°",
                        wind.windMagnitude * 2.23694, wind.windDirection))
                }
                layer.sustainedWindEstimation != null -> {
                    val wind = layer.sustainedWindEstimation
                    sb.append(String.format(" - Sustained: %.1f MPH @ %.0f°",
                        wind.windMagnitude * 2.23694, wind.windDirection))
                }
                else -> sb.append(" - No wind data")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    /**
     * Check if wind data is available
     */
    fun hasWindData(): Boolean {
        windLayerManager?.let { manager ->
            return manager.getLayers().isNotEmpty()
        }
        return false
    }

    /**
     * Enable or disable wind system output
     */
    fun setEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            clearCache() // Clear cache when state changes
            Log.d(TAG, "Wind system ${if (enabled) "enabled" else "disabled"}")
        }
    }

    /**
     * Check if wind system is enabled
     */
    fun isEnabled(): Boolean {
        return isEnabled
    }

    /**
     * Clear cached wind estimates (call when significant changes occur)
     */
    fun clearCache() {
        cachedWindEstimate = null
        cacheAltitude = Double.NaN
        cacheTimestamp = 0
        Log.d(TAG, "Wind estimate cache cleared")
    }
}