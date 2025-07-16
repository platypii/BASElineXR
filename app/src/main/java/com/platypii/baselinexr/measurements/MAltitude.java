package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * An altitude measurement
 */
public class MAltitude extends Measurement {
    public final String sensor = "Alt";

    // Altimeter
    public final double altitude;  // Altitude (m)
    public final double climb;     // Rate of climb (m/s)

    public MAltitude(long millis, double altitude, double climb) {
        this.millis = millis;
        this.altitude = altitude;
        this.climb = climb;
    }

    @NonNull
    @Override
    public String toRow() {
        return "";
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US, "MAltitude(%d,%.1f)", millis, altitude);
    }

}
