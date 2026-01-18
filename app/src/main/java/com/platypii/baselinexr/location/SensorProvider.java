package com.platypii.baselinexr.location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumidityData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MSensorData;

/**
 * Common interface for sensor data providers.
 * Implemented by both MockSensorProvider (for recorded data playback)
 * and LiveSensorProvider (for real-time BLE sensor streaming).
 * 
 * This allows app components to access sensor data uniformly regardless
 * of whether the data comes from a recorded file or live BLE connection.
 */
public interface SensorProvider {
    
    // ========== Type-specific sensor accessors (preferred API) ==========
    
    /**
     * Get IMU data (accelerometer + gyroscope) at or near the given time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent IMU data, or null if not available
     */
    @Nullable
    MImuData getImuAtTime(long gpsMillis);
    
    /**
     * Get magnetometer data at or near the given time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent magnetometer data, or null if not available
     */
    @Nullable
    MMagData getMagAtTime(long gpsMillis);
    
    /**
     * Get barometer data at or near the given time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent barometer data, or null if not available
     */
    @Nullable
    MBaroData getBaroAtTime(long gpsMillis);
    
    /**
     * Get humidity data at or near the given time
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent humidity data, or null if not available
     */
    @Nullable
    MHumidityData getHumidityAtTime(long gpsMillis);
    
    // ========== Legacy combined sensor accessor (for backwards compatibility) ==========
    
    /**
     * Get combined sensor data at or near the given time.
     * This is the legacy API - prefer using type-specific methods.
     * 
     * @param gpsMillis GPS time in milliseconds
     * @return Most recent combined sensor data, or null if not available
     * @deprecated Use type-specific methods like {@link #getImuAtTime(long)}
     */
    @Deprecated
    @Nullable
    MSensorData getSensorAtTime(long gpsMillis);
    
    // ========== Status methods ==========
    
    /**
     * Check if sensor data is available
     * 
     * @return true if any sensor data is available
     */
    boolean hasSensorData();
    
    /**
     * Check if IMU data is available
     */
    boolean hasImuData();
    
    /**
     * Check if magnetometer data is available
     */
    boolean hasMagData();
    
    /**
     * Check if barometer data is available
     */
    boolean hasBaroData();
    
    /**
     * Check if humidity data is available
     */
    boolean hasHumidityData();
    
    /**
     * Get a human-readable status summary for debugging
     */
    @NonNull
    String getStatusSummary();
}
