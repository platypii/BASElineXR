package com.platypii.baselinexr;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptions {

    public enum ShaderType {
        DEFAULT_SHADER,
        LOD_SHADER
    }

    public final String name;
    public final String mockTrack;
    public final String sourceModel;
    // Place the summit at the destination
    public final LatLngAlt destination;
    // If true, walking around the room moves in the map
    public final boolean roomMovement;
    // Show direction arrow below for alignment
    public final boolean showDirectionArrow;
    // Show target landing zone
    public final boolean showTarget;
    public final boolean showPortal;
    public final ShaderType shader;

    // Current active configuration
    public static VROptions current = VROptionsList.EIGER_SKYDIVE;

    public VROptions(String name, String mockTrack, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showTarget, boolean showPortal) {
        this.name = name;
        this.mockTrack = mockTrack;
        this.sourceModel = sourceModel;
        this.destination = destination;
        this.shader = shader;
        this.roomMovement = roomMovement;
        this.showDirectionArrow = showDirectionArrow;
        this.showTarget = showTarget;
        this.showPortal = showPortal;
    }

    public String getTerrainModel() {
        return "terrain/" + sourceModel + "_tile.json";
    }

    public LatLngAlt getDestination() {
        if (destination == null) {
            return null;
        }

        // Convert north/east adjustments (in meters) to lat/lng offsets
        double latRad = Math.toRadians(destination.lat);
        double deltaLat = Adjustments.northAdjustment / 6371000.0; // Convert north meters to radians
        double deltaLon = Adjustments.eastAdjustment / (6371000.0 * Math.cos(latRad)); // Convert east meters to radians

        // Apply adjustments and return new LatLngAlt
        return new LatLngAlt(
            destination.lat + Math.toDegrees(deltaLat),
            destination.lng + Math.toDegrees(deltaLon),
            destination.alt
        );
    }

    // Kpow student field
    public static LatLngAlt target = new LatLngAlt(47.2375, -123.1458, 84);

    // Portal location
    public static LatLngAlt portalLocation = new LatLngAlt(46.57835, 7.984, 2670.5);

    // Default lighting constants
    public static final Vector3 AMBIENT_COLOR = new Vector3(1.4f);
    public static final Vector3 SUN_COLOR = new Vector3(1f);
    public static final Vector3 SUN_DIRECTION = new Vector3(-4f, 10f, -2f).normalize();
    public static final float ENVIRONMENT_INTENSITY = 0.01f;
}
