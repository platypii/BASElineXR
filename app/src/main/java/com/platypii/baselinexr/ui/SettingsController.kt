package com.platypii.baselinexr.ui

import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.platypii.baselinexr.AtmosphereSettings
import com.platypii.baselinexr.R
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.measurements.MSensorData

/**
 * Controller for the Settings menu UI.
 * Handles atmosphere settings including temperature mode and offset.
 */
class SettingsController(private val rootView: View) {
    private val TAG = "SettingsController"

    private var btnShowAtmospheric: Button? = null
    private var btnTempMode: Button? = null
    private var btnOffsetMinus: Button? = null
    private var btnOffsetPlus: Button? = null
    private var tempOffsetValue: TextView? = null
    private var tempOffsetCelsius: TextView? = null
    private var calculatedTempValue: TextView? = null
    private var tempOffsetContainer: LinearLayout? = null
    private var deviceTempContainer: LinearLayout? = null
    private var deviceTempValue: TextView? = null

    init {
        setupUI()
    }

    private fun setupUI() {
        // Find views
        btnShowAtmospheric = rootView.findViewById(R.id.btn_show_atmospheric)
        btnTempMode = rootView.findViewById(R.id.btn_temp_mode)
        btnOffsetMinus = rootView.findViewById(R.id.btn_offset_minus)
        btnOffsetPlus = rootView.findViewById(R.id.btn_offset_plus)
        tempOffsetValue = rootView.findViewById(R.id.temp_offset_value)
        tempOffsetCelsius = rootView.findViewById(R.id.temp_offset_celsius)
        calculatedTempValue = rootView.findViewById(R.id.calculated_temp_value)
        tempOffsetContainer = rootView.findViewById(R.id.temp_offset_container)
        deviceTempContainer = rootView.findViewById(R.id.device_temp_container)
        deviceTempValue = rootView.findViewById(R.id.device_temp_value)

        // Setup button listeners
        btnShowAtmospheric?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Show Atmospheric button clicked!")
            val newValue = !AtmosphereSettings.showAtmosphericPanel
            AtmosphereSettings.setShowAtmosphericPanel(newValue)
            updateShowAtmosphericButton()
        }

        btnTempMode?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Temperature Mode button clicked!")
            val newValue = !AtmosphereSettings.useTemperatureOffset
            AtmosphereSettings.setUseTemperatureOffset(newValue)
            updateTempModeUI()
        }

        btnOffsetMinus?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Offset Minus button clicked!")
            AtmosphereSettings.decrementOffset()
            updateOffsetDisplay()
        }

        btnOffsetPlus?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Offset Plus button clicked!")
            AtmosphereSettings.incrementOffset()
            updateOffsetDisplay()
        }

        // Initialize UI state
        updateShowAtmosphericButton()
        updateTempModeUI()
        updateOffsetDisplay()
    }

    private fun updateShowAtmosphericButton() {
        if (AtmosphereSettings.showAtmosphericPanel) {
            btnShowAtmospheric?.text = "ON"
            btnShowAtmospheric?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00AA00.toInt())
        } else {
            btnShowAtmospheric?.text = "OFF"
            btnShowAtmospheric?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFAA0000.toInt())
        }
    }

    private fun updateTempModeUI() {
        if (AtmosphereSettings.useTemperatureOffset) {
            btnTempMode?.text = "OFFSET"
            btnTempMode?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF0066AA.toInt())
            tempOffsetContainer?.visibility = View.VISIBLE
            deviceTempContainer?.visibility = View.GONE
            updateOffsetDisplay()
        } else {
            btnTempMode?.text = "DEVICE"
            btnTempMode?.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFAA6600.toInt())
            tempOffsetContainer?.visibility = View.GONE
            deviceTempContainer?.visibility = View.VISIBLE
            updateDeviceTempDisplay()
        }
    }

    private fun updateOffsetDisplay() {
        val offsetC = AtmosphereSettings.temperatureOffsetC
        val offsetF = AtmosphereSettings.getTemperatureOffsetF()
        
        // Format offset with sign
        val signF = if (offsetF >= 0) "+" else ""
        val signC = if (offsetC >= 0) "+" else ""
        
        tempOffsetValue?.text = "$signF${String.format("%.0f", offsetF)}°F"
        tempOffsetCelsius?.text = "($signC${String.format("%.0f", offsetC)}°C)"
        
        // Update calculated temperature at current altitude
        updateCalculatedTemp()
    }

    private fun updateCalculatedTemp() {
        val loc = Services.location.lastLoc
        if (loc != null) {
            val calculatedTempF = AtmosphereSettings.getCalculatedTemperatureF(loc.altitude_gps.toFloat())
            calculatedTempValue?.text = String.format("%.1f°F", calculatedTempF)
        } else {
            calculatedTempValue?.text = "---"
        }
    }

    private fun updateDeviceTempDisplay() {
        // Try to get sensor data
        val sensorData: MSensorData? = Services.location.sensorProvider?.getSensorAtTime(System.currentTimeMillis())
        
        if (sensorData != null && !sensorData.baroTemp.isNaN() && sensorData.baroTemp != 0f) {
            val tempC = sensorData.baroTemp
            val tempF = tempC * 9f / 5f + 32f
            deviceTempValue?.text = String.format("%.1f°F", tempF)
            deviceTempValue?.setTextColor(0xFF00FFFF.toInt())
        } else if (sensorData != null && !sensorData.humidityTemp.isNaN() && sensorData.humidityTemp != 0f) {
            val tempC = sensorData.humidityTemp
            val tempF = tempC * 9f / 5f + 32f
            deviceTempValue?.text = String.format("%.1f°F", tempF)
            deviceTempValue?.setTextColor(0xFF00FFFF.toInt())
        } else {
            // No sensor data - show ISA + offset as fallback
            val loc = Services.location.lastLoc
            if (loc != null) {
                val fallbackTempF = AtmosphereSettings.getCalculatedTemperatureF(loc.altitude_gps.toFloat())
                deviceTempValue?.text = String.format("%.1f°F (ISA+offset)", fallbackTempF)
                deviceTempValue?.setTextColor(0xFFFFAA00.toInt())
            } else {
                deviceTempValue?.text = "No sensor data"
                deviceTempValue?.setTextColor(0xFFFFAA00.toInt())
            }
        }
    }

    /**
     * Call this periodically to update dynamic values
     */
    fun update() {
        if (AtmosphereSettings.useTemperatureOffset) {
            updateCalculatedTemp()
        } else {
            updateDeviceTempDisplay()
        }
    }
}
