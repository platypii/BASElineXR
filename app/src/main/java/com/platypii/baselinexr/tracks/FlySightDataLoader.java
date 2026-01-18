package com.platypii.baselinexr.tracks;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumidityData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MSensorData;
import com.platypii.baselinexr.measurements.Measurement;
import com.platypii.baselinexr.measurements.SensorDataSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads synchronized GPS and sensor data from a FlySight folder containing TRACK.CSV and SENSOR.CSV
 */
public class FlySightDataLoader {
    private static final String TAG = "FlySightDataLoader";

    /**
     * Combined GPS + Sensor data package
     */
    public static class FlySightData {
        @NonNull
        public final List<MLocation> trackData;

        /** 
         * Separate sensor data by type (new structure)
         */
        @NonNull
        public final SensorDataSet sensorDataSet;
        
        /**
         * Legacy combined sensor data (for backwards compatibility)
         * @deprecated Use sensorDataSet instead
         */
        @Deprecated
        @NonNull
        public final List<MSensorData> sensorData;

        public FlySightData(@NonNull List<MLocation> trackData, @NonNull SensorDataSet sensorDataSet) {
            this.trackData = trackData;
            this.sensorDataSet = sensorDataSet;
            this.sensorData = sensorDataSet.combinedData;
        }
        
        /**
         * Legacy constructor for backwards compatibility
         * @deprecated Use constructor with SensorDataSet
         */
        @Deprecated
        public FlySightData(@NonNull List<MLocation> trackData, @NonNull List<MSensorData> sensorData) {
            this.trackData = trackData;
            this.sensorData = sensorData;
            this.sensorDataSet = new SensorDataSet(
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 
                    new ArrayList<>(), new ArrayList<>(), sensorData
            );
        }
    }

    /**
     * Load both TRACK.CSV and SENSOR.CSV from a folder in assets
     *
     * @param context Android context for asset access
     * @param folderPath Path to folder containing TRACK.CSV and SENSOR.CSV (e.g., "squaw072925/14-59-30")
     * @param startSec Optional start time in seconds (null = from beginning)
     * @param endSec Optional end time in seconds (null = until end)
     * @return FlySightData containing synchronized GPS and sensor measurements
     */
    @NonNull
    public static FlySightData loadFromAssets(@NonNull Context context, @NonNull String folderPath,
                                              @Nullable Integer startSec, @Nullable Integer endSec) {
        Log.i(TAG, String.format("Loading FlySight data from %s (start=%s, end=%s)",
                folderPath, startSec, endSec));

        // Debug: List available assets in the folder
        try {
            String[] files = context.getAssets().list(folderPath);
            if (files != null && files.length > 0) {
                Log.i(TAG, "Found " + files.length + " files in " + folderPath + ": " + String.join(", ", files));
            } else {
                Log.e(TAG, "Folder " + folderPath + " is empty or doesn't exist");
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot list assets in " + folderPath, e);
        }

        // Load track data
        final String trackPath = folderPath + "/TRACK.CSV";
        List<MLocation> trackData = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(context.getAssets().open(trackPath), StandardCharsets.UTF_8))) {
            trackData = TrackFileReader.parse(br);
            Log.i(TAG, String.format("Loaded %d GPS measurements from %s", trackData.size(), trackPath));
        } catch (IOException e) {
            Log.e(TAG, "Error reading track data from " + trackPath, e);
        }

        // Load sensor data using new parser that separates sensor types
        final String sensorPath = folderPath + "/SENSOR.CSV";
        SensorDataSet sensorDataSet = new SensorDataSet();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(context.getAssets().open(sensorPath), StandardCharsets.UTF_8))) {
            sensorDataSet = SensorCSVParser.parseToDataSet(br);
            Log.i(TAG, String.format("Loaded sensor data from %s: %s", sensorPath, sensorDataSet));
        } catch (IOException e) {
            Log.e(TAG, "Error reading sensor data from " + sensorPath, e);
        }

        // Apply time windowing if requested
        if (startSec != null || endSec != null) {
            trackData = applyTimeWindow(trackData, startSec, endSec);
            sensorDataSet = applyTimeWindowSensorDataSet(sensorDataSet, startSec, endSec);
            Log.i(TAG, String.format("After time windowing: %d GPS, %s",
                    trackData.size(), sensorDataSet));
        }

        return new FlySightData(trackData, sensorDataSet);
    }

    /**
     * Apply time window to GPS track data
     */
    @NonNull
    private static List<MLocation> applyTimeWindow(@NonNull List<MLocation> data,
                                                   @Nullable Integer startSec,
                                                   @Nullable Integer endSec) {
        if (data.isEmpty()) return data;

        final long firstMillis = data.get(0).millis;
        final Long startMillis = startSec != null ? firstMillis + startSec * 1000L : null;
        final Long endMillis = endSec != null ? firstMillis + endSec * 1000L : null;

        final List<MLocation> windowed = new ArrayList<>();
        for (MLocation loc : data) {
            if (startMillis != null && loc.millis < startMillis) continue;
            if (endMillis != null && loc.millis > endMillis) break;
            windowed.add(loc);
        }

        return windowed;
    }

    /**
     * Apply time window to SensorDataSet - filters all sensor lists by time range
     */
    @NonNull
    private static SensorDataSet applyTimeWindowSensorDataSet(@NonNull SensorDataSet data,
                                                               @Nullable Integer startSec,
                                                               @Nullable Integer endSec) {
        // Find earliest timestamp across all sensor types for reference
        long[] timeRange = data.getTimeRange();
        if (timeRange == null) return data;
        
        final long firstMillis = timeRange[0];
        final Long startMillis = startSec != null ? firstMillis + startSec * 1000L : null;
        final Long endMillis = endSec != null ? firstMillis + endSec * 1000L : null;

        return new SensorDataSet(
                filterByTime(data.imuData, startMillis, endMillis),
                filterByTime(data.magData, startMillis, endMillis),
                filterByTime(data.baroData, startMillis, endMillis),
                filterByTime(data.humidityData, startMillis, endMillis),
                data.timeSyncData, // Don't filter time sync entries
                filterByTime(data.combinedData, startMillis, endMillis)
        );
    }

    /**
     * Generic time filter for any Measurement list
     */
    @NonNull
    private static <T extends Measurement> List<T> filterByTime(@NonNull List<T> data,
                                                                 @Nullable Long startMillis,
                                                                 @Nullable Long endMillis) {
        if (data.isEmpty()) return data;
        
        final List<T> windowed = new ArrayList<>();
        for (T item : data) {
            if (startMillis != null && item.millis < startMillis) continue;
            if (endMillis != null && item.millis > endMillis) break;
            windowed.add(item);
        }
        return windowed;
    }

    /**
     * Apply time window to legacy sensor data (for backwards compatibility)
     * @deprecated Use applyTimeWindowSensorDataSet instead
     */
    @Deprecated
    @NonNull
    private static List<MSensorData> applyTimeWindowSensor(@NonNull List<MSensorData> data,
                                                           @Nullable Integer startSec,
                                                           @Nullable Integer endSec) {
        if (data.isEmpty()) return data;

        final long firstMillis = data.get(0).millis;
        final Long startMillis = startSec != null ? firstMillis + startSec * 1000L : null;
        final Long endMillis = endSec != null ? firstMillis + endSec * 1000L : null;

        final List<MSensorData> windowed = new ArrayList<>();
        for (MSensorData sensor : data) {
            if (startMillis != null && sensor.millis < startMillis) continue;
            if (endMillis != null && sensor.millis > endMillis) break;
            windowed.add(sensor);
        }

        return windowed;
    }
}
