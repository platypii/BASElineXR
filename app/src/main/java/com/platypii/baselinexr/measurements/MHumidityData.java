package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;

/**
 * Represents humidity data from FlySight device.
 * 
 * Matches BLE characteristic 0x0013 (8 bytes):
 * - System timestamp (uint32, ms)
 * - Humidity (int16, 0.01 %RH)
 * - Temperature (int16, 0.01 °C)
 * 
 * Timestamps are synchronized to GPS time using TIME sync data.
 */
public class MHumidityData extends Measurement {
    
    /** Device monotonic time in milliseconds (raw from sensor) */
    public final long deviceTimeMs;
    
    // Humidity data
    public final float humidity;      // %RH (relative humidity)
    public final float temperature;   // °C
    
    /**
     * Create humidity data from parsed values
     * 
     * @param millis GPS-synchronized timestamp (Unix epoch milliseconds)
     * @param deviceTimeMs Device monotonic time (milliseconds)
     * @param humidity Relative humidity (%)
     * @param temperature Humidity sensor temperature (°C)
     */
    public MHumidityData(long millis, long deviceTimeMs,
                         float humidity, float temperature) {
        this.millis = millis;
        this.nano = 0; // Not used for file-based data
        this.sensor = "flysight_humidity";
        
        this.deviceTimeMs = deviceTimeMs;
        this.humidity = humidity;
        this.temperature = temperature;
    }
    
    /**
     * Check if humidity value is valid (0-100%)
     */
    public boolean isValid() {
        return !Float.isNaN(humidity) && humidity >= 0f && humidity <= 100f;
    }
    
    @NonNull
    @Override
    public String toRow() {
        return String.format("%d,%d,%s,%.2f,%.2f",
                millis, deviceTimeMs, sensor,
                humidity, temperature);
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("MHumidityData[t=%d, humidity=%.1f%%, temp=%.1f°C]",
                millis, humidity, temperature);
    }
}
