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
     */
    public static Vector3 calculateOffset(LatLngAlt from, LatLngAlt to) {
        double lat1 = Math.toRadians(from.lat);
        double lon1 = Math.toRadians(from.lng);
        double lat2 = Math.toRadians(to.lat);
        double lon2 = Math.toRadians(to.lng);

        // Calculate East-West offset (x-axis)
        double deltaLon = lon2 - lon1;
        double eastOffset = R * deltaLon * Math.cos((lat1 + lat2) / 2);

        // Calculate North-South offset (z-axis)
        double deltaLat = lat2 - lat1;
        double northOffset = R * deltaLat;

        // Calculate altitude offset (y-axis)
        double altOffset = to.alt - from.alt;

        return new Vector3((float)eastOffset, (float)altOffset, (float)northOffset);
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