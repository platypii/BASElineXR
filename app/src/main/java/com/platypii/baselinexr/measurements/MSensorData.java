package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Represents sensor data (compass, IMU, barometer, etc.) from FlySight device.
 * Timestamps are synchronized to GPS time for proper correlation with GPS measurements.
 */
public class MSensorData extends Measurement {
    
    // Magnetometer data (in gauss)
    public final float magX;
    public final float magY;
    public final float magZ;
    
    // IMU data
    public final float gyroX;    // deg/s
    public final float gyroY;    // deg/s
    public final float gyroZ;    // deg/s
    public final float accelX;   // g
    public final float accelY;   // g
    public final float accelZ;   // g
    
    // Barometer data
    public final float pressure;      // Pa
    public final float baroTemp;      // deg C
    
    // Humidity data
    public final float humidity;      // percent
    public final float humidityTemp;  // deg C
    
    // IMU/Magnetometer temperature
    public final float imuTemp;       // deg C
    public final float magTemp;       // deg C
    
    // Battery voltage
    public final float vbat;          // volts
    
    /**
     * Create sensor data with all available fields
     */
    public MSensorData(long millis, long nano,
                      float magX, float magY, float magZ, float magTemp,
                      float gyroX, float gyroY, float gyroZ,
                      float accelX, float accelY, float accelZ, float imuTemp,
                      float pressure, float baroTemp,
                      float humidity, float humidityTemp,
                      float vbat) {
        this.millis = millis;
        this.nano = nano;
        this.sensor = "flysight_sensor";
        
        // Magnetometer
        this.magX = magX;
        this.magY = magY;
        this.magZ = magZ;
        this.magTemp = magTemp;
        
        // IMU
        this.gyroX = gyroX;
        this.gyroY = gyroY;
        this.gyroZ = gyroZ;
        this.accelX = accelX;
        this.accelY = accelY;
        this.accelZ = accelZ;
        this.imuTemp = imuTemp;
        
        // Barometer
        this.pressure = pressure;
        this.baroTemp = baroTemp;
        
        // Humidity
        this.humidity = humidity;
        this.humidityTemp = humidityTemp;
        
        // Battery
        this.vbat = vbat;
    }
    
    /**
     * Calculate magnetic field magnitude
     */
    public float getMagneticFieldMagnitude() {
        return (float) Math.sqrt(magX * magX + magY * magY + magZ * magZ);
    }
    
    /**
     * Calculate magnetic heading (yaw) from magnetometer data
     * Returns angle in radians from magnetic north
     */
    public float getMagneticHeading() {
        // atan2(-magY, magX) gives heading where:
        // - magX points East
        // - magY points North (but negated due to sensor orientation)
        // - magZ points Down
        return (float) Math.atan2(-magY, magX);
    }
    
    /**
     * Get magnetic field vector as array [x, y, z]
     */
    @NonNull
    public float[] getMagneticFieldVector() {
        return new float[]{magX, magY, magZ};
    }
    
    @NonNull
    @Override
    public String toRow() {
        // Format: millis,nano,sensor,magX,magY,magZ,gyroX,gyroY,gyroZ,accelX,accelY,accelZ,pressure,temp
        return String.format("%d,%d,%s,%.6f,%.6f,%.6f,%.3f,%.3f,%.3f,%.5f,%.5f,%.5f,%.2f,%.2f",
                millis, nano, sensor,
                magX, magY, magZ,
                gyroX, gyroY, gyroZ,
                accelX, accelY, accelZ,
                pressure, baroTemp);
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("MSensorData[t=%d, mag=(%.3f,%.3f,%.3f), heading=%.1f°]",
                millis, magX, magY, magZ, Math.toDegrees(getMagneticHeading()));
    }
}
