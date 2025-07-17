package com.platypii.baselinexr;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.MLocation;

public class GpsToWorldTransform {
    
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    
    private double originLat;
    private double originLon;
    private double originAlt;
    private boolean originSet = false;
    
    // Track the initial origin for calculating deltas
    private double initialOriginLat;
    private double initialOriginLon;
    private double initialOriginAlt;
    private boolean initialOriginSet = false;
    
    // Velocity tracking for extrapolation
    private Vector3 velocity = new Vector3(0, 0, 0);
    private long lastUpdateTimestamp = 0;

    public void setOrigin(double lat, double lon, double alt) {
        // Store initial origin on first call
        if (!initialOriginSet) {
            this.initialOriginLat = lat;
            this.initialOriginLon = lon;
            this.initialOriginAlt = alt;
            this.initialOriginSet = true;
        }
        
        this.originLat = lat;
        this.originLon = lon;
        this.originAlt = alt;
        this.originSet = true;
    }
    
    public void setOrigin(MLocation location) {
        setOrigin(location.latitude, location.longitude, location.altitude_gps);

        // Update velocity and timestamp from the location
        // Convert GPS velocities (vN, vE) to world coordinates
        // vN is velocity north, vE is velocity east
        // In world coordinates: X=East, Y=Up, Z=North
        velocity = new Vector3((float)location.vE, (float)location.climb, (float)location.vN);
        lastUpdateTimestamp = location.millis;
    }

    private Vector3 toWorldCoordinates(double lat, double lon, double alt) {
        if (!originSet) {
            throw new IllegalStateException("Origin must be set before converting coordinates");
        }

        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        double originLatRad = Math.toRadians(originLat);
        double originLonRad = Math.toRadians(originLon);

        double deltaLat = latRad - originLatRad;
        double deltaLon = lonRad - originLonRad;

        double north = deltaLat * EARTH_RADIUS_METERS;
        double east = deltaLon * EARTH_RADIUS_METERS * Math.cos(originLatRad);
        double up = alt - originAlt;

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
        if (!originSet) {
            throw new IllegalStateException("Origin must be set before converting coordinates");
        }
        
        // First convert the GPS position to world coordinates
        Vector3 basePosition = toWorldCoordinates(lat, lon, alt);
        
        // If we have velocity data and a valid timestamp, extrapolate
        if (lastUpdateTimestamp > 0 && currentTimeMillis > lastUpdateTimestamp) {
            double deltaTime = (currentTimeMillis - lastUpdateTimestamp) / 1000.0; // Convert to seconds

            // Extrapolate position: position = position + velocity * deltaTime
            float extrapolatedX = basePosition.getX() - velocity.getX() * (float)deltaTime;
            float extrapolatedY = basePosition.getY() - velocity.getY() * (float)deltaTime;
            float extrapolatedZ = basePosition.getZ() - velocity.getZ() * (float)deltaTime;

            return new Vector3(extrapolatedX, extrapolatedY, extrapolatedZ);
        }
        
        return basePosition;
    }
    
    /**
     * Calculate the world space offset between the initial origin and the current origin.
     * This can be used to shift trail positions when the origin is updated.
     */
    public Vector3 getOriginDelta() {
        if (!initialOriginSet || !originSet) {
            return new Vector3(0, 0, 0);
        }
        
        // Calculate the difference between current origin and initial origin
        double latRad = Math.toRadians(originLat);
        double lonRad = Math.toRadians(originLon);
        double initialLatRad = Math.toRadians(initialOriginLat);
        double initialLonRad = Math.toRadians(initialOriginLon);
        
        double deltaLat = latRad - initialLatRad;
        double deltaLon = lonRad - initialLonRad;
        
        // Use initial origin latitude for consistency with coordinate conversion
        double north = deltaLat * EARTH_RADIUS_METERS;
        double east = deltaLon * EARTH_RADIUS_METERS * Math.cos(initialLatRad);
        double up = originAlt - initialOriginAlt;
        
        return new Vector3((float)east, (float)up, (float)north);
    }
}