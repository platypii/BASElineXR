package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * IMU measurement from FlySight SENSOR.CSV or BLE
 * Format: $IMU,time,wx,wy,wz,ax,ay,az,temperature
 * Units: s, deg/s, deg/s, deg/s, g, g, g, deg C
 * Quaternion (optional, from sensor fusion): NWU frame, normalized
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

    // Quaternion from sensor fusion (NWU frame, normalized)
    // NaN if not available (sensor fusion disabled)
    public final float qw;
    public final float qx;
    public final float qy;
    public final float qz;

    public MImuData(long millis, float gyroX, float gyroY, float gyroZ,
                    float accelX, float accelY, float accelZ, float temperature) {
        this(millis, gyroX, gyroY, gyroZ, accelX, accelY, accelZ, temperature,
                Float.NaN, Float.NaN, Float.NaN, Float.NaN);
    }

    public MImuData(long millis, float gyroX, float gyroY, float gyroZ,
                    float accelX, float accelY, float accelZ, float temperature,
                    float qw, float qx, float qy, float qz) {
        this.millis = millis;
        this.sensor = "IMU";
        this.gyroX = gyroX;
        this.gyroY = gyroY;
        this.gyroZ = gyroZ;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.temperature = temperature;
        this.qw = qw;
        this.qx = qx;
        this.qy = qy;
        this.qz = qz;
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

    /**
     * Create from sensor time with quaternion data (BLE sensor fusion)
     */
    public static MImuData fromSensorTimeWithQuat(double sensorTimeSec, MTimeSync timeSync,
                                                  float gyroX, float gyroY, float gyroZ,
                                                  float accelX, float accelY, float accelZ,
                                                  float temperature,
                                                  float qw, float qx, float qy, float qz) {
        long millis = timeSync != null ? timeSync.toGpsMillis(sensorTimeSec) : (long) (sensorTimeSec * 1000);
        return new MImuData(millis, gyroX, gyroY, gyroZ, accelX, accelY, accelZ, temperature, qw, qx, qy, qz);
    }

    /**
     * Check if quaternion data is available
     */
    public boolean hasQuaternion() {
        return !Float.isNaN(qw);
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
        String quatStr = hasQuaternion() ? " quat=(" + qw + "," + qx + "," + qy + "," + qz + ")" : "";
        return "IMU[" + millis + " gyro=(" + gyroX + "," + gyroY + "," + gyroZ +
                ") accel=(" + accelX + "," + accelY + "," + accelZ + ")" + quatStr + "]";
    }
}
