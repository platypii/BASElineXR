package com.platypii.baselinexr.tracks;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.MSensorData;

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

        @NonNull
        public final List<MSensorData> sensorData;

        public FlySightData(@NonNull List<MLocation> trackData, @NonNull List<MSensorData> sensorData) {
            this.trackData = trackData;
            this.sensorData = sensorData;
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

        // Load sensor data
        final String sensorPath = folderPath + "/SENSOR.CSV";
        List<MSensorData> sensorData = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(context.getAssets().open(sensorPath), StandardCharsets.UTF_8))) {
            sensorData = SensorCSVParser.parse(br);
            Log.i(TAG, String.format("Loaded %d sensor measurements from %s", sensorData.size(), sensorPath));
        } catch (IOException e) {
            Log.e(TAG, "Error reading sensor data from " + sensorPath, e);
        }

        // Apply time windowing if requested
        if (startSec != null || endSec != null) {
            trackData = applyTimeWindow(trackData, startSec, endSec);
            sensorData = applyTimeWindowSensor(sensorData, startSec, endSec);
            Log.i(TAG, String.format("After time windowing: %d GPS, %d sensor measurements",
                    trackData.size(), sensorData.size()));
        }

        return new FlySightData(trackData, sensorData);
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
     * Apply time window to sensor data
     */
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
