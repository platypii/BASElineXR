package com.platypii.baselinexr.location;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.tensor.Vector3;

public interface MotionEstimator {
    void update(MLocation gps);
    
    Vector3 predictDelta(long currentTimeMillis);
    
    double ld();

    MLocation getLastUpdate();

    record State(Vector3 position, Vector3 velocity, Vector3 acceleration) {
    }
}