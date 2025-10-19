package com.platypii.baselinexr.jarvis;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.location.KalmanFilter3D;
import com.platypii.baselinexr.measurements.MLocation;

/**
 * Determines the current flight mode
 * Attempts to detect: ground, plane, wingsuit, freefall, canopy, etc
 */
public class FlightMode {

    static final int MODE_UNKNOWN = 0;
    public static final int MODE_GROUND = 1;
    public static final int MODE_PLANE = 2;
    public static final int MODE_WINGSUIT = 3;
    public static final int MODE_FREEFALL = 4;
    public static final int MODE_CANOPY = 5;

    /**
     * Human readable mode strings
     */
    private static final String[] modeString = {
            "", "Ground", "Plane", "Wingsuit", "Freefall", "Canopy"
    };

    /**
     * Predict flight mode based on instantaneous horizontal and vertical velocity.
     */
    public static int getMode(@NonNull MLocation loc) {
        final double groundSpeed = loc.groundSpeed();
        final double climb = loc.climb;

        if (-0.3 * groundSpeed + 7 < climb && 33 < groundSpeed) {
            return MODE_PLANE;
        } else if (climb < -13 && climb < -groundSpeed - 10 && groundSpeed < 19) {
            return MODE_FREEFALL;
        } else if (climb < groundSpeed - 32 && climb < -0.3 * groundSpeed + 5.5) {
            return MODE_WINGSUIT;
        } else if (climb < -17) {
            return MODE_WINGSUIT;
        } else if (isCanopyMode(loc, groundSpeed, climb)) {
            return MODE_CANOPY;
        } else if (groundSpeed + Math.abs(climb - 1) < 5) {
            return MODE_GROUND;
        } else if (-1 < climb && climb < 2 && !(groundSpeed > 10)) {
            return MODE_GROUND;
        } else {
            return MODE_UNKNOWN;
        }
    }

    /**
     * Enhanced canopy mode detection using sustained speeds from KL/KD coefficients
     */
    private static boolean isCanopyMode(@NonNull MLocation loc, double groundSpeed, double climb) {
        // First check basic canopy conditions as fallback
        boolean basicCanopyCondition = (-18 < climb && climb < -1.1 && groundSpeed - 31 < climb &&
                climb < groundSpeed - 4 && 1.1 < groundSpeed && groundSpeed < 23.5 &&
                climb < -groundSpeed + 20);

        // Try to get sustained speeds from KalmanFilter3D
        if (Services.location != null && Services.location.motionEstimator instanceof KalmanFilter3D) {
            KalmanFilter3D kf3d = (KalmanFilter3D) Services.location.motionEstimator;
            KalmanFilter3D.KFState predictedState = kf3d.getCachedPredictedState(System.currentTimeMillis());

            double kl = predictedState.kl();
            double kd = predictedState.kd();



            final double klkd_squared = kl * kl + kd * kd;
            final double klkd_power = Math.pow(klkd_squared, 0.75);

            final double vxs = kl / klkd_power;
            final double vys = -kd / klkd_power;
            // Canopy detection using sustained speeds with same logic as original condition
            // Original: (-11.5 < climb && climb < -1.1 && groundSpeed - 31 < climb && climb < groundSpeed - 4 && 1.1 < groundSpeed && groundSpeed < 23.5 && climb < -groundSpeed + 20)
            // Translated to sustained speeds: (-11.5 < vys && vys < -1.1 && vxs - 31 < vys && vys < vxs - 4 && 1.1 < vxs && vxs < 23.5 && vys < -vxs + 20)
            boolean sustainedSpeedCanopy = (-18 < vys && vys < -1.1 &&
                    vxs - 31 < vys && vys < vxs - 4 &&
                    1.1 < vxs && vxs < 23.5 &&
                    vys < -vxs + 20);

            return sustainedSpeedCanopy;

        }

        // Fall back to basic condition if sustained speeds not available
        return basicCanopyCondition;
    }

    public static boolean isFlight(int mode) {
        return mode == MODE_PLANE || mode == MODE_WINGSUIT || mode == MODE_FREEFALL || mode == MODE_CANOPY;
    }

    static String getModeString(int mode) {
        return modeString[mode];
    }

}
