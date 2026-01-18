package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Represents barometer data from FlySight device.
 * 
 * Matches BLE characteristic 0x0012 (10 bytes):
 * - System timestamp (uint32, ms)
 * - Pressure (uint32, Pa)
 * - Temperature (int16, 0.01 °C)
 * 
 * Timestamps are synchronized to GPS time using TIME sync data.
 */
public class MBaroData extends Measurement {
    
    /** Device monotonic time in milliseconds (raw from sensor) */
    public final long deviceTimeMs;
    
    // Barometer data
    public final float pressure;      // Pascals
    public final float temperature;   // °C
    
    /**
     * Create barometer data from parsed values
     * 
     * @param millis GPS-synchronized timestamp (Unix epoch milliseconds)
     * @param deviceTimeMs Device monotonic time (milliseconds)
     * @param pressure Atmospheric pressure (Pa)
     * @param temperature Barometer temperature (°C)
     */
    public MBaroData(long millis, long deviceTimeMs,
                     float pressure, float temperature) {
        this.millis = millis;
        this.nano = 0; // Not used for file-based data
        this.sensor = "flysight_baro";
        
        this.deviceTimeMs = deviceTimeMs;
        this.pressure = pressure;
        this.temperature = temperature;
    }
    
    /**
     * Get pressure in hectopascals (hPa / mbar)
     */
    public float getPressureHPa() {
        return pressure / 100.0f;
    }
    
    /**
     * Calculate altitude from pressure using barometric formula
     * 
     * @param seaLevelPa Sea level pressure in Pascals (default 101325 Pa)
     * @return Altitude in meters
     */
    public float getAltitude(float seaLevelPa) {
        return (float) (44330.0 * (1.0 - Math.pow(pressure / seaLevelPa, 0.1903)));
    }
    
    /**
     * Calculate altitude using standard sea level pressure (101325 Pa)
     */
    public float getAltitude() {
        return getAltitude(101325.0f);
    }
    
    @NonNull
    @Override
    public String toRow() {
        return String.format("%d,%d,%s,%.2f,%.2f",
                millis, deviceTimeMs, sensor,
                pressure, temperature);
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("MBaroData[t=%d, pressure=%.0fPa (%.1fhPa), alt=%.1fm]",
                millis, pressure, getPressureHPa(), getAltitude());
    }
}
