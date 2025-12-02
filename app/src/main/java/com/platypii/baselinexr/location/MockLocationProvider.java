package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.VROptions;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.replay.ReplayManager;
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
    private volatile boolean started = false;
    private volatile boolean paused = false;
    private volatile boolean completed = false;
    private long trackStartTime = 0; // First GPS timestamp for time delta calculation
    private long trackEndTime = 0;   // Last GPS timestamp for duration calculation
    private long pauseElapsedTime = 0;  // Elapsed track time when paused
    private long pauseSystemTime = 0;   // System time when paused
    private long totalPauseDuration = 0; // Cumulative time spent paused (for lastFixDuration adjustment)
    private final Object pauseLock = new Object();
    
    // Seek support
    private volatile int currentIndex = 0;        // Current position in track data
    private volatile long seekTargetTimeMs = -1;  // Target GPS time for pending seek (-1 = no seek pending)
    private final Object seekLock = new Object();
    
    // Track data cache for restart
    private List<MLocation> trackData = null;
    private Context cachedContext = null;
    
    // Thread reference for proper cleanup
    private Thread emitterThread = null;

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
     * Override lastFixDuration to account for time spent paused.
     * Without this, after a 30-second pause, lastFixDuration would return 30000ms
     * even though GPS is only ~200ms behind in track time.
     */
    @Override
    public long lastFixDuration() {
        if (paused) {
            // While paused, time since last fix is frozen at the moment we paused
            return System.currentTimeMillis() - pauseSystemTime;
        }
        // Subtract cumulative pause time from the normal calculation
        long duration = super.lastFixDuration();
        if (duration > 0) {
            duration = Math.max(0, duration - totalPauseDuration);
        }
        return duration;
    }

    /**
     * Get the total time spent paused (for adjusting time-based calculations).
     * Returns the cumulative pause duration if currently playing,
     * or includes current pause duration if currently paused.
     */
    public long getTotalPauseDuration() {
        if (paused) {
            return totalPauseDuration + (System.currentTimeMillis() - pauseSystemTime);
        }
        return totalPauseDuration;
    }

    /**
     * Get the first GPS timestamp (track start time) for synchronizing sensor data
     */
    public long getTrackStartTime() {
        return trackStartTime;
    }
    
    /**
     * Get the track duration in milliseconds
     */
    public long getTrackDuration() {
        if (trackStartTime > 0 && trackEndTime > 0) {
            return trackEndTime - trackStartTime;
        }
        return 0;
    }
    
    /**
     * Pause playback - can resume later
     */
    public void pause() {
        if (started && !paused && !completed) {
            synchronized (pauseLock) {
                paused = true;
                pauseSystemTime = System.currentTimeMillis();
                pauseElapsedTime = pauseSystemTime - systemStartTime;
                Log.i(TAG, "GPS playback paused at elapsed=" + pauseElapsedTime + "ms");
            }
        }
    }
    
    /**
     * Resume from paused position.
     * 
     * TIME SYNC DESIGN:
     * When resuming, we shift systemStartTime forward by the pause duration.
     * This means GPS timestamps (loc.millis + timeDelta) will continue from current
     * wall clock time, keeping them synchronized with getEffectiveCurrentTime().
     * 
     * Example: If paused for 5 seconds at track position 10s:
     * - Before pause: systemStartTime=1000, trackStartTime=0, so loc at 10s -> millis=1010
     * - After resume: systemStartTime=1005, so same loc at 10s -> millis=1015 (matches wall clock)
     */
    public void resume() {
        if (started && paused && !completed) {
            synchronized (pauseLock) {
                // Shift systemStartTime forward so GPS timestamps match wall clock
                long pauseDuration = System.currentTimeMillis() - pauseSystemTime;
                systemStartTime += pauseDuration;
                totalPauseDuration += pauseDuration;
                paused = false;
                Log.i(TAG, "GPS resumed after " + pauseDuration + "ms pause");
                pauseLock.notifyAll();
            }
        }
    }
    
    /**
     * Seek to a specific GPS timestamp in the track.
     * Works whether playing, paused, or stopped.
     * 
     * @param targetGpsTimeMs The original GPS timestamp to seek to
     * @param resumeAfterSeek If true, start/resume playback after seeking
     */
    public void seekTo(long targetGpsTimeMs, boolean resumeAfterSeek) {
        if (trackData == null || trackData.isEmpty()) {
            Log.w(TAG, "Cannot seek - no track data loaded");
            return;
        }
        
        // Clamp to track bounds
        targetGpsTimeMs = Math.max(trackStartTime, Math.min(trackEndTime, targetGpsTimeMs));
        
        Log.i(TAG, "Seeking to GPS time: " + targetGpsTimeMs + " (resumeAfterSeek=" + resumeAfterSeek + ")");
        
        // Find the index of the closest point at or before target time
        int targetIndex = findIndexForTime(targetGpsTimeMs);
        
        synchronized (seekLock) {
            // Update current position
            currentIndex = targetIndex;
            
            // Recalculate timing: make it so the target point is "now"
            long now = System.currentTimeMillis();
            MLocation targetLoc = trackData.get(targetIndex);
            
            // Adjust systemStartTime so that (now - systemStartTime) equals the track elapsed time
            long trackElapsed = targetLoc.millis - trackStartTime;
            systemStartTime = now - trackElapsed;
            
            // Reset pause tracking
            totalPauseDuration = 0;
            if (paused) {
                pauseSystemTime = now;
                pauseElapsedTime = trackElapsed;
            }
            
            // Clear completed flag if seeking back
            if (completed) {
                completed = false;
            }
        }
        
        // Emit the target location immediately
        emitLocationAtIndex(currentIndex);
        
        // Handle resume after seek
        if (resumeAfterSeek && paused) {
            resume();
        }
        
        Log.i(TAG, "Seek complete to index " + targetIndex + " (time=" + trackData.get(targetIndex).millis + ")");
    }
    
    /**
     * Find the track index for a given GPS timestamp.
     * Returns the index of the point at or just before the target time.
     */
    private int findIndexForTime(long targetGpsTimeMs) {
        if (trackData == null || trackData.isEmpty()) return 0;
        
        // Binary search for efficiency on long tracks
        int low = 0;
        int high = trackData.size() - 1;
        
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (trackData.get(mid).millis <= targetGpsTimeMs) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        
        return low;
    }
    
    /**
     * Emit a location at the given track index.
     * Used for immediate updates during seek.
     */
    private void emitLocationAtIndex(int index) {
        if (trackData == null || index < 0 || index >= trackData.size()) return;
        
        MLocation loc = trackData.get(index);
        long timeDelta = systemStartTime - trackStartTime;
        long adjustedTime = loc.millis + timeDelta - phoneSkew;
        
        MLocation adjustedLoc = new MLocation(
            adjustedTime,
            loc.latitude, loc.longitude, loc.altitude_gps,
            loc.climb, loc.vN, loc.vE,
            loc.hAcc, loc.pdop, loc.hdop, loc.vdop,
            loc.satellitesUsed, loc.satellitesInView
        );
        
        updateLocation(adjustedLoc);
    }
    
    /**
     * Get current playback position as elapsed milliseconds from track start.
     */
    public long getCurrentElapsedMs() {
        if (trackData == null || currentIndex < 0 || currentIndex >= trackData.size()) {
            return 0;
        }
        return trackData.get(currentIndex).millis - trackStartTime;
    }
    
    /**
     * Get the current GPS timestamp being played.
     */
    public long getCurrentGpsTimeMs() {
        if (trackData == null || currentIndex < 0 || currentIndex >= trackData.size()) {
            return trackStartTime;
        }
        return trackData.get(currentIndex).millis;
    }
    
    /**
     * Check if playback is paused
     */
    public boolean isPaused() {
        return paused;
    }
    
    /**
     * Check if playback has completed
     */
    public boolean isCompleted() {
        return completed;
    }
    
    /**
     * Reset to beginning for fresh start.
     * Stops playback, clears all state, loads data if needed.
     * Does NOT emit any GPS points - that's start()'s job.
     * After this call, the provider is ready to play from the beginning.
     */
    public void reset(@NonNull Context context) {
        Log.i(TAG, "Resetting GPS playback to beginning");
        
        // Stop any running thread
        stop();
        
        // Clear all state
        completed = false;
        paused = false;
        pauseElapsedTime = 0;
        pauseSystemTime = 0;
        totalPauseDuration = 0;  // Reset cumulative pause time
        
        // Cache context for later use
        cachedContext = context;
        
        // Load track data if not already loaded
        if (trackData == null || trackData.isEmpty()) {
            trackData = loadData(context);
        }
        
        if (trackData == null || trackData.isEmpty()) {
            Log.e(TAG, "No GPS data available for reset");
            return;
        }
        
        // Store track timing info (for reference, start() will set these too)
        trackStartTime = trackData.get(0).millis;
        trackEndTime = trackData.get(trackData.size() - 1).millis;
        
        Log.i(TAG, String.format("GPS reset complete - ready to play. Duration: %d s, %d points", 
            (trackEndTime - trackStartTime) / 1000, trackData.size()));
    }
    
    /**
     * Legacy reset without context - tries to use cached context
     */
    public void reset() {
        if (cachedContext != null) {
            reset(cachedContext);
        } else {
            Log.e(TAG, "Cannot reset without context - no cached context available");
            // Fall back to basic reset
            stop();
            completed = false;
            paused = false;
            pauseElapsedTime = 0;
            pauseSystemTime = 0;
            totalPauseDuration = 0;
        }
    }

    /**
     * Start location updates
     *
     * @param context The Application context
     */
    @Override
    public void start(@NonNull Context context) throws SecurityException {
        Log.i(TAG, "Starting mock location service");
        
        // Cache context
        cachedContext = context;
        
        // Use cached track data if available, otherwise load fresh
        List<MLocation> all;
        if (trackData != null && !trackData.isEmpty()) {
            all = trackData;
            Log.i(TAG, "Using cached track data: " + all.size() + " points");
        } else {
            all = loadData(context);
            trackData = all;  // Cache for future use
        }

        if (all.isEmpty()) {
            Log.e(TAG, "No GPS data loaded, cannot start mock location provider");
            started = false;
            return;
        }
        
        started = true;
        completed = false;
        paused = false;

        trackStartTime = all.get(0).millis; // Store for sensor synchronization
        trackEndTime = all.get(all.size() - 1).millis; // Store end time
        
        // Start emitting updates
        systemStartTime = System.currentTimeMillis();
        // Time offset to make first fix "now"
        final long timeDelta = systemStartTime - trackStartTime;
        final long trackDuration = trackEndTime - trackStartTime;
        
        Log.i(TAG, String.format("TIMESYNC: GPS provider starting - systemStartTime=%d, trackStartTime=%d, timeDelta=%d ms, duration=%d s", 
            systemStartTime, trackStartTime, timeDelta, trackDuration / 1000));

        // Initialize current index
        currentIndex = 0;

        // Emit first point immediately (synchronously) so HUD updates right away
        MLocation firstLoc = all.get(0);
        MLocation adjustedFirst = new MLocation(
            firstLoc.millis + timeDelta - phoneSkew,
            firstLoc.latitude, firstLoc.longitude, firstLoc.altitude_gps,
            firstLoc.climb, firstLoc.vN, firstLoc.vE,
            firstLoc.hAcc, firstLoc.pdop, firstLoc.hdop, firstLoc.vdop,
            firstLoc.satellitesUsed, firstLoc.satellitesInView
        );
        updateLocation(adjustedFirst);
        ReplayManager.INSTANCE.onGpsStarted();
        Log.i(TAG, "First GPS point emitted synchronously");

        // Start thread to emit remaining points
        final List<MLocation> trackList = all;
        emitterThread = new Thread(() -> {
            Log.i(TAG, String.format("TIMESYNC: GPS thread started, will emit %d locations", trackList.size()));
            
            // Start from index 1 (first point already emitted)
            currentIndex = 1;
            
            while (started && currentIndex < trackList.size()) {
                // Check for pause state
                synchronized (pauseLock) {
                    while (paused && started) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Interrupted while paused", e);
                        }
                    }
                }
                if (!started) break;
                
                // Get current location (may have changed due to seek)
                int idx = currentIndex;
                if (idx >= trackList.size()) break;
                
                MLocation loc = trackList.get(idx);
                
                // Wait until it's time to emit this point
                final long elapsed = System.currentTimeMillis() - systemStartTime;
                final long locElapsed = loc.millis - trackStartTime;
                if (locElapsed > elapsed) {
                    try {
                        // Sleep in small increments to allow seek interrupts
                        long sleepTime = Math.min(locElapsed - elapsed, 50);
                        Thread.sleep(sleepTime);
                        // Check if we were seeked while sleeping
                        if (currentIndex != idx) {
                            continue; // Index changed, re-check
                        }
                        // Still need to wait more?
                        if (locElapsed > System.currentTimeMillis() - systemStartTime) {
                            continue; // Keep waiting
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Mock location thread interrupted", e);
                    }
                }
                if (!started) break;
                
                // Check again in case of seek during sleep
                if (currentIndex != idx) continue;
                
                // Calculate time delta (may have changed due to seek)
                final long currentTimeDelta = systemStartTime - trackStartTime;
                final long adjustedTime = loc.millis + currentTimeDelta - phoneSkew;
                MLocation adjustedLoc = new MLocation(
                    adjustedTime,
                    loc.latitude, loc.longitude, loc.altitude_gps,
                    loc.climb, loc.vN, loc.vE,
                    loc.hAcc, loc.pdop, loc.hdop, loc.vdop,
                    loc.satellitesUsed, loc.satellitesInView
                );
                
                updateLocation(adjustedLoc);
                currentIndex++;
            }
            Log.i(TAG, "Finished emitting mock locations");
            // Signal playback has completed
            if (started && currentIndex >= trackList.size()) {
                completed = true;
                ReplayManager.INSTANCE.onGpsCompleted();
            }
        }, "GPS-Emitter");
        emitterThread.start();
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
        Log.i(TAG, "stop() called, started=" + started);
        // Stop thread
        started = false;
        // Wake up paused thread so it can exit
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
        
        // Interrupt the thread so it can exit faster
        if (emitterThread != null && emitterThread.isAlive()) {
            Log.i(TAG, "Interrupting emitter thread");
            emitterThread.interrupt();
        }
        // Don't wait for thread - let it terminate on its own
        // The next start() will create a new thread
        emitterThread = null;
        
        super.stop();
    }
    
    /**
     * Restart playback from the beginning.
     * This is equivalent to reset() + start().
     */
    public void restart(@NonNull Context context) throws SecurityException {
        Log.i(TAG, "Restarting GPS playback from beginning");
        // Reset clears state and emits first point
        reset(context);
        // Start begins the playback thread
        start(context);
    }
    
    /**
     * Restart playback with a delay offset.
     * Used when video starts before GPS on the shared timeline.
     * The delay shifts systemStartTime forward so GPS timestamps align with video.
     * 
     * @param context Application context
     * @param delayMs Milliseconds to delay (shifts systemStartTime forward)
     */
    public void restartWithDelay(@NonNull Context context, long delayMs) throws SecurityException {
        Log.i(TAG, "Restarting GPS playback with delay: " + delayMs + "ms");
        reset(context);
        startWithDelay(context, delayMs);
    }
    
    /**
     * Start GPS playback with a delay applied BEFORE emitting any points.
     * This ensures the delay is baked into systemStartTime from the beginning,
     * so the GPS thread waits the correct amount before emitting the first point.
     * 
     * Used when video starts before GPS on the shared timeline - the delay
     * synchronizes GPS to start at the right moment relative to video.
     * 
     * @param context Application context
     * @param delayMs Milliseconds to delay GPS start (shifts systemStartTime forward)
     */
    private void startWithDelay(@NonNull Context context, long delayMs) throws SecurityException {
        Log.i(TAG, "Starting mock GPS with delay: " + delayMs + "ms");
        
        // Cache context
        cachedContext = context;
        
        // Use cached track data if available, otherwise load fresh
        List<MLocation> all;
        if (trackData != null && !trackData.isEmpty()) {
            all = trackData;
            Log.i(TAG, "Using cached track data: " + all.size() + " points");
        } else {
            all = loadData(context);
            trackData = all;
        }

        if (all.isEmpty()) {
            Log.e(TAG, "No GPS data loaded, cannot start mock location provider");
            started = false;
            return;
        }
        
        started = true;
        completed = false;
        paused = false;

        trackStartTime = all.get(0).millis;
        trackEndTime = all.get(all.size() - 1).millis;
        
        // Apply delay BEFORE calculating timeDelta - this shifts everything forward
        systemStartTime = System.currentTimeMillis() + delayMs;
        final long timeDelta = systemStartTime - trackStartTime;
        final long trackDuration = trackEndTime - trackStartTime;
        
        Log.i(TAG, String.format("TIMESYNC: GPS starting with %dms delay, duration=%ds", 
            delayMs, trackDuration / 1000));

        // Initialize current index - start from 0, thread will emit all points after delay
        currentIndex = 0;
        
        // Notify that GPS playback is starting
        ReplayManager.INSTANCE.onGpsStarted();

        // Start thread to emit ALL points (including first, after delay elapses)
        final List<MLocation> trackList = all;
        emitterThread = new Thread(() -> {
            Log.i(TAG, String.format("GPS thread started, will emit %d locations after delay", trackList.size()));
            
            while (started && currentIndex < trackList.size()) {
                // Check for pause state
                synchronized (pauseLock) {
                    while (paused && started) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Interrupted while paused", e);
                        }
                    }
                }
                if (!started) break;
                
                int idx = currentIndex;
                if (idx >= trackList.size()) break;
                
                MLocation loc = trackList.get(idx);
                
                // Wait until it's time to emit this point (including initial delay)
                final long elapsed = System.currentTimeMillis() - systemStartTime;
                final long locElapsed = loc.millis - trackStartTime;
                if (locElapsed > elapsed) {
                    try {
                        long sleepTime = Math.min(locElapsed - elapsed, 50);
                        Thread.sleep(sleepTime);
                        if (currentIndex != idx) continue;
                        if (locElapsed > System.currentTimeMillis() - systemStartTime) continue;
                    } catch (InterruptedException e) {
                        Log.e(TAG, "GPS thread interrupted", e);
                    }
                }
                if (!started) break;
                if (currentIndex != idx) continue;
                
                final long currentTimeDelta = systemStartTime - trackStartTime;
                final long adjustedTime = loc.millis + currentTimeDelta - phoneSkew;
                MLocation adjustedLoc = new MLocation(
                    adjustedTime,
                    loc.latitude, loc.longitude, loc.altitude_gps,
                    loc.climb, loc.vN, loc.vE,
                    loc.hAcc, loc.pdop, loc.hdop, loc.vdop,
                    loc.satellitesUsed, loc.satellitesInView
                );
                
                updateLocation(adjustedLoc);
                currentIndex++;
            }
            Log.i(TAG, "Finished emitting mock locations");
            if (started && currentIndex >= trackList.size()) {
                completed = true;
                ReplayManager.INSTANCE.onGpsCompleted();
            }
        }, "GPS-Emitter-Delayed");
        emitterThread.start();
    }
    
    /**
     * Apply a delay to the current playback.
     * This shifts systemStartTime forward, which means GPS won't emit points
     * until the elapsed time catches up.
     * 
     * Used for timeline synchronization when video starts before GPS.
     * 
     * @param delayMs Milliseconds to delay GPS relative to video
     */
    public void applyStartDelay(long delayMs) {
        if (delayMs <= 0) return;
        
        // Shift systemStartTime forward by the delay
        // This means GPS won't start emitting until wall clock time catches up
        // For example: if delay=2000ms, GPS waits 2 seconds before first point
        long oldStartTime = systemStartTime;
        systemStartTime += delayMs;
        
        Log.i(TAG, String.format("Applied start delay: %dms (systemStartTime: %d -> %d)", 
            delayMs, oldStartTime, systemStartTime));
    }
}
