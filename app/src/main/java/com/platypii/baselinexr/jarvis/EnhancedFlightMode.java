package com.platypii.baselinexr.jarvis;

import android.util.Log;
import androidx.annotation.NonNull;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.location.KalmanFilter3D;
import com.platypii.baselinexr.measurements.MLocation;

/**
 * Enhanced flight mode detection with improved reliability and transition detection.
 * Adds DEPLOY and LANDING modes for better state tracking.
 * Uses gated transitions to prevent mode oscillation while maintaining fast response.
 */
public class EnhancedFlightMode {
    private static final String TAG = "EnhancedFlightMode";

    // Extended mode constants
    public static final int MODE_UNKNOWN = 0;
    public static final int MODE_GROUND = 1;
    public static final int MODE_PLANE = 2;
    public static final int MODE_WINGSUIT = 3;
    public static final int MODE_FREEFALL = 4;
    public static final int MODE_DEPLOY = 5;
    public static final int MODE_CANOPY = 6;
    public static final int MODE_LANDING = 7;

    /**
     * Human readable mode strings
     */
    private static final String[] modeString = {
            "Unknown", "Ground", "Plane", "Wingsuit", "Freefall", "Deploy", "Canopy", "Landing"
    };

    // Current mode state
    private int currentMode = MODE_UNKNOWN;

    // Slow mode detection (for stability)
    private int slowMode = MODE_UNKNOWN;
    private double slowModeConfidence = 0.0;
    private static final double SLOW_ALPHA = 0.1; // Slow integration rate
    private static final double SLOW_THRESHOLD = 0.7; // Confidence threshold for slow mode change

    // Fast mode detection (for transitions)
    private int fastMode = MODE_UNKNOWN;

    // Deploy detection state
    private boolean deployDetected = false;
    private double deployConfidence = 0.0;
    private boolean wingsuitEstablished = false;  // Track if vxs has been >19 in wingsuit mode
    private static final double DEPLOY_ALPHA_FAST = 0.3;  // Integration speed for deploy detection
    private static final double DEPLOY_THRESHOLD = 0.6;  // Confidence threshold for deploy mode

    // Landing detection state
    private boolean landingDetected = false;
    private double landingConfidence = 0.0;
    private static final double LANDING_ALPHA = 0.25; // Faster integration for landing detection
    private static final double LANDING_THRESHOLD = 0.5; // Lower threshold for earlier detection

    // Altitude tracking for landing detection
    private double altMin = Double.NaN;
    private double altMax = Double.NaN;
    private static final double MIN_JUMP_HEIGHT = 60; // meters

    // Last update time for 1Hz processing
    private long lastSlowUpdate = 0;

    /**
     * Update flight mode with new location data.
     * Called at GPS rate (~5-10Hz).
     */
    public void update(@NonNull MLocation loc) {
        // Fast update every sample
        updateFast(loc);

        // Slow update at most once per second
        if (loc.millis - lastSlowUpdate >= 1000) {
            lastSlowUpdate = loc.millis;
            updateSlow(loc);
        }

        // Apply gated transitions
        currentMode = applyTransitionGates(loc);
    }

    /**
     * Fast update for transition detection (runs every GPS sample)
     */
    private void updateFast(@NonNull MLocation loc) {
        // Get basic flight mode from FlightMode
        fastMode = FlightMode.getMode(loc);

        // Get sustained speeds if available
        double vxs = 0.0;
        double vys = 0.0;
        if (Services.location != null && Services.location.motionEstimator instanceof KalmanFilter3D) {
            KalmanFilter3D kf3d = (KalmanFilter3D) Services.location.motionEstimator;
            KalmanFilter3D.KFState state = kf3d.getCachedPredictedState(System.currentTimeMillis());
            double kl = state.kl();
            double kd = state.kd();
            if (!Double.isNaN(kl) && !Double.isNaN(kd)) {
                final double klkd_squared = kl * kl + kd * kd;
                final double klkd_power = Math.pow(klkd_squared, 0.75);
                vxs = kl / klkd_power;
                vys = -kd / klkd_power;
            }
        }

        // Deploy detection based on current mode
        if (currentMode == MODE_WINGSUIT) {
            // Check if wingsuit mode is established (vxs has been >19)
            if (vxs > 19) {
                wingsuitEstablished = true;
            }

            // WINGSUIT->DEPLOY triggered by vxs < 19
            // Only allow deploy detection once wingsuit mode is established
            if (wingsuitEstablished && vxs < 19 && vxs > 0) {
                deployConfidence += (1 - deployConfidence) * DEPLOY_ALPHA_FAST;
            } else {
                deployConfidence *= (1 - DEPLOY_ALPHA_FAST);
            }
            deployDetected = deployConfidence > DEPLOY_THRESHOLD;
        } else if (currentMode == MODE_FREEFALL) {
            // FREEFALL->DEPLOY triggered by vys > -33
            if (vys > -33 && vys < 0) {
                deployConfidence += (1 - deployConfidence) * DEPLOY_ALPHA_FAST;
            } else {
                deployConfidence *= (1 - DEPLOY_ALPHA_FAST);
            }
            deployDetected = deployConfidence > DEPLOY_THRESHOLD;
        } else {
            // Reset deploy detection in other modes
            deployConfidence = 0.0;
            deployDetected = false;
            wingsuitEstablished = false;
        }
    }

    /**
     * Slow update for mode stability (runs at ~1Hz)
     */
    private void updateSlow(@NonNull MLocation loc) {
        // Update altitude tracking
        final double alt = loc.altitude_gps;
        if (!Double.isNaN(alt)) {
            if (Double.isNaN(altMin) || alt < altMin) altMin = alt;
            if (Double.isNaN(altMax) || alt > altMax) altMax = alt;
        }

        // Slow mode integration for stability
        if (fastMode == slowMode) {
            // Reinforce current slow mode
            slowModeConfidence += (1 - slowModeConfidence) * SLOW_ALPHA * 2;
        } else {
            // Different mode, reduce confidence
            slowModeConfidence *= (1 - SLOW_ALPHA);
            if (slowModeConfidence < (1 - SLOW_THRESHOLD)) {
                // Switch slow mode
                slowMode = fastMode;
                slowModeConfidence = 0.5;
            }
        }

        // Landing detection (only from CANOPY or LANDING mode)
        if (currentMode == MODE_CANOPY || currentMode == MODE_LANDING) {
            boolean onGround = FlightMode.getMode(loc) == FlightMode.MODE_GROUND;
            boolean slowSpeed = loc.groundSpeed() < 7; // Slightly higher speed threshold
            boolean lowClimb = Math.abs(loc.climb) < 3; // Low vertical speed
            boolean significantAlt = !Double.isNaN(altMax) && !Double.isNaN(altMin) && (altMax - altMin) > MIN_JUMP_HEIGHT;
            boolean nearMinAlt = !Double.isNaN(alt) && !Double.isNaN(altMin) && !Double.isNaN(altMax);

            if ((onGround || (slowSpeed && lowClimb)) && significantAlt && nearMinAlt) {
                // Weight by normalized altitude (closer to min = more likely landed)
                double altNormalized = (alt - altMin) / (altMax - altMin);
                landingConfidence += (1 - landingConfidence) * (1 - altNormalized) * LANDING_ALPHA;
            } else {
                landingConfidence *= (1 - LANDING_ALPHA * 0.3);
            }
            landingDetected = landingConfidence > LANDING_THRESHOLD;
        } else {
            landingConfidence = 0.0;
            landingDetected = false;
        }
    }

    /**
     * Apply transition gates to ensure only valid mode sequences occur.
     * Prevents mode oscillation while maintaining fast transition response.
     */
    private int applyTransitionGates(@NonNull MLocation loc) {
        // Basic ground detection
        final double groundSpeed = loc.groundSpeed();
        final double climb = loc.climb;
        if (groundSpeed + Math.abs(climb - 1) < 5 && currentMode == MODE_GROUND) {
            return MODE_GROUND;
        }

        switch (currentMode) {
            case MODE_GROUND:
                // Can transition to PLANE, WINGSUIT, FREEFALL, or stay GROUND
                if (fastMode == FlightMode.MODE_PLANE) {
                    Log.i(TAG, "Transition: GROUND -> PLANE");
                    return MODE_PLANE;
                } else if (fastMode == FlightMode.MODE_WINGSUIT) {
                    Log.i(TAG, "Transition: GROUND -> WINGSUIT");
                    return MODE_WINGSUIT;
                } else if (fastMode == FlightMode.MODE_FREEFALL) {
                    Log.i(TAG, "Transition: GROUND -> FREEFALL");
                    return MODE_FREEFALL;
                }
                return MODE_GROUND;

            case MODE_PLANE:
                // Can transition to WINGSUIT, FREEFALL, or back to GROUND
                if (fastMode == FlightMode.MODE_WINGSUIT) {
                    Log.i(TAG, "Transition: PLANE -> WINGSUIT");
                    return MODE_WINGSUIT;
                } else if (fastMode == FlightMode.MODE_FREEFALL) {
                    Log.i(TAG, "Transition: PLANE -> FREEFALL");
                    return MODE_FREEFALL;
                } else if (fastMode == FlightMode.MODE_GROUND) {
                    Log.i(TAG, "Transition: PLANE -> GROUND");
                    return MODE_GROUND;
                }
                return MODE_PLANE;

            case MODE_WINGSUIT:
                // Can transition to DEPLOY or back to PLANE (error recovery)
                if (deployDetected) {
                    Log.i(TAG, "Transition: WINGSUIT -> DEPLOY");
                    return MODE_DEPLOY;
                } else if (fastMode == FlightMode.MODE_PLANE) {
                    Log.i(TAG, "Transition: WINGSUIT -> PLANE (error recovery)");
                    return MODE_PLANE;
                }
                return MODE_WINGSUIT;

            case MODE_FREEFALL:
                // Can only transition to DEPLOY or WINGSUIT
                if (deployDetected) {
                    Log.i(TAG, "Transition: FREEFALL -> DEPLOY");
                    return MODE_DEPLOY;
                } else if (fastMode == FlightMode.MODE_WINGSUIT) {
                    Log.i(TAG, "Transition: FREEFALL -> WINGSUIT");
                    return MODE_WINGSUIT;
                }
                return MODE_FREEFALL;

            case MODE_DEPLOY:
                // DEPLOY always transitions to CANOPY
                if (fastMode == FlightMode.MODE_CANOPY) {
                    Log.i(TAG, "Transition: DEPLOY -> CANOPY");
                    return MODE_CANOPY;
                }
                // Stay in DEPLOY until canopy detected
                return MODE_DEPLOY;

            case MODE_CANOPY:
                // Can only transition to LANDING
                // Note: CANOPY can only be entered through DEPLOY mode, so we should never get stuck here
                if (landingDetected) {
                    Log.i(TAG, "Transition: CANOPY -> LANDING");
                    return MODE_LANDING;
                }
                return MODE_CANOPY;

            case MODE_LANDING:
                // Can only transition to GROUND
                if (fastMode == FlightMode.MODE_GROUND && landingDetected) {
                    Log.i(TAG, "Transition: LANDING -> GROUND");
                    resetJumpState();
                    return MODE_GROUND;
                }
                return MODE_LANDING;

            case MODE_UNKNOWN:
            default:
                // Use fast mode to initialize
                return mapBasicModeToEnhanced(fastMode);
        }
    }

    /**
     * Map basic FlightMode constants to EnhancedFlightMode constants
     * Note: CANOPY mode is filtered out to prevent getting stuck in canopy
     * before actual deployment detection.
     */
    private int mapBasicModeToEnhanced(int basicMode) {
        switch (basicMode) {
            case FlightMode.MODE_GROUND:
                return MODE_GROUND;
            case FlightMode.MODE_PLANE:
                return MODE_PLANE;
            case FlightMode.MODE_WINGSUIT:
                return MODE_WINGSUIT;
            case FlightMode.MODE_FREEFALL:
                return MODE_FREEFALL;
            case FlightMode.MODE_CANOPY:
                // Don't map directly to canopy - must go through DEPLOY first
                // Return UNKNOWN to let current mode persist
                return MODE_UNKNOWN;
            default:
                return MODE_UNKNOWN;
        }
    }

    /**
     * Reset state after landing
     */
    private void resetJumpState() {
        altMin = Double.NaN;
        altMax = Double.NaN;
        deployConfidence = 0.0;
        deployDetected = false;
        landingConfidence = 0.0;
        landingDetected = false;
        slowModeConfidence = 0.0;
    }

    /**
     * Get current mode
     */
    public int getMode() {
        return currentMode;
    }

    /**
     * Get human readable mode string
     */
    public String getModeString() {
        if (currentMode >= 0 && currentMode < modeString.length) {
            return modeString[currentMode];
        }
        return "Unknown";
    }

    /**
     * Check if currently in flight
     */
    public boolean isFlight() {
        return currentMode == MODE_PLANE || currentMode == MODE_WINGSUIT ||
                currentMode == MODE_FREEFALL || currentMode == MODE_DEPLOY ||
                currentMode == MODE_CANOPY || currentMode == MODE_LANDING;
    }

    /**
     * Force mode (for testing or initialization)
     */
    public void setMode(int mode) {
        if (mode != currentMode) {
            Log.i(TAG, "Force mode change: " + modeString[currentMode] + " -> " + modeString[mode]);
            currentMode = mode;
            slowMode = toBasicMode(mode);
            fastMode = slowMode;
            slowModeConfidence = 1.0;
        }
    }

    /**
     * Map enhanced mode back to basic mode (for systems that don't need DEPLOY/LANDING granularity)
     * This is useful for wind estimation, polars, and other systems that treat DEPLOY and LANDING as CANOPY.
     */
    public static int toBasicMode(int enhancedMode) {
        switch (enhancedMode) {
            case MODE_GROUND:
                return FlightMode.MODE_GROUND;
            case MODE_PLANE:
                return FlightMode.MODE_PLANE;
            case MODE_WINGSUIT:
                return FlightMode.MODE_WINGSUIT;
            case MODE_FREEFALL:
                return FlightMode.MODE_FREEFALL;
            case MODE_DEPLOY:
            case MODE_CANOPY:
            case MODE_LANDING:
                return FlightMode.MODE_CANOPY;
            default:
                return FlightMode.MODE_UNKNOWN;
        }
    }

    /**
     * Reset to initial state
     */
    public void reset() {
        currentMode = MODE_GROUND;
        slowMode = MODE_UNKNOWN;
        fastMode = MODE_UNKNOWN;
        resetJumpState();
        slowModeConfidence = 0.0;
        lastSlowUpdate = 0;
    }
}
