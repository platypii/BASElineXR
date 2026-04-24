package com.platypii.baselinexr.location;

import android.util.Log;

import androidx.annotation.Nullable;

import com.meta.spatial.core.Quaternion;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.MMagData;

/**
 * Simple rotation estimator that integrates gyroscope data.
 * This is a placeholder implementation - will be replaced with Madgwick AHRS.
 */
public class SimpleRotationEstimator implements RotationEstimator {
    private static final String TAG = "RotationEstimator";

    // Current orientation quaternion (w, x, y, z)
    private float qw = 1f;
    private float qx = 0f;
    private float qy = 0f;
    private float qz = 0f;

    // Last update timestamps
    @Nullable
    private MImuData lastImu = null;
    @Nullable
    private MMagData lastMag = null;

    // Euler angles (cached)
    private float heading = 0f;
    private float pitch = 0f;
    private float roll = 0f;

    // Logging rate limiter (log every N updates)
    private int imuUpdateCount = 0;
    private static final int LOG_EVERY_N_UPDATES = 50; // ~1 log per second at 50Hz IMU

    @Override
    public void updateImu(MImuData imu) {
        if (lastImu != null) {
            // Calculate delta time in seconds
            double dt = (imu.millis - lastImu.millis) * 0.001;
            if (dt > 0 && dt < 1.0) {  // Sanity check
                // Convert gyro from deg/s to rad/s
                float gx = (float) Math.toRadians(imu.gyroX);
                float gy = (float) Math.toRadians(imu.gyroY);
                float gz = (float) Math.toRadians(imu.gyroZ);

                // Integrate gyroscope (simple Euler integration)
                integrateGyro(gx, gy, gz, (float) dt);

                // Update Euler angles from quaternion
                updateEulerAngles();

                // Periodic logging of orientation
                imuUpdateCount++;
                if (imuUpdateCount % LOG_EVERY_N_UPDATES == 0) {
                    Log.d(TAG, String.format("Orientation: heading=%.1f° pitch=%.1f° roll=%.1f° (gyro: %.1f, %.1f, %.1f)",
                            Math.toDegrees(heading), Math.toDegrees(pitch), Math.toDegrees(roll),
                            imu.gyroX, imu.gyroY, imu.gyroZ));
                }
            }
        }
        lastImu = imu;
    }

    @Override
    public void updateMag(MMagData mag) {
        lastMag = mag;
        // TODO: Use magnetometer for heading correction
    }

    @Override
    public void updateGps(MLocation gps) {
        // TODO: Use GPS velocity for heading when moving fast
    }

    @Override
    public Quaternion predict(long currentTimeMillis) {
        // For now, just return current orientation
        // TODO: Extrapolate using last gyro reading
        return new Quaternion(qx, qy, qz, qw);
    }

    @Override
    public float getHeadingRad() {
        return heading;
    }

    @Override
    public float getPitchRad() {
        return pitch;
    }

    @Override
    public float getRollRad() {
        return roll;
    }

    @Override
    @Nullable
    public MImuData getLastImuUpdate() {
        return lastImu;
    }

    @Override
    public void reset() {
        qw = 1f;
        qx = 0f;
        qy = 0f;
        qz = 0f;
        heading = 0f;
        pitch = 0f;
        roll = 0f;
        lastImu = null;
        lastMag = null;
    }

    /**
     * Integrate gyroscope readings into quaternion
     */
    private void integrateGyro(float gx, float gy, float gz, float dt) {
        // Quaternion derivative: q_dot = 0.5 * q * omega
        // where omega = (0, gx, gy, gz)
        float halfDt = 0.5f * dt;

        float dqw = halfDt * (-qx * gx - qy * gy - qz * gz);
        float dqx = halfDt * (qw * gx + qy * gz - qz * gy);
        float dqy = halfDt * (qw * gy - qx * gz + qz * gx);
        float dqz = halfDt * (qw * gz + qx * gy - qy * gx);

        qw += dqw;
        qx += dqx;
        qy += dqy;
        qz += dqz;

        // Normalize quaternion
        float norm = (float) Math.sqrt(qw * qw + qx * qx + qy * qy + qz * qz);
        if (norm > 0) {
            qw /= norm;
            qx /= norm;
            qy /= norm;
            qz /= norm;
        }
    }

    /**
     * Update Euler angles from current quaternion
     */
    private void updateEulerAngles() {
        // Roll (x-axis rotation)
        float sinr_cosp = 2f * (qw * qx + qy * qz);
        float cosr_cosp = 1f - 2f * (qx * qx + qy * qy);
        roll = (float) Math.atan2(sinr_cosp, cosr_cosp);

        // Pitch (y-axis rotation)
        float sinp = 2f * (qw * qy - qz * qx);
        if (Math.abs(sinp) >= 1) {
            pitch = (float) Math.copySign(Math.PI / 2, sinp);
        } else {
            pitch = (float) Math.asin(sinp);
        }

        // Yaw/Heading (z-axis rotation)
        float siny_cosp = 2f * (qw * qz + qx * qy);
        float cosy_cosp = 1f - 2f * (qy * qy + qz * qz);
        heading = (float) Math.atan2(siny_cosp, cosy_cosp);
    }
}
