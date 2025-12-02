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

    record State(Vector3 position, Vector3 velocity, Vector3 acceleration) {
    }
}