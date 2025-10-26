package com.platypii.baselinexr.wind;

import android.util.Log;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages wind layers in altitude-ordered system
 * - Top layer always has highest altitude
 * - Layers are ordered by altitude (descending)
 * - No gaps or overlaps in altitude ranges
 * - One active layer at a time
 * - Top layer continuously updated with incoming data
 */
public class WindLayerManager {
    private static final String TAG = "WindLayerManager";
    
    /**
     * Interface for receiving notifications about layer boundary changes
     */
    public interface BoundaryChangeListener {
        void onBoundaryChanged(WindLayer layer);
    }
    
    private BoundaryChangeListener boundaryChangeListener;
    
    // Single source of truth for all data
    private final List<WindDataPoint> centralDataset;
    
    private final List<WindLayer> layers;
    private WindLayer activeLayer;
    private WindLayer topLayer;
    private double datasetMinAltitude = Double.MAX_VALUE;
    private double datasetMaxAltitude = Double.MIN_VALUE;
    private long datasetStartTime;
    private boolean hasDataset = false;
    
    public WindLayerManager() {
        this.layers = new ArrayList<>();
        this.centralDataset = new ArrayList<>();
    }
    
    /**
     * Check if any layers exist
     */
    public boolean hasLayers() {
        return !layers.isEmpty();
    }
    
    /**
     * Initialize with dataset bounds - creates the default active layer
     */
    public void initializeWithDataset(double minAlt, double maxAlt, long startTime) {
        this.datasetMinAltitude = minAlt;
        this.datasetMaxAltitude = maxAlt;
        this.datasetStartTime = startTime;
        this.hasDataset = true;
        
        // Create default layer spanning entire dataset
        if (layers.isEmpty()) {
            WindLayer defaultLayer = WindLayer.createDefaultLayer(minAlt, maxAlt, startTime);
            defaultLayer.setLayerManager(this); // Set reference to this manager
            layers.add(defaultLayer);
            activeLayer = defaultLayer;
            topLayer = defaultLayer;
            
            Log.d(TAG, "Created default layer: " + minAlt + "-" + maxAlt + "m, layers count now: " + layers.size());
            Log.d(TAG, "Active layer: " + (activeLayer != null ? activeLayer.name : "null"));
            Log.d(TAG, "Top layer: " + (topLayer != null ? topLayer.name : "null"));
        }
    }
    
    /**
     * Add incoming data point to central dataset and update bounds
     */
    public void addDataPoint(WindDataPoint point) {
        // Add to central dataset (single source of truth)
        centralDataset.add(point);
        
        if (!hasDataset) {
            // Auto-initialize with first data point
            Log.i(TAG, "Auto-initializing dataset with first data point at altitude: " + point.altitude + "m");
            initializeWithDataset(point.altitude, point.altitude, point.millis);
        }
        
        // Update dataset bounds
        boolean altitudeUpdated = false;
        if (point.altitude > datasetMaxAltitude) {
            datasetMaxAltitude = point.altitude;
            altitudeUpdated = true;
            Log.d(TAG, "Dataset max altitude updated to: " + datasetMaxAltitude + "m");
        }
        if (point.altitude < datasetMinAltitude) {
            datasetMinAltitude = point.altitude;
            altitudeUpdated = true;
            Log.d(TAG, "Dataset min altitude updated to: " + datasetMinAltitude + "m");
        }
        
        // Update top layer altitude range to encompass all new data
        if (topLayer != null) {
            topLayer.updateTopLayerAltitude(point.altitude);
            
            // Update top layer's data boundaries to include new data, respecting manual adjustments
            boolean boundaryUpdated = false;
            
            // Check upper boundary expansion - only if not manually controlled
            if (point.altitude > topLayer.dataMaxAltitude && !topLayer.hasManualDataBoundaries) {
                Log.d(TAG, String.format("Expanding upper data boundary for %s from %.1f to %.1f", 
                    topLayer.name, topLayer.dataMaxAltitude, point.altitude));
                topLayer.dataMaxAltitude = point.altitude;
                boundaryUpdated = true;
            } else if (point.altitude > topLayer.dataMaxAltitude) {
                Log.d(TAG, String.format("Skipping upper boundary expansion for %s - manually controlled (hasManualDataBoundaries=%b)", 
                    topLayer.name, topLayer.hasManualDataBoundaries));
            }
            
            // Check lower boundary expansion - only if not manually controlled
            if (point.altitude < topLayer.dataMinAltitude && !topLayer.hasManualLowerDataBoundary) {
                Log.d(TAG, String.format("Expanding lower data boundary for %s from %.1f to %.1f", 
                    topLayer.name, topLayer.dataMinAltitude, point.altitude));
                topLayer.dataMinAltitude = point.altitude;
                boundaryUpdated = true;
            } else if (point.altitude < topLayer.dataMinAltitude) {
                Log.d(TAG, String.format("Skipping lower boundary expansion for %s - manually controlled (hasManualLowerDataBoundary=%b)", 
                    topLayer.name, topLayer.hasManualLowerDataBoundary));
            }
            
            // If boundaries were updated, recalculate wind estimations and notify listeners
            if (boundaryUpdated) {
                Log.d(TAG, String.format("Data boundaries updated for %s, recalculating wind estimations", topLayer.name));
                recalculateWindEstimations(topLayer);
                // Only notify if this is the active layer or if no active layer is set
                if (topLayer == activeLayer || activeLayer == null) {
                    Log.d(TAG, String.format("Notifying boundary change for active top layer: %s", topLayer.name));
                    notifyBoundaryChanged(topLayer);
                } else {
                    Log.d(TAG, String.format("Skipping boundary change notification for non-active top layer: %s (active: %s)", 
                        topLayer.name, activeLayer != null ? activeLayer.name : "null"));
                }
            }
            
            if (altitudeUpdated) {
                Log.d(TAG, "Top layer altitude range: " + topLayer.minAltitude + "-" + topLayer.maxAltitude + "m");
                Log.d(TAG, "Top layer data boundaries: " + topLayer.dataMinAltitude + "-" + topLayer.dataMaxAltitude + "m");
            }
        }
    }
    
    /**
     * Save current top layer and create new layer for continued data collection
     */
    public WindLayer saveActiveLayer(String layerType, double currentAltitude) {
        if (topLayer == null) {
            Log.w(TAG, "No top layer to save");
            return null;
        }
        
        // Always work with the top layer, regardless of which layer is currently active for viewing
        WindLayer layerToSave = topLayer;
        
        // Debug logging for save attempt
        Log.d(TAG, String.format("Attempting to save top layer '%s' at altitude %.0fm", layerToSave.name, currentAltitude));
        Log.d(TAG, String.format("Top layer range: %.0f-%.0fm, isTopLayer: %b", 
            layerToSave.minAltitude, layerToSave.maxAltitude, layerToSave.isTopLayer));
        Log.d(TAG, String.format("Current active layer for viewing: %s", activeLayer != null ? activeLayer.name : "null"));
        
        // Check if split is valid - allow split at the current max altitude
        if (currentAltitude <= layerToSave.minAltitude) {
            Log.w(TAG, String.format("Cannot split layer at %.0fm - below min altitude %.0fm", 
                currentAltitude, layerToSave.minAltitude));
            return null;
        }
        
        // Check if there's any data to save (at least some data points below currentAltitude)
        boolean hasDataToSave = false;
        for (WindDataPoint point : centralDataset) {
            if (point.altitude >= layerToSave.minAltitude && point.altitude <= currentAltitude) {
                hasDataToSave = true;
                break;
            }
        }
        
        if (!hasDataToSave) {
            Log.w(TAG, String.format("No data points to save below altitude %.0fm", currentAltitude));
            return null;
        }
        
        try {
            // Find the actual max altitude of data points below currentAltitude for the saved layer
            double actualMaxAltitude = layerToSave.minAltitude;
            for (WindDataPoint point : centralDataset) {
                if (point.altitude >= layerToSave.minAltitude && point.altitude <= currentAltitude && point.altitude > actualMaxAltitude) {
                    actualMaxAltitude = point.altitude;
                }
            }
            
            // Create a new saved layer with the correct name and actual data bounds
            String savedLayerName = "Saved " + layerType + " Layer " + (layers.size());
            WindLayer savedLayer = new WindLayer(savedLayerName, layerToSave.minAltitude, actualMaxAltitude, layerToSave.startTime);
            savedLayer.setLayerManager(this); // Set reference to this manager
            
            // Set data boundaries to match the actual data being saved
            savedLayer.dataMinAltitude = layerToSave.minAltitude;
            savedLayer.dataMaxAltitude = actualMaxAltitude;
            
            // Set basic properties for the saved layer
            savedLayer.endTime = layerToSave.endTime;
            savedLayer.isTopLayer = false;
            savedLayer.isActive = false;
            
            // Calculate wind estimations for the saved layer based on the specific layer type requested
            calculateWindEstimationsForSavedLayer(savedLayer, layerType);
            
            // Create a new live layer for data above currentAltitude
            // Note: Data stays in central dataset, new layer will access via boundaries
            
            // Create new live layer with remaining data points
            String newLayerName = "Live Layer " + layers.size();
            
            // Set initial bounds for the new layer - start from currentAltitude and allow expansion
            double newLayerMin = currentAltitude;
            double newLayerMax = currentAltitude;
            
            // Find the range of data points above currentAltitude from central dataset
            for (WindDataPoint point : centralDataset) {
                if (point.altitude > currentAltitude && point.altitude > newLayerMax) {
                    newLayerMax = point.altitude;
                }
            }
            
            WindLayer newLiveLayer = new WindLayer(newLayerName, newLayerMin, newLayerMax, System.currentTimeMillis());
            newLiveLayer.setLayerManager(this); // Set reference to this manager
            
            // Set data boundaries to match the actual data range for this layer
            newLiveLayer.dataMinAltitude = newLayerMin;
            newLiveLayer.dataMaxAltitude = newLayerMax;
            
            // Ensure new live layer allows automatic boundary expansion
            newLiveLayer.hasManualDataBoundaries = false;
            newLiveLayer.hasManualLowerDataBoundary = false;
            
            newLiveLayer.isTopLayer = true;
            newLiveLayer.isActive = true;
            
            // Remove the original top layer and add the new layers
            layers.remove(layerToSave);
            layers.add(savedLayer);
            layers.add(newLiveLayer);
            
            // Update references
            topLayer = newLiveLayer;
            if (activeLayer == layerToSave) {
                activeLayer = newLiveLayer;
            }
            
            // Sort layers by altitude (descending)
            sortLayersByAltitude();
            
            Log.d(TAG, "Saved layer: " + savedLayerName + " (" + layerToSave.minAltitude + " to " + actualMaxAltitude + "m)");
            Log.d(TAG, "Created new live layer: " + newLiveLayer.name + " (from " + newLiveLayer.minAltitude + "m)");
            return newLiveLayer; // Return the new live layer
        } catch (Exception e) {
            Log.e(TAG, "Error saving active layer: " + e.getMessage(), e);
            return null;
        }
    }
    

    
    /**
     * Set a layer as active
     */
    public void setActiveLayer(WindLayer layer) {
        if (layers.contains(layer)) {
            layer.setActive(layers);
            activeLayer = layer;
            Log.d(TAG, "Set active layer: " + layer.name);
        }
    }
    
    /**
     * Get all layers sorted by altitude (highest first)
     */
    public List<WindLayer> getLayers() {
        sortLayersByAltitude();
        return new ArrayList<>(layers);
    }
    
    /**
     * Get currently active layer
     */
    public WindLayer getActiveLayer() {
        return activeLayer;
    }
    
    /**
     * Get top layer (highest altitude, receives live data)
     */
    public WindLayer getTopLayer() {
        return topLayer;
    }
    
    /**
     * Sort layers by altitude in descending order (highest first)
     */
    private void sortLayersByAltitude() {
        layers.sort(new Comparator<WindLayer>() {
            @Override
            public int compare(WindLayer a, WindLayer b) {
                // Sort by max altitude descending (highest first)
                return Double.compare(b.maxAltitude, a.maxAltitude);
            }
        });
    }
    
    /**
     * Delete a layer (cannot delete if it's the only layer)
     */
    public boolean deleteLayer(WindLayer layer) {
        if (layers.size() <= 1) {
            Log.w(TAG, "Cannot delete the only remaining layer");
            return false;
        }
        
        boolean removed = layers.remove(layer);
        
        if (removed) {
            // If we deleted the active layer, activate the first remaining layer
            if (layer == activeLayer) {
                sortLayersByAltitude();
                if (!layers.isEmpty()) {
                    setActiveLayer(layers.get(0));
                }
            }
            
            // If we deleted the top layer, make the highest layer the new top layer
            if (layer == topLayer) {
                sortLayersByAltitude();
                if (!layers.isEmpty()) {
                    topLayer = layers.get(0);
                    topLayer.isTopLayer = true;
                }
            }
            
            Log.d(TAG, "Deleted layer: " + layer.name);
        }
        
        return removed;
    }
    
    /**
     * Split active layer at current altitude
     */
    public WindLayer splitActiveLayer(double splitAltitude) {
        if (activeLayer == null) {
            Log.w(TAG, "No active layer to split");
            return null;
        }
        
        if (splitAltitude <= activeLayer.minAltitude || splitAltitude >= activeLayer.maxAltitude) {
            Log.w(TAG, "Cannot split layer: split altitude must be within layer range");
            return null;
        }
        
        // Create new upper layer
        WindLayer upperLayer = new WindLayer("Split Layer", splitAltitude, activeLayer.maxAltitude, activeLayer.startTime);
        upperLayer.setLayerManager(this);  // Set reference to access central dataset
        upperLayer.isActive = activeLayer.isActive;
        upperLayer.isTopLayer = activeLayer.isTopLayer;
        
        // Update existing layer to be the lower portion
        activeLayer.maxAltitude = splitAltitude;
        activeLayer.isTopLayer = false;  // Lower layer is no longer top
        
        layers.add(upperLayer);
        sortLayersByAltitude();
        
        Log.d(TAG, "Split layer at " + splitAltitude + "m");
        return upperLayer;
    }
    
    /**
     * Check if any layer overlaps (should not happen in well-managed system)
     */
    public boolean hasOverlaps() {
        for (int i = 0; i < layers.size(); i++) {
            for (int j = i + 1; j < layers.size(); j++) {
                if (layers.get(i).overlapsWith(layers.get(j))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Validate layer system integrity
     */
    public void validateLayers() {
        if (hasOverlaps()) {
            Log.e(TAG, "Layer system has overlaps!");
        }
        
        sortLayersByAltitude();
        
        // Check for gaps
        for (int i = 0; i < layers.size() - 1; i++) {
            WindLayer upper = layers.get(i);
            WindLayer lower = layers.get(i + 1);
            
            if (upper.minAltitude > lower.maxAltitude) {
                Log.w(TAG, String.format("Gap detected between layers: %s (%.1f-%.1fm) and %s (%.1f-%.1fm)", 
                    upper.name, upper.minAltitude, upper.maxAltitude,
                    lower.name, lower.minAltitude, lower.maxAltitude));
            }
        }
        
        // Detailed boundary validation
        Log.d(TAG, "Layer boundary validation:");
        for (WindLayer layer : layers) {
            Log.d(TAG, String.format("  %s: %.1f-%.1fm (data: %.1f-%.1fm)", 
                layer.name, layer.minAltitude, layer.maxAltitude,
                layer.dataMinAltitude, layer.dataMaxAltitude));
        }
    }
    
    /**
     * Validate and fix layer boundaries after adjustment
     */
    public void validateAndFixBoundaries() {
        sortLayersByAltitude();
        
        // Ensure no gaps between adjacent layers
        for (int i = 0; i < layers.size() - 1; i++) {
            WindLayer upper = layers.get(i);
            WindLayer lower = layers.get(i + 1);
            
            // Adjacent layers should have touching boundaries
            if (Math.abs(upper.minAltitude - lower.maxAltitude) > 0.1) {
                Log.w(TAG, String.format("Fixing boundary gap between %s and %s: %.1f vs %.1f", 
                    upper.name, lower.name, upper.minAltitude, lower.maxAltitude));
                // Set the boundary to the average to close the gap
                double midPoint = (upper.minAltitude + lower.maxAltitude) / 2.0;
                upper.minAltitude = midPoint;
                lower.maxAltitude = midPoint;
                Log.d(TAG, String.format("Fixed boundaries at midpoint: %.1f", midPoint));
            }
        }
    }
    
    /**
     * Test layer boundary adjustment system
     */
    public void testBoundaryAdjustments() {
        Log.d(TAG, "=== Testing Layer Boundary Adjustments ===");
        
        if (layers.size() < 2) {
            Log.w(TAG, "Need at least 2 layers to test boundary adjustments");
            return;
        }
        
        // Get a non-top layer for testing
        WindLayer testLayer = null;
        for (WindLayer layer : layers) {
            if (!layer.isTopLayer && layers.indexOf(layer) > 0) {
                testLayer = layer;
                break;
            }
        }
        
        if (testLayer == null) {
            Log.w(TAG, "No suitable layer found for boundary testing");
            return;
        }
        
        Log.d(TAG, "Testing with layer: " + testLayer.name);
        Log.d(TAG, "Initial boundary: " + testLayer.minAltitude + "-" + testLayer.maxAltitude);
        
        // Test small adjustment
        double originalMin = testLayer.minAltitude;
        double originalMax = testLayer.maxAltitude;
        
        // Find adjacent layers
        WindLayer lowerLayer = null;
        WindLayer upperLayer = null;
        for (WindLayer layer : layers) {
            if (layer.maxAltitude == testLayer.minAltitude && layer != testLayer) {
                lowerLayer = layer;
            }
            if (layer.minAltitude == testLayer.maxAltitude && layer != testLayer) {
                upperLayer = layer;
            }
        }
        
        Log.d(TAG, String.format("Adjacent layers - Lower: %s, Upper: %s", 
            lowerLayer != null ? lowerLayer.name : "none",
            upperLayer != null ? upperLayer.name : "none"));
            
        validateLayers();
    }
    
    /**
     * Get current dataset altitude range
     */
    public double getDatasetMinAltitude() {
        return datasetMinAltitude;
    }
    
    public double getDatasetMaxAltitude() {
        return datasetMaxAltitude;
    }
    
    /**
     * Get dataset altitude range as formatted string
     */
    public String getDatasetAltitudeRangeString() {
        if (hasDataset) {
            // Convert from meters to feet (1 meter = 3.28084 feet)
            double minAltitudeFt = datasetMinAltitude * 3.28084;
            double maxAltitudeFt = datasetMaxAltitude * 3.28084;
            return String.format("Dataset: %.0f-%.0fft (%d layers)", minAltitudeFt, maxAltitudeFt, layers.size());
        }
        return "No dataset";
    }
    
    /**
     * Get debug info about layer manager state
     */
    public String getDebugInfo() {
        return String.format("LayerManager: hasDataset=%b, layers=%d, activeLayer=%s, topLayer=%s", 
            hasDataset, layers.size(), 
            activeLayer != null ? activeLayer.name : "null", 
            topLayer != null ? topLayer.name : "null");
    }
    
    /**
     * Calculate wind estimations for a saved layer based on its data points and the requested layer type
     */
    private void calculateWindEstimationsForSavedLayer(WindLayer savedLayer, String layerType) {
        List<WindDataPoint> layerData = getDataForLayer(savedLayer);
        
        if (layerData.size() < 3) {
            Log.w(TAG, "Not enough data points (" + layerData.size() + ") to calculate wind estimation for " + savedLayer.name);
            return;
        }
        
        try {
            if ("GPS".equalsIgnoreCase(layerType)) {
                // Calculate GPS wind estimation for GPS layer type
                try {
                    LeastSquaresCircleFit.CircleFitResult gpsCircleFit = LeastSquaresCircleFit.fitCircleToGPSVelocities(layerData);
                    
                    if (gpsCircleFit != null && gpsCircleFit.pointCount >= 3) {
                        savedLayer.gpsWindEstimation = new WindEstimation(
                            gpsCircleFit.getWindE(),
                            gpsCircleFit.getWindN(),
                            gpsCircleFit.rSquared,
                            gpsCircleFit.pointCount
                        );
                        Log.d(TAG, "Calculated GPS wind estimation for " + savedLayer.name + ": " + 
                              String.format("%.1f mph @ %.0f°", 
                                  savedLayer.gpsWindEstimation.windMagnitude * 2.23694, 
                                  savedLayer.gpsWindEstimation.windDirection));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to calculate GPS wind estimation for " + savedLayer.name + ": " + e.getMessage());
                }
                
            } else if ("Sustained".equalsIgnoreCase(layerType)) {
                // Calculate sustained wind estimation for Sustained layer type
                try {
                    LeastSquaresCircleFit.CircleFitResult sustainedCircleFit = LeastSquaresCircleFit.fitCircleToSustainedVelocities(layerData);
                    
                    if (sustainedCircleFit != null && sustainedCircleFit.pointCount >= 3) {
                        savedLayer.sustainedWindEstimation = new WindEstimation(
                            sustainedCircleFit.getWindE(),
                            sustainedCircleFit.getWindN(),
                            sustainedCircleFit.rSquared,
                            sustainedCircleFit.pointCount
                        );
                        Log.d(TAG, "Calculated sustained wind estimation for " + savedLayer.name + ": " + 
                              String.format("%.1f mph @ %.0f°", 
                                  savedLayer.sustainedWindEstimation.windMagnitude * 2.23694, 
                                  savedLayer.sustainedWindEstimation.windDirection));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to calculate sustained wind estimation for " + savedLayer.name + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating wind estimations for " + savedLayer.name + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Get data points from central dataset within layer boundaries
     */
    public List<WindDataPoint> getDataForLayer(WindLayer layer) {
        List<WindDataPoint> layerData = new ArrayList<>();
        for (WindDataPoint point : centralDataset) {
            if (point.altitude >= layer.minAltitude && point.altitude <= layer.maxAltitude) {
                layerData.add(point);
            }
        }
        return layerData;
    }
    
    /**
     * Get data points from central dataset within layer's data boundaries
     */
    public List<WindDataPoint> getDataForLayerDataBoundaries(WindLayer layer) {
        List<WindDataPoint> layerData = new ArrayList<>();
        for (WindDataPoint point : centralDataset) {
            if (point.altitude >= layer.dataMinAltitude && point.altitude <= layer.dataMaxAltitude) {
                layerData.add(point);
            }
        }
        return layerData;
    }
    
    /**
     * Get data points from central dataset within time interval
     */
    public List<WindDataPoint> getDataInTimeInterval(long startTime, long endTime) {
        List<WindDataPoint> intervalData = new ArrayList<>();
        for (WindDataPoint point : centralDataset) {
            if (point.millis >= startTime && point.millis <= endTime) {
                intervalData.add(point);
            }
        }
        return intervalData;
    }
    
    /**
     * Get the complete central dataset
     */
    public List<WindDataPoint> getCentralDataset() {
        return new ArrayList<>(centralDataset);
    }
    
    /**
     * Set listener for boundary change notifications
     */
    public void setBoundaryChangeListener(BoundaryChangeListener listener) {
        this.boundaryChangeListener = listener;
    }
    
    /**
     * Notify listener that layer boundaries have changed
     */
    private void notifyBoundaryChanged(WindLayer layer) {
        if (boundaryChangeListener != null) {
            boundaryChangeListener.onBoundaryChanged(layer);
        }
    }
    
    /**
     * Public method to notify boundary change (for manual adjustments)
     */
    public void triggerBoundaryChangeNotification(WindLayer layer) {
        notifyBoundaryChanged(layer);
    }
    
    /**
     * Force boundary change notification regardless of active layer
     */
    public void forceBoundaryChangeNotification(WindLayer layer) {
        Log.d(TAG, String.format("Forcing boundary change notification for layer: %s", layer.name));
        notifyBoundaryChanged(layer);
    }
    
    /**
     * Recalculate wind estimations for a layer based on current data boundaries
     */
    public void recalculateWindEstimations(WindLayer layer) {
        if (layer == null) return;
        
        // Get data within current data boundaries
        List<WindDataPoint> layerData = getDataForLayerDataBoundaries(layer);
        
        if (layerData.size() < 3) {
            Log.w(TAG, "Not enough data points (" + layerData.size() + ") to calculate wind estimation for " + layer.name);
            // Clear existing estimations if insufficient data
            layer.gpsWindEstimation = null;
            layer.sustainedWindEstimation = null;
            return;
        }
        
        try {
            // Calculate both GPS and sustained wind estimations
            try {
                LeastSquaresCircleFit.CircleFitResult gpsCircleFit = LeastSquaresCircleFit.fitCircleToGPSVelocities(layerData);
                
                if (gpsCircleFit != null && gpsCircleFit.pointCount >= 3) {
                    layer.gpsWindEstimation = new WindEstimation(
                        gpsCircleFit.getWindE(),
                        gpsCircleFit.getWindN(),
                        gpsCircleFit.rSquared,
                        gpsCircleFit.pointCount
                    );
                    Log.d(TAG, "Recalculated GPS wind estimation for " + layer.name + ": " + 
                          String.format("%.1f mph @ %.0f°", 
                              layer.gpsWindEstimation.windMagnitude * 2.23694, 
                              layer.gpsWindEstimation.windDirection));
                } else {
                    layer.gpsWindEstimation = null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to recalculate GPS wind estimation for " + layer.name + ": " + e.getMessage());
                layer.gpsWindEstimation = null;
            }
            
            try {
                LeastSquaresCircleFit.CircleFitResult sustainedCircleFit = LeastSquaresCircleFit.fitCircleToSustainedVelocities(layerData);
                
                if (sustainedCircleFit != null && sustainedCircleFit.pointCount >= 3) {
                    layer.sustainedWindEstimation = new WindEstimation(
                        sustainedCircleFit.getWindE(),
                        sustainedCircleFit.getWindN(),
                        sustainedCircleFit.rSquared,
                        sustainedCircleFit.pointCount
                    );
                    Log.d(TAG, "Recalculated sustained wind estimation for " + layer.name + ": " + 
                          String.format("%.1f mph @ %.0f°", 
                              layer.sustainedWindEstimation.windMagnitude * 2.23694, 
                              layer.sustainedWindEstimation.windDirection));
                } else {
                    layer.sustainedWindEstimation = null;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to recalculate sustained wind estimation for " + layer.name + ": " + e.getMessage());
                layer.sustainedWindEstimation = null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error recalculating wind estimations for " + layer.name + ": " + e.getMessage(), e);
        }
    }
}