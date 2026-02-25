package com.platypii.baselinexr.tracks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MTimeSync;

import java.util.List;

/**
 * Container for all sensor data parsed from a SENSOR.CSV file.
 * Includes IMU, magnetometer, barometer data and time sync entries.
 */
public class SensorDataSet {

    @NonNull
    public final List<MImuData> imuData;

    @NonNull
    public final List<MMagData> magData;

    @NonNull
    public final List<MBaroData> baroData;

    @NonNull
    public final List<MTimeSync> timeSyncData;

    @Nullable
    public final String sessionId;

    @Nullable
    public final String deviceId;

    @Nullable
    public final String firmwareVer;

    public SensorDataSet(@NonNull List<MImuData> imuData,
                         @NonNull List<MMagData> magData,
                         @NonNull List<MBaroData> baroData,
                         @NonNull List<MTimeSync> timeSyncData,
                         @Nullable String sessionId,
                         @Nullable String deviceId,
                         @Nullable String firmwareVer) {
        this.imuData = imuData;
        this.magData = magData;
        this.baroData = baroData;
        this.timeSyncData = timeSyncData;
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.firmwareVer = firmwareVer;
    }

    /**
     * Check if this dataset has IMU data
     */
    public boolean hasImu() {
        return !imuData.isEmpty();
    }

    /**
     * Check if this dataset has magnetometer data
     */
    public boolean hasMag() {
        return !magData.isEmpty();
    }

    /**
     * Check if this dataset has barometer data
     */
    public boolean hasBaro() {
        return !baroData.isEmpty();
    }

    /**
     * Check if this dataset has time sync data (required for valid timestamps)
     */
    public boolean hasTimeSync() {
        return !timeSyncData.isEmpty();
    }

    /**
     * Get the first timestamp in GPS millis (for synchronization with track data)
     */
    public long getFirstTimestamp() {
        long first = Long.MAX_VALUE;
        if (!imuData.isEmpty()) {
            first = Math.min(first, imuData.get(0).millis);
        }
        if (!magData.isEmpty()) {
            first = Math.min(first, magData.get(0).millis);
        }
        if (!baroData.isEmpty()) {
            first = Math.min(first, baroData.get(0).millis);
        }
        return first == Long.MAX_VALUE ? 0 : first;
    }

    /**
     * Get the last timestamp in GPS millis
     */
    public long getLastTimestamp() {
        long last = 0;
        if (!imuData.isEmpty()) {
            last = Math.max(last, imuData.get(imuData.size() - 1).millis);
        }
        if (!magData.isEmpty()) {
            last = Math.max(last, magData.get(magData.size() - 1).millis);
        }
        if (!baroData.isEmpty()) {
            last = Math.max(last, baroData.get(baroData.size() - 1).millis);
        }
        return last;
    }

    /**
     * Get total sample count across all sensor types
     */
    public int getTotalSampleCount() {
        return imuData.size() + magData.size() + baroData.size();
    }

    @NonNull
    @Override
    public String toString() {
        return "SensorDataSet[imu=" + imuData.size() +
                ", mag=" + magData.size() +
                ", baro=" + baroData.size() +
                ", timeSync=" + timeSyncData.size() + "]";
    }
}
