package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Represents magnetometer data from FlySight device.
 * 
 * Matches BLE characteristic 0x0011 (12 bytes):
 * - System timestamp (uint32, ms)
 * - Magnetic field X, Y, Z (int16, mGauss)
 * - Temperature (int16, 0.01 °C)
 * 
 * Timestamps are synchronized to GPS time using TIME sync data.
 */
public class MMagData extends Measurement {
    
    /** Device monotonic time in milliseconds (raw from sensor) */
    public final long deviceTimeMs;
    
    // Magnetometer data (Gauss - converted from mGauss in BLE)
    public final float magX;
    public final float magY;
    public final float magZ;
    
    // Temperature (°C)
    public final float temperature;
    
    /**
     * Create magnetometer data from parsed values
     * 
     * @param millis GPS-synchronized timestamp (Unix epoch milliseconds)
     * @param deviceTimeMs Device monotonic time (milliseconds)
     * @param magX Magnetic field X (Gauss)
     * @param magY Magnetic field Y (Gauss)
     * @param magZ Magnetic field Z (Gauss)
     * @param temperature Magnetometer temperature (°C)
     */
    public MMagData(long millis, long deviceTimeMs,
                    float magX, float magY, float magZ,
                    float temperature) {
        this.millis = millis;
        this.nano = 0; // Not used for file-based data
        this.sensor = "flysight_mag";
        
        this.deviceTimeMs = deviceTimeMs;
        this.magX = magX;
        this.magY = magY;
        this.magZ = magZ;
        this.temperature = temperature;
    }
    
    /**
     * Calculate magnetic field magnitude
     */
    public float getMagnitude() {
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
        return String.format("%d,%d,%s,%.6f,%.6f,%.6f,%.2f",
                millis, deviceTimeMs, sensor,
                magX, magY, magZ, temperature);
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("MMagData[t=%d, mag=(%.3f,%.3f,%.3f), heading=%.1f°]",
                millis, magX, magY, magZ, Math.toDegrees(getMagneticHeading()));
    }
}
