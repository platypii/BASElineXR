package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.tracks.TrackFileReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MockLocationProvider extends LocationProvider {
    private static final String TAG = "MockLocationProvider";

    private long systemStartTime = System.currentTimeMillis();

    @NonNull
    @Override
    protected String providerName() {
        return TAG;
    }

    @NonNull
    @Override
    protected String dataSource() {
        return "MockFlysight";
    }

    /**
     * Start location updates
     *
     * @param context The Application context
     */
    @Override
    public void start(@NonNull Context context) throws SecurityException {
        Log.i(TAG, "Starting mock location service");
        // Load track from eiger.csv
        final List<MLocation> all = loadData(context)
                .subList(0, 10); // WTF TODO

        // Start emitting updates
        systemStartTime = System.currentTimeMillis();

        new Thread(() -> {
            for (MLocation loc : all) {
                final long elapsed = System.currentTimeMillis() - systemStartTime;
                final long locElapsed = loc.millis - all.get(0).millis; // Time since first fix
                if (locElapsed > elapsed) {
                    try {
                        Thread.sleep(locElapsed - elapsed);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Mock location thread interrupted", e);
                    }
                }
                updateLocation(loc);
            }
            Log.i(TAG, "Finished emitting mock locations");
        }).start();
    }

    public static List<MLocation> loadData(Context context) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("eiger.csv"), StandardCharsets.UTF_8))) {
            return TrackFileReader.parse(br);
        } catch (IOException e) {
            Log.e(TAG, "Error reading track data from eiger.csv", e);
            return List.of();
        }
    }

}
