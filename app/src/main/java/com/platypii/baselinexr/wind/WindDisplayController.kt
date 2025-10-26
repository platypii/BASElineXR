package com.platypii.baselinexr.wind

/**
 * Manages display updates to ensure only the active layer updates charts and displays
 */
class WindDisplayController {
    
    private var activeLayerName: String? = null
    private var suppressNonActiveUpdates = true
    
    /**
     * Set the currently active layer that should receive display updates
     */
    fun setActiveLayer(layerName: String?) {
        val oldActive = activeLayerName
        activeLayerName = layerName
        android.util.Log.d("WindDisplayController", "Active layer changed from '$oldActive' to '$layerName'")
    }
    
    /**
     * Check if the given layer should update displays
     */
    fun shouldUpdateDisplays(layer: WindLayer): Boolean {
        if (!suppressNonActiveUpdates) return true
        
        val shouldUpdate = layer.name == activeLayerName
        if (!shouldUpdate) {
            android.util.Log.d("WindDisplayController", 
                "Suppressing display update for ${layer.name} (active: $activeLayerName)")
        }
        return shouldUpdate
    }
    
    /**
     * Check if chart updates should be processed for this layer
     */
    fun shouldUpdateChart(layer: WindLayer): Boolean {
        return shouldUpdateDisplays(layer)
    }
    
    /**
     * Enable/disable suppression of non-active layer updates
     */
    fun setSuppressNonActiveUpdates(suppress: Boolean) {
        suppressNonActiveUpdates = suppress
        android.util.Log.d("WindDisplayController", "Suppress non-active updates: $suppress")
    }
    
    /**
     * Allow updates from any layer (useful during initialization)
     */
    fun allowAllUpdates() {
        suppressNonActiveUpdates = false
    }
    
    /**
     * Only allow updates from active layer (normal operation)
     */
    fun restrictToActiveLayer() {
        suppressNonActiveUpdates = true
    }
}