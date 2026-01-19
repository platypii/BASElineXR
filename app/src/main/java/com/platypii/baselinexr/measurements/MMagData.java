package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Magnetometer measurement from FlySight SENSOR.CSV
 * Format: $MAG,time,x,y,z,temperature
 * Units: s, gauss, gauss, gauss, deg C
 */
public class MMagData extends Measurement {

    // Magnetometer (in gauss)
    public final float magX;
    public final float magY;
    public final float magZ;

    // Temperature
    public final float temperature;

    public MMagData(long millis, float magX, float magY, float magZ, float temperature) {
        this.millis = millis;
        this.sensor = "MAG";
        this.magX = magX;
        this.magY = magY;
        this.magZ = magZ;
        this.temperature = temperature;
    }

    /**
     * Create from sensor time (seconds since device boot) and time sync
     */
    public static MMagData fromSensorTime(double sensorTimeSec, MTimeSync timeSync,
                                          float magX, float magY, float magZ,
                                          float temperature) {
        long millis = timeSync != null ? timeSync.toGpsMillis(sensorTimeSec) : (long) (sensorTimeSec * 1000);
        return new MMagData(millis, magX, magY, magZ, temperature);
    }

    @NonNull
    @Override
    public String toRow() {
        return millis + ",,MAG,," + magX + "," + magY + "," + magZ + "," + temperature;
    }

    @NonNull
    @Override
    public String toString() {
        return "MAG[" + millis + " (" + magX + "," + magY + "," + magZ + ")]";
    }
}
