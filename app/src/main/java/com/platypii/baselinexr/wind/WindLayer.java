package com.platypii.baselinexr.wind;

import java.util.ArrayList;
import java.util.List;

/**
 * Wind layer representing wind conditions over an altitude range
 */
public class WindLayer {
    public final String name;
    public double minAltitude;  // Minimum altitude for this layer (layer boundary)
    public double maxAltitude;  // Maximum altitude for this layer (layer boundary)
    public double dataMinAltitude;  // Minimum altitude for data used in wind calculations
    public double dataMaxAltitude;  // Maximum altitude for data used in wind calculations
    public boolean hasManualDataBoundaries = false;  // True if user has manually adjusted data boundaries
    public boolean hasManualLowerDataBoundary = false;  // True if user has manually adjusted lower data boundary
    public long startTime;
    public long endTime;
    public WindEstimation gpsWindEstimation;
    public WindEstimation sustainedWindEstimation;
    public boolean isActive;  // True for the currently active layer
    public boolean isTopLayer; // True if this is the highest altitude layer
    
    // Reference to the layer manager for accessing central dataset
    private WindLayerManager layerManager;
    
    public WindLayer(String name, double minAltitude, double maxAltitude, long startTime) {
        this.name = name;
        this.minAltitude = minAltitude;
        this.maxAltitude = maxAltitude;
        // Initialize data boundaries to match layer boundaries
        this.dataMinAltitude = minAltitude;
        this.dataMaxAltitude = maxAltitude;
        this.startTime = startTime;
        this.endTime = startTime;
        this.isActive = false;
        this.isTopLayer = false;
    }
    
    /**
     * Set reference to layer manager for accessing central dataset
     */
    public void setLayerManager(WindLayerManager manager) {
        this.layerManager = manager;
    }
    
    /**
     * Create a layer for the entire dataset range (default active layer)
     */
    public static WindLayer createDefaultLayer(double minAlt, double maxAlt, long startTime) {
        WindLayer layer = new WindLayer("Active Layer", minAlt, maxAlt, startTime);
        layer.isActive = true;
        layer.isTopLayer = true;
        return layer;
    }
    
    /**
     * Update top layer altitude range to encompass new data
     */
    public void updateTopLayerAltitude(double altitude) {
        if (isTopLayer) {
            minAltitude = Math.min(minAltitude, altitude);
            maxAltitude = Math.max(maxAltitude, altitude);
        }
    }
    
    /**
     * Check if a data point falls within this layer's altitude range
     */
    public boolean containsAltitude(double altitude) {
        return altitude >= minAltitude && altitude <= maxAltitude;
    }
    
    /**
     * Check if this layer overlaps with another layer's altitude range
     */
    public boolean overlapsWith(WindLayer other) {
        return !(maxAltitude < other.minAltitude || minAltitude > other.maxAltitude);
    }
    
    public void updateTimeInterval(long newStartTime, long newEndTime) {
        this.startTime = newStartTime;
        this.endTime = newEndTime;
        
        // Don't update altitude range based on time interval anymore
        // Altitude range is now managed by the layer system
    }
    
    /**
     * Get data from central dataset within this layer's boundaries
     */
    public List<WindDataPoint> getDataInTimeInterval() {
        if (layerManager == null) {
            return new ArrayList<>();
        }
        return layerManager.getDataForLayer(this);
    }
    
    public double getAltitudeRange() {
        return maxAltitude - minAltitude;
    }
    
    public double getCenterAltitude() {
        return (minAltitude + maxAltitude) / 2.0;
    }
    
    /**
     * Get formatted altitude range string for display
     */
    public String getAltitudeRangeString() {
        // Convert from meters to feet (1 meter = 3.28084 feet)
        double minAltitudeFt = minAltitude * 3.28084;
        double maxAltitudeFt = maxAltitude * 3.28084;
        return String.format("%.0f-%.0fft", minAltitudeFt, maxAltitudeFt);
    }
    
    /**
     * Set this layer as active and deactivate others in the provided list
     */
    public void setActive(List<WindLayer> allLayers) {
        for (WindLayer layer : allLayers) {
            layer.isActive = false;
        }
        this.isActive = true;
    }
    
    /**
     * Get data within the data boundary altitude range (for wind calculations)
     */
    /**
     * Get data from central dataset within this layer's data boundaries
     */
    public List<WindDataPoint> getDataInDataBoundaries() {
        if (layerManager == null) {
            return new ArrayList<>();
        }
        return layerManager.getDataForLayerDataBoundaries(this);
    }
    
    /**
     * Adjust data minimum altitude with validation
     */
    public boolean adjustDataMinAltitude(double delta, double datasetMinAltitude) {
        double newMin = dataMinAltitude + delta;
        
        android.util.Log.d("WindLayer", String.format("Adjusting data min altitude for %s: %f + %f = %f (isTopLayer: %b)", 
            name, dataMinAltitude, delta, newMin, isTopLayer));
        
        // Validate against dataset bounds
        if (newMin < datasetMinAltitude) {
            android.util.Log.d("WindLayer", "Rejected: below dataset minimum " + datasetMinAltitude);
            return false;
        }
        
        // For live layers, be more permissive - allow adjustment as long as it's reasonable
        if (isTopLayer) {
            // Find the actual minimum data altitude within this layer
            double actualMinData = Double.MAX_VALUE;
            if (layerManager != null) {
                for (WindDataPoint point : layerManager.getCentralDataset()) {
                    if (point.altitude >= minAltitude && point.altitude <= maxAltitude) {
                        actualMinData = Math.min(actualMinData, point.altitude);
                    }
                }
            }
            // Don't allow setting below the actual minimum data
            if (actualMinData != Double.MAX_VALUE && newMin < actualMinData) {
                android.util.Log.d("WindLayer", "Rejected: below actual minimum data " + actualMinData);
                return false;
            }
            // For live layers, allow setting closer to max altitude (leave some buffer)
            if (newMin >= dataMaxAltitude - 10.0) { // 10m buffer
                android.util.Log.d("WindLayer", "Rejected: too close to data max altitude " + dataMaxAltitude);
                return false;
            }
        } else {
            // For saved layers, use stricter validation
            if (newMin >= dataMaxAltitude) {
                android.util.Log.d("WindLayer", "Rejected: exceeds data max altitude " + dataMaxAltitude);
                return false;
            }
        }
        
        dataMinAltitude = newMin;
        
        // Mark as manually adjusted to prevent automatic boundary expansion
        hasManualDataBoundaries = true;
        hasManualLowerDataBoundary = true;
        android.util.Log.d("WindLayer", String.format("Set hasManualLowerDataBoundary=true for %s, new min=%.1f", name, newMin));
        
        // Trigger wind recalculation for saved layers (not live layer)
        if (!isTopLayer && layerManager != null) {
            layerManager.recalculateWindEstimations(this);
        }
        
        return true;
    }
    
    /**
     * Adjust data maximum altitude with validation
     */
    public boolean adjustDataMaxAltitude(double delta, double datasetMaxAltitude) {
        // Cannot adjust upper boundary of live layer
        if (isTopLayer) {
            return false;
        }
        
        double newMax = dataMaxAltitude + delta;
        
        // Validate against dataset bounds and data min boundary
        if (newMax > datasetMaxAltitude || newMax <= dataMinAltitude) {
            return false;
        }
        
        dataMaxAltitude = newMax;
        
        // Mark as manually adjusted to prevent automatic boundary expansion
        hasManualDataBoundaries = true;
        
        // Trigger wind recalculation for saved layers (not live layer)
        if (!isTopLayer && layerManager != null) {
            layerManager.recalculateWindEstimations(this);
        }
        
        return true;
    }
    
    /**
     * Reset manual data boundaries flag to allow automatic expansion again
     */
    public void resetManualDataBoundaries() {
        hasManualDataBoundaries = false;
        hasManualLowerDataBoundary = false;
    }
    
    /**
     * Reset manual lower data boundary flag to allow automatic expansion of lower boundary
     */
    public void resetManualLowerDataBoundary() {
        hasManualLowerDataBoundary = false;
    }
    
    /**
     * Get formatted data boundary range string for display
     */
    public String getDataBoundaryRangeString() {
        // Convert from meters to feet (1 meter = 3.28084 feet)
        double minDataAltitudeFt = dataMinAltitude * 3.28084;
        double maxDataAltitudeFt = dataMaxAltitude * 3.28084;
        return String.format("Data: %.0f-%.0fft", minDataAltitudeFt, maxDataAltitudeFt);
    }
    
    /**
     * Validate and adjust layer boundary with rules to prevent overlap
     */
    public boolean adjustLayerBoundary(boolean isUpper, double delta, List<WindLayer> allLayers, 
                                       double datasetMinAltitude, double datasetMaxAltitude) {
        android.util.Log.d("WindLayer", String.format("Adjusting %s boundary for %s by %.1fm", 
            isUpper ? "upper" : "lower", name, delta));
            
        if (isUpper) {
            // Can't adjust upper boundary of top layer
            if (isTopLayer) {
                android.util.Log.w("WindLayer", "Cannot adjust upper boundary of top layer: " + name);
                return false;
            }
            
            double newMax = maxAltitude + delta;
            
            // Find the layer above this one
            WindLayer upperLayer = null;
            for (WindLayer layer : allLayers) {
                if (layer.minAltitude == this.maxAltitude && layer != this) {
                    upperLayer = layer;
                    break;
                }
            }
            
            // Check minimum layer size (10 meters for practical use)
            if (newMax <= minAltitude + 10.0) {
                android.util.Log.w("WindLayer", String.format("Cannot adjust upper boundary: would make layer too small (%.1f -> %.1f, min=%.1f)", 
                    maxAltitude, newMax, minAltitude));
                return false;
            }
            
            // Check that upper layer won't become too small
            if (upperLayer != null && newMax >= upperLayer.maxAltitude - 10.0) {
                android.util.Log.w("WindLayer", String.format("Cannot adjust upper boundary: would make upper layer %s too small", 
                    upperLayer.name));
                return false;
            }
            
            // Update this layer and the layer above
            android.util.Log.d("WindLayer", String.format("Updating %s upper boundary: %.1f -> %.1f", name, maxAltitude, newMax));
            this.maxAltitude = newMax;
            
            if (upperLayer != null) {
                android.util.Log.d("WindLayer", String.format("Updating adjacent %s lower boundary: %.1f -> %.1f", 
                    upperLayer.name, upperLayer.minAltitude, newMax));
                upperLayer.minAltitude = newMax;
            } else {
                android.util.Log.w("WindLayer", "No upper adjacent layer found for " + name);
            }
            
        } else {
            double newMin = minAltitude + delta;
            
            // Check dataset bounds
            if (newMin < datasetMinAltitude) {
                android.util.Log.w("WindLayer", String.format("Cannot adjust lower boundary: would go below dataset minimum (%.1f < %.1f)", 
                    newMin, datasetMinAltitude));
                return false;
            }
            
            // Find the layer below this one
            WindLayer lowerLayer = null;
            for (WindLayer layer : allLayers) {
                if (layer.maxAltitude == this.minAltitude && layer != this) {
                    lowerLayer = layer;
                    break;
                }
            }
            
            // Check minimum layer size (10 meters for practical use)
            if (newMin >= maxAltitude - 10.0) {
                android.util.Log.w("WindLayer", String.format("Cannot adjust lower boundary: would make layer too small (%.1f -> %.1f, max=%.1f)", 
                    minAltitude, newMin, maxAltitude));
                return false;
            }
            
            // Check that lower layer won't become too small
            if (lowerLayer != null && newMin <= lowerLayer.minAltitude + 10.0) {
                android.util.Log.w("WindLayer", String.format("Cannot adjust lower boundary: would make lower layer %s too small", 
                    lowerLayer.name));
                return false;
            }
            
            // Update this layer and the layer below
            android.util.Log.d("WindLayer", String.format("Updating %s lower boundary: %.1f -> %.1f", name, minAltitude, newMin));
            this.minAltitude = newMin;
            
            if (lowerLayer != null) {
                android.util.Log.d("WindLayer", String.format("Updating adjacent %s upper boundary: %.1f -> %.1f", 
                    lowerLayer.name, lowerLayer.maxAltitude, newMin));
                lowerLayer.maxAltitude = newMin;
            } else {
                android.util.Log.w("WindLayer", "No lower adjacent layer found for " + name);
            }
        }
        
        return true;
    }
}