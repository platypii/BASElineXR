package com.platypii.baselinexr.location;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Shared timeline for synchronized playback of GPS and sensor data.
 * Both TRACK.CSV and SENSOR.CSV (after $TIME conversion) use GPS epoch timestamps,
 * so they share the same timeDelta offset to phone time.
 *
 * This is for REPLAY MODE ONLY. Live mode uses real-time timestamps directly.
 *
 * Either GPS or Sensor data can initialize the timeline (whichever loads first),
 * allowing for scenarios where only one data source is available.
 */
public class PlaybackTimeline {
    private static final String TAG = "PlaybackTimeline";

    // Singleton instance
    private static final PlaybackTimeline instance = new PlaybackTimeline();

    // The first GPS epoch timestamp from the data (either GPS track or sensor $TIME sync)
    private long trackStartTimeGps = 0;

    // Phone time when playback started
    private long systemStartTime = 0;

    // Offset to convert GPS epoch → phone time: phoneTime = gpsEpoch + timeDelta
    private long timeDelta = 0;

    // Whether the timeline has been initialized
    private volatile boolean ready = false;

    // Generation counter - incremented on each reset to detect stale threads
    private volatile int generation = 0;

    // Lock for thread-safe initialization
    private final Object lock = new Object();

    // Source of timeline initialization (for debugging)
    private String initSource = null;

    private PlaybackTimeline() {
    }

    @NonNull
    public static PlaybackTimeline getInstance() {
        return instance;
    }

    /**
     * Initialize the timeline with the first GPS epoch timestamp.
     * Can be called by either MockLocationProvider or MockSensorProvider.
     * Only the first call takes effect; subsequent calls are ignored.
     *
     * @param firstGpsEpochMillis First timestamp in GPS epoch milliseconds
     * @param source              Name of the source (for debugging)
     */
    public void init(long firstGpsEpochMillis, @NonNull String source) {
        synchronized (lock) {
            if (ready) {
                Log.d(TAG, "Timeline already initialized by " + initSource + ", ignoring init from " + source);
                return;
            }

            trackStartTimeGps = firstGpsEpochMillis;
            systemStartTime = System.currentTimeMillis();
            timeDelta = systemStartTime - trackStartTimeGps;
            initSource = source;
            ready = true;

            Log.i(TAG, "Timeline initialized by " + source +
                    ": trackStart=" + trackStartTimeGps +
                    ", systemStart=" + systemStartTime +
                    ", timeDelta=" + timeDelta);

            // Wake up any threads waiting for initialization
            lock.notifyAll();
        }
    }

    /**
     * Wait for the timeline to be initialized, with timeout.
     *
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if timeline is ready, false if timeout occurred
     */
    public boolean waitForReady(long timeoutMs) {
        synchronized (lock) {
            if (ready) return true;

            try {
                lock.wait(timeoutMs);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for timeline");
                Thread.currentThread().interrupt();
            }

            return ready;
        }
    }

    /**
     * Check if timeline is ready without blocking.
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Get the first GPS epoch timestamp from the data.
     * Only valid after isReady() returns true.
     */
    public long getTrackStartTimeGps() {
        return trackStartTimeGps;
    }

    /**
     * Get the phone time when playback started.
     * Only valid after isReady() returns true.
     */
    public long getSystemStartTime() {
        return systemStartTime;
    }

    /**
     * Get the offset to convert GPS epoch → phone time.
     * Usage: phoneTimeMillis = gpsEpochMillis + timeDelta
     * Only valid after isReady() returns true.
     */
    public long getTimeDelta() {
        return timeDelta;
    }

    /**
     * Convert a GPS epoch timestamp to current phone time.
     * Only valid after isReady() returns true.
     */
    public long toPhoneTime(long gpsEpochMillis) {
        return gpsEpochMillis + timeDelta;
    }

    /**
     * Get elapsed time since playback started.
     * Useful for pacing playback.
     */
    public long getElapsedSinceStart() {
        return System.currentTimeMillis() - systemStartTime;
    }

    /**
     * Get elapsed time in track time since track start.
     * Useful for knowing current position in the data.
     */
    public long getTrackElapsed() {
        return System.currentTimeMillis() - systemStartTime;
    }

    /**
     * Reset the timeline. Call when switching tracks or stopping playback.
     */
    public void reset() {
        synchronized (lock) {
            generation++;
            ready = false;
            trackStartTimeGps = 0;
            systemStartTime = 0;
            timeDelta = 0;
            initSource = null;
            Log.i(TAG, "Timeline reset, generation=" + generation);
        }
    }

    /**
     * Get the current generation counter.
     * Threads can capture this value and check if it changed to detect timeline resets.
     */
    public int getGeneration() {
        return generation;
    }

    /**
     * Get debug info about the timeline state.
     */
    @NonNull
    public String getDebugInfo() {
        if (!ready) {
            return "PlaybackTimeline: not ready";
        }
        return "PlaybackTimeline: ready, source=" + initSource +
                ", trackStart=" + trackStartTimeGps +
                ", systemStart=" + systemStartTime +
                ", timeDelta=" + timeDelta +
                ", elapsed=" + getElapsedSinceStart() + "ms";
    }
}
