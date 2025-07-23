package com.platypii.baselinexr;

import android.util.Log;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.location.MotionEstimator;

public class GpsToWorldTransform {
    private static final String TAG = "GpsToWorldTransform";
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    public MLocation initialOrigin;
    public MLocation lastOrigin;

    public void setOrigin(MLocation location) {
        // Store initial origin on first call
        if (initialOrigin == null) {
            this.initialOrigin = location;
        }
        lastOrigin = location;
    }

    private Vector3 toWorldCoordinates(double lat, double lon, double alt) {
        if (lastOrigin == null) {
            Log.w(TAG, "Origin must be set before converting coordinates");
            return new Vector3(0f);
        }

        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        double originLatRad = Math.toRadians(lastOrigin.latitude);
        double originLonRad = Math.toRadians(lastOrigin.longitude);

        double deltaLat = latRad - originLatRad;
        double deltaLon = lonRad - originLonRad;

        double north = deltaLat * EARTH_RADIUS_METERS;
        double east = deltaLon * EARTH_RADIUS_METERS * Math.cos(originLatRad);
        double up = alt - lastOrigin.altitude_gps;

        return new Vector3((float)east, (float)up, (float)north);
    }

    public Vector3 toWorldCoordinates(MLocation location) {
        return toWorldCoordinates(location.latitude, location.longitude, location.altitude_gps, location.millis);
    }

    /**
     * Convert GPS coordinates to world coordinates with position extrapolation.
     * @param lat Latitude
     * @param lon Longitude
     * @param alt Altitude
     * @param currentTimeMillis Current timestamp for extrapolation
     * @return World coordinates with position extrapolated based on velocity
     */
    public Vector3 toWorldCoordinates(double lat, double lon, double alt, long currentTimeMillis) {
        if (lastOrigin == null) {
            Log.w(TAG, "Origin must be set before converting coordinates");
            return new Vector3(0f);
        }
        
        // First convert the GPS position to world coordinates
        Vector3 basePosition = toWorldCoordinates(lat, lon, alt);
        
        // If we have velocity data and a valid timestamp, extrapolate
        if (lastOrigin.millis > 0 && currentTimeMillis > lastOrigin.millis) {
            double deltaTime = (currentTimeMillis - lastOrigin.millis) / 1000.0; // Convert to seconds

            // Extrapolate position: position = position + velocity * deltaTime
            float extrapolatedX = basePosition.getX() + (float)lastOrigin.vE * (float)deltaTime;
            float extrapolatedY = basePosition.getY() + (float)lastOrigin.climb * (float)deltaTime;
            float extrapolatedZ = basePosition.getZ() + (float)lastOrigin.vN * (float)deltaTime;

            return new Vector3(extrapolatedX, extrapolatedY, extrapolatedZ);
        }
        
        return basePosition;
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
        if (lastOrigin == null) {
            Log.w(TAG, "Origin must be set before converting coordinates");
            return new Vector3(0f);
        }

        // First convert the GPS position to world coordinates
        Vector3 basePosition = toWorldCoordinates(lat, lon, alt);

        // If motion estimator is available, use it for velocity and acceleration-based prediction
        if (motionEstimator != null && lastOrigin.millis > 0 && currentTimeMillis > lastOrigin.millis) {
            // Calculate time delta
            double deltaTime = (currentTimeMillis - lastOrigin.millis) / 1000.0; // Convert to seconds

            // Extract velocity and acceleration from predicted state
            // MotionEstimator uses ENU (East, North, Up)
            double vE = motionEstimator.v.x;
            double vN = motionEstimator.v.y;
            double vU = motionEstimator.v.z;

            double aE = motionEstimator.a.x;
            double aN = motionEstimator.a.y;
            double aU = motionEstimator.a.z;

//            Log.i(TAG, "dt " + deltaTime + " vE " + vE + " vN " + vN + " vU " + vU + " aE " + aE  + " aN " + aN + " aU " + aU);
//            Log.i(TAG, "gps vE " + lastOrigin.vE + " vN " + lastOrigin.vN + " vU " + lastOrigin.climb);

            // Calculate position update using velocity
            // position = basePosition + velocity * deltaTime
//            float extrapolatedX = basePosition.getX() - (float)(vE * deltaTime);
//            float extrapolatedY = basePosition.getY() - (float)(vU * deltaTime);
//            float extrapolatedZ = basePosition.getZ() - (float)(vN * deltaTime);

                    // Calculate position update using velocity and acceleration
            // position = basePosition + velocity * deltaTime + 0.5 * acceleration * deltaTime^2
            float extrapolatedX = basePosition.getX() - (float)(vE * deltaTime + 0.5 * aE * deltaTime * deltaTime);
            float extrapolatedY = basePosition.getY() - (float)(vU * deltaTime + 0.5 * aU * deltaTime * deltaTime);
            float extrapolatedZ = basePosition.getZ() - (float)(vN * deltaTime + 0.5 * aN * deltaTime * deltaTime);

//            Log.i(TAG, "PROJECT ACC x " + extrapolatedX + " y " + extrapolatedY +" z " + extrapolatedZ);

            return new Vector3(extrapolatedX, extrapolatedY, extrapolatedZ);
        }

        // Fall back to original velocity-based extrapolation if no motion estimator
        if (lastOrigin.millis > 0 && currentTimeMillis > lastOrigin.millis) {
            double deltaTime = (currentTimeMillis - lastOrigin.millis) / 1000.0;
            float extrapolatedX = basePosition.getX() - (float)(lastOrigin.vE * deltaTime);
            float extrapolatedY = basePosition.getY() - (float)(lastOrigin.climb * deltaTime);
            float extrapolatedZ = basePosition.getZ() - (float)(lastOrigin.vN * deltaTime);

//            Log.i(TAG, "PROJECT VEL x " + extrapolatedX + " y " + extrapolatedY +" z " + extrapolatedZ);

            return new Vector3(extrapolatedX, extrapolatedY, extrapolatedZ);
        }

        return basePosition;
    }
    
    /**
     * Calculate the world space offset between the initial origin and the current origin.
     * This can be used to shift trail positions when the origin is updated.
     */
    public Vector3 getOriginDelta() {
        if (initialOrigin == null || lastOrigin == null) {
            return new Vector3(0, 0, 0);
        }
        
        // Calculate the difference between current origin and initial origin
        double latRad = Math.toRadians(lastOrigin.latitude);
        double lonRad = Math.toRadians(lastOrigin.longitude);
        double initialLatRad = Math.toRadians(initialOrigin.latitude);
        double initialLonRad = Math.toRadians(initialOrigin.longitude);
        
        double deltaLat = latRad - initialLatRad;
        double deltaLon = lonRad - initialLonRad;
        
        // Use initial origin latitude for consistency with coordinate conversion
        double north = deltaLat * EARTH_RADIUS_METERS;
        double east = deltaLon * EARTH_RADIUS_METERS * Math.cos(initialLatRad);
        double up = lastOrigin.altitude_gps - initialOrigin.altitude_gps;
        
        return new Vector3((float)east, (float)up, (float)north);
    }
}