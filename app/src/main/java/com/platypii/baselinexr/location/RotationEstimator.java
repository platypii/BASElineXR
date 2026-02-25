package com.platypii.baselinexr.location;

import androidx.annotation.Nullable;

import com.meta.spatial.core.Quaternion;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.MMagData;

/**
 * Interface for rotation estimation algorithms (AHRS).
 * Parallel to MotionEstimator but for orientation instead of position.
 * 
 * Implementations receive IMU and magnetometer updates and provide
 * orientation predictions for rendering.
 */
public interface RotationEstimator {

    /**
     * Update with new IMU data (gyroscope + accelerometer)
     */
    void updateImu(MImuData imu);

    /**
     * Update with new magnetometer data
     */
    void updateMag(MMagData mag);

    /**
     * Update with new GPS data (for GPS-aided heading)
     */
    void updateGps(MLocation gps);

    /**
     * Predict current orientation at the given time.
     * Uses gyroscope integration to extrapolate from last update.
     *
     * @param currentTimeMillis Current wall-clock time in milliseconds
     * @return Predicted orientation quaternion (w, x, y, z)
     */
    Quaternion predict(long currentTimeMillis);

    /**
     * Get the current heading in radians (0 = North, increasing clockwise/East)
     */
    float getHeadingRad();

    /**
     * Get the current pitch in radians (positive = nose up)
     */
    float getPitchRad();

    /**
     * Get the current roll in radians (positive = right wing down)
     */
    float getRollRad();

    /**
     * Get the last IMU update for timing purposes
     */
    @Nullable
    MImuData getLastImuUpdate();

    /**
     * Reset to identity orientation
     */
    void reset();

    /**
     * State snapshot for inspection
     */
    record State(Quaternion orientation, float heading, float pitch, float roll) {
    }
}
