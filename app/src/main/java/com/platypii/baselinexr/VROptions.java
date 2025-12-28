package com.platypii.baselinexr;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptions {

    // Target landing zone in the real world
//    public static LatLngAlt dropzone = new LatLngAlt(47.2375, -123.1458, 84);
    public static LatLngAlt dropzone = new LatLngAlt(27.8165, -80.499, 5); // seb

    // Current active configuration
    public static VROptions current = VROptionsList.LIVE_SEB_GODZ;

    public enum ShaderType {
        DEFAULT_SHADER,
        LOD_SHADER
    }

    // Configuration name (displayed in HUD)
    public final String name;
    // Replay pre-recorded GPS track. null = live gps data from flysight
    public final String mockTrack;
    // 3D terrain model, options: eiger, kpow, branded (with BASElineXR logo on summit)
    public final String sourceModel;
    // Place the source point-of-interest (summit) at the destination location
    private final LatLngAlt destination;
    // If true, walking around the room moves in the map
    public final boolean roomMovement;
    // Show direction arrow below for alignment?
    public final boolean showDirectionArrow;
    // Show target reticle on the dropzone location?
    public final boolean showTarget;
    // Show speed chart panel?
    public final boolean showSpeedChart;
    public final LatLngAlt portalLocation;
    // Render options: DEFAULT_SHADER, LOD_SHADER
    public final ShaderType shader;

    public VROptions(String name, String mockTrack, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showTarget, boolean showSpeedChart, LatLngAlt portalLocation) {
        this.name = name;
        this.mockTrack = mockTrack;
        this.sourceModel = sourceModel;
        this.destination = destination;
        this.roomMovement = roomMovement;
        this.showDirectionArrow = showDirectionArrow;
        this.showTarget = showTarget;
        this.showSpeedChart = showSpeedChart;
        this.portalLocation = portalLocation;
        this.shader = shader;
    }

    // Minimap configuration (Kapowsin)
//    public static final MiniMapOptions minimap = new MiniMapOptions(
//        47.214,    // latMin
//        47.2637,   // latMax
//        -123.2033, // lngMin
//        -123.0856, // lngMax
//        R.drawable.minimap_kpow
//    );
    // Minimap configuration (Sebastian)
    public static final MiniMapOptions minimap = new MiniMapOptions(
            27.7957,    // latMin
            27.8315,   // latMax
            -80.5334, // lngMin
            -80.4552, // lngMax
            R.drawable.minimap_sebastian
    );

    // Offset distance when clicking NSEW
    public static float offsetDistance = 300; // meters

    // Default lighting constants
    public static final Vector3 AMBIENT_COLOR = new Vector3(1.4f);
    public static final Vector3 SUN_COLOR = new Vector3(1f);
    public static final Vector3 SUN_DIRECTION = new Vector3(-4f, 10f, -2f).normalize();
    public static final float ENVIRONMENT_INTENSITY = 0.01f;

    // Map the short "sourceModel" string to the asset location
    public String getTerrainModel() {
        return "terrain/" + sourceModel + "_tile.json";
    }

    // Get the offset-adjusted destination for the eiger
    public LatLngAlt getDestination() {
        // Apply north/east adjustments
        return GeoUtils.applyOffset(destination,
                new Vector3(Adjustments.eastAdjustment, 0, Adjustments.northAdjustment));
    }
}
