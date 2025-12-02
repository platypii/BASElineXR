package com.platypii.baselinexr

import com.platypii.baselinexr.location.AtmosphericModel

/**
 * Singleton to hold atmosphere-related settings.
 * These settings control temperature calculation and display visibility.
 */
object AtmosphereSettings {
    
    // If true, use temperature offset from ISA. If false, use device/sensor temperature.
    private var _useTemperatureOffset: Boolean = true
    val useTemperatureOffset: Boolean get() = _useTemperatureOffset
    
    // Temperature offset in degrees Celsius (default +10°C)
    var temperatureOffsetC: Float = 10f
    
    // Whether to show the atmospheric panel - defaults based on sensor data availability
    private var _showAtmosphericPanel: Boolean = VROptions.current.mockSensor != null
    val showAtmosphericPanel: Boolean get() = _showAtmosphericPanel
    
    // Whether to use VPD-based humidity adjustment for temperature offset calculation
    // When enabled, uses humidity sensor to damp the diurnal temperature swing
    private var _useVpdHumidityAdjustment: Boolean = false
    val useVpdHumidityAdjustment: Boolean get() = _useVpdHumidityAdjustment
    
    // Track if VPD adjustment has been applied (only apply once when humidity stabilizes)
    var vpdAdjustmentApplied: Boolean = false
        private set
    
    // Listeners for settings changes
    private val listeners = mutableListOf<() -> Unit>()
    
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners() {
        listeners.forEach { it() }
    }
    
    /**
     * Increment temperature offset by 1°C
     */
    fun incrementOffset() {
        temperatureOffsetC += 1f
        notifyListeners()
    }
    
    /**
     * Decrement temperature offset by 1°C
     */
    fun decrementOffset() {
        temperatureOffsetC -= 1f
        notifyListeners()
    }
    
    /**
     * Set whether to use temperature offset mode
     */
    fun setUseTemperatureOffset(use: Boolean) {
        _useTemperatureOffset = use
        notifyListeners()
    }
    
    /**
     * Set whether to show the atmospheric panel
     */
    fun setShowAtmosphericPanel(show: Boolean) {
        _showAtmosphericPanel = show
        notifyListeners()
    }
    
    /**
     * Set whether to use VPD-based humidity adjustment for temperature offset.
     * When enabled, humidity sensor data will be used to refine the temperature estimate.
     */
    fun setUseVpdHumidityAdjustment(use: Boolean) {
        _useVpdHumidityAdjustment = use
        if (!use) {
            vpdAdjustmentApplied = false  // Reset so it can be applied again if re-enabled
        }
        notifyListeners()
    }
    
    /**
     * Mark that VPD adjustment has been applied.
     */
    fun markVpdAdjustmentApplied() {
        vpdAdjustmentApplied = true
    }
    
    /**
     * Get the temperature offset in Fahrenheit for display
     */
    fun getTemperatureOffsetF(): Float {
        return temperatureOffsetC * 9f / 5f
    }
    
    /**
     * Calculate the air temperature at a given altitude using ISA + offset.
     * @param altitudeMeters Altitude in meters
     * @return Temperature in Kelvin
     */
    fun getCalculatedTemperatureK(altitudeMeters: Float): Float {
        val isaTemp = AtmosphericModel.getStandardTemperature(altitudeMeters)
        return isaTemp + temperatureOffsetC
    }
    
    /**
     * Calculate the air temperature at a given altitude using ISA + offset.
     * @param altitudeMeters Altitude in meters
     * @return Temperature in Fahrenheit
     */
    fun getCalculatedTemperatureF(altitudeMeters: Float): Float {
        val tempK = getCalculatedTemperatureK(altitudeMeters)
        val tempC = tempK - 273.15f
        return tempC * 9f / 5f + 32f
    }
    
    /**
     * Calculate the air temperature at a given altitude using ISA + offset.
     * @param altitudeMeters Altitude in meters
     * @return Temperature in Celsius
     */
    fun getCalculatedTemperatureC(altitudeMeters: Float): Float {
        val tempK = getCalculatedTemperatureK(altitudeMeters)
        return tempK - 273.15f
    }
}
