package com.platypii.baselinexr;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.location.MotionEstimator;

public class GpsToWorldTransform {
    private static final String TAG = "GpsToWorldTransform";
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    private static final String PREF_NAME = "BASElineXRPrefs";
    private static final String KEY_YAW_ADJUSTMENT = "yawAdjustment";

    public MLocation initialOrigin;
    public MLocation lastOrigin;
    
    // Yaw adjustment in radians for north orientation reset
    public double yawAdjustment = 0.0;

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
//            Log.w(TAG, "Origin must be set before converting coordinates");
            return new Vector3(0f);
        }

        // First convert the GPS position to world coordinates
        Vector3 basePosition = toWorldCoordinates(lat, lon, alt);

        float extrapolatedX = basePosition.getX();
        float extrapolatedY = basePosition.getY();
        float extrapolatedZ = basePosition.getZ();

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
            extrapolatedX = basePosition.getX() - (float)(vE * deltaTime + 0.5 * aE * deltaTime * deltaTime);
            extrapolatedY = basePosition.getY() - (float)(vU * deltaTime + 0.5 * aU * deltaTime * deltaTime);
            extrapolatedZ = basePosition.getZ() - (float)(vN * deltaTime + 0.5 * aN * deltaTime * deltaTime);
        }

        // Fall back to original velocity-based extrapolation if no motion estimator
        if (lastOrigin.millis > 0 && currentTimeMillis > lastOrigin.millis) {
            double deltaTime = (currentTimeMillis - lastOrigin.millis) / 1000.0;
            extrapolatedX = basePosition.getX() - (float)(lastOrigin.vE * deltaTime);
            extrapolatedY = basePosition.getY() - (float)(lastOrigin.climb * deltaTime);
            extrapolatedZ = basePosition.getZ() - (float)(lastOrigin.vN * deltaTime);
        }

        // Apply yaw adjustment rotation
        if (yawAdjustment != 0.0) {
            double cos = Math.cos(-yawAdjustment);
            double sin = Math.sin(-yawAdjustment);
            double rotatedEast = extrapolatedX * cos - extrapolatedZ * sin;
            double rotatedNorth = extrapolatedX * sin + extrapolatedZ * cos;
            extrapolatedX = (float) rotatedEast;
            extrapolatedZ = (float) rotatedNorth;
        }

        return new Vector3(extrapolatedX, extrapolatedY, extrapolatedZ);
    }

    public void loadYawAdjustment(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        yawAdjustment = Double.longBitsToDouble(prefs.getLong(KEY_YAW_ADJUSTMENT, 
            Double.doubleToLongBits(0.0)));
        Log.d(TAG, "Loaded yawAdjustment: " + Math.toDegrees(yawAdjustment) + " degrees");
    }

    public void saveYawAdjustment(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_YAW_ADJUSTMENT, Double.doubleToLongBits(yawAdjustment)).apply();
        Log.d(TAG, "Saved yawAdjustment: " + Math.toDegrees(yawAdjustment) + " degrees");
    }

}