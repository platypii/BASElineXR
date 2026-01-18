package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Represents IMU data (accelerometer + gyroscope) from FlySight device.
 * 
 * Matches BLE characteristic 0x0010 (30 bytes):
 * - System timestamp (uint32, ms)
 * - Gyroscope X, Y, Z (int32, 0.001 °/s)
 * - Accelerometer X, Y, Z (int32, 0.001 g)
 * - Temperature (int16, 0.01 °C)
 * 
 * Timestamps are synchronized to GPS time using TIME sync data.
 */
public class MImuData extends Measurement {
    
    /** Device monotonic time in milliseconds (raw from sensor) */
    public final long deviceTimeMs;
    
    // Gyroscope data (°/s)
    public final float gyroX;
    public final float gyroY;
    public final float gyroZ;
    
    // Accelerometer data (g)
    public final float accelX;
    public final float accelY;
    public final float accelZ;
    
    // Temperature (°C)
    public final float temperature;
    
    /**
     * Create IMU data from parsed values
     * 
     * @param millis GPS-synchronized timestamp (Unix epoch milliseconds)
     * @param deviceTimeMs Device monotonic time (milliseconds)
     * @param gyroX Gyroscope X (°/s)
     * @param gyroY Gyroscope Y (°/s)
     * @param gyroZ Gyroscope Z (°/s)
     * @param accelX Accelerometer X (g)
     * @param accelY Accelerometer Y (g)
     * @param accelZ Accelerometer Z (g)
     * @param temperature IMU temperature (°C)
     */
    public MImuData(long millis, long deviceTimeMs,
                    float gyroX, float gyroY, float gyroZ,
                    float accelX, float accelY, float accelZ,
                    float temperature) {
        this.millis = millis;
        this.nano = 0; // Not used for file-based data
        this.sensor = "flysight_imu";
        
        this.deviceTimeMs = deviceTimeMs;
        this.gyroX = gyroX;
        this.gyroY = gyroY;
        this.gyroZ = gyroZ;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.temperature = temperature;
    }
    
    /**
     * Calculate total acceleration magnitude
     */
    public float getAccelMagnitude() {
        return (float) Math.sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ);
    }
    
    /**
     * Calculate total angular velocity magnitude
     */
    public float getGyroMagnitude() {
        return (float) Math.sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ);
    }
    
    @NonNull
    @Override
    public String toRow() {
        return String.format("%d,%d,%s,%.4f,%.4f,%.4f,%.5f,%.5f,%.5f,%.2f",
                millis, deviceTimeMs, sensor,
                gyroX, gyroY, gyroZ,
                accelX, accelY, accelZ,
                temperature);
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("MImuData[t=%d, gyro=(%.2f,%.2f,%.2f), accel=(%.3f,%.3f,%.3f)]",
                millis, gyroX, gyroY, gyroZ, accelX, accelY, accelZ);
    }
}
