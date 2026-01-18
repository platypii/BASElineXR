package com.platypii.baselinexr.bluetooth;

import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Converts FlySight device monotonic timestamps to GPS-aligned Unix epoch milliseconds.
 * 
 * FlySight sensors report timestamps in device monotonic time (milliseconds since boot).
 * The TIME sync characteristic (0x0014) provides the mapping between device time and GPS time.
 * This class stores that mapping and converts sensor timestamps to GPS time.
 * 
 * Similar to TimeOffset (which handles phone↔GPS clock), this handles device↔GPS clock.
 */
public class FlysightTimeSync {
    private static final String TAG = "FlysightTimeSync";

    /** GPS epoch: January 6, 1980, 00:00:00 UTC minus 18 leap seconds */
    private static final long GPS_EPOCH_MILLIS = 315964800000L - 18000L;
    private static final long MILLIS_PER_WEEK = 604800000L;

    /** offset = gpsMillis - deviceTimeMs */
    private static long deviceToGpsOffset = 0;
    private static boolean initialized = false;
    
    /** Last received device time for staleness detection */
    private static long lastDeviceTimeMs = 0;
    
    /** Maximum age before considering time sync stale (5 minutes) */
    private static final long STALE_THRESHOLD_MS = 300000;

    /**
     * Update the time sync mapping from a TIME sync BLE packet.
     * 
     * @param deviceTimeMs Device monotonic time (ms) from TIME sync packet
     * @param gpsTowMs GPS Time of Week (ms)
     * @param gpsWeek GPS Week Number
     */
    public static void update(long deviceTimeMs, long gpsTowMs, int gpsWeek) {
        // Convert GPS week + TOW to Unix epoch millis
        long gpsMillis = GPS_EPOCH_MILLIS + (gpsWeek * MILLIS_PER_WEEK) + gpsTowMs;
        
        // Calculate offset: gpsMillis = deviceTimeMs + offset
        long newOffset = gpsMillis - deviceTimeMs;
        
        if (!initialized) {
            Log.i(TAG, String.format("Initial time sync: device=%d, gps=%d, offset=%d ms",
                    deviceTimeMs, gpsMillis, newOffset));
            deviceToGpsOffset = newOffset;
            lastDeviceTimeMs = deviceTimeMs;
            initialized = true;
        } else {
            // Check for significant drift (more than 1 second)
            long drift = Math.abs(newOffset - deviceToGpsOffset);
            if (drift > 1000) {
                Log.w(TAG, String.format("Time sync drift detected: %d ms, updating offset", drift));
            }
            deviceToGpsOffset = newOffset;
            lastDeviceTimeMs = deviceTimeMs;
        }
    }

    /**
     * Convert device monotonic time to GPS-aligned Unix epoch milliseconds.
     * 
     * @param deviceTimeMs Device monotonic time from sensor reading (ms)
     * @return Unix epoch milliseconds, or estimated time if not initialized
     */
    public static long deviceToGpsTime(long deviceTimeMs) {
        if (!initialized) {
            // Fallback: assume device time is close to GPS time
            // This will be corrected once TIME sync arrives
            Log.d(TAG, "Time sync not initialized, using device time as fallback");
            return deviceTimeMs;
        }
        return deviceTimeMs + deviceToGpsOffset;
    }

    /**
     * Check if time sync has been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if the time sync is stale (no recent updates).
     */
    public static boolean isStale() {
        if (!initialized) return true;
        long age = System.currentTimeMillis() - deviceToGpsTime(lastDeviceTimeMs);
        return Math.abs(age) > STALE_THRESHOLD_MS;
    }

    /**
     * Reset the time sync state (e.g., on disconnect).
     */
    public static void reset() {
        initialized = false;
        deviceToGpsOffset = 0;
        lastDeviceTimeMs = 0;
        Log.i(TAG, "Time sync reset");
    }

    /**
     * Get current offset for debugging.
     */
    public static long getOffset() {
        return deviceToGpsOffset;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("FlysightTimeSync[initialized=%b, offset=%d ms]", 
                initialized, deviceToGpsOffset);
    }
}
