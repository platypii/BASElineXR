package com.platypii.baselinexr;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptions {

    // Target landing zone in the real world
    // Ogden 41.199716, -112.001832, 2086 m 1353M 41.213346, -111.984535, 2420
    // kpow 47.2375, -123.1458, 84
    // tooele 40.6109180, -112.3480495, 1314
    public static LatLngAlt dropzone = new LatLngAlt(41.199716, -112.001832, 1353 );
    //public static LatLngAlt dropzone = new LatLngAlt(47.2375, -123.1458, 84);

    // Current active configuration
    public static VROptions current = VROptionsList.SQUAW_CANOPY;
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
    // Show wingsuit/canopy 3D models?
    public final boolean showWingsuitCanopy;

    public final boolean showTarget;
    // Show speed chart panel?
    public final boolean showSpeedChart;
    public final LatLngAlt portalLocation;
    // Render options: DEFAULT_SHADER, LOD_SHADER
    public final ShaderType shader;

    public VROptions(String name, String mockTrack, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showWingsuitCanopy, boolean showTarget, boolean showSpeedChart, LatLngAlt portalLocation) {
        this.name = name;
        this.mockTrack = mockTrack;
        this.sourceModel = sourceModel;
        this.destination = destination;
        this.roomMovement = roomMovement;
        this.showDirectionArrow = showDirectionArrow;
        this.showWingsuitCanopy = showWingsuitCanopy;
        this.showTarget = showTarget;
        this.showSpeedChart = showSpeedChart;
        this.portalLocation = portalLocation;
        this.shader = shader;
    }

    public static MiniMapOptions minimap = VROptionsList.MM_OGDEN;


    // Offset distance when clicking NSEW
    public static float offsetDistance = 300; // meters



    /**
     * Auto-select the best minimap based on GPS coordinates
     */
    public static void autoSelectMinimap(double lat, double lng) {
        // Check if coordinates are within any minimap bounds
        MiniMapOptions[] availableMinimaps = {
                VROptionsList.MM_TOOELE,
                VROptionsList.MM_OGDEN,
                VROptionsList.MM_KPOW
        };

        for (MiniMapOptions map : availableMinimaps) {
            if (lat >= map.latMin() && lat <= map.latMax() &&
                    lng >= map.lngMin() && lng <= map.lngMax()) {
                minimap = map;
                return;
            }
        }

        // If no match found, keep current minimap
    }

    /**
     * Manually set the minimap
     */
    public static void setMinimap(MiniMapOptions newMinimap) {
        minimap = newMinimap;
    }

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
