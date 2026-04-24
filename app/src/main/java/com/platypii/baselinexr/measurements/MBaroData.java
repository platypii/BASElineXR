package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Barometer measurement from FlySight SENSOR.CSV
 * Format: $BARO,time,pressure,temperature
 * Units: s, Pa, deg C
 */
public class MBaroData extends Measurement {

    // Pressure in Pascals
    public final float pressure;

    // Temperature in degrees C
    public final float temperature;

    public MBaroData(long millis, float pressure, float temperature) {
        this.millis = millis;
        this.sensor = "BARO";
        this.pressure = pressure;
        this.temperature = temperature;
    }

    /**
     * Create from sensor time (seconds since device boot) and time sync
     */
    public static MBaroData fromSensorTime(double sensorTimeSec, MTimeSync timeSync,
                                           float pressure, float temperature) {
        long millis = timeSync != null ? timeSync.toGpsMillis(sensorTimeSec) : (long) (sensorTimeSec * 1000);
        return new MBaroData(millis, pressure, temperature);
    }

    @NonNull
    @Override
    public String toRow() {
        return millis + ",,BARO,," + pressure + "," + temperature;
    }

    @NonNull
    @Override
    public String toString() {
        return "BARO[" + millis + " pressure=" + pressure + " Pa]";
    }
}
