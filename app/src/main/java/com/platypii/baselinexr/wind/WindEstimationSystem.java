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

    // Data collection - NO LOCAL STORAGE, use WindLayerManager.centralDataset as single source
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

            // Only calculate sustained velocity if we have valid KL/KD coefficients
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

            // Add to layer manager (single source of truth for all data)
            if (layerManager != null) {
                layerManager.addDataPoint(dataPoint);
                // Update currentLayer reference to match the layer manager's active layer
                currentLayer = layerManager.getActiveLayer();
            }
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

        if (intervalData.size() >= 3) {
            // Compute circle fits
            currentGpsCircleFit = LeastSquaresCircleFit.fitCircleToGPSVelocities(intervalData);
            currentSustainedCircleFit = LeastSquaresCircleFit.fitCircleToSustainedVelocities(intervalData);

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
        }
    }

    private void notifyListeners() {
        if (listener != null) {
            List<WindDataPoint> intervalData = getDataInTimeInterval();
            listener.onDataUpdated(intervalData);
            listener.onWindEstimationUpdated(currentGpsWindEstimation, currentSustainedWindEstimation);
            listener.onCircleFitsUpdated(currentGpsCircleFit, currentSustainedCircleFit);

            // Use layer manager's layers if available
            if (layerManager != null && layerManager.hasLayers()) {
                listener.onLayersUpdated(layerManager.getLayers(), layerManager.getActiveLayer());
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







    // Helper methods
    private List<WindDataPoint> getDataInTimeInterval() {
        // If using layer manager, get data from central dataset within time interval
        if (layerManager != null && layerManager.hasLayers()) {
            return layerManager.getDataInTimeInterval(intervalStartTime, intervalEndTime);
        }

        // Fallback: should not happen since layerManager is always set, but return empty list
        return new ArrayList<>();
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
        return new ArrayList<>();
    }

    public WindLayer getCurrentLayer() {
        if (layerManager != null && layerManager.hasLayers()) {
            return layerManager.getActiveLayer();
        }
        return currentLayer;
    }

    public List<WindDataPoint> getAllDataPoints() {
        // Get data from single source of truth (layer manager)
        if (layerManager != null) {
            return layerManager.getCentralDataset();
        }
        return new ArrayList<>();
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
        return false;
    }
}