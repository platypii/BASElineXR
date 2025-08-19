package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptions {

    public final String name;
    public final String shader;
    public final String mockTrack;
    public final String sourceModel;
    public final String destinationName;
    public final boolean roomMovement;
    public final boolean showDirectionArrow;
    public final boolean showTarget;
    public final boolean showPortal;

    // Current active configuration
    public static VROptions current = VROptionsList.EIGER_SKYDIVE;

    public VROptions(String name, String shader, String mockTrack, String sourceModel, String destinationName,
                     boolean roomMovement, boolean showDirectionArrow, boolean showTarget, boolean showPortal) {
        this.name = name;
        this.shader = shader;
        this.mockTrack = mockTrack;
        this.sourceModel = sourceModel;
        this.destinationName = destinationName;
        this.roomMovement = roomMovement;
        this.showDirectionArrow = showDirectionArrow;
        this.showTarget = showTarget;
        this.showPortal = showPortal;
    }

    public String getTerrainModel() {
        return "terrain/" + sourceModel + "_tile.json";
    }

    public LatLngAlt getDestination() {
        LatLngAlt baseDestination = VROptionsList.destinations.get(this.destinationName);
        if (baseDestination == null) {
            return null;
        }

        // Convert north/east adjustments (in meters) to lat/lng offsets
        double latRad = Math.toRadians(baseDestination.lat);
        double deltaLat = Adjustments.northAdjustment / 6371000.0; // Convert north meters to radians
        double deltaLon = Adjustments.eastAdjustment / (6371000.0 * Math.cos(latRad)); // Convert east meters to radians

        // Apply adjustments and return new LatLngAlt
        return new LatLngAlt(
            baseDestination.lat + Math.toDegrees(deltaLat),
            baseDestination.lng + Math.toDegrees(deltaLon),
            baseDestination.alt
        );
    }

    // Kpow student field
    public static LatLngAlt target = new LatLngAlt(47.2375, -123.1458, 84);

    // Portal location
    public static LatLngAlt portalLocation = new LatLngAlt(46.57835, 7.984, 2673.0);
}
