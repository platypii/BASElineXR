package com.platypii.baselinexr.wind;

import android.util.Log;
import androidx.annotation.NonNull;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.location.KalmanFilter3D;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.PubSub.Subscriber;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Wind estimation system for collecting velocity data and managing wind layers
 */
public class WindEstimationSystem implements Subscriber<MLocation> {
    private static final String TAG = "WindEstimationSystem";
    
    // Data collection
    private final List<WindDataPoint> allDataPoints = new ArrayList<>();
    private final List<WindLayer> windLayers = new ArrayList<>();
    private WindLayer currentLayer;
    
    // Time interval selection
    private long intervalStartTime = 0;
    private long intervalEndTime = 0;
    private boolean includeLiveData = true;
    private static final long TIME_STEP_MS = 1000; // 1 second intervals
    private static final long TIME_ADJUSTMENT_MS = 5000; // 5 second adjustments
    
    // Data filtering
    private long lastDataTime = 0;
    private static final long DATA_INTERVAL_MS = 1000; // Collect data every second
    
    // Current estimations
    private LeastSquaresCircleFit.CircleFitResult currentGpsCircleFit;
    private LeastSquaresCircleFit.CircleFitResult currentSustainedCircleFit;
    private WindEstimation currentGpsWindEstimation;
    private WindEstimation currentSustainedWindEstimation;
    
    // UI callbacks
    public interface WindEstimationListener {
        void onDataUpdated(List<WindDataPoint> intervalData);
        void onWindEstimationUpdated(WindEstimation gpsWind, WindEstimation sustainedWind);
        void onCircleFitsUpdated(LeastSquaresCircleFit.CircleFitResult gpsCircle, LeastSquaresCircleFit.CircleFitResult sustainedCircle);
        void onLayersUpdated(List<WindLayer> layers, WindLayer currentLayer);
    }
    
    private WindEstimationListener listener;
    private WindLayerManager layerManager;
    private boolean isCollecting = false;
    
    public void setListener(WindEstimationListener listener) {
        this.listener = listener;
    }
    
    public void setLayerManager(WindLayerManager layerManager) {
        this.layerManager = layerManager;
    }
    
    public void startCollection() {
        if (!isCollecting) {
            isCollecting = true;
            Services.location.locationUpdates.subscribe(this);
            
            // Initialize first layer
            long currentTime = System.currentTimeMillis();
            double currentAltitude = getCurrentAltitude();
            
            // Initialize layer manager if not already done
            if (layerManager != null) {
                if (!layerManager.hasLayers()) {
                    layerManager.initializeWithDataset(currentAltitude - 100, currentAltitude + 1000, currentTime);
                }
                currentLayer = layerManager.getActiveLayer();
                // Don't add to windLayers - let layer manager handle everything
            } else {
                // Fallback: Create initial layer with current altitude as both min and max (will expand as data comes in)
                currentLayer = new WindLayer("Layer 1", currentAltitude, currentAltitude, currentTime);
                currentLayer.isTopLayer = true; // Mark as top layer so altitude range can expand
                currentLayer.isActive = true; // Mark as active layer initially
                windLayers.add(currentLayer);
            }
            
            intervalStartTime = currentTime;
            intervalEndTime = currentTime;
            
            Log.i(TAG, "Wind estimation collection started");
        }
    }
    
    public void stopCollection() {
        if (isCollecting) {
            isCollecting = false;
            Services.location.locationUpdates.unsubscribe(this);
            Log.i(TAG, "Wind estimation collection stopped");
        }
    }
    
    @Override
    public void apply(@NonNull MLocation loc) {
        if (!isCollecting) return;
        
        // Throttle data collection to 1 Hz
        if (loc.millis - lastDataTime < DATA_INTERVAL_MS) {
            return;
        }
        lastDataTime = loc.millis;
        
        // Get sustained speeds from KalmanFilter3D
        double sustainedVE = Double.NaN, sustainedVN = Double.NaN, sustainedVD = Double.NaN;
        boolean hasSustainedData = false;
        
        if (Services.location != null && Services.location.motionEstimator instanceof KalmanFilter3D) {
            KalmanFilter3D kf3d = (KalmanFilter3D) Services.location.motionEstimator;
            KalmanFilter3D.KFState predictedState = kf3d.getCachedPredictedState(System.currentTimeMillis());
            
            double kl = predictedState.kl();
            double kd = predictedState.kd();
            
            Log.d(TAG, String.format("KL=%.3f, KD=%.3f", kl, kd));
            
            // Only calculate sustained velocity if we have valid KL/KD coefficients
             // Use smaller threshold to catch more data
                // Calculate sustained speed magnitude using same formula as FlightMode
                final double klkd_squared = kl * kl + kd * kd;
                final double klkd_power = Math.pow(klkd_squared, 0.75);
                final double sustainedSpeed = kl / klkd_power;
                
                // Get GPS velocity heading (bearing) - always use GPS direction when available
                double gpsSpeed = Math.sqrt(loc.vE * loc.vE + loc.vN * loc.vN);
                
                if (gpsSpeed > 0.05) { // Lower threshold for heading detection
                    double heading = Math.atan2(loc.vE, loc.vN); // Bearing in radians
                    
                    // Apply sustained speed magnitude in the same direction as GPS velocity
                    sustainedVE = sustainedSpeed * Math.sin(heading);
                    sustainedVN = sustainedSpeed * Math.cos(heading);
                    hasSustainedData = true;
                    
                    Log.d(TAG, String.format("Sustained: speed=%.2f, heading=%.1f°, vE=%.2f, vN=%.2f", 
                          sustainedSpeed, Math.toDegrees(heading), sustainedVE, sustainedVN));
                } else {
                    // If GPS velocity is too small, we can't determine direction reliably
                    Log.d(TAG, "GPS speed too low for sustained velocity direction");
                }
                
                // Calculate sustained descent rate (approximate)
                sustainedVD = -kd / klkd_power;
            
        }
        
        // Only create data point if we have GPS data, sustained data is optional but logged
        if (loc.vE != 0 || loc.vN != 0) { // Only exclude true zero GPS velocities
            // Use zero for sustained velocities if not available (better than NaN for circle fitting)
            double finalSustainedVE = hasSustainedData ? sustainedVE : 0.0;
            double finalSustainedVN = hasSustainedData ? sustainedVN : 0.0;
            double finalSustainedVD = hasSustainedData ? sustainedVD : 0.0;
            
            WindDataPoint dataPoint = new WindDataPoint(
                loc.millis,
                loc.altitude_gps,
                loc.vE,          // East velocity
                loc.vN,          // North velocity  
                -loc.climb,      // Down velocity (negate climb)
                finalSustainedVE,
                finalSustainedVN,
                finalSustainedVD
            );
            
            Log.d(TAG, String.format("Data point: GPS(%.2f,%.2f) Sustained(%.2f,%.2f) HasSustained=%b", 
                  loc.vE, loc.vN, finalSustainedVE, finalSustainedVN, hasSustainedData));
            
            // Add to collections
            allDataPoints.add(dataPoint);
            
            // Add to layer manager for the new layer system (it will auto-initialize if needed)
            if (layerManager != null) {
                layerManager.addDataPoint(dataPoint);
                // Update currentLayer reference to match the layer manager's active layer
                currentLayer = layerManager.getActiveLayer();
            } else {
                Log.w(TAG, "No layer manager available to add data point");
            }
        } else {
            Log.d(TAG, "Skipping data point with zero GPS velocity");
        }
        
        // Update end time if including live data
        if (includeLiveData) {
            intervalEndTime = loc.millis;
            if (currentLayer != null) {
                currentLayer.endTime = loc.millis;
            }
            
            // If using layer manager, sync with active layer's time interval
            if (layerManager != null) {
                WindLayer activeLayer = layerManager.getActiveLayer();
                if (activeLayer != null) {
                    intervalStartTime = activeLayer.startTime;
                    intervalEndTime = activeLayer.endTime;
                }
            }
        }
        
        // Update estimations
        updateCurrentEstimations();
        
        // Notify listeners
        notifyListeners();
    }
    
    private void updateCurrentEstimations() {
        List<WindDataPoint> intervalData = getDataInTimeInterval();
        
        // Count valid data points
        int gpsValidCount = 0;
        int sustainedValidCount = 0;
        for (WindDataPoint point : intervalData) {
            double gpsSpeed = Math.sqrt(point.vE * point.vE + point.vN * point.vN);
            double sustainedSpeed = Math.sqrt(point.sustainedVE * point.sustainedVE + point.sustainedVN * point.sustainedVN);
            
            if (gpsSpeed > 0.1) gpsValidCount++;
            if (sustainedSpeed > 0.1) sustainedValidCount++;
        }
        
        Log.d(TAG, String.format("Estimation update: %d total points, %d GPS valid, %d sustained valid", 
              intervalData.size(), gpsValidCount, sustainedValidCount));
        
        if (intervalData.size() >= 3) {
            // Compute circle fits
            currentGpsCircleFit = LeastSquaresCircleFit.fitCircleToGPSVelocities(intervalData);
            currentSustainedCircleFit = LeastSquaresCircleFit.fitCircleToSustainedVelocities(intervalData);
            
            // Log circle fit results
            if (currentGpsCircleFit != null) {
                Log.d(TAG, String.format("GPS circle fit: center(%.2f,%.2f) radius=%.2f R²=%.3f points=%d", 
                      currentGpsCircleFit.centerX, currentGpsCircleFit.centerY, 
                      currentGpsCircleFit.radius, currentGpsCircleFit.rSquared, currentGpsCircleFit.pointCount));
            }
            
            if (currentSustainedCircleFit != null) {
                Log.d(TAG, String.format("Sustained circle fit: center(%.2f,%.2f) radius=%.2f R²=%.3f points=%d", 
                      currentSustainedCircleFit.centerX, currentSustainedCircleFit.centerY, 
                      currentSustainedCircleFit.radius, currentSustainedCircleFit.rSquared, currentSustainedCircleFit.pointCount));
            }
            
            // Convert to wind estimations
            if (currentGpsCircleFit != null && currentGpsCircleFit.pointCount >= 3) {
                currentGpsWindEstimation = new WindEstimation(
                    currentGpsCircleFit.getWindE(),
                    currentGpsCircleFit.getWindN(),
                    currentGpsCircleFit.rSquared,
                    currentGpsCircleFit.pointCount
                );
            }
            
            if (currentSustainedCircleFit != null && currentSustainedCircleFit.pointCount >= 3) {
                currentSustainedWindEstimation = new WindEstimation(
                    currentSustainedCircleFit.getWindE(),
                    currentSustainedCircleFit.getWindN(),
                    currentSustainedCircleFit.rSquared,
                    currentSustainedCircleFit.pointCount
                );
            }
        } else {
            Log.d(TAG, "Not enough data points for circle fitting: " + intervalData.size());
        }
    }
    
    private void notifyListeners() {
        if (listener != null) {
            List<WindDataPoint> intervalData = getDataInTimeInterval();
            listener.onDataUpdated(intervalData);
            listener.onWindEstimationUpdated(currentGpsWindEstimation, currentSustainedWindEstimation);
            listener.onCircleFitsUpdated(currentGpsCircleFit, currentSustainedCircleFit);
            
            // Use layer manager's layers if available, otherwise fall back to local windLayers
            if (layerManager != null && layerManager.hasLayers()) {
                listener.onLayersUpdated(layerManager.getLayers(), layerManager.getActiveLayer());
            } else {
                listener.onLayersUpdated(windLayers, currentLayer);
            }
        }
    }
    
    // Time interval management
    public void adjustStartTime(boolean increase) {
        long adjustment = increase ? TIME_ADJUSTMENT_MS : -TIME_ADJUSTMENT_MS;
        intervalStartTime = Math.max(0, intervalStartTime + adjustment);
        intervalStartTime = Math.min(intervalStartTime, intervalEndTime - TIME_STEP_MS);
        
        updateCurrentEstimations();
        notifyListeners();
    }
    
    public void adjustEndTime(boolean increase) {
        if (includeLiveData) return; // Can't adjust end time when including live data
        
        long adjustment = increase ? TIME_ADJUSTMENT_MS : -TIME_ADJUSTMENT_MS;
        intervalEndTime = Math.max(intervalStartTime + TIME_STEP_MS, intervalEndTime + adjustment);
        intervalEndTime = Math.min(intervalEndTime, System.currentTimeMillis());
        
        updateCurrentEstimations();
        notifyListeners();
    }
    
    public void setIncludeLiveData(boolean includeLive) {
        this.includeLiveData = includeLive;
        if (includeLive) {
            intervalEndTime = System.currentTimeMillis();
        }
        
        updateCurrentEstimations();
        notifyListeners();
    }
    
    // Layer management
    public void saveGpsWindEstimation() {
        if (currentLayer != null && currentGpsWindEstimation != null) {
            currentLayer.gpsWindEstimation = currentGpsWindEstimation;
            currentLayer.updateTimeInterval(intervalStartTime, intervalEndTime);
            Log.i(TAG, "Saved GPS wind estimation: " + currentGpsWindEstimation);
            notifyListeners();
        }
    }
    
    public void saveSustainedWindEstimation() {
        if (currentLayer != null && currentSustainedWindEstimation != null) {
            currentLayer.sustainedWindEstimation = currentSustainedWindEstimation;
            currentLayer.updateTimeInterval(intervalStartTime, intervalEndTime);
            Log.i(TAG, "Saved sustained wind estimation: " + currentSustainedWindEstimation);
            notifyListeners();
        }
    }
    
    public void createNewLayer() {
        if (currentLayer != null) {
            currentLayer.isActive = false;
        }
        
        double currentAltitude = getCurrentAltitude();
        long currentTime = System.currentTimeMillis();
        String layerName = "Layer " + (windLayers.size() + 1);
        
        // Create new layer with current altitude as both min and max (will expand as data comes in)
        currentLayer = new WindLayer(layerName, currentAltitude, currentAltitude, currentTime);
        currentLayer.isTopLayer = true; // Mark as top layer so altitude range can expand
        currentLayer.isActive = true; // Mark as active layer initially
        windLayers.add(currentLayer);
        
        intervalStartTime = currentTime;
        intervalEndTime = currentTime;
        includeLiveData = true;
        
        Log.i(TAG, "Created new layer: " + layerName);
        notifyListeners();
    }
    
    // DEPRECATED: Layer splitting is now handled by WindLayerManager
    // public void splitLayer(double splitAltitude) {
    //     // Functionality moved to WindLayerManager.splitActiveLayer()
    // }
    
    public void selectLayer(WindLayer layer) {
        if (windLayers.contains(layer)) {
            // Deactivate current layer
            if (currentLayer != null) {
                currentLayer.isActive = false;
            }
            
            // Activate selected layer
            currentLayer = layer;
            currentLayer.isActive = true;
            
            // Update time interval to match layer
            intervalStartTime = layer.startTime;
            intervalEndTime = layer.endTime;
            includeLiveData = layer.isActive && windLayers.indexOf(layer) == windLayers.size() - 1;
            
            updateCurrentEstimations();
            notifyListeners();
        }
    }
    
    // Helper methods
    private List<WindDataPoint> getDataInTimeInterval() {
        // If using layer manager, get data from central dataset within time interval
        if (layerManager != null && layerManager.hasLayers()) {
            return layerManager.getDataInTimeInterval(intervalStartTime, intervalEndTime);
        }
        
        // Fallback: use time interval on all data points
        List<WindDataPoint> intervalData = new ArrayList<>();
        for (WindDataPoint point : allDataPoints) {
            if (point.millis >= intervalStartTime && point.millis <= intervalEndTime) {
                intervalData.add(point);
            }
        }
        
        return intervalData;
    }
    
    private double getCurrentAltitude() {
        if (Services.location != null) {
            MLocation lastUpdate = Services.location.motionEstimator.getLastUpdate();
            if (lastUpdate != null) {
                return lastUpdate.altitude_gps;
            }
        }
        return 0.0;
    }
    
    // Getters
    public List<WindLayer> getWindLayers() {
        if (layerManager != null && layerManager.hasLayers()) {
            return layerManager.getLayers();
        }
        return new ArrayList<>(windLayers);
    }
    
    public WindLayer getCurrentLayer() {
        if (layerManager != null && layerManager.hasLayers()) {
            return layerManager.getActiveLayer();
        }
        return currentLayer;
    }
    
    public List<WindDataPoint> getAllDataPoints() {
        return new ArrayList<>(allDataPoints);
    }
    
    public WindEstimation getCurrentGpsWindEstimation() {
        return currentGpsWindEstimation;
    }
    
    public WindEstimation getCurrentSustainedWindEstimation() {
        return currentSustainedWindEstimation;
    }
    
    public boolean hasEstimations() {
        if (layerManager != null && layerManager.hasLayers()) {
            List<WindLayer> layers = layerManager.getLayers();
            return !layers.isEmpty() && layers.stream().anyMatch(layer -> 
                layer.gpsWindEstimation != null || layer.sustainedWindEstimation != null);
        }
        return !windLayers.isEmpty() && (windLayers.get(0).gpsWindEstimation != null || windLayers.get(0).sustainedWindEstimation != null);
    }
}