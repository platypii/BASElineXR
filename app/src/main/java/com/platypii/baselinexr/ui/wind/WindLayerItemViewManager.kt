package com.platypii.baselinexr.ui.wind

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.wind.WindLayer
import com.platypii.baselinexr.wind.WindLayerManager
import com.platypii.baselinexr.wind.WindDisplayFormatter
import com.platypii.baselinexr.wind.WindSystemIntegration

/**
 * Manages the creation and configuration of wind layer item views
 */
class WindLayerItemViewManager(private val activity: BaselineActivity) {

    /**
     * Create a view for a single layer item
     */
    fun createLayerItemView(
        layer: WindLayer,
        isActive: Boolean,
        isTopLayer: Boolean,
        windLayerManager: WindLayerManager?,
        updateLayersList: () -> Unit,
        updateChartWithLayer: (WindLayer) -> Unit,
        updateWindDisplaysFromActiveLayer: () -> Unit,
        getCurrentAltitude: () -> Double
    ): View {
        try {
            val inflater = LayoutInflater.from(activity)
            val layerView = inflater.inflate(R.layout.wind_layer_item, null)

            // Layer name
            val layerName = layerView.findViewById<TextView>(R.id.layer_name)
            layerName.text = layer.name

            // Active indicator
            val activeIndicator = layerView.findViewById<TextView>(R.id.layer_active_indicator)
            activeIndicator.visibility = if (isActive) View.VISIBLE else View.GONE

            // Background highlighting for active layer
            val container = layerView.findViewById<LinearLayout>(R.id.layer_item_container)
            container.setBackgroundColor(if (isActive) 0xFF4CAF50.toInt() else 0xFF333333.toInt())

            // Altitude range
            val altitudeRange = layerView.findViewById<TextView>(R.id.altitude_range)
            altitudeRange.text = layer.getAltitudeRangeString()

            // Data boundary range
            val dataBoundaryRange = layerView.findViewById<TextView>(R.id.data_boundary_range)
            dataBoundaryRange.text = layer.getDataBoundaryRangeString()

            // Wind data display
            setupWindDataDisplay(layerView, layer, isTopLayer)

            // Select button
            setupSelectButton(layerView, layer, windLayerManager, updateLayersList, updateChartWithLayer, updateWindDisplaysFromActiveLayer)

            // Save buttons (only for active top layer)
            setupSaveButtons(layerView, isActive, isTopLayer, windLayerManager, getCurrentAltitude, updateLayersList)

            // Altitude adjustment buttons
            setupAltitudeAdjustmentButtons(layerView, layer, isActive, isTopLayer, windLayerManager, updateLayersList)

            // Layer controls (split/delete)
            setupLayerControls(layerView, layer, windLayerManager, getCurrentAltitude, updateLayersList)

            return layerView
        } catch (e: Exception) {
            android.util.Log.e("WindLayerItemViewManager", "Error creating layer item view: ${e.message}", e)
            // Return a simple fallback view
            val fallbackView = TextView(activity)
            fallbackView.text = "Error loading layer: ${layer.name}"
            fallbackView.setTextColor(0xFFFF0000.toInt())
            fallbackView.setPadding(8, 8, 8, 8)
            return fallbackView
        }
    }

    private fun setupWindDataDisplay(layerView: View, layer: WindLayer, isTopLayer: Boolean) {
        val savedWindData = layerView.findViewById<TextView>(R.id.saved_wind_data)

        // Check layer name to determine which type was saved
        val layerNameText = layer.name.lowercase()

        // Determine wind type and data to display
        val (windType, windData, displayText) = when {
            layerNameText.contains("gps") && layer.gpsWindEstimation != null -> {
                Triple("gps", layer.gpsWindEstimation, WindDisplayFormatter.formatWindEstimationShort(layer.gpsWindEstimation, "GPS Wind"))
            }
            layerNameText.contains("sustained") && layer.sustainedWindEstimation != null -> {
                Triple("sustained", layer.sustainedWindEstimation, WindDisplayFormatter.formatWindEstimationShort(layer.sustainedWindEstimation, "Sus Wind"))
            }
            isTopLayer -> {
                // Live layer - show current wind data (GPS preferred)
                when {
                    layer.gpsWindEstimation != null -> Triple("gps", layer.gpsWindEstimation, WindDisplayFormatter.formatWindEstimationShort(layer.gpsWindEstimation, "Live GPS Wind"))
                    layer.sustainedWindEstimation != null -> Triple("sustained", layer.sustainedWindEstimation, WindDisplayFormatter.formatWindEstimationShort(layer.sustainedWindEstimation, "Live Sus Wind"))
                    else -> Triple("none", null, "Live: -- MPH @ --°")
                }
            }
            else -> {
                // Default case - show any available wind estimate
                when {
                    layer.gpsWindEstimation != null -> Triple("gps", layer.gpsWindEstimation, WindDisplayFormatter.formatWindEstimationShort(layer.gpsWindEstimation, "GPS Wind"))
                    layer.sustainedWindEstimation != null -> Triple("sustained", layer.sustainedWindEstimation, WindDisplayFormatter.formatWindEstimationShort(layer.sustainedWindEstimation, "Sus Wind"))
                    else -> Triple("none", null, "-- MPH @ --°")
                }
            }
        }

        // Apply display text and colors
        savedWindData.text = displayText
        savedWindData.setTextColor(WindDisplayFormatter.getWindColor(windType))
        savedWindData.setBackgroundColor(WindDisplayFormatter.getWindBackgroundColor(windType))
    }

    private fun setupSelectButton(
        layerView: View,
        layer: WindLayer,
        windLayerManager: WindLayerManager?,
        updateLayersList: () -> Unit,
        updateChartWithLayer: (WindLayer) -> Unit,
        updateWindDisplaysFromActiveLayer: () -> Unit
    ) {
        val selectButton = layerView.findViewById<Button>(R.id.layer_select_button)
        selectButton.setOnClickListener {
            android.util.Log.d("WindLayerItemViewManager", "Layer selected: ${layer.name}")

            // Set as active layer in manager
            windLayerManager?.setActiveLayer(layer)

            // Update layers list to show new selection
            updateLayersList()

            // Update chart and displays with selected layer's data
            updateChartWithLayer(layer)
            updateWindDisplaysFromActiveLayer()
        }
    }

    private fun setupSaveButtons(
        layerView: View,
        isActive: Boolean,
        isTopLayer: Boolean,
        windLayerManager: WindLayerManager?,
        getCurrentAltitude: () -> Double,
        updateLayersList: () -> Unit
    ) {
        val saveButtons = layerView.findViewById<LinearLayout>(R.id.layer_save_buttons)
        saveButtons.visibility = if (isActive && isTopLayer) View.VISIBLE else View.GONE

        val saveGpsLayerButton = layerView.findViewById<Button>(R.id.save_gps_layer_button)
        saveGpsLayerButton?.setOnClickListener {
            try {
                val currentAltitude = getCurrentAltitude()
                val savedLayer = windLayerManager?.saveActiveLayer("GPS", currentAltitude)
                if (savedLayer != null) {
                    activity.runOnUiThread { updateLayersList() }
                } else {
                    android.util.Log.w("WindLayerItemViewManager", "Failed to save GPS layer from layer item")
                }
            } catch (e: Exception) {
                android.util.Log.e("WindLayerItemViewManager", "Error saving GPS layer from layer item: ${e.message}", e)
            }
        }

        val saveSustainedLayerButton = layerView.findViewById<Button>(R.id.save_sustained_layer_button)
        saveSustainedLayerButton?.setOnClickListener {
            try {
                val currentAltitude = getCurrentAltitude()
                val savedLayer = windLayerManager?.saveActiveLayer("Sustained", currentAltitude)
                if (savedLayer != null) {
                    activity.runOnUiThread { updateLayersList() }
                } else {
                    android.util.Log.w("WindLayerItemViewManager", "Failed to save Sustained layer from layer item")
                }
            } catch (e: Exception) {
                android.util.Log.e("WindLayerItemViewManager", "Error saving Sustained layer from layer item: ${e.message}", e)
            }
        }
    }

    private fun setupAltitudeAdjustmentButtons(
        layerView: View,
        layer: WindLayer,
        isActive: Boolean,
        isTopLayer: Boolean,
        windLayerManager: WindLayerManager?,
        updateLayersList: () -> Unit
    ) {
        val altitudeAdjustmentButtons = layerView.findViewById<LinearLayout>(R.id.altitude_adjustment_buttons)
        val upperBoundaryControls = layerView.findViewById<LinearLayout>(R.id.upper_boundary_controls)

        // Show altitude adjustment buttons for active layer, hide upper controls for top layer
        altitudeAdjustmentButtons.visibility = if (isActive) View.VISIBLE else View.GONE
        upperBoundaryControls?.visibility = if (isActive && !isTopLayer) View.VISIBLE else View.GONE

        // Lower boundary adjustment buttons
        val lowerMinusButton = layerView.findViewById<Button>(R.id.lower_boundary_minus_button)
        val lowerPlusButton = layerView.findViewById<Button>(R.id.lower_boundary_plus_button)

        lowerMinusButton?.setOnClickListener {
            adjustLayerBoundary(layer, isLowerBoundary = true, increment = false, windLayerManager, updateLayersList) // -500ft
        }

        lowerPlusButton?.setOnClickListener {
            adjustLayerBoundary(layer, isLowerBoundary = true, increment = true, windLayerManager, updateLayersList) // +500ft
        }

        // Upper boundary adjustment buttons
        val upperMinusButton = layerView.findViewById<Button>(R.id.upper_boundary_minus_button)
        val upperPlusButton = layerView.findViewById<Button>(R.id.upper_boundary_plus_button)

        upperMinusButton?.setOnClickListener {
            adjustLayerBoundary(layer, isLowerBoundary = false, increment = false, windLayerManager, updateLayersList) // -500ft
        }

        upperPlusButton?.setOnClickListener {
            adjustLayerBoundary(layer, isLowerBoundary = false, increment = true, windLayerManager, updateLayersList) // +500ft
        }
    }

    private fun setupLayerControls(
        layerView: View,
        layer: WindLayer,
        windLayerManager: WindLayerManager?,
        getCurrentAltitude: () -> Double,
        updateLayersList: () -> Unit
    ) {
        val deleteLayerButton = layerView.findViewById<Button>(R.id.delete_layer_button)
        deleteLayerButton?.setOnClickListener {
            windLayerManager?.deleteLayer(layer)
            WindSystemIntegration.notifyWindDataChanged()
            updateLayersList()
        }
    }

    /**
     * Adjust layer altitude boundary by 500ft increments
     */
    private fun adjustLayerBoundary(
        layer: WindLayer,
        isLowerBoundary: Boolean,
        increment: Boolean,
        windLayerManager: WindLayerManager?,
        updateLayersList: () -> Unit
    ) {
        val altitudeChange = if (increment) 152.4 else -152.4 // 500 feet in meters

        try {
            windLayerManager?.let { manager ->
                val allLayers = manager.getLayers()
                val success = layer.adjustLayerBoundary(
                    !isLowerBoundary, // isUpper parameter (opposite of isLowerBoundary)
                    altitudeChange,
                    allLayers,
                    manager.datasetMinAltitude,
                    manager.datasetMaxAltitude
                )

                if (success) {
                    android.util.Log.d("WindLayerItemViewManager", "Successfully adjusted ${layer.name} layer boundary")

                    // Validate and fix any boundary issues
                    manager.validateAndFixBoundaries()

                    // Notify wind system that layer boundaries have changed
                    WindSystemIntegration.notifyWindDataChanged()

                    // Only refresh the layer list display to show updated boundaries
                    // Do NOT recalculate wind estimations or update charts - those are controlled by data boundaries
                    activity.runOnUiThread {
                        updateLayersList()
                    }
                } else {
                    android.util.Log.w("WindLayerItemViewManager", "Failed to adjust ${layer.name} layer boundary - validation failed")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WindLayerItemViewManager", "Error adjusting layer boundary: ${e.message}", e)
        }
    }
}