package com.platypii.baselinexr.location;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shared timeline for synchronized playback of GPS and sensor data.
 * Both TRACK.CSV and SENSOR.CSV (after $TIME conversion) use GPS epoch timestamps,
 * so they share the same timeDelta offset to phone time.
 *
 * This is for REPLAY MODE ONLY. Live mode uses real-time timestamps directly.
 *
 * Either GPS or Sensor data can initialize the timeline (whichever loads first),
 * allowing for scenarios where only one data source is available.
 * 
 * Supports:
 * - Play/Pause/Resume
 * - Seek to position
 * - Playback speed control
 */
public class PlaybackTimeline {
    private static final String TAG = "PlaybackTimeline";

    // Singleton instance
    private static final PlaybackTimeline instance = new PlaybackTimeline();

    // The first GPS epoch timestamp from the data (either GPS track or sensor $TIME sync)
    private long trackStartTimeGps = 0;
    
    // The last GPS epoch timestamp (end of track)
    private long trackEndTimeGps = 0;

    // Phone time when playback started
    private long systemStartTime = 0;

    // Offset to convert GPS epoch → phone time: phoneTime = gpsEpoch + timeDelta
    private long timeDelta = 0;

    // Whether the timeline has been initialized
    private volatile boolean ready = false;
    
    // Playback state
    private volatile boolean playing = true;
    private volatile float playbackSpeed = 1.0f;
    
    // Pause tracking: when paused, we freeze elapsed time
    private long pausedElapsedMs = 0;
    private long pauseStartTime = 0;

    // Generation counter - incremented on each reset to detect stale threads
    private volatile int generation = 0;

    // Lock for thread-safe initialization
    private final Object lock = new Object();

    // Source of timeline initialization (for debugging)
    private String initSource = null;
    
    // Listener for playback state changes
    public interface PlaybackListener {
        void onPlaybackStateChanged(boolean playing);
        void onSeek(long newPositionMs);
        void onSpeedChanged(float speed);
    }
    @Nullable
    private PlaybackListener listener = null;

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
     * Get elapsed time since playback started (accounting for pause and speed).
     * Useful for pacing playback.
     */
    public long getElapsedSinceStart() {
        if (!playing) return pausedElapsedMs;
        long realElapsed = System.currentTimeMillis() - systemStartTime;
        return (long)(realElapsed * playbackSpeed);
    }

    /**
     * Get elapsed time in track time since track start.
     * Useful for knowing current position in the data.
     */
    public long getTrackElapsed() {
        return getElapsedSinceStart();
    }
    
    // =========================================================================
    // Playback Control Methods
    // =========================================================================
    
    /**
     * Set the track end time (for calculating duration).
     */
    public void setTrackEndTime(long endTimeGps) {
        this.trackEndTimeGps = endTimeGps;
    }

    /**
     * Get the track end time in GPS epoch milliseconds.
     */
    public long getTrackEndTimeGps() {
        return trackEndTimeGps;
    }

    /**
     * Get total track duration in milliseconds.
     */
    public long getTrackDuration() {
        return trackEndTimeGps - trackStartTimeGps;
    }

    /**
     * Get current playback position in milliseconds from track start.
     */
    public long getPlaybackPosition() {
        if (!ready) return 0;
        return getElapsedSinceStart();
    }

    /**
     * Check if playback is currently playing (not paused).
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * Pause playback.
     */
    public void pause() {
        synchronized (lock) {
            if (playing) {
                playing = false;
                pausedElapsedMs = getElapsedSinceStart();
                pauseStartTime = System.currentTimeMillis();
                Log.i(TAG, "Playback paused at " + pausedElapsedMs + "ms");
                if (listener != null) listener.onPlaybackStateChanged(false);
            }
        }
    }

    /**
     * Resume playback.
     */
    public void resume() {
        synchronized (lock) {
            if (!playing) {
                // Adjust systemStartTime to account for pause duration
                long pauseDuration = System.currentTimeMillis() - pauseStartTime;
                systemStartTime += pauseDuration;
                playing = true;
                Log.i(TAG, "Playback resumed from " + pausedElapsedMs + "ms");
                if (listener != null) listener.onPlaybackStateChanged(true);
            }
        }
    }

    /**
     * Toggle play/pause state.
     */
    public void togglePlayPause() {
        if (playing) {
            pause();
        } else {
            resume();
        }
    }

    /**
     * Seek to a specific position in the track.
     * @param positionMs Position in milliseconds from track start
     */
    public void seekTo(long positionMs) {
        synchronized (lock) {
            if (!ready) return;
            
            long duration = getTrackDuration();
            positionMs = Math.max(0, Math.min(positionMs, duration));
            
            // Reset the timeline offset to simulate starting at this position
            long now = System.currentTimeMillis();
            systemStartTime = now - (long)(positionMs / playbackSpeed);
            timeDelta = systemStartTime - trackStartTimeGps;
            pausedElapsedMs = positionMs;
            
            // Increment generation to signal providers to restart
            generation++;
            
            Log.i(TAG, "Seeked to " + positionMs + "ms, generation=" + generation);
            if (listener != null) listener.onSeek(positionMs);
        }
    }

    /**
     * Set playback speed.
     * @param speed Playback speed multiplier (1.0 = normal, 2.0 = 2x, 0.5 = half speed)
     */
    public void setPlaybackSpeed(float speed) {
        synchronized (lock) {
            if (speed <= 0) speed = 1.0f;
            long currentPos = getPlaybackPosition();
            playbackSpeed = speed;
            // Recalculate systemStartTime to maintain current position
            long now = System.currentTimeMillis();
            systemStartTime = now - (long)(currentPos / playbackSpeed);
            Log.i(TAG, "Playback speed set to " + speed + "x");
            if (listener != null) listener.onSpeedChanged(speed);
        }
    }

    /**
     * Get current playback speed.
     */
    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    /**
     * Set playback listener.
     */
    public void setListener(@Nullable PlaybackListener listener) {
        this.listener = listener;
    }

    /**
     * Restart playback from the beginning.
     */
    public void restart() {
        seekTo(0);
        resume();
    }

    /**
     * Reset the timeline. Call when switching tracks or stopping playback.
     */
    public void reset() {
        synchronized (lock) {
            generation++;
            ready = false;
            trackStartTimeGps = 0;
            trackEndTimeGps = 0;
            systemStartTime = 0;
            timeDelta = 0;
            initSource = null;
            playing = true;
            playbackSpeed = 1.0f;
            pausedElapsedMs = 0;
            pauseStartTime = 0;
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
