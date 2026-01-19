package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.MockTrackList;
import com.platypii.baselinexr.MockTrackOptions;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.tracks.TrackFileReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MockLocationProvider extends LocationProvider {
    private static final String TAG = "MockLocationProvider";

    // Use shared PlaybackTimeline for synchronized playback with sensor data
    private final PlaybackTimeline timeline = PlaybackTimeline.getInstance();

    boolean started = false;
    private int generation = 0; // Incremented on each start to detect stale threads
    private volatile Thread playbackThread = null; // Track thread for interruption on stop

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

        // Clear previous location to avoid non-monotonic timestamp errors when switching tracks
        lastLoc = null;

        // Reset timeline for new playback session
        timeline.reset();

        // Load track from csv
        List<MLocation> all = loadData(context);
        if (all.isEmpty()) {
            Log.e(TAG, "No GPS data loaded");
            return;
        }

        // Initialize shared timeline with first GPS timestamp
        final long trackStartTime = all.get(0).millis;
        timeline.init(trackStartTime, "MockLocationProvider");

        // Capture timeline generation to detect if timeline is reset while we're running
        final int timelineGeneration = timeline.getGeneration();

        playbackThread = new Thread(() -> {
            for (MLocation loc : all) {
                // Check if we should stop: provider stopped, generation changed, or timeline was reset
                if (!started || generation != myGeneration || timeline.getGeneration() != timelineGeneration) break;

                final long elapsed = timeline.getElapsedSinceStart();
                final long locElapsed = loc.millis - trackStartTime; // Time since first fix
                if (locElapsed > elapsed) {
                    try {
                        Thread.sleep(locElapsed - elapsed);
                    } catch (InterruptedException e) {
                        // Thread was interrupted - exit cleanly
                        Log.i(TAG, "Mock location thread interrupted, exiting");
                        break;
                    }
                }
                // Shift timestamp to current phone time
                loc.millis = timeline.toPhoneTime(loc.millis) - phoneSkew;
                updateLocation(loc);
            }
            if (generation != myGeneration) {
                Log.i(TAG, "Mock location thread superseded by newer generation");
            } else if (Thread.currentThread().isInterrupted()) {
                Log.i(TAG, "Mock location thread was interrupted");
            } else {
                Log.i(TAG, "Finished emitting mock locations");
            }
            playbackThread = null;
        }, "MockLocation-Playback");
        playbackThread.start();
    }

    public static List<MLocation> loadData(Context context) {
        // Get path dynamically from current MockTrackOptions
        final String path = MockTrackOptions.current;
        if (path == null) {
            Log.e(TAG, "No mock track configured");
            return List.of();
        }

        // Get TrackInfo to determine if this is a folder or file
        MockTrackList.TrackInfo trackInfo = MockTrackList.getTrackInfo(path);
        String trackPath = (trackInfo != null) ? trackInfo.getTrackPath() : path;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                context.getAssets().open(trackPath), StandardCharsets.UTF_8))) {
            return TrackFileReader.parse(br);
        } catch (IOException e) {
            Log.e(TAG, "Error reading track data from " + trackPath, e);
            return List.of();
        }
    }

    @Override
    public void stop() {
        // Stop thread - interrupt if sleeping
        started = false;
        Thread thread = playbackThread;
        if (thread != null) {
            thread.interrupt();
        }
        super.stop();
    }
}
