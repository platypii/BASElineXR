package com.platypii.baselinexr.wind

import android.util.Log
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.jarvis.FlightMode

/**
 * Integration class that connects the wind estimation system to the global wind system
 * This ensures that wind estimations collected during airplane mode are available
 * to other systems during flight
 */
class WindSystemIntegration {
    companion object {
        private const val TAG = "WindSystemIntegration"
        private var isInitialized = false

        /**
         * Initialize the integration between wind estimation and wind system
         * Should be called when the wind estimation system is set up
         */
        fun initialize(windEstimationSystem: WindEstimationSystem?, windLayerManager: WindLayerManager?) {
            if (isInitialized) {
                Log.d(TAG, "Wind system integration already initialized")
                return
            }

            val windSystem = WindSystem.getInstance()

            // Set the wind sources in the wind system
            windEstimationSystem?.let { system ->
                windSystem.setWindEstimationSystem(system)
                Log.d(TAG, "Wind estimation system connected to WindSystem")
            }

            windLayerManager?.let { manager ->
                windSystem.setWindLayerManager(manager)
                Log.d(TAG, "Wind layer manager connected to WindSystem")
            }

            isInitialized = true
            Log.i(TAG, "Wind system integration initialized successfully")

            // Log current wind data availability
            logWindDataStatus(windSystem)
        }

        /**
         * Update wind system when new layers are saved or data changes
         */
        fun notifyWindDataChanged() {
            if (!isInitialized) {
                Log.w(TAG, "Wind system integration not initialized")
                return
            }

            val windSystem = WindSystem.getInstance()
            windSystem.clearCache() // Clear cache to get fresh data

            Log.d(TAG, "Wind data change notification sent to WindSystem")
            logWindDataStatus(windSystem)
        }

        /**
         * Get current wind information for debugging
         */
        fun getCurrentWindInfo(): String {
            if (!isInitialized) {
                return "Wind system integration not initialized"
            }

            val windSystem = WindSystem.getInstance()
            val currentAltitude = getCurrentAltitude()

            val sb = StringBuilder()
            sb.append("Current Wind System Status:\n")
            sb.append("Current altitude: ${currentAltitude.toInt()}m\n")
            sb.append("Has wind data: ${windSystem.hasWindData()}\n")

            if (windSystem.hasWindData()) {
                sb.append("\n")
                sb.append(windSystem.getWindLayersInfo())

                // Get wind estimates for different flight modes
                try {
                    val airplaneWind = windSystem.getCurrentWind(FlightMode.MODE_PLANE)
                    val wingsuitWind = windSystem.getCurrentWind(FlightMode.MODE_WINGSUIT)

                    sb.append("\nCurrent Wind Estimates:\n")
                    sb.append("Airplane mode: ${airplaneWind.getDisplayString()}\n")
                    sb.append("Wingsuit mode: ${wingsuitWind.getDisplayString()}\n")
                } catch (e: Exception) {
                    sb.append("Error getting wind estimates: ${e.message}\n")
                }
            }

            return sb.toString()
        }

        /**
         * Log current wind data status
         */
        private fun logWindDataStatus(windSystem: WindSystem) {
            val hasData = windSystem.hasWindData()
            val currentAltitude = getCurrentAltitude()

            Log.i(TAG, "Wind data status: hasData=$hasData, altitude=${currentAltitude.toInt()}m")

            if (hasData) {
                try {
                    val currentWind = windSystem.getCurrentWind()
                    Log.i(TAG, "Current wind: ${currentWind.getDisplayString()}")
                } catch (e: Exception) {
                    Log.w(TAG, "Error getting current wind: ${e.message}")
                }
            }
        }

        /**
         * Get current altitude from GPS
         */
        private fun getCurrentAltitude(): Double {
            return Services.location?.lastLoc?.altitude_gps ?: 0.0
        }

        /**
         * Check if integration is ready
         */
        fun isReady(): Boolean {
            return isInitialized
        }

        /**
         * Reset integration (for testing or reinitialization)
         */
        fun reset() {
            isInitialized = false
            Log.d(TAG, "Wind system integration reset")
        }
    }
}