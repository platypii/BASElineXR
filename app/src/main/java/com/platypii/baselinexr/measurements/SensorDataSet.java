package com.platypii.baselinexr.measurements;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for all sensor data types, keeping each type in separate lists
 * with independent timestamps. This matches the BLE characteristic structure
 * where each sensor type streams independently.
 * 
 * Each sensor type has its own sampling rate and timing, so they are stored
 * separately rather than bundled into combined entries.
 */
public class SensorDataSet {
    
    /** IMU data (accelerometer + gyroscope) */
    @NonNull
    public final List<MImuData> imuData;
    
    /** Magnetometer data */
    @NonNull
    public final List<MMagData> magData;
    
    /** Barometer data */
    @NonNull
    public final List<MBaroData> baroData;
    
    /** Humidity data */
    @NonNull
    public final List<MHumidityData> humidityData;
    
    /** Time sync entries */
    @NonNull
    public final List<MTimeSync> timeSyncData;
    
    /** Legacy combined sensor data (for backwards compatibility) */
    @NonNull
    public final List<MSensorData> combinedData;
    
    public SensorDataSet() {
        this.imuData = new ArrayList<>();
        this.magData = new ArrayList<>();
        this.baroData = new ArrayList<>();
        this.humidityData = new ArrayList<>();
        this.timeSyncData = new ArrayList<>();
        this.combinedData = new ArrayList<>();
    }
    
    public SensorDataSet(@NonNull List<MImuData> imuData,
                         @NonNull List<MMagData> magData,
                         @NonNull List<MBaroData> baroData,
                         @NonNull List<MHumidityData> humidityData,
                         @NonNull List<MTimeSync> timeSyncData,
                         @NonNull List<MSensorData> combinedData) {
        this.imuData = imuData;
        this.magData = magData;
        this.baroData = baroData;
        this.humidityData = humidityData;
        this.timeSyncData = timeSyncData;
        this.combinedData = combinedData;
    }
    
    /**
     * Check if any sensor data is available
     */
    public boolean isEmpty() {
        return imuData.isEmpty() && magData.isEmpty() && baroData.isEmpty() && humidityData.isEmpty();
    }
    
    /**
     * Get total count of all sensor measurements
     */
    public int getTotalCount() {
        return imuData.size() + magData.size() + baroData.size() + humidityData.size();
    }
    
    /**
     * Get time range of all sensor data (earliest to latest timestamp)
     * 
     * @return [startMillis, endMillis] or null if no data
     */
    @Nullable
    public long[] getTimeRange() {
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        
        if (!imuData.isEmpty()) {
            minTime = Math.min(minTime, imuData.get(0).millis);
            maxTime = Math.max(maxTime, imuData.get(imuData.size() - 1).millis);
        }
        if (!magData.isEmpty()) {
            minTime = Math.min(minTime, magData.get(0).millis);
            maxTime = Math.max(maxTime, magData.get(magData.size() - 1).millis);
        }
        if (!baroData.isEmpty()) {
            minTime = Math.min(minTime, baroData.get(0).millis);
            maxTime = Math.max(maxTime, baroData.get(baroData.size() - 1).millis);
        }
        if (!humidityData.isEmpty()) {
            minTime = Math.min(minTime, humidityData.get(0).millis);
            maxTime = Math.max(maxTime, humidityData.get(humidityData.size() - 1).millis);
        }
        
        if (minTime == Long.MAX_VALUE) {
            return null;
        }
        
        return new long[]{minTime, maxTime};
    }
    
    /**
     * Apply time delta to all sensor data (for mock playback synchronization)
     * 
     * @param timeDelta Milliseconds to add to all timestamps
     */
    public void applyTimeDelta(long timeDelta) {
        for (MImuData imu : imuData) {
            imu.millis += timeDelta;
        }
        for (MMagData mag : magData) {
            mag.millis += timeDelta;
        }
        for (MBaroData baro : baroData) {
            baro.millis += timeDelta;
        }
        for (MHumidityData hum : humidityData) {
            hum.millis += timeDelta;
        }
        for (MSensorData combined : combinedData) {
            combined.millis += timeDelta;
        }
    }
    
    /**
     * Find IMU data at or before the given time using binary search
     * 
     * @param gpsMillis Target time in milliseconds
     * @param maxAgeMs Maximum age in milliseconds (return null if older)
     * @return IMU data or null if not available or too old
     */
    @Nullable
    public MImuData getImuAtTime(long gpsMillis, long maxAgeMs) {
        return findAtTime(imuData, gpsMillis, maxAgeMs);
    }
    
    /**
     * Find magnetometer data at or before the given time using binary search
     */
    @Nullable
    public MMagData getMagAtTime(long gpsMillis, long maxAgeMs) {
        return findAtTime(magData, gpsMillis, maxAgeMs);
    }
    
    /**
     * Find barometer data at or before the given time using binary search
     */
    @Nullable
    public MBaroData getBaroAtTime(long gpsMillis, long maxAgeMs) {
        return findAtTime(baroData, gpsMillis, maxAgeMs);
    }
    
    /**
     * Find humidity data at or before the given time using binary search
     */
    @Nullable
    public MHumidityData getHumidityAtTime(long gpsMillis, long maxAgeMs) {
        return findAtTime(humidityData, gpsMillis, maxAgeMs);
    }
    
    /**
     * Generic binary search to find measurement at or before target time
     */
    @Nullable
    private <T extends Measurement> T findAtTime(@NonNull List<T> data, long gpsMillis, long maxAgeMs) {
        if (data.isEmpty()) return null;
        
        // If before first reading, return null
        if (gpsMillis < data.get(0).millis) return null;
        
        // If after last reading, check age
        if (gpsMillis > data.get(data.size() - 1).millis) {
            T last = data.get(data.size() - 1);
            if (gpsMillis - last.millis > maxAgeMs) return null;
            return last;
        }
        
        // Binary search for most recent reading at or before gpsMillis
        int left = 0;
        int right = data.size() - 1;
        int result = 0;
        
        while (left <= right) {
            int mid = (left + right) / 2;
            T item = data.get(mid);
            
            if (item.millis <= gpsMillis) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        T found = data.get(result);
        if (gpsMillis - found.millis > maxAgeMs) return null;
        
        return found;
    }
    
    @NonNull
    @Override
    public String toString() {
        return String.format("SensorDataSet[imu=%d, mag=%d, baro=%d, hum=%d, sync=%d]",
                imuData.size(), magData.size(), baroData.size(), humidityData.size(), timeSyncData.size());
    }
}
