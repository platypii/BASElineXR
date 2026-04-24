package com.platypii.baselinexr.measurements;

/**
 * Time synchronization entry from FlySight SENSOR.CSV
 * Format: $TIME,time,tow,week
 * Units: s (sensor time), s (GPS time of week), week number
 *
 * This maps FlySight device sensor time to GPS time, allowing conversion
 * of all sensor timestamps to GPS epoch milliseconds.
 */
public class MTimeSync {

    // GPS epoch: January 6, 1980 00:00:00 UTC
    // Adjusted for leap seconds (18 as of 2017)
    private static final long GPS_EPOCH_MILLIS = 315964800000L;
    private static final long MILLIS_PER_WEEK = 7L * 24L * 60L * 60L * 1000L;

    // Sensor time in seconds (since FlySight device boot)
    public final double sensorTimeSec;

    // GPS time of week in seconds
    public final double towSec;

    // GPS week number
    public final int week;

    // Computed offset: gpsMillis = (sensorTimeSec + offsetSec) * 1000
    private final double offsetSec;

    public MTimeSync(double sensorTimeSec, double towSec, int week) {
        this.sensorTimeSec = sensorTimeSec;
        this.towSec = towSec;
        this.week = week;

        // Calculate offset from sensor time to GPS epoch milliseconds
        // GPS epoch millis = GPS_EPOCH_MILLIS + week * MILLIS_PER_WEEK + tow * 1000
        double gpsEpochSec = (GPS_EPOCH_MILLIS / 1000.0) + (week * MILLIS_PER_WEEK / 1000.0) + towSec;
        this.offsetSec = gpsEpochSec - sensorTimeSec;
    }

    /**
     * Convert sensor time (seconds since device boot) to GPS epoch milliseconds
     */
    public long toGpsMillis(double sensorTimeSec) {
        return (long) ((sensorTimeSec + offsetSec) * 1000.0);
    }

    /**
     * Get the GPS epoch milliseconds for this TIME entry
     */
    public long getGpsMillis() {
        return toGpsMillis(sensorTimeSec);
    }

    @Override
    public String toString() {
        return "TimeSync[sensor=" + sensorTimeSec + "s, tow=" + towSec + "s, week=" + week + "]";
    }
}
