package com.platypii.baselinexr.location;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.MLocation;

public class GpsToWorldTransform {
    
    private static final double EARTH_RADIUS_METERS = 6371000.0;
    
    private double originLat;
    private double originLon;
    private double originAlt;
    private boolean originSet = false;
    
    public void setOrigin(double lat, double lon, double alt) {
        this.originLat = lat;
        this.originLon = lon;
        this.originAlt = alt;
        this.originSet = true;
    }
    
    public void setOrigin(MLocation location) {
        setOrigin(location.latitude, location.longitude, location.altitude_gps);
    }
    
    public Vector3 toWorldCoordinates(double lat, double lon, double alt) {
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
        return toWorldCoordinates(location.latitude, location.longitude, location.altitude_gps);
    }
}