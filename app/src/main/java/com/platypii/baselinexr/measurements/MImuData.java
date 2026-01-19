package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * IMU measurement from FlySight SENSOR.CSV
 * Format: $IMU,time,wx,wy,wz,ax,ay,az,temperature
 * Units: s, deg/s, deg/s, deg/s, g, g, g, deg C
 */
public class MImuData extends Measurement {

    // Gyroscope (angular velocity in deg/s)
    public final float gyroX;
    public final float gyroY;
    public final float gyroZ;

    // Accelerometer (in g's)
    public final float accelX;
    public final float accelY;
    public final float accelZ;

    // Temperature
    public final float temperature;

    public MImuData(long millis, float gyroX, float gyroY, float gyroZ,
                    float accelX, float accelY, float accelZ, float temperature) {
        this.millis = millis;
        this.sensor = "IMU";
        this.gyroX = gyroX;
        this.gyroY = gyroY;
        this.gyroZ = gyroZ;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.temperature = temperature;
    }

    /**
     * Create from sensor time (seconds since device boot) and time sync
     */
    public static MImuData fromSensorTime(double sensorTimeSec, MTimeSync timeSync,
                                          float gyroX, float gyroY, float gyroZ,
                                          float accelX, float accelY, float accelZ,
                                          float temperature) {
        long millis = timeSync != null ? timeSync.toGpsMillis(sensorTimeSec) : (long) (sensorTimeSec * 1000);
        return new MImuData(millis, gyroX, gyroY, gyroZ, accelX, accelY, accelZ, temperature);
    }

    @NonNull
    @Override
    public String toRow() {
        return millis + ",,IMU,," + gyroX + "," + gyroY + "," + gyroZ + "," +
                accelX + "," + accelY + "," + accelZ + "," + temperature;
    }

    @NonNull
    @Override
    public String toString() {
        return "IMU[" + millis + " gyro=(" + gyroX + "," + gyroY + "," + gyroZ +
                ") accel=(" + accelX + "," + accelY + "," + accelZ + ")]";
    }
}
