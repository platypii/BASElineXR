package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.VROptions;
import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumidityData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MSensorData;
import com.platypii.baselinexr.measurements.SensorDataSet;
import com.platypii.baselinexr.tracks.FlySightDataLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides sensor data (compass, IMU, barometer) synchronized with GPS data.
 * Supports mock/replay mode using FlySight SENSOR.CSV files.
 * 
 * Sensor data is now stored separately by type to match the BLE streaming model
 * where each sensor type has its own characteristic and timestamp.
 */
public class MockSensorProvider implements SensorProvider {
    private static final String TAG = "MockSensorProvider";

    // Loaded sensor data - now stored separately by type
    @NonNull
    private SensorDataSet sensorDataSet = new SensorDataSet();
    
    // Legacy combined sensor data (for backwards compatibility)
    @NonNull
    private List<MSensorData> sensorData = new ArrayList<>();

    // Current playback state
    private int currentIndex = 0;
    private boolean started = false;

    // Timing
    public static long systemStartTime = System.currentTimeMillis();
    
    // Default max age for sensor lookups (ms)
    private static final long DEFAULT_MAX_AGE_MS = 5000;

    /**
     * Preload sensor data without starting playback.
     * This allows sensor data to be available before GPS starts.
     * Used when video is configured and may start before GPS.
     */
    public void preloadData(@NonNull Context context) {
        if (!sensorDataSet.isEmpty()) {
            Log.d(TAG, "Sensor data already loaded");
            return;
        }
        loadData(context);
        if (!sensorDataSet.isEmpty()) {
            long[] range = sensorDataSet.getTimeRange();
            if (range != null) {
                Log.i(TAG, String.format("Sensor data preloaded: %d total measurements, range=[%d, %d]", 
                    sensorDataSet.getTotalCount(), range[0], range[1]));
            }
        }
    }
    
    /**
     * Get the time range of sensor data.
     * @return [startMillis, endMillis] or null if no data
     */
    @Nullable
    public long[] getSensorTimeRange() {
        return sensorDataSet.getTimeRange();
    }

    /**
     * Load sensor data from FlySight folder
     */
    public void loadData(@NonNull Context context) {
        final String mockSensor = VROptions.current.mockSensor;
        if (mockSensor == null) {
            Log.w(TAG, "No mockSensor folder specified, sensor data unavailable");
            return;
        }

        Log.i(TAG, "Loading sensor data from: " + mockSensor);

        try {
            FlySightDataLoader.FlySightData data = FlySightDataLoader.loadFromAssets(
                    context,
                    mockSensor,
                    VROptions.current.mockTrackStartSec,
                    VROptions.current.mockTrackEndSec
            );

            sensorDataSet = data.sensorDataSet;
            sensorData = data.sensorData; // Legacy combined data
            Log.i(TAG, String.format("Loaded sensor data: %s", sensorDataSet));

        } catch (Exception e) {
            Log.e(TAG, "Error loading sensor data", e);
            sensorDataSet = new SensorDataSet();
            sensorData = new ArrayList<>();
        }
    }

    /**
     * Start sensor data playback
     *
     * @param context Application context
     * @param trackStartTime First GPS timestamp to use as reference for time delta (or 0 to use first sensor time)
     */
    public void start(@NonNull Context context, long trackStartTime) {
        Log.i(TAG, "Starting mock sensor provider");
        systemStartTime = System.currentTimeMillis();
        loadData(context);

        // Log sensor data time range for debugging
        // Note: We keep sensor data in original GPS timestamps to match PlaybackTimeline coordinates
        long[] timeRange = sensorDataSet.getTimeRange();
        if (timeRange != null) {
            final long sensorStart = timeRange[0];
            final long sensorEnd = timeRange[1];
            final long sensorGpsOffset = sensorStart - trackStartTime;
            Log.i(TAG, String.format("TIMESYNC: Sensor data range: [%d, %d], span=%ds",
                    sensorStart, sensorEnd, (sensorEnd - sensorStart) / 1000));
            Log.i(TAG, String.format("TIMESYNC: Sensor starts %d ms after GPS track start (trackStartTime=%d)",
                    sensorGpsOffset, trackStartTime));
        }

        started = true;
        currentIndex = 0;
    }

    /**
     * Stop sensor data playback
     */
    public void stop() {
        started = false;
        currentIndex = 0;
        sensorDataSet = new SensorDataSet();
        sensorData = new ArrayList<>();
    }
    
    /**
     * Pause sensor data playback (no-op for query-based provider)
     */
    public void pause() {
        Log.i(TAG, "Sensor provider paused");
        // Sensor data is query-based, no active thread to pause
    }
    
    /**
     * Resume sensor data playback (no-op for query-based provider)
     */
    public void resume() {
        Log.i(TAG, "Sensor provider resumed");
        // Sensor data is query-based, no active thread to resume
    }
    
    /**
     * Restart sensor data playback from beginning
     */
    public void restart(@NonNull Context context, long trackStartTime) {
        Log.i(TAG, "Restarting sensor provider");
        stop();
        start(context, trackStartTime);
    }
    
    // ========== Type-specific sensor accessors (new API) ==========
    
    /**
     * Get IMU data at or before the given GPS time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent IMU data, or null if not available
     */
    @Override
    @Nullable
    public MImuData getImuAtTime(long gpsMillis) {
        return sensorDataSet.getImuAtTime(gpsMillis, DEFAULT_MAX_AGE_MS);
    }
    
    /**
     * Get magnetometer data at or before the given GPS time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent magnetometer data, or null if not available
     */
    @Override
    @Nullable
    public MMagData getMagAtTime(long gpsMillis) {
        return sensorDataSet.getMagAtTime(gpsMillis, DEFAULT_MAX_AGE_MS);
    }
    
    /**
     * Get barometer data at or before the given GPS time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent barometer data, or null if not available
     */
    @Override
    @Nullable
    public MBaroData getBaroAtTime(long gpsMillis) {
        return sensorDataSet.getBaroAtTime(gpsMillis, DEFAULT_MAX_AGE_MS);
    }
    
    /**
     * Get humidity data at or before the given GPS time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent humidity data, or null if not available
     */
    @Override
    @Nullable
    public MHumidityData getHumidityAtTime(long gpsMillis) {
        return sensorDataSet.getHumidityAtTime(gpsMillis, DEFAULT_MAX_AGE_MS);
    }
    
    /**
     * Get the full SensorDataSet for direct access to all sensor types
     */
    @NonNull
    public SensorDataSet getSensorDataSet() {
        return sensorDataSet;
    }
    
    // ========== Legacy combined sensor accessor (backwards compatibility) ==========

    /**
     * Get the sensor measurement closest to the given GPS time
     * Returns the most recent sensor reading at or before the requested time
     *
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent sensor data at or before the requested time, or null if no data available
     * @deprecated Use type-specific methods like {@link #getImuAtTime(long)}, {@link #getMagAtTime(long)}, etc.
     */
    @Deprecated
    @Override
    @Nullable
    public MSensorData getSensorAtTime(long gpsMillis) {
        if (sensorData.isEmpty()) {
            // Silently return null - no need to spam logs when sensor data isn't loaded
            return null;
        }

        // Debug: Log time range and request (only when data is loaded)
        long firstTime = sensorData.get(0).millis;
        long lastTime = sensorData.get(sensorData.size() - 1).millis;
        Log.d(TAG, String.format("TIMESYNC: Requested GPS time=%d, sensor range=[%d, %d], span=%ds",
                gpsMillis, firstTime, lastTime, (lastTime - firstTime) / 1000));

        // If before first sensor reading, return null
        if (gpsMillis < firstTime) {
            Log.d(TAG, String.format("TIMESYNC: GPS time %d is before first sensor reading at %d", gpsMillis, firstTime));
            return null;
        }

        // If after last sensor reading, return last reading (with time limit)
        if (gpsMillis > lastTime) {
            long diff = gpsMillis - lastTime;
            if (diff > 1000) { // Don't use sensor data more than 1 second old
                Log.d(TAG, String.format("TIMESYNC: GPS time %d is %dms after last sensor reading, too old", gpsMillis, diff));
                return null;
            }
            Log.d(TAG, String.format("TIMESYNC: Using last sensor reading at %d (GPS time is %dms later)", lastTime, diff));
            return sensorData.get(sensorData.size() - 1);
        }

        // Binary search to find the most recent sensor reading at or before gpsMillis
        int left = 0;
        int right = sensorData.size() - 1;
        int result = 0; // Index of most recent reading at or before gpsMillis

        while (left <= right) {
            int mid = (left + right) / 2;
            MSensorData sensor = sensorData.get(mid);

            if (sensor.millis <= gpsMillis) {
                result = mid; // This is a candidate
                left = mid + 1; // Look for a more recent one
            } else {
                right = mid - 1; // Look earlier
            }
        }

        MSensorData sensorReading = sensorData.get(result);
        long age = gpsMillis - sensorReading.millis;

        // Check if this entry has valid magnetic data, if not search backwards
        if (!Float.isFinite(sensorReading.magX) || !Float.isFinite(sensorReading.magY) || !Float.isFinite(sensorReading.magZ)) {
            Log.d(TAG, String.format("TIMESYNC: Sensor at %d has no MAG data, searching backwards", sensorReading.millis));
            // Search backwards for most recent entry with valid MAG data
            for (int i = result - 1; i >= 0; i--) {
                MSensorData candidate = sensorData.get(i);
                if (Float.isFinite(candidate.magX) && Float.isFinite(candidate.magY) && Float.isFinite(candidate.magZ)) {
                    long candidateAge = gpsMillis - candidate.millis;
                    if (candidateAge > 5000) { // Increased to 5 seconds for sparse MAG data
                        Log.d(TAG, String.format("TIMESYNC: Found MAG data at %d but too old (age=%dms)", candidate.millis, candidateAge));
                        return null;
                    }
                    Log.d(TAG, String.format("TIMESYNC: Found valid MAG data at %d (age=%dms)", candidate.millis, candidateAge));
                    return candidate;
                }
            }
            Log.d(TAG, "TIMESYNC: No valid MAG data found in sensor history");
            return null;
        }

        // Don't use sensor data more than 5 seconds old (increased for sparse MAG data)
        if (age > 5000) {
            Log.d(TAG, String.format("TIMESYNC: Most recent sensor at %d is too old (age=%dms)", sensorReading.millis, age));
            return null;
        }

        Log.d(TAG, String.format("TIMESYNC: Using sensor at %d (age=%dms from GPS time %d)",
                sensorReading.millis, age, gpsMillis));
        return sensorReading;
    }

    /**
     * Get current sensor measurement based on playback time
     * Automatically advances through sensor data as time progresses
     * @deprecated Use type-specific methods for new code
     */
    @Deprecated
    @Nullable
    public MSensorData getCurrentSensor() {
        if (!started || sensorData.isEmpty()) return null;

        // Calculate elapsed time since start
        final long elapsed = System.currentTimeMillis() - systemStartTime;
        final long trackStartTime = sensorData.get(0).millis;
        final long targetTime = trackStartTime + elapsed;

        // Advance index to match current time
        while (currentIndex < sensorData.size() - 1) {
            MSensorData next = sensorData.get(currentIndex + 1);
            if (next.millis > targetTime) {
                break;
            }
            currentIndex++;
        }

        // Return current sensor data
        if (currentIndex < sensorData.size()) {
            return sensorData.get(currentIndex);
        }

        return null;
    }

    /**
     * Get all loaded sensor data (legacy combined format)
     * @deprecated Use getSensorDataSet() for new code
     */
    @Deprecated
    @NonNull
    public List<MSensorData> getAllSensorData() {
        return sensorData;
    }

    /**
     * Check if sensor data is available
     */
    @Override
    public boolean hasSensorData() {
        return !sensorDataSet.isEmpty() || !sensorData.isEmpty();
    }
    
    /**
     * Check if specific sensor type has data
     */
    @Override
    public boolean hasImuData() {
        return !sensorDataSet.imuData.isEmpty();
    }
    
    @Override
    public boolean hasMagData() {
        return !sensorDataSet.magData.isEmpty();
    }
    
    @Override
    public boolean hasBaroData() {
        return !sensorDataSet.baroData.isEmpty();
    }
    
    @Override
    public boolean hasHumidityData() {
        return !sensorDataSet.humidityData.isEmpty();
    }
    
    @NonNull
    @Override
    public String getStatusSummary() {
        if (!started) {
            return "MockSensor: Not started";
        }
        if (sensorDataSet.isEmpty() && sensorData.isEmpty()) {
            return "MockSensor: No data loaded";
        }
        return String.format("MockSensor: IMU=%d MAG=%d BARO=%d HUM=%d",
                sensorDataSet.imuData.size(),
                sensorDataSet.magData.size(),
                sensorDataSet.baroData.size(),
                sensorDataSet.humidityData.size());
    }

    /**
     * Get the time range of available sensor data
     */
    public long[] getTimeRange() {
        long[] range = sensorDataSet.getTimeRange();
        if (range != null) {
            return range;
        }
        // Fallback to legacy data
        if (sensorData.isEmpty()) {
            return new long[]{0, 0};
        }
        return new long[]{
                sensorData.get(0).millis,
                sensorData.get(sensorData.size() - 1).millis
        };
    }
}
