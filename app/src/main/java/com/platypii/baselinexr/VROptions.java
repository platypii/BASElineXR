package com.platypii.baselinexr;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptions {

    // Current active configuration
    public static VROptions current = VROptionsList.LIVE;

    public enum ShaderType {
        DEFAULT_SHADER,
        LOD_SHADER
    }

    public final String name;
    public final String mockTrack;
    public final String sourceModel;
    // Place the summit at the destination
    private final LatLngAlt destination;
    // If true, walking around the room moves in the map
    public final boolean roomMovement;
    // Show direction arrow below for alignment
    public final boolean showDirectionArrow;
    // Show target landing zone
    public final boolean showTarget;
    public final LatLngAlt portalLocation;
    public final ShaderType shader;

    public VROptions(String name, String mockTrack, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showTarget, LatLngAlt portalLocation) {
        this.name = name;
        this.mockTrack = mockTrack;
        this.sourceModel = sourceModel;
        this.destination = destination;
        this.shader = shader;
        this.roomMovement = roomMovement;
        this.showDirectionArrow = showDirectionArrow;
        this.showTarget = showTarget;
        this.portalLocation = portalLocation;
    }

    public String getTerrainModel() {
        return "terrain/" + sourceModel + "_tile.json";
    }

    public LatLngAlt getDestination() {
        // Apply north/east adjustments
        return GeoUtils.applyOffset(destination,
            new Vector3(Adjustments.eastAdjustment, 0, Adjustments.northAdjustment));
    }

    // Target landing zone in the real world (kpow student field)
    public static LatLngAlt target = new LatLngAlt(47.2375, -123.1458, 84);


    // Default lighting constants
    public static final Vector3 AMBIENT_COLOR = new Vector3(1.4f);
    public static final Vector3 SUN_COLOR = new Vector3(1f);
    public static final Vector3 SUN_DIRECTION = new Vector3(-4f, 10f, -2f).normalize();
    public static final float ENVIRONMENT_INTENSITY = 0.01f;
}
