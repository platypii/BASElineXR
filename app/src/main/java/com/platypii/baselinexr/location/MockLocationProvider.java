package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.VROptions;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.tracks.FlySightDataLoader;
import com.platypii.baselinexr.tracks.TrackFileReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MockLocationProvider extends LocationProvider {
    private static final String TAG = "MockLocationProvider";

    public static long systemStartTime = System.currentTimeMillis();
    private boolean started = false;
    private long trackStartTime = 0; // First GPS timestamp for time delta calculation

    // Introduce a fake phone/gps time skew for testing
    private static final long phoneSkew = 0;

    @NonNull
    @Override
    protected String providerName() {
        return TAG;
    }

    @NonNull
    @Override
    protected String dataSource() {
        return "Mock";
    }

    /**
     * Get the first GPS timestamp (track start time) for synchronizing sensor data
     */
    public long getTrackStartTime() {
        return trackStartTime;
    }

    /**
     * Start location updates
     *
     * @param context The Application context
     */
    @Override
    public void start(@NonNull Context context) throws SecurityException {
        Log.i(TAG, "Starting mock location service");
        started = true;
        // Load track from csv
        List<MLocation> all = loadData(context);
//        all = all.subList(0, 100);

        if (all.isEmpty()) {
            Log.e(TAG, "No GPS data loaded, cannot start mock location provider");
            started = false;
            return;
        }

        trackStartTime = all.get(0).millis; // Store for sensor synchronization
        // Start emitting updates
        systemStartTime = System.currentTimeMillis();
        // Time offset to make first fix "now"
        final long timeDelta = systemStartTime - trackStartTime;
        
        Log.i(TAG, String.format("TIMESYNC: GPS provider starting - systemStartTime=%d, trackStartTime=%d, timeDelta=%d ms", 
            systemStartTime, trackStartTime, timeDelta));

        new Thread(() -> {
            Log.i(TAG, String.format("TIMESYNC: GPS thread started, will emit %d locations", all.size()));
            boolean firstUpdate = true;
            for (MLocation loc : all) {
                if (!started) break;
                final long elapsed = System.currentTimeMillis() - systemStartTime;
                final long locElapsed = loc.millis - trackStartTime; // Time since first fix
                if (locElapsed > elapsed) {
                    try {
                        Thread.sleep(locElapsed - elapsed);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Mock location thread interrupted", e);
                    }
                }
                // Update the time
                final long originalTime = loc.millis;
                loc.millis = loc.millis + timeDelta - phoneSkew;
                if (firstUpdate) {
                    Log.i(TAG, String.format("TIMESYNC: First GPS update - original=%d, adjusted=%d (timeDelta=%d, phoneSkew=%d)", 
                        originalTime, loc.millis, timeDelta, phoneSkew));
                    firstUpdate = false;
                }
                updateLocation(loc);
            }
            Log.i(TAG, "Finished emitting mock locations");
        }).start();
    }

    public static List<MLocation> loadData(Context context) {
        // Check if we should load from a FlySight folder (TRACK.CSV + SENSOR.CSV)
        if (VROptions.current.mockSensor != null) {
            try {
                Log.i(TAG, "Loading FlySight data from folder: " + VROptions.current.mockSensor);
                FlySightDataLoader.FlySightData data = FlySightDataLoader.loadFromAssets(
                    context,
                    VROptions.current.mockSensor,
                    VROptions.current.mockTrackStartSec,
                    VROptions.current.mockTrackEndSec
                );
                if (data.trackData.isEmpty()) {
                    Log.e(TAG, "No track data loaded from " + VROptions.current.mockSensor);
                } else {
                    Log.i(TAG, "Successfully loaded " + data.trackData.size() + " GPS points from " + VROptions.current.mockSensor);
                }
                return data.trackData;
            } catch (Exception e) {
                Log.e(TAG, "Error reading track data from FlySight folder " + VROptions.current.mockSensor, e);
                return List.of();
            }
        }
        
        // Fall back to legacy single-file loading
        final String filename = VROptions.current.mockTrack; // Read dynamically
        if (filename == null) {
            Log.e(TAG, "No mock track file specified");
            return List.of();
        }
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(filename), StandardCharsets.UTF_8))) {
            List<MLocation> data = TrackFileReader.parse(br);
            Log.i(TAG, "Successfully loaded " + data.size() + " GPS points from " + filename);
            return data;
        } catch (IOException e) {
            Log.e(TAG, "Error reading track data from " + filename, e);
            return List.of();
        }
    }

    @Override
    public void stop() {
        // Stop thread
        started = false;
        super.stop();
    }
}
