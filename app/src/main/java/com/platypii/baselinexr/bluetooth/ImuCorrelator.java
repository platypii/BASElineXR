package com.platypii.baselinexr.bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MTimeSync;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Correlates accelerometer and gyroscope BLE packets by timestamp.
 * 
 * FlySight 2 sends accel first, then gyro immediately after, for the same IMU reading.
 * Both packets share the same timestamp (ms). We buffer accel data and combine with
 * gyro when it arrives to produce a unified MImuData with matched timestamps.
 */
public class ImuCorrelator {
    private static final String TAG = "ImuCorrelator";
    
    // Maximum age for pending accel data before expiring (ms)
    private static final long TIMEOUT_MS = 500;
    
    // Pending accel data keyed by sensor timestamp (ms)
    private final Map<Long, PendingAccel> pendingAccel = new HashMap<>();
    
    // Track last cleanup time to avoid cleaning on every packet
    private long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 1000;
    
    /**
     * Pending accelerometer data waiting for matching gyroscope packet
     */
    private static class PendingAccel {
        final long sensorTimeMs;
        final long receivedTimeMs;
        final float ax;
        final float ay;
        final float az;
        final float temperature;
        
        PendingAccel(long sensorTimeMs, float ax, float ay, float az, float temperature) {
            this.sensorTimeMs = sensorTimeMs;
            this.receivedTimeMs = System.currentTimeMillis();
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.temperature = temperature;
        }
    }
    
    /**
     * Process accelerometer BLE packet. Buffers data until matching gyro arrives.
     * 
     * @param value Raw BLE characteristic value
     */
    public void onAccelReceived(@NonNull byte[] value) {
        if (value.length < 1) {
            android.util.Log.w(TAG, "onAccelReceived: empty packet");
            return;
        }
        
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        int mask = buf.get() & 0xFF;
        
        // Must have timestamp to correlate
        if ((mask & 0x80) == 0) {
            android.util.Log.w(TAG, "onAccelReceived: no timestamp in mask 0x" + String.format("%02X", mask));
            return;
        }
        
        long sensorTimeMs = Integer.toUnsignedLong(buf.getInt());
        
        // Parse accelerometer data (if present)
        float ax = 0, ay = 0, az = 0;
        if ((mask & 0x40) != 0) {
            ax = buf.getInt() / 100000f;  // g × 100000 -> g
            ay = buf.getInt() / 100000f;
            az = buf.getInt() / 100000f;
        }
        
        // Parse temperature (if present)
        float temperature = Float.NaN;
        if ((mask & 0x20) != 0) {
            temperature = buf.getShort() / 100f;  // 0.01°C -> °C
        }
        
        // Store pending accel
        pendingAccel.put(sensorTimeMs, new PendingAccel(sensorTimeMs, ax, ay, az, temperature));
        android.util.Log.d(TAG, "onAccelReceived: stored t=" + sensorTimeMs + " accel=(" + ax + "," + ay + "," + az + ") pending=" + pendingAccel.size());
        
        // Periodic cleanup of old entries
        cleanupIfNeeded();
    }
    
    /**
     * Process gyroscope BLE packet. Looks up matching accel by timestamp and
     * returns combined MImuData if found.
     * 
     * @param value Raw BLE characteristic value
     * @param timeSync Time synchronization (nullable if not yet synced)
     * @return Combined MImuData if matching accel found, null otherwise
     */
    @Nullable
    public MImuData onGyroReceived(@NonNull byte[] value, @Nullable MTimeSync timeSync) {
        if (value.length < 1) {
            android.util.Log.w(TAG, "onGyroReceived: empty packet");
            return null;
        }
        
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        int mask = buf.get() & 0xFF;
        
        // Must have timestamp to correlate
        if ((mask & 0x80) == 0) {
            android.util.Log.w(TAG, "onGyroReceived: no timestamp in mask 0x" + String.format("%02X", mask));
            return null;
        }
        
        long sensorTimeMs = Integer.toUnsignedLong(buf.getInt());
        
        // Parse gyroscope data (if present)
        float wx = 0, wy = 0, wz = 0;
        if ((mask & 0x40) != 0) {
            wx = buf.getInt() / 1000f;  // deg/s × 1000 -> deg/s
            wy = buf.getInt() / 1000f;
            wz = buf.getInt() / 1000f;
        }
        
        // Parse temperature (if present)
        float gyroTemp = Float.NaN;
        if ((mask & 0x20) != 0) {
            gyroTemp = buf.getShort() / 100f;  // 0.01°C -> °C
        }
        
        // Parse quaternion (if present, sensor fusion enabled)
        float qw = Float.NaN, qx = Float.NaN, qy = Float.NaN, qz = Float.NaN;
        if ((mask & 0x10) != 0) {
            qw = buf.getShort() / 10000f;  // normalized × 10000
            qx = buf.getShort() / 10000f;
            qy = buf.getShort() / 10000f;
            qz = buf.getShort() / 10000f;
        }
        
        // Look up matching accel - firmware always sends accel first, then gyro for same sample
        // Timestamps may differ by 80-160ms due to firmware clock quirks, so use most recent accel
        PendingAccel accel = getMostRecentAccel();
        android.util.Log.d(TAG, "onGyroReceived: t=" + sensorTimeMs + " gyro=(" + wx + "," + wy + "," + wz + ") accelMatch=" + (accel != null) + " pending=" + pendingAccel.size());
        if (accel == null) {
            // No pending accel available
            return null;
        }
        long timeDiff = sensorTimeMs - accel.sensorTimeMs;
        android.util.Log.d(TAG, "  -> paired with accel t=" + accel.sensorTimeMs + " (diff=" + timeDiff + "ms)");
        
        // Use accel temperature if gyro temperature not available
        float temperature = Float.isNaN(gyroTemp) ? accel.temperature : gyroTemp;
        
        // Convert sensor time to GPS millis
        double sensorTimeSec = sensorTimeMs / 1000.0;
        
        // Create combined IMU data with quaternion
        return MImuData.fromSensorTimeWithQuat(sensorTimeSec, timeSync,
                wx, wy, wz, accel.ax, accel.ay, accel.az, temperature,
                qw, qx, qy, qz);
    }
    
    /**
     * Get and remove the most recent pending accel.
     * Since firmware always sends accel first, then gyro, this is the correct match.
     * 
     * @return Most recent PendingAccel or null if none pending
     */
    @Nullable
    private PendingAccel getMostRecentAccel() {
        if (pendingAccel.isEmpty()) {
            return null;
        }
        
        // Find the entry with the highest (most recent) sensor timestamp
        Long mostRecentKey = null;
        for (Long key : pendingAccel.keySet()) {
            if (mostRecentKey == null || key > mostRecentKey) {
                mostRecentKey = key;
            }
        }
        
        // Remove and return the most recent accel
        // Also clear any older entries (they're orphaned)
        PendingAccel result = pendingAccel.remove(mostRecentKey);
        if (pendingAccel.size() > 0) {
            android.util.Log.d(TAG, "Clearing " + pendingAccel.size() + " orphaned accel entries");
            pendingAccel.clear();
        }
        return result;
    }

    /**
     * Find the closest accel timestamp within tolerance window.
     * Removes and returns the matching entry if found.
     * 
     * @param targetTimeMs Target timestamp to match
     * @param toleranceMs Maximum allowed difference in milliseconds
     * @return Matching PendingAccel or null if none within tolerance
     */
    @Nullable
    private PendingAccel findClosestAccel(long targetTimeMs, long toleranceMs) {
        Long bestKey = null;
        long bestDiff = Long.MAX_VALUE;
        
        for (Long key : pendingAccel.keySet()) {
            long diff = Math.abs(key - targetTimeMs);
            if (diff <= toleranceMs && diff < bestDiff) {
                bestDiff = diff;
                bestKey = key;
            }
        }
        
        if (bestKey != null) {
            return pendingAccel.remove(bestKey);
        }
        return null;
    }
    
    /**
     * Clean up old pending entries periodically to prevent memory leaks
     */
    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupTime = now;
        
        // Remove entries older than TIMEOUT_MS
        Iterator<Map.Entry<Long, PendingAccel>> it = pendingAccel.entrySet().iterator();
        while (it.hasNext()) {
            PendingAccel accel = it.next().getValue();
            if (now - accel.receivedTimeMs > TIMEOUT_MS) {
                it.remove();
            }
        }
    }
    
    /**
     * Clear all pending data (e.g., on disconnect)
     */
    public void clear() {
        pendingAccel.clear();
    }
}
