package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.MockTrackList;
import com.platypii.baselinexr.MockTrackOptions;
import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.tracks.SensorCSVParser;
import com.platypii.baselinexr.tracks.SensorDataSet;
import com.platypii.baselinexr.util.PubSub;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock sensor provider that replays sensor data from SENSOR.CSV files.
 * Follows the same push-based pattern as MockLocationProvider.
 * Uses shared PlaybackTimeline for synchronized playback with GPS data.
 */
public class MockSensorProvider {
    private static final String TAG = "MockSensorProvider";
    private static final String PLAYBACK_DEBUG = "PLAYBACK_DEBUG";

    // Use shared PlaybackTimeline for synchronized playback
    private final PlaybackTimeline timeline = PlaybackTimeline.getInstance();

    // PubSub for sensor updates
    public final PubSub<MImuData> imuUpdates = new PubSub<>();
    public final PubSub<MMagData> magUpdates = new PubSub<>();
    public final PubSub<MBaroData> baroUpdates = new PubSub<>();

    boolean started = false;
    private int generation = 0;
    private volatile Thread playbackThread = null; // Track thread for interruption on stop

    // Loaded sensor data
    @Nullable
    private SensorDataSet sensorData;

    // Rate tracking
    private long imuCount = 0;
    private long magCount = 0;
    private long baroCount = 0;

    // Timeout for waiting on GPS timeline (ms)
    private static final long TIMELINE_WAIT_TIMEOUT = 2000;

    @NonNull
    public String providerName() {
        return TAG;
    }

    @NonNull
    public String dataSource() {
        return "MockSensor";
    }

    /**
     * Start sensor updates.
     * Uses shared PlaybackTimeline for timing - either waits for GPS to initialize it,
     * or initializes it ourselves if we have sensor data with $TIME sync but no GPS.
     */
    public void start(@NonNull Context context) {
        startInternal(context, true);
    }

    /**
     * Restart from current timeline position (for seek operations).
     * Does not wait for or reset the timeline.
     */
    public void restartFromCurrentPosition(@NonNull Context context) {
        startInternal(context, false);
    }

    private void startInternal(@NonNull Context context, boolean waitForTimeline) {
        Log.i(TAG, "Starting mock sensor service (waitForTimeline=" + waitForTimeline + ")");
        generation++;
        final int myGeneration = generation;
        started = true;

        // Load sensor data from CSV
        sensorData = loadData(context);
        if (sensorData == null) {
            Log.w(TAG, "No sensor data available");
            return;
        }

        // Merge all sensor data into a single timeline sorted by timestamp
        List<TimestampedData> dataTimeline = buildTimeline(sensorData);
        if (dataTimeline.isEmpty()) {
            Log.w(TAG, "No sensor data to replay");
            return;
        }

        // Check if we have time sync (required for GPS epoch alignment)
        if (!sensorData.hasTimeSync()) {
            Log.w(TAG, "No $TIME sync in sensor data, cannot align with GPS timeline");
            return;
        }

        final long sensorStartTime = dataTimeline.get(0).millis;

        // Wait for GPS timeline to be ready only on fresh start
        if (waitForTimeline && !timeline.isReady()) {
            Log.i(TAG, "Waiting for PlaybackTimeline to be initialized...");
            boolean ready = timeline.waitForReady(TIMELINE_WAIT_TIMEOUT);

            if (!ready) {
                // GPS timeline not available - initialize from sensor data
                // This supports sensor-only playback scenarios
                Log.i(TAG, "No GPS timeline, initializing from sensor data");
                timeline.init(sensorStartTime, "MockSensorProvider");
            }
        }

        // Use timeline's track start time for elapsed calculations
        // This ensures sensor data is synchronized with GPS data
        final long trackStartTime = timeline.getTrackStartTimeGps();

        // Update track end time if sensor data extends beyond GPS data (only on fresh start)
        if (waitForTimeline) {
            final long sensorEndTime = dataTimeline.get(dataTimeline.size() - 1).millis;
            if (sensorEndTime > timeline.getTrackEndTimeGps()) {
                timeline.setTrackEndTime(sensorEndTime);
            }
        }

        // Get current playback position to skip data before this point
        final long seekPositionMs = timeline.getPlaybackPosition();

        Log.i(TAG, "Sensor playback: " + dataTimeline.size() + " samples, " +
                "sensorStart=" + sensorStartTime + ", trackStart=" + trackStartTime + 
                ", seekPosition=" + seekPositionMs + ", " + timeline.getDebugInfo());
        
        // Log offset between sensor and GPS start times for debugging
        final long startOffset = sensorStartTime - trackStartTime;
        Log.i(PLAYBACK_DEBUG, "Sensor/GPS start offset: " + startOffset + "ms (sensor=" + 
                sensorStartTime + ", gps=" + trackStartTime + ")");

        // Capture timeline generation to detect if timeline is reset while we're running
        final int timelineGeneration = timeline.getGeneration();

        playbackThread = new Thread(() -> {
            long lastLogTime = 0;
            int samplesEmitted = 0;
            
            for (TimestampedData data : dataTimeline) {
                // Check if we should stop: provider stopped, generation changed, or timeline was reset
                if (!started || generation != myGeneration || timeline.getGeneration() != timelineGeneration) break;

                // Skip data points before seek position
                final long dataElapsed = data.millis - trackStartTime;
                if (dataElapsed < seekPositionMs) {
                    continue;  // Skip this data point, it's before the seek position
                }

                final long elapsed = timeline.getElapsedSinceStart();
                final long sleepTime = dataElapsed - elapsed;
                if (sleepTime > 0) {
                    // Log unusually large sleep times
                    if (sleepTime > 500) {
                        Log.w(PLAYBACK_DEBUG, "Large sensor sleep: " + sleepTime + "ms, dataElapsed=" + 
                                dataElapsed + ", elapsed=" + elapsed + ", samples=" + samplesEmitted);
                    }
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        // Thread was interrupted - exit cleanly
                        Log.i(TAG, "Mock sensor thread interrupted, exiting");
                        break;
                    }
                }
                
                samplesEmitted++;

                // Shift timestamp to current phone time using shared timeline
                long shiftedMillis = timeline.toPhoneTime(data.millis);

                // Emit the appropriate update asynchronously (like GPS does)
                // This prevents subscriber processing from slowing down the playback loop
                if (data.imu != null) {
                    MImuData shifted = new MImuData(shiftedMillis,
                            data.imu.gyroX, data.imu.gyroY, data.imu.gyroZ,
                            data.imu.accelX, data.imu.accelY, data.imu.accelZ,
                            data.imu.temperature);
                    imuUpdates.postAsync(shifted);
                    imuCount++;
                } else if (data.mag != null) {
                    MMagData shifted = new MMagData(shiftedMillis,
                            data.mag.magX, data.mag.magY, data.mag.magZ,
                            data.mag.temperature);
                    magUpdates.postAsync(shifted);
                    magCount++;
                } else if (data.baro != null) {
                    MBaroData shifted = new MBaroData(shiftedMillis,
                            data.baro.pressure, data.baro.temperature);
                    baroUpdates.postAsync(shifted);
                    baroCount++;
                }
            }

            if (generation != myGeneration) {
                Log.i(TAG, "Mock sensor thread superseded by newer generation");
            } else if (Thread.currentThread().isInterrupted()) {
                Log.i(TAG, "Mock sensor thread was interrupted");
            } else {
                Log.i(TAG, "Finished emitting mock sensor data: " +
                        imuCount + " IMU, " + magCount + " MAG, " + baroCount + " BARO");
            }
            playbackThread = null;
        }, "MockSensor-Playback");
        playbackThread.start();
    }

    /**
     * Build a merged timeline of all sensor data sorted by timestamp
     */
    private List<TimestampedData> buildTimeline(SensorDataSet data) {
        List<TimestampedData> timeline = new ArrayList<>();

        for (MImuData imu : data.imuData) {
            timeline.add(new TimestampedData(imu.millis, imu, null, null));
        }
        for (MMagData mag : data.magData) {
            timeline.add(new TimestampedData(mag.millis, null, mag, null));
        }
        for (MBaroData baro : data.baroData) {
            timeline.add(new TimestampedData(baro.millis, null, null, baro));
        }

        // Sort by timestamp
        timeline.sort((a, b) -> Long.compare(a.millis, b.millis));

        return timeline;
    }

    @Nullable
    public static SensorDataSet loadData(Context context) {
        // Get path dynamically from current MockTrackOptions
        final String path = MockTrackOptions.current;
        if (path == null) {
            Log.e(TAG, "No mock track configured");
            return null;
        }

        // Get TrackInfo to determine if this is a folder
        MockTrackList.TrackInfo trackInfo = MockTrackList.getTrackInfo(path);
        if (trackInfo == null || !trackInfo.isFolder) {
            Log.i(TAG, "Track is not a folder, no sensor data available");
            return null;
        }

        String sensorPath = trackInfo.getSensorPath();
        if (sensorPath == null) {
            Log.i(TAG, "No sensor path for track");
            return null;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                context.getAssets().open(sensorPath), StandardCharsets.UTF_8))) {
            return SensorCSVParser.parse(br);
        } catch (IOException e) {
            Log.e(TAG, "Error reading sensor data from " + sensorPath, e);
            return null;
        }
    }

    public void stop() {
        // Stop thread - interrupt if sleeping
        started = false;
        Thread thread = playbackThread;
        if (thread != null) {
            thread.interrupt();
        }
        imuCount = 0;
        magCount = 0;
        baroCount = 0;
    }

    /**
     * Check if sensor data is available for the current track
     */
    public boolean hasSensorData() {
        return sensorData != null && sensorData.hasTimeSync();
    }

    /**
     * Get the loaded sensor data (for inspection)
     */
    @Nullable
    public SensorDataSet getSensorData() {
        return sensorData;
    }

    /**
     * Internal class to hold timestamped sensor data for merged timeline
     */
    private record TimestampedData(long millis,
                                   @Nullable MImuData imu,
                                   @Nullable MMagData mag,
                                   @Nullable MBaroData baro) {
    }
}
