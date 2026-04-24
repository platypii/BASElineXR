package com.platypii.baselinexr.bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MTimeSync;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Simple IMU data combiner.
 * 
 * FlySight 2 sends accel and gyro as separate BLE packets.
 * We store the latest accel data and combine with gyro when it arrives
 * to produce a unified MImuData. No timestamp correlation - just use latest values.
 */
public class ImuCorrelator {
    private static final String TAG = "ImuCorrelator";
    
    // Latest accelerometer data
    private float latestAx = 0;
    private float latestAy = 0;
    private float latestAz = 0;
    private float latestAccelTemp = Float.NaN;
    private boolean hasAccel = false;
    
    /**
     * Process accelerometer BLE packet. Stores latest values for next gyro packet.
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
        
        // Skip timestamp if present
        if ((mask & 0x80) != 0) {
            buf.getInt();
        }
        
        // Parse accelerometer data (if present)
        if ((mask & 0x40) != 0) {
            latestAx = buf.getInt() / 100000f;  // g × 100000 -> g
            latestAy = buf.getInt() / 100000f;
            latestAz = buf.getInt() / 100000f;
            hasAccel = true;
        }
        
        // Parse temperature (if present)
        if ((mask & 0x20) != 0) {
            latestAccelTemp = buf.getShort() / 100f;  // 0.01°C -> °C
        }
    }
    
    /**
     * Process gyroscope BLE packet. Combines with latest accel to produce MImuData.
     * 
     * @param value Raw BLE characteristic value
     * @param timeSync Time synchronization (nullable if not yet synced)
     * @return Combined MImuData, or null if no accel data available yet
     */
    @Nullable
    public MImuData onGyroReceived(@NonNull byte[] value, @Nullable MTimeSync timeSync) {
        if (value.length < 1) {
            android.util.Log.w(TAG, "onGyroReceived: empty packet");
            return null;
        }
        
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        int mask = buf.get() & 0xFF;
        
        // Parse timestamp
        long sensorTimeMs = 0;
        if ((mask & 0x80) != 0) {
            sensorTimeMs = Integer.toUnsignedLong(buf.getInt());
        }
        
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
        // Bytes 19-26: qw, qx, qy, qz as int16_t × 10000 (little-endian)
        // Frame: NWU (North-West-Up) - see docs/FlySight2-Coordinate-Systems.md
        // Identity (1,0,0,0): device flat, +Y local = North, +X local = East, +Z local = Up
        float qw = Float.NaN, qx = Float.NaN, qy = Float.NaN, qz = Float.NaN;
        if ((mask & 0x10) != 0) {
            short rawW = buf.getShort();
            short rawX = buf.getShort();
            short rawY = buf.getShort();
            short rawZ = buf.getShort();
            qw = rawW / 10000f;
            qx = rawX / 10000f;
            qy = rawY / 10000f;
            qz = rawZ / 10000f;
        }
        
        if (!hasAccel) {
            android.util.Log.w(TAG, "onGyroReceived: no accel data yet");
            return null;
        }
        
        // Use accel temperature if gyro temperature not available
        float temperature = Float.isNaN(gyroTemp) ? latestAccelTemp : gyroTemp;
        
        // Convert sensor time to GPS millis
        double sensorTimeSec = sensorTimeMs / 1000.0;
        
        // Create combined IMU data with quaternion
        return MImuData.fromSensorTimeWithQuat(sensorTimeSec, timeSync,
                wx, wy, wz, latestAx, latestAy, latestAz, temperature,
                qw, qx, qy, qz);
    }
    
    /**
     * Clear stored data (e.g., on disconnect)
     */
    public void clear() {
        hasAccel = false;
        latestAx = latestAy = latestAz = 0;
        latestAccelTemp = Float.NaN;
    }
}
