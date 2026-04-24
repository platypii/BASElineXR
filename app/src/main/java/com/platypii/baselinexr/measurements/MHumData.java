package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Humidity measurement from FlySight BLE
 * Units: % RH, deg C
 */
public class MHumData extends Measurement {

    // Relative humidity in percent
    public final float humidity;

    // Temperature in degrees C
    public final float temperature;

    public MHumData(long millis, float humidity, float temperature) {
        this.millis = millis;
        this.sensor = "HUM";
        this.humidity = humidity;
        this.temperature = temperature;
    }

    /**
     * Create from sensor time (seconds since device boot) and time sync
     */
    public static MHumData fromSensorTime(double sensorTimeSec, MTimeSync timeSync,
                                          float humidity, float temperature) {
        long millis = timeSync != null ? timeSync.toGpsMillis(sensorTimeSec) : (long) (sensorTimeSec * 1000);
        return new MHumData(millis, humidity, temperature);
    }

    @NonNull
    @Override
    public String toRow() {
        return millis + ",,HUM,," + humidity + "," + temperature;
    }

    @NonNull
    @Override
    public String toString() {
        return "HUM[" + millis + " humidity=" + humidity + "% RH]";
    }
}
