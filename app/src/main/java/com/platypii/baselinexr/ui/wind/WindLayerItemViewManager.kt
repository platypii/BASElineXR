package com.platypii.baselinexr.ui.wind

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.wind.WindLayer
import com.platypii.baselinexr.wind.WindLayerManager
import com.platypii.baselinexr.wind.WindDisplayFormatter

/**
 * Manages the creation and configuration of wind layer item views (display-only)
 * Layer controls have been moved to external panel for better pointer interaction
 */
class WindLayerItemViewManager(private val activity: BaselineActivity) {

    /**
     * Create a view for a single layer item (display-only)
     */
    fun createLayerItemView(
        layer: WindLayer,
        isActive: Boolean,
        isTopLayer: Boolean,
        isSelected: Boolean,
        windLayerManager: WindLayerManager?
    ): View {
        try {
            val inflater = LayoutInflater.from(activity)
            val layerView = inflater.inflate(R.layout.wind_layer_item, null)

            // Layer name
            val layerName = layerView.findViewById<TextView>(R.id.layer_name)
            layerName.text = layer.name

            // Selection indicator (shown when this layer is selected for editing via external controls)
            val selectedIndicator = layerView.findViewById<TextView>(R.id.layer_selected_indicator)
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE

            // Active indicator (shown when this is the altitude-based active layer)
            val activeIndicator = layerView.findViewById<TextView>(R.id.layer_active_indicator)
            activeIndicator.visibility = if (isActive) View.VISIBLE else View.GONE

            // Background highlighting - selected takes priority, then active
            val container = layerView.findViewById<LinearLayout>(R.id.layer_item_container)
            container.setBackgroundColor(when {
                isSelected -> 0xFF1E5631.toInt() // Dark green for selected
                isActive -> 0xFF2E7D32.toInt() // Lighter green for active
                else -> 0xFF333333.toInt() // Default gray
            })

            // Altitude range
            val altitudeRange = layerView.findViewById<TextView>(R.id.altitude_range)
            altitudeRange.text = layer.getAltitudeRangeString()

            // Data boundary range
            val dataBoundaryRange = layerView.findViewById<TextView>(R.id.data_boundary_range)
            dataBoundaryRange.text = layer.getDataBoundaryRangeString()

            // Wind data display
            setupWindDataDisplay(layerView, layer, isTopLayer)

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

    /**
     * Legacy method for backward compatibility - creates display-only view
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
        // Delegate to simplified version, not selected by default
        return createLayerItemView(layer, isActive, isTopLayer, isSelected = false, windLayerManager)
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
}