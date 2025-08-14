package com.platypii.baselinexr;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.LatLngAlt;

/**
 * Geographic utilities for 3D coordinate calculations
 */
public class GeoUtils {

    private static final double R = 6371000; // Earth radius in meters

    /**
     * Calculate 3D offset vector from point1 to point2 using geographic coordinates
     * Returns offset in meters (east, up, north)
     * Uses accurate spherical earth calculations
     */
    public static Vector3 calculateOffset(LatLngAlt from, LatLngAlt to) {
        double lat1 = Math.toRadians(from.lat);
        double lon1 = Math.toRadians(from.lng);
        double lat2 = Math.toRadians(to.lat);
        double lon2 = Math.toRadians(to.lng);

        double deltaLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;

        // Calculate North-South offset using haversine for accuracy
        double northOffset = R * deltaLat;

        // Calculate East-West offset with latitude correction
        // Use average latitude for more accurate distance calculation
        double avgLat = (lat1 + lat2) / 2;
        double eastOffset = R * deltaLon * Math.cos(avgLat);

        // For very long distances, use more accurate calculation
        if (Math.abs(deltaLat) > 0.01 || Math.abs(deltaLon) > 0.01) {
            // Use bearing and distance for better accuracy over long distances
            double bearing = Math.atan2(
                Math.sin(deltaLon) * Math.cos(lat2),
                Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon)
            );

            double distance = haversineDistance(from, to);
            eastOffset = distance * Math.sin(bearing);
            northOffset = distance * Math.cos(bearing);
        }

        // Calculate altitude offset (y-axis)
        double altOffset = to.alt - from.alt;

        return new Vector3((float)eastOffset, (float)altOffset, (float)northOffset);
    }

    /**
     * Calculate distance between two geographic points using haversine formula
     * Returns distance in meters
     */
    public static double haversineDistance(LatLngAlt from, LatLngAlt to) {
        double lat1 = Math.toRadians(from.lat);
        double lon1 = Math.toRadians(from.lng);
        double lat2 = Math.toRadians(to.lat);
        double lon2 = Math.toRadians(to.lng);

        double deltaLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * Apply Vector3 offset(s) to a LatLngAlt coordinate
     * Offsets are in meters (east, up, north)
     */
    public static LatLngAlt applyOffset(LatLngAlt base, Vector3... offsets) {
        double totalEast = 0;
        double totalUp = 0;
        double totalNorth = 0;

        for (Vector3 offset : offsets) {
            totalEast += offset.getX();
            totalUp += offset.getY();
            totalNorth += offset.getZ();
        }

        double lat = Math.toRadians(base.lat);
        double lon = Math.toRadians(base.lng);

        // Convert offsets back to lat/lng
        double newLat = lat + (totalNorth / R);
        double newLon = lon + (totalEast / (R * Math.cos(lat)));
        double newAlt = base.alt + totalUp;

        return new LatLngAlt(Math.toDegrees(newLat), Math.toDegrees(newLon), newAlt);
    }
}