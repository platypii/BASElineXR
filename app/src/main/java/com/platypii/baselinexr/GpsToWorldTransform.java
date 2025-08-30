package com.platypii.baselinexr;

import android.util.Log;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.LatLngAlt;
import com.platypii.baselinexr.location.MotionEstimator;

public class GpsToWorldTransform {
    private static final String TAG = "GpsToWorldTransform";

    public MLocation initialOrigin;
    public MLocation lastOrigin;
    
    public void setOrigin(MLocation location) {
        // Store initial origin on first call
        if (initialOrigin == null) {
            this.initialOrigin = location;
        }
        lastOrigin = location;
    }


    /**
     * Convert GPS coordinates to world coordinates with MotionEstimator-based extrapolation.
     * @param lat Latitude
     * @param lon Longitude
     * @param alt Altitude
     * @param currentTimeMillis Current timestamp for extrapolation
     * @param motionEstimator MotionEstimator instance for sophisticated prediction
     * @return World coordinates with position extrapolated using MotionEstimator or fallback velocity-based extrapolation
     */
    public Vector3 toWorldCoordinates(double lat, double lon, double alt, long currentTimeMillis, MotionEstimator motionEstimator) {
        // Fix race with location service:
        if (motionEstimator != null && motionEstimator.getLastUpdate() != null) {
            setOrigin(motionEstimator.getLastUpdate());
        }
        if (lastOrigin == null) {
//            Log.w(TAG, "Origin must be set before converting coordinates");
            return new Vector3(0f);
        }

        // First convert the GPS position to world coordinates
        LatLngAlt from = lastOrigin.toLatLngAlt();
        LatLngAlt to = new LatLngAlt(lat, lon, alt);
        Vector3 basePosition = GeoUtils.calculateOffset(from, to);

        float extrapolatedX = basePosition.getX();
        float extrapolatedY = basePosition.getY();
        float extrapolatedZ = basePosition.getZ();

        // If motion estimator is available, use it for velocity and acceleration-based prediction
        if (motionEstimator != null && lastOrigin.millis > 0 && currentTimeMillis > lastOrigin.millis) {
            com.platypii.baselinexr.util.tensor.Vector3 delta = motionEstimator.predictDelta(currentTimeMillis);

            // Calculate position with predicted delta
            extrapolatedX = basePosition.getX() - (float) delta.x;
            extrapolatedY = basePosition.getY() - (float) delta.y;
            extrapolatedZ = basePosition.getZ() - (float) delta.z;

//            Log.i(TAG, "extra: " + extrapolatedX + " " + extrapolatedY + " " + extrapolatedZ + " delta: " + delta);
        } else if (lastOrigin.millis > 0 && currentTimeMillis > lastOrigin.millis) {
            // Fall back to original velocity-based extrapolation if no motion estimator
            double deltaTime = (currentTimeMillis - lastOrigin.millis) / 1000.0;
            extrapolatedX = basePosition.getX() - (float)(lastOrigin.vE * deltaTime);
            extrapolatedY = basePosition.getY() - (float)(lastOrigin.climb * deltaTime);
            extrapolatedZ = basePosition.getZ() - (float)(lastOrigin.vN * deltaTime);
        }

        // Apply yaw adjustment rotation
        if (Adjustments.yawAdjustment != 0.0) {
            double cos = Math.cos(-Adjustments.yawAdjustment);
            double sin = Math.sin(-Adjustments.yawAdjustment);
            double rotatedEast = extrapolatedX * cos - extrapolatedZ * sin;
            double rotatedNorth = extrapolatedX * sin + extrapolatedZ * cos;
            extrapolatedX = (float) rotatedEast;
            extrapolatedZ = (float) rotatedNorth;
        }

        return new Vector3(extrapolatedX, extrapolatedY, extrapolatedZ);
    }

}