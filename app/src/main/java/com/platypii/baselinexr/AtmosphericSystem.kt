package com.platypii.baselinexr

import android.util.Log
import android.widget.TextView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.location.AtmosphericModel
import com.platypii.baselinexr.location.TemperatureEstimator
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.measurements.MSensorData

/**
 * System for displaying atmospheric sensor data (pressure, temperature, humidity, density).
 * Displays real sensor data when available from replay configs that contain sensor data.
 * Respects AtmosphereSettings for temperature mode and panel visibility.
 */
class AtmosphericSystem : SystemBase() {
    companion object {
        private const val TAG = "AtmosphericSystem"
        private const val HUMIDITY_STABILITY_COUNT = 10  // Number of stable readings required
        private const val HUMIDITY_STABILITY_THRESHOLD = 5f  // Max % deviation for stable reading
    }

    private var initialized = false
    private var grabbablePanel: GrabbablePanel? = null
    private var panelEntity: Entity? = null

    // Display labels
    private var altitudeValue: TextView? = null
    private var densityAltValue: TextView? = null
    private var densityAltOffset: TextView? = null
    private var densityValue: TextView? = null
    private var tempValue: TextView? = null
    private var tempOffset: TextView? = null
    private var pressureValue: TextView? = null
    private var pressureOffset: TextView? = null
    private var humidityValue: TextView? = null

    // Sensor data availability
    private var hasSensorData = false
    
    // Track visibility state
    private var lastVisibilityState = true

    // Humidity stability tracking for VPD adjustment
    private var humidityReadings = mutableListOf<Float>()
    private var humidityStabilized = false

    override fun execute() {
        val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
        if (!activity.glxfLoaded) return

        if (!initialized) {
            initializePanel(activity)
        }

        if (initialized) {
            grabbablePanel?.setupInteraction()
            grabbablePanel?.updatePosition()
            
            // Update visibility based on settings
            updateVisibility()
        }

        updateDisplay()
    }

    private fun initializePanel(activity: BaselineActivity) {
        val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val panel = composition.tryGetNodeByName("AtmosphericPanel")
        if (panel?.entity != null) {
            panelEntity = panel.entity
            // Position on bottom right of HUD, below speed chart (y=-2.3 is about one panel height lower)
            val atmosphericOffset = Vector3(1.6f, -2.3f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, atmosphericOffset)
            initialized = true
        }
    }
    
    private fun updateVisibility() {
        val shouldShow = AtmosphereSettings.showAtmosphericPanel
        if (shouldShow != lastVisibilityState) {
            panelEntity?.setComponent(Visible(shouldShow))
            lastVisibilityState = shouldShow
        }
    }

    fun setLabels(
        altitudeValue: TextView?,
        densityAltValue: TextView?,
        densityAltOffset: TextView?,
        densityValue: TextView?,
        tempValue: TextView?,
        tempOffset: TextView?,
        pressureValue: TextView?,
        pressureOffset: TextView?,
        humidityValue: TextView?
    ) {
        this.altitudeValue = altitudeValue
        this.densityAltValue = densityAltValue
        this.densityAltOffset = densityAltOffset
        this.densityValue = densityValue
        this.tempValue = tempValue
        this.tempOffset = tempOffset
        this.pressureValue = pressureValue
        this.pressureOffset = pressureOffset
        this.humidityValue = humidityValue
        updateDisplay()
    }

    private fun updateDisplay() {
        val loc = Services.location.lastLoc ?: return

        // Try to get sensor data from MockSensorProvider
        val sensorData: MSensorData? = Services.location.sensorProvider?.getSensorAtTime(System.currentTimeMillis())
        hasSensorData = sensorData != null && !sensorData.pressure.isNaN() && sensorData.pressure > 0

        // Get altitude in feet MSL
        val altitudeFt = loc.altitude_gps * 3.28084  // meters to feet
        altitudeValue?.text = String.format("%.0f", altitudeFt)

        if (hasSensorData && sensorData != null) {
            // Use real sensor data
            updateFromSensorData(sensorData, loc.altitude_gps)
        } else {
            // Use ISA model estimates based on GPS altitude
            updateFromISAModel(loc.altitude_gps)
        }
    }

    private fun updateFromSensorData(sensor: MSensorData, altitudeMeters: Double) {
        // Pressure: sensor gives Pa, convert to inHg (1 Pa = 0.0002953 inHg)
        val pressurePa = sensor.pressure
        val pressureInHg = pressurePa * 0.0002953
        pressureValue?.text = String.format("%.2f", pressureInHg)

        // Calculate ISA pressure at this altitude for offset
        val isaPressurePa = AtmosphericModel.altitudeToPressure(altitudeMeters.toFloat())
        val isaPressureInHg = isaPressurePa * 0.0002953
        val pressureDiff = pressureInHg - isaPressureInHg
        if (pressureDiff >= 0) {
            pressureOffset?.text = String.format("▲%.2f", pressureDiff)
            pressureOffset?.setTextColor(0xFF00FF00.toInt()) // Green for higher
        } else {
            pressureOffset?.text = String.format("▼%.2f", -pressureDiff)
            pressureOffset?.setTextColor(0xFFFF0000.toInt()) // Red for lower
        }

        // Temperature handling based on AtmosphereSettings
        val tempC: Float
        val tempF: Float
        val tempK: Float
        val usingOffset: Boolean
        
        if (AtmosphereSettings.useTemperatureOffset) {
            // Use ISA + offset instead of sensor temperature
            tempK = AtmosphereSettings.getCalculatedTemperatureK(altitudeMeters.toFloat())
            tempC = tempK - 273.15f
            tempF = tempC * 9f / 5f + 32f
            usingOffset = true
        } else {
            // Use sensor temperature if available
            val sensorTempC = if (!sensor.baroTemp.isNaN()) sensor.baroTemp else sensor.humidityTemp
            tempC = sensorTempC
            tempK = tempC + 273.15f
            tempF = tempC * 9f / 5f + 32f
            usingOffset = false
        }
        tempValue?.text = String.format("%.1f", tempF)

        // Calculate ISA temperature at this altitude for offset display
        val isaTempK = AtmosphericModel.getStandardTemperature(altitudeMeters.toFloat())
        val isaTempC = isaTempK - 273.15
        val isaTempF = isaTempC * 9.0 / 5.0 + 32.0
        val tempDiff = tempF - isaTempF
        if (usingOffset) {
            // Show the configured offset
            val offsetF = AtmosphereSettings.getTemperatureOffsetF()
            val sign = if (offsetF >= 0) "+" else ""
            tempOffset?.text = "$sign${String.format("%.0f", offsetF)}°"
            tempOffset?.setTextColor(0xFF00FFFF.toInt()) // Cyan for offset mode
        } else if (tempDiff >= 0) {
            tempOffset?.text = String.format("▲+%.0f", tempDiff)
            tempOffset?.setTextColor(0xFFFF0000.toInt()) // Red for hotter
        } else {
            tempOffset?.text = String.format("▼%.0f", tempDiff)
            tempOffset?.setTextColor(0xFF00FFFF.toInt()) // Cyan for cooler
        }

        // Humidity: sensor gives percent directly (validate 0-100% range)
        val humidity = sensor.humidity
        val validHumidity = !humidity.isNaN() && humidity >= 0f && humidity <= 100f
        if (validHumidity) {
            humidityValue?.text = String.format("%.1f", humidity)
            // Check if we should apply VPD adjustment
            checkAndApplyVpdAdjustment(humidity, altitudeMeters, tempC)
        } else {
            humidityValue?.text = "---"
        }

        // Air density: calculate from pressure and temperature (using selected temp source)
        val density = if (validHumidity) {
            AtmosphericModel.calculateDensityWithHumidity(pressurePa, tempK, humidity / 100f)
        } else {
            AtmosphericModel.calculateDensityFromPressureTemp(pressurePa, tempK)
        }
        densityValue?.text = String.format("%.3f", density)

        // Density altitude: altitude at which ISA density equals actual density
        // Use iterative approach or formula
        val densityAltFt = calculateDensityAltitude(density)
        densityAltValue?.text = String.format("%.0f", densityAltFt)

        // Density altitude offset from geometric altitude
        val altFt = altitudeMeters * 3.28084
        val densityAltDiff = densityAltFt - altFt
        if (densityAltDiff >= 0) {
            densityAltOffset?.text = String.format("▲+%.0f", densityAltDiff)
            densityAltOffset?.setTextColor(0xFFFF0000.toInt()) // Red for higher density alt (worse performance)
        } else {
            densityAltOffset?.text = String.format("▼%.0f", densityAltDiff)
            densityAltOffset?.setTextColor(0xFF00FF00.toInt()) // Green for lower density alt (better performance)
        }
    }

    private fun updateFromISAModel(altitudeMeters: Double) {
        // Use ISA model - no real sensor data
        val altFt = altitudeMeters * 3.28084

        // ISA Pressure at altitude
        val pressurePa = AtmosphericModel.altitudeToPressure(altitudeMeters.toFloat())
        val pressureInHg = pressurePa * 0.0002953
        pressureValue?.text = String.format("%.2f", pressureInHg)
        pressureOffset?.text = "ISA"
        pressureOffset?.setTextColor(0xFF888888.toInt()) // Gray for ISA model

        // Temperature: use ISA + offset from settings
        val tempK = AtmosphereSettings.getCalculatedTemperatureK(altitudeMeters.toFloat())
        val tempC = tempK - 273.15f
        val tempF = tempC * 9f / 5f + 32f
        tempValue?.text = String.format("%.1f", tempF)
        
        // Show the configured offset
        val offsetF = AtmosphereSettings.getTemperatureOffsetF()
        val sign = if (offsetF >= 0) "+" else ""
        tempOffset?.text = "$sign${String.format("%.0f", offsetF)}°"
        tempOffset?.setTextColor(0xFF00FFFF.toInt()) // Cyan for offset mode

        // No humidity data in ISA model
        humidityValue?.text = "---"

        // Density calculated with offset temperature
        val density = AtmosphericModel.calculateDensityFromPressureTemp(pressurePa, tempK)
        densityValue?.text = String.format("%.3f", density)

        // Density altitude based on calculated density
        val densityAltFt = calculateDensityAltitude(density)
        densityAltValue?.text = String.format("%.0f", densityAltFt)
        
        // Density altitude offset from geometric altitude
        val densityAltDiff = densityAltFt - altFt
        if (densityAltDiff >= 0) {
            densityAltOffset?.text = String.format("▲+%.0f", densityAltDiff)
            densityAltOffset?.setTextColor(0xFFFF0000.toInt()) // Red for higher density alt
        } else {
            densityAltOffset?.text = String.format("▼%.0f", densityAltDiff)
            densityAltOffset?.setTextColor(0xFF00FF00.toInt()) // Green for lower density alt
        }
    }

    /**
     * Calculate density altitude from air density.
     * Finds the altitude in ISA where density equals the given value.
     * Returns altitude in feet.
     */
    private fun calculateDensityAltitude(density: Float): Double {
        // ISA sea level density: 1.225 kg/m³
        // Use the relationship: rho = rho0 * (1 - L*h/T0)^(g*M/(R*L) - 1)
        // Solving for h is complex, so use iterative approach
        val rho0 = 1.225 // kg/m³
        val L = 0.0065   // K/m lapse rate
        val T0 = 288.15  // K sea level temp
        val g = 9.80665  // m/s²
        val M = 0.0289644 // kg/mol molar mass of air
        val R = 8.31447  // J/(mol·K)

        // Exponent for barometric formula
        val exp = g * M / (R * L) - 1

        // rho/rho0 = (1 - L*h/T0)^exp
        // (rho/rho0)^(1/exp) = 1 - L*h/T0
        // h = T0/L * (1 - (rho/rho0)^(1/exp))
        val ratio = density / rho0
        val altitudeMeters = T0 / L * (1 - Math.pow(ratio, 1.0 / exp))

        return altitudeMeters * 3.28084 // Convert to feet
    }

    /**
     * Check if humidity has stabilized and apply VPD adjustment to temperature offset if enabled.
     * Stability is determined by having N consecutive readings within a threshold.
     */
    private fun checkAndApplyVpdAdjustment(humidity: Float, altitudeMeters: Double, currentTempC: Float) {
        // Skip if VPD adjustment is disabled or already applied
        if (!AtmosphereSettings.useVpdHumidityAdjustment || AtmosphereSettings.vpdAdjustmentApplied) {
            return
        }

        // Track humidity reading for stability check
        humidityReadings.add(humidity)
        if (humidityReadings.size > HUMIDITY_STABILITY_COUNT) {
            humidityReadings.removeAt(0)
        }

        // Check if we have enough readings
        if (humidityReadings.size < HUMIDITY_STABILITY_COUNT) {
            return
        }

        // Check stability: all readings within threshold of mean
        val mean = humidityReadings.average().toFloat()
        val isStable = humidityReadings.all { kotlin.math.abs(it - mean) <= HUMIDITY_STABILITY_THRESHOLD }

        if (isStable && !humidityStabilized) {
            humidityStabilized = true
            Log.i(TAG, String.format("Humidity stabilized at %.1f%% (mean of %d readings)", mean, HUMIDITY_STABILITY_COUNT))

            // Get current location for temperature offset calculation
            val loc = Services.location.lastLoc
            if (loc != null) {
                // Get original GPS time for accurate estimation
                val originalGpsTime = Services.location.getOriginalGpsTime(loc.millis)
                val originalLoc = MLocation(
                    originalGpsTime, loc.latitude, loc.longitude, loc.altitude_gps,
                    loc.climb, loc.vN, loc.vE, loc.hAcc, loc.pdop, loc.hdop, loc.vdop,
                    loc.satellitesUsed, loc.satellitesInView
                )

                // Calculate temperature offset (standard, non-VPD)
                val oldOffset = AtmosphereSettings.temperatureOffsetC
                val newOffset = TemperatureEstimator.calculateIsaOffset(originalLoc)

                // Apply the new offset
                AtmosphereSettings.temperatureOffsetC = newOffset
                AtmosphereSettings.markVpdAdjustmentApplied()

                Log.i(TAG, String.format("Temperature offset updated: changed from %.2f°C to %.2f°C (RH=%.1f%%)",
                    oldOffset, newOffset, mean))
            }
        }
    }

    fun cleanup() {
        grabbablePanel = null
        altitudeValue = null
        densityAltValue = null
        densityAltOffset = null
        densityValue = null
        tempValue = null
        tempOffset = null
        pressureValue = null
        pressureOffset = null
        humidityValue = null
        humidityReadings.clear()
        humidityStabilized = false
        initialized = false
    }
}
