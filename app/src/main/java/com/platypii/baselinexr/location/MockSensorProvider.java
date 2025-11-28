package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.VROptions;
import com.platypii.baselinexr.measurements.MSensorData;
import com.platypii.baselinexr.tracks.FlySightDataLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides sensor data (compass, IMU, barometer) synchronized with GPS data.
 * Supports mock/replay mode using FlySight SENSOR.CSV files.
 */
public class MockSensorProvider {
    private static final String TAG = "MockSensorProvider";

    // Loaded sensor data
    @NonNull
    private List<MSensorData> sensorData = new ArrayList<>();

    // Current playback state
    private int currentIndex = 0;
    private boolean started = false;

    // Timing
    public static long systemStartTime = System.currentTimeMillis();

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

            sensorData = data.sensorData;
            Log.i(TAG, String.format("Loaded %d sensor measurements", sensorData.size()));

        } catch (Exception e) {
            Log.e(TAG, "Error loading sensor data", e);
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

        // Apply time delta to sensor data (same adjustment as GPS data in MockLocationProvider)
        if (!sensorData.isEmpty()) {
            final long sensorOriginalStart = sensorData.get(0).millis;
            final long sensorOriginalEnd = sensorData.get(sensorData.size() - 1).millis;

            // Calculate offset between sensor data and GPS data in the original recording
            final long sensorGpsOffset = sensorOriginalStart - trackStartTime;
            Log.i(TAG, String.format("TIMESYNC: Sensor data starts %d ms after GPS track in original recording", sensorGpsOffset));

            // Use GPS track start time as reference for time delta
            // This ensures GPS and sensor data are synchronized in the replay
            final long referenceTime = trackStartTime > 0 ? trackStartTime : sensorOriginalStart;
            final long timeDelta = systemStartTime - referenceTime;
            Log.i(TAG, String.format("TIMESYNC: systemStartTime=%d, trackStartTime=%d, timeDelta=%d ms",
                    systemStartTime, trackStartTime, timeDelta));
            Log.i(TAG, String.format("TIMESYNC: Original sensor range: [%d, %d], span=%ds",
                    sensorOriginalStart, sensorOriginalEnd, (sensorOriginalEnd - sensorOriginalStart) / 1000));

            // Adjust all sensor timestamps: subtract offset to align with GPS start, then add timeDelta
            // This removes the original recording delay and synchronizes both data sources to "now"
            for (MSensorData sensor : sensorData) {
                sensor.millis = sensor.millis - sensorGpsOffset + timeDelta;
            }

            final long sensorAdjustedStart = sensorData.get(0).millis;
            final long sensorAdjustedEnd = sensorData.get(sensorData.size() - 1).millis;
            Log.i(TAG, String.format("TIMESYNC: Adjusted sensor range: [%d, %d], span=%ds (offset removed, aligned with GPS)",
                    sensorAdjustedStart, sensorAdjustedEnd, (sensorAdjustedEnd - sensorAdjustedStart) / 1000));
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
        sensorData = new ArrayList<>();
    }

    /**
     * Get the sensor measurement closest to the given GPS time
     * Returns the most recent sensor reading at or before the requested time
     *
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent sensor data at or before the requested time, or null if no data available
     */
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
     */
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
     * Get all loaded sensor data (for analysis/visualization)
     */
    @NonNull
    public List<MSensorData> getAllSensorData() {
        return sensorData;
    }

    /**
     * Check if sensor data is available
     */
    public boolean hasSensorData() {
        return !sensorData.isEmpty();
    }

    /**
     * Get the time range of available sensor data
     */
    public long[] getTimeRange() {
        if (sensorData.isEmpty()) {
            return new long[]{0, 0};
        }
        return new long[]{
                sensorData.get(0).millis,
                sensorData.get(sensorData.size() - 1).millis
        };
    }
}
