package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.VROptions;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.tracks.TrackFileReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MockLocationProvider extends LocationProvider {
    private static final String TAG = "MockLocationProvider";

    public static long systemStartTime = System.currentTimeMillis();
    boolean started = false;
    private int generation = 0; // Incremented on each start to detect stale threads

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
     * Start location updates
     *
     * @param context The Application context
     */
    @Override
    public void start(@NonNull Context context) throws SecurityException {
        Log.i(TAG, "Starting mock location service");
        generation++;
        final int myGeneration = generation;
        started = true;
        // Load track from csv
        List<MLocation> all = loadData(context);
//        all = all.subList(0, 100);

        final long trackStartTime = all.get(0).millis;
        // Start emitting updates
        systemStartTime = System.currentTimeMillis();
        // Time offset to make first fix "now"
        final long timeDelta = systemStartTime - trackStartTime;

        new Thread(() -> {
            for (MLocation loc : all) {
                if (!started || generation != myGeneration) break;
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
                loc.millis = loc.millis + timeDelta - phoneSkew;
                updateLocation(loc);
            }
            if (generation != myGeneration) {
                Log.i(TAG, "Mock location thread superseded by newer generation");
            } else {
                Log.i(TAG, "Finished emitting mock locations");
            }
        }).start();
    }

    public static List<MLocation> loadData(Context context) {
        // Get filename dynamically from current VROptions
        final String filename = VROptions.current.mockTrack;
        if (filename == null) {
            Log.e(TAG, "No mock track configured");
            return List.of();
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open(filename), StandardCharsets.UTF_8))) {
            return TrackFileReader.parse(br);
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
