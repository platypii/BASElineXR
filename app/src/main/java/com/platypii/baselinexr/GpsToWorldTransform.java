package com.platypii.baselinexr;

import android.util.Log;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.MLocation;

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

        // Use more accurate geodetic calculations
        double[] worldCoords = latlonToMeters(lat, lon, lastOrigin.latitude, lastOrigin.longitude);
        double east = worldCoords[0];
        double north = worldCoords[1];
        double up = alt - lastOrigin.altitude_gps;

        return new Vector3((float)east, (float)up, (float)north);
    }

    /**
     * Convert lat/lon coordinates to meters from origin using accurate geodetic calculations.
     * Uses the haversine formula for distance and proper bearing calculations.
     *
     * @param lat Target latitude in degrees
     * @param lon Target longitude in degrees
     * @param originLat Origin latitude in degrees
     * @param originLon Origin longitude in degrees
     * @return Array containing [east_meters, north_meters]
     */
    private double[] latlonToMeters(double lat, double lon, double originLat, double originLon) {
        // Convert to radians
        double lat1Rad = Math.toRadians(originLat);
        double lon1Rad = Math.toRadians(originLon);
        double lat2Rad = Math.toRadians(lat);
        double lon2Rad = Math.toRadians(lon);

        // Calculate distance using haversine formula
        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_METERS * c;

        // Calculate initial bearing from origin to target point
        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);
        double bearing = Math.atan2(y, x);

        // Convert to east/north components
        double east = distance * Math.sin(bearing);
        double north = distance * Math.cos(bearing);

        return new double[]{east, north};
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
            float extrapolatedX = basePosition.getX() - (float)lastOrigin.vE * (float)deltaTime;
            float extrapolatedY = basePosition.getY() - (float)lastOrigin.climb * (float)deltaTime;
            float extrapolatedZ = basePosition.getZ() - (float)lastOrigin.vN * (float)deltaTime;

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
        
        // Use accurate geodetic calculations for origin delta
        double[] worldCoords = latlonToMeters(
            lastOrigin.latitude, lastOrigin.longitude,
            initialOrigin.latitude, initialOrigin.longitude
        );
        double east = worldCoords[0];
        double north = worldCoords[1];
        double up = lastOrigin.altitude_gps - initialOrigin.altitude_gps;
        
        return new Vector3((float)east, (float)up, (float)north);
    }
}