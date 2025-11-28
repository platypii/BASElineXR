package com.platypii.baselinexr.ui.wind

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.wind.WindEstimationSystem
import com.platypii.baselinexr.wind.WindVelocityChart
import com.platypii.baselinexr.wind.WindLayerManager
import com.platypii.baselinexr.wind.WindEstimation
import com.platypii.baselinexr.wind.WindDataPoint
import com.platypii.baselinexr.wind.WindLayer
import com.platypii.baselinexr.wind.LeastSquaresCircleFit
import com.platypii.baselinexr.wind.WindDisplayFormatter
import com.platypii.baselinexr.wind.WindCalculationCache
import com.platypii.baselinexr.wind.WindDisplayController
import com.platypii.baselinexr.wind.WindSystemIntegration

/**
 * Controller for wind estimation functionality
 */
class WindEstimationController(private val activity: BaselineActivity) {

    private var windEstimationSystem: WindEstimationSystem? = null
    private var windLayerManager: WindLayerManager? = null
    private var windVelocityChart: WindVelocityChart? = null
    private var windLayersList: LinearLayout? = null
    private var gpsWindText: TextView? = null
    private var sustainedWindText: TextView? = null
    private var gpsAircraftSpeedText: TextView? = null
    private var sustainedAircraftSpeedText: TextView? = null
    private var datasetAltitudeRangeText: TextView? = null
    private var currentAltitudeText: TextView? = null

    // Store latest live data for chart updates
    private var latestLiveData: List<WindDataPoint> = emptyList()

    // Cache to avoid redundant calculations
    private var calculationCache: WindCalculationCache? = null

    // Controls which layer can update displays
    private val displayController = WindDisplayController()

    private val layerItemViewManager = WindLayerItemViewManager(activity)

    fun initialize(rootView: View?) {
        // Initialize wind estimation system and layer manager
        windEstimationSystem = WindEstimationSystem()
        windLayerManager = WindLayerManager()

        // Initialize integration with global wind system
        WindSystemIntegration.initialize(windEstimationSystem, windLayerManager)

        // Set up boundary change listener to update UI when boundaries change during live updates
        windLayerManager?.setBoundaryChangeListener(object : WindLayerManager.BoundaryChangeListener {
            override fun onBoundaryChanged(layer: WindLayer) {
                android.util.Log.d("WindEstimationController", "Received boundary change notification for layer: ${layer.name}")

                // Notify wind system that boundaries have changed
                WindSystemIntegration.notifyWindDataChanged()

                // Only update displays if this layer should control displays
                if (displayController.shouldUpdateDisplays(layer)) {
                    android.util.Log.d("WindEstimationController", "Processing display update for layer: ${layer.name}")
                    activity.runOnUiThread {
                        updateChartWithLayer(layer)
                        updateWindDisplaysFromActiveLayer()
                    }
                } else {
                    android.util.Log.d("WindEstimationController", "Skipping display update for non-active layer: ${layer.name}")
                }

                // Always update layers list to show data changes
                activity.runOnUiThread {
                    updateLayersList()
                }
            }
        })

        // Connect layer manager to wind estimation system
        windEstimationSystem?.setLayerManager(windLayerManager)

        setupWindEstimationUI(rootView)
        setupWindEstimationCallbacks()
    }

    fun setupWindEstimationUI(rootView: View?) {
        // Initialize velocity chart
        val velocityChartContainer = rootView?.findViewById<FrameLayout>(R.id.wind_velocity_chart_container)
        velocityChartContainer?.let { container ->
            windVelocityChart = WindVelocityChart(activity)
            container.addView(windVelocityChart)
        }

        // Initialize wind layers list
        windLayersList = rootView?.findViewById(R.id.wind_layers_list)
        if (windLayersList == null) {
            android.util.Log.e("WindEstimationController", "wind_layers_list not found in layout!")
        }

        // Initialize text displays
        gpsWindText = rootView?.findViewById(R.id.gps_wind_text)
        sustainedWindText = rootView?.findViewById(R.id.sustained_wind_text)
        gpsAircraftSpeedText = rootView?.findViewById(R.id.gps_aircraft_speed_text)
        sustainedAircraftSpeedText = rootView?.findViewById(R.id.sustained_aircraft_speed_text)
        datasetAltitudeRangeText = rootView?.findViewById(R.id.dataset_altitude_range_text)
        currentAltitudeText = rootView?.findViewById(R.id.current_altitude_text)

        setupDataBoundaryControls(rootView)
        setupScrollControls(rootView)
    }

    private fun setupScrollControls(rootView: View?) {
        val scrollView = rootView?.findViewById<android.widget.ScrollView>(R.id.wind_layers_scroll)
        val scrollAmount = 100 // pixels to scroll per click
        
        rootView?.findViewById<Button>(R.id.layers_scroll_up_button)?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Scroll up button clicked!")
            scrollView?.smoothScrollBy(0, -scrollAmount)
        }
        
        rootView?.findViewById<Button>(R.id.layers_scroll_down_button)?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Scroll down button clicked!")
            scrollView?.smoothScrollBy(0, scrollAmount)
        }
    }

    private fun setupDataBoundaryControls(rootView: View?) {
        // Data boundary adjustment buttons
        val upperDataPlusButton = rootView?.findViewById<Button>(R.id.upper_data_boundary_plus_button)
        upperDataPlusButton?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "U+ button clicked!")
            adjustDataBoundary(true, 10.0) // Upper boundary +10m
        }

        val upperDataMinusButton = rootView?.findViewById<Button>(R.id.upper_data_boundary_minus_button)
        upperDataMinusButton?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "U- button clicked!")
            adjustDataBoundary(true, -10.0) // Upper boundary -10m
        }

        val lowerDataPlusButton = rootView?.findViewById<Button>(R.id.lower_data_boundary_plus_button)
        lowerDataPlusButton?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "L+ button clicked!")
            adjustDataBoundary(false, 10.0) // Lower boundary +10m
        }

        val lowerDataMinusButton = rootView?.findViewById<Button>(R.id.lower_data_boundary_minus_button)
        lowerDataMinusButton?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "L- button clicked!")
            adjustDataBoundary(false, -10.0) // Lower boundary -10m
        }
    }

    private fun setupWindEstimationCallbacks() {
        // Set up wind estimation listener
        windEstimationSystem?.setListener(object : WindEstimationSystem.WindEstimationListener {
            override fun onDataUpdated(intervalData: List<WindDataPoint>) {
                // Store latest live data for chart updates
                latestLiveData = intervalData

                // Chart will be updated in onCircleFitsUpdated
                // Also update layers list to show latest data bounds - must be on UI thread
                activity.runOnUiThread {
                    updateLayersList()
                    // Update dataset info displays
                    updateDatasetInfoDisplay()
                }
            }

            override fun onWindEstimationUpdated(gpsWind: WindEstimation?, sustainedWind: WindEstimation?) {
                activity.runOnUiThread {
                    // Use formatted display text
                    gpsWindText?.text = WindDisplayFormatter.formatWindEstimation(gpsWind, "GPS Wind")
                    sustainedWindText?.text = WindDisplayFormatter.formatWindEstimation(sustainedWind, "Sustained Wind")

                    // Update the top layer with live wind estimations
                    windLayerManager?.getTopLayer()?.let { topLayer ->
                        topLayer.gpsWindEstimation = gpsWind
                        topLayer.sustainedWindEstimation = sustainedWind
                    }

                    // Refresh the layer list to show updated wind data
                    updateLayersList()
                }
            }

            override fun onCircleFitsUpdated(gpsCircle: LeastSquaresCircleFit.CircleFitResult?, sustainedCircle: LeastSquaresCircleFit.CircleFitResult?) {
                activity.runOnUiThread {
                    // Update aircraft speed displays using formatter
                    gpsAircraftSpeedText?.text = WindDisplayFormatter.formatAircraftSpeed(gpsCircle, "GPS Aircraft Speed")
                    sustainedAircraftSpeedText?.text = WindDisplayFormatter.formatAircraftSpeed(sustainedCircle, "Sustained Aircraft Speed")

                    // Update charts only if the active layer should control displays
                    windLayerManager?.let { manager ->
                        val activeLayer = manager.getActiveLayer()
                        val topLayer = manager.getTopLayer()

                        if (activeLayer != null && displayController.shouldUpdateChart(activeLayer)) {
                            if (activeLayer == topLayer) {
                                // For live top layer, use the live data and circle fit results directly
                                windVelocityChart?.updateData(latestLiveData, gpsCircle, sustainedCircle, "Wind Estimation ${activeLayer.name}")
                            } else {
                                // For saved layers, use cached data
                                updateChartWithLayer(activeLayer)
                            }
                        }
                    }
                }
            }

            override fun onLayersUpdated(layers: List<WindLayer>, currentLayer: WindLayer?) {
                activity.runOnUiThread {
                    updateLayersList()
                }
            }
        })
    }

    fun startCollection() {
        windEstimationSystem?.startCollection()

        // Initialize layer manager with dataset if available
        initializeLayerManager()

        // Set initial active layer for display controller
        windLayerManager?.getActiveLayer()?.let { activeLayer ->
            displayController.setActiveLayer(activeLayer.name)
            android.util.Log.d("WindEstimationController", "Initial active layer set to: ${activeLayer.name}")
        }

        // Always update the layers list to show current state (even if empty)
        updateLayersList()
    }

    fun stopCollection() {
        windEstimationSystem?.stopCollection()
    }

    /**
     * Initialize layer manager with current dataset bounds
     */
    private fun initializeLayerManager() {
        try {
            android.util.Log.d("WindEstimationController", "Initializing layer manager")

            windEstimationSystem?.let { system ->
                val dataPoints = system.getAllDataPoints()
                android.util.Log.d("WindEstimationController", "Found ${dataPoints.size} data points")

                if (dataPoints.isNotEmpty()) {
                    val minAlt = dataPoints.minByOrNull { it.altitude }?.altitude ?: 0.0
                    val maxAlt = dataPoints.maxByOrNull { it.altitude }?.altitude ?: 1000.0
                    val startTime = dataPoints.minByOrNull { it.millis }?.millis ?: System.currentTimeMillis()

                    android.util.Log.d("WindEstimationController", "Initializing with altitude range: $minAlt - $maxAlt")
                    windLayerManager?.initializeWithDataset(minAlt, maxAlt, startTime)
                    updateLayersList()
                } else {
                    android.util.Log.d("WindEstimationController", "No data points yet, will initialize when data arrives")
                    updateLayersList() // Still update to show placeholder
                }
            } ?: android.util.Log.w("WindEstimationController", "WindEstimationSystem is null")
        } catch (e: Exception) {
            android.util.Log.e("WindEstimationController", "Error initializing layer manager: ${e.message}", e)
        }
    }

    /**
     * Add new data point to layer manager
     */
    fun addDataPointToLayers(dataPoint: WindDataPoint) {
        windLayerManager?.addDataPoint(dataPoint)
    }

    private fun saveLayer(layerType: String) {
        try {
            // Get current altitude for layer splitting
            val currentAltitude = getCurrentAltitude()
            val savedLayer = windLayerManager?.saveActiveLayer(layerType, currentAltitude)
            if (savedLayer != null) {
                android.util.Log.d("WindEstimationController", "$layerType layer saved successfully")

                // Notify wind system that data has changed
                WindSystemIntegration.notifyWindDataChanged()

                activity.runOnUiThread {
                    updateLayersList()
                    // Update chart to show the new active layer
                    windLayerManager?.getActiveLayer()?.let { newActiveLayer ->
                        updateChartWithLayer(newActiveLayer)
                    }
                }
            } else {
                android.util.Log.w("WindEstimationController", "Failed to save $layerType layer")
            }
        } catch (e: Exception) {
            android.util.Log.e("WindEstimationController", "Error saving $layerType layer: ${e.message}", e)
        }
    }

    /**
     * Update the wind layers list UI
     */
    private fun updateLayersList() {
        try {
            android.util.Log.d("WindEstimationController", "updateLayersList() called")

            windLayersList?.let { container ->
                container.removeAllViews()

                val layers = windLayerManager?.getLayers() ?: emptyList()
                val activeLayer = windLayerManager?.getActiveLayer()
                val topLayer = windLayerManager?.getTopLayer()

                android.util.Log.d("WindEstimationController", "Found ${layers.size} layers")
                windLayerManager?.let { manager ->
                    android.util.Log.d("WindEstimationController", manager.getDebugInfo())
                }

                if (layers.isEmpty()) {
                    // Show placeholder text when no layers
                    android.util.Log.d("WindEstimationController", "No layers, showing placeholder")
                    val placeholderText = TextView(activity)
                    placeholderText.text = "Waiting for wind data..."
                    placeholderText.setTextColor(0xFFFFFFFF.toInt()) // White text
                    placeholderText.textSize = 12f
                    placeholderText.setPadding(16, 16, 16, 16)
                    placeholderText.gravity = android.view.Gravity.CENTER
                    container.addView(placeholderText)
                    return@let
                }

                for (layer in layers) {
                    val layerView = layerItemViewManager.createLayerItemView(
                        layer,
                        layer == activeLayer,
                        layer == topLayer,
                        windLayerManager,
                        ::updateLayersList,
                        { selectedLayer ->
                            // Use the proper layer selection method
                            selectLayer(selectedLayer)
                        },
                        ::updateWindDisplaysFromActiveLayer,
                        ::getCurrentAltitude
                    )
                    container.addView(layerView)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WindEstimationController", "Error updating layers list: ${e.message}", e)
        }
    }

    /**
     * Get current altitude from GPS data
     */
    private fun getCurrentAltitude(): Double {
        // Get from GPS service using Services.location.lastLoc
        return Services.location?.lastLoc?.altitude_gps ?: 1000.0
    }

    /**
     * Update dataset information display
     * Note: Must be called from UI thread
     */
    private fun updateDatasetInfoDisplay() {
        windLayerManager?.let { manager ->
            // Update dataset altitude range
            datasetAltitudeRangeText?.text = manager.getDatasetAltitudeRangeString()

            // Update current altitude
            val currentAlt = getCurrentAltitude()
            currentAltitudeText?.text = String.format("Current: %.0fm", currentAlt)
        }
    }

    /**
     * Update chart with specific layer's data using cached calculations
     */
    private fun updateChartWithLayer(layer: WindLayer) {
        android.util.Log.d("WindEstimationController", "updateChartWithLayer called for: ${layer.name}")

        // Check if this layer should update the chart
        if (!displayController.shouldUpdateChart(layer)) {
            android.util.Log.d("WindEstimationController", "Chart update suppressed for non-active layer: ${layer.name}")
            return
        }

        // Get the layer's data points from data boundaries (for calculations)
        val layerData = layer.getDataInDataBoundaries()
        android.util.Log.d("WindEstimationController", "Layer ${layer.name} has ${layerData.size} data points")

        // Use cached calculations or create new cache
        val cache = if (calculationCache?.isValidFor(layerData) == true) {
            android.util.Log.d("WindEstimationController", "Using cached calculations for ${layer.name}")
            calculationCache!!
        } else {
            android.util.Log.d("WindEstimationController", "Creating new calculations for ${layer.name}")
            WindCalculationCache.createFromData(layerData).also { calculationCache = it }
        }

        // Update the chart
        activity.runOnUiThread {
            android.util.Log.d("WindEstimationController", "Updating chart with layer: ${layer.name}")
            windVelocityChart?.updateData(layerData, cache.gpsCircleFit, cache.sustainedCircleFit, "Wind Estimation ${layer.name}")
        }
    }

    /**
     * Update wind displays based on the currently active layer using cached calculations
     */
    private fun updateWindDisplaysFromActiveLayer() {
        windLayerManager?.getActiveLayer()?.let { activeLayer ->
            // Update wind speed displays from active layer's stored estimations using formatter
            gpsWindText?.text = WindDisplayFormatter.formatWindEstimation(activeLayer.gpsWindEstimation, "GPS Wind")
            sustainedWindText?.text = WindDisplayFormatter.formatWindEstimation(activeLayer.sustainedWindEstimation, "Sustained Wind")

            // Get cached calculations or create new cache for aircraft speed displays
            val layerData = activeLayer.getDataInTimeInterval()
            val cache = if (calculationCache?.isValidFor(layerData) == true) {
                calculationCache!!
            } else {
                WindCalculationCache.createFromData(layerData).also { calculationCache = it }
            }

            // Update aircraft speed displays using formatter
            gpsAircraftSpeedText?.text = WindDisplayFormatter.formatAircraftSpeed(cache.gpsCircleFit, "GPS Aircraft Speed")
            sustainedAircraftSpeedText?.text = WindDisplayFormatter.formatAircraftSpeed(cache.sustainedCircleFit, "Sustained Aircraft Speed")
        }
    }

    /**
     * Adjust data boundary for the active layer
     */
    private fun adjustDataBoundary(isUpper: Boolean, delta: Double) {
        android.util.Log.d("WindEstimationController", "adjustDataBoundary called: isUpper=$isUpper, delta=$delta")
        windLayerManager?.let { manager ->
            val activeLayer = manager.getActiveLayer()
            if (activeLayer == null) {
                android.util.Log.w("WindEstimationController", "No active layer for data boundary adjustment")
                return
            }

            val success = if (isUpper) {
                activeLayer.adjustDataMaxAltitude(delta, manager.datasetMaxAltitude)
            } else {
                activeLayer.adjustDataMinAltitude(delta, manager.datasetMinAltitude)
            }

            android.util.Log.d("WindEstimationController", "Data boundary adjustment result: $success")

            if (success) {
                // Invalidate cache since data boundaries changed
                invalidateCache()
                // Recalculate wind estimations using new data boundaries
                manager.recalculateWindEstimations(activeLayer)
                // Force boundary change notification since this was a manual adjustment
                manager.forceBoundaryChangeNotification(activeLayer)
                // Refresh all displays with new data
                refreshAllDisplays()
            }
        }
    }

    /**
     * Handle layer selection and ensure display controller is updated
     */
    private fun selectLayer(layer: WindLayer) {
        android.util.Log.d("WindEstimationController", "Selecting layer: ${layer.name}")

        // Set as active in layer manager
        windLayerManager?.setActiveLayer(layer)

        // Set as active in display controller
        displayController.setActiveLayer(layer.name)

        // Update displays for the selected layer
        activity.runOnUiThread {
            updateLayersList()
            updateChartWithLayer(layer)
            updateWindDisplaysFromActiveLayer()
        }
    }

    /**
     * Simplified update method that refreshes all displays without redundant calculations
     */
    private fun refreshAllDisplays() {
        activity.runOnUiThread {
            updateLayersList()
            updateDatasetInfoDisplay()

            // Update active layer displays
            windLayerManager?.getActiveLayer()?.let { activeLayer ->
                updateChartWithLayer(activeLayer)
                updateWindDisplaysFromActiveLayer()
            }
        }
    }

    /**
     * Invalidate calculation cache when data changes
     */
    private fun invalidateCache() {
        calculationCache = null
    }

    fun cleanup() {
        windEstimationSystem?.stopCollection()
    }
}