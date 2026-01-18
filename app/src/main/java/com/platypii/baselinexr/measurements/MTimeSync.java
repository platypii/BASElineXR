package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Represents time synchronization data from FlySight device.
 * 
 * Matches BLE characteristic 0x0014 (10 bytes):
 * - System timestamp (uint32, ms) - Device monotonic time
 * - GPS Time of Week (uint32, ms)
 * - GPS Week Number (uint16)
 * 
 * This allows converting device monotonic timestamps to GPS-aligned Unix epoch time.
 */
public class MTimeSync {
    
    /** GPS epoch: January 6, 1980, 00:00:00 UTC */
    private static final long GPS_EPOCH_MILLIS = 315964800000L;
    
    /** Device monotonic time in milliseconds */
    public final long deviceTimeMs;
    
    /** GPS Time of Week in milliseconds */
    public final long gpsTowMs;
    
    /** GPS Week Number */
    public final int gpsWeek;
    
    /**
     * Create time sync data
     * 
     * @param deviceTimeMs Device monotonic time (ms)
     * @param gpsTowMs GPS Time of Week (ms)
     * @param gpsWeek GPS Week Number
     */
    public MTimeSync(long deviceTimeMs, long gpsTowMs, int gpsWeek) {
        this.deviceTimeMs = deviceTimeMs;
        this.gpsTowMs = gpsTowMs;
        this.gpsWeek = gpsWeek;
    }
    
    /**
     * Create time sync from SENSOR.CSV $TIME entry (time values in seconds)
     * 
     * @param localTimeSec Local sensor time in seconds
     * @param towSec GPS Time of Week in seconds
     * @param week GPS Week Number
     */
    public static MTimeSync fromCsvSeconds(double localTimeSec, double towSec, int week) {
        return new MTimeSync(
                (long) (localTimeSec * 1000),
                (long) (towSec * 1000),
                week
        );
    }
    
    /**
     * Convert device monotonic time to GPS-aligned Unix epoch milliseconds
     * 
     * @param sensorDeviceTimeMs Device monotonic time from a sensor reading (ms)
     * @return Unix epoch milliseconds
     */
    public long toUnixMillis(long sensorDeviceTimeMs) {
        // Calculate time delta from this sync point
        long deltaMs = sensorDeviceTimeMs - deviceTimeMs;
        
        // Apply delta to GPS TOW
        long adjustedTowMs = gpsTowMs + deltaMs;
        
        // Convert GPS week + TOW to milliseconds since GPS epoch
        long gpsMillis = (long) gpsWeek * 7L * 24L * 3600L * 1000L + adjustedTowMs;
        
        // Convert to Unix epoch
        return GPS_EPOCH_MILLIS + gpsMillis;
    }
    
    /**
     * Convert device time in seconds to GPS-aligned Unix epoch milliseconds
     * (for compatibility with SENSOR.CSV parsing which uses seconds)
     * 
     * @param sensorTimeSec Device time in seconds
     * @return Unix epoch milliseconds
     */
    public long toUnixMillis(double sensorTimeSec) {
        return toUnixMillis((long) (sensorTimeSec * 1000));
    }
    
    /**
     * Get the GPS time represented by this sync point as Unix epoch milliseconds
     */
    public long getSyncUnixMillis() {
        return toUnixMillis(deviceTimeMs);
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("MTimeSync[device=%d, tow=%d, week=%d, unix=%d]",
                deviceTimeMs, gpsTowMs, gpsWeek, getSyncUnixMillis());
    }
}
