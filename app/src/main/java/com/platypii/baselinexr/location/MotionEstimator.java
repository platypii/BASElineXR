package com.platypii.baselinexr.location;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.tensor.Vector3;

public interface MotionEstimator {
    void update(MLocation gps);
    
    Vector3 predictDelta(long currentTimeMillis);
    
    double ld();

    MLocation getLastUpdate();
    
    /**
     * Freeze the motion estimator - predictDelta will return zero instead of extrapolating.
     * Used when playback is paused or stopped.
     */
    void freeze();
    
    /**
     * Unfreeze the motion estimator - resume normal prediction.
     * Used when playback resumes.
     */
    void unfreeze();
    
    /**
     * Check if the motion estimator is frozen.
     */
    boolean isFrozen();
    
    /**
     * Reset the motion estimator state completely.
     * Called when restarting playback from the beginning.
     * Clears all state including timing references for a fresh start.
     */
    void reset();
    
    /**
     * Soft reset - clears cached predictions but preserves filter state.
     * Called when seeking. The filter will naturally converge when it receives new GPS points.
     */
    default void softReset() {
        // Default implementation is same as reset for backward compatibility
        reset();
    }

    record State(Vector3 position, Vector3 velocity, Vector3 acceleration) {
    }
}