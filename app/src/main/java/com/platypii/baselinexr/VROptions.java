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
    public static VROptions current = VROptionsList.OGDEN_AIRPLANE;
    public enum ShaderType {
        DEFAULT_SHADER,
        LOD_SHADER
    }

    // Configuration name (displayed in HUD)
    public final String name;
    // Replay pre-recorded GPS track. null = live gps data from flysight
    public final String mockTrack;
    // Folder containing SENSOR.CSV and TRACK.CSV files. If non-null, overrides mockTrack
    public final String mockSensor;
    // Start time in seconds for track replay (null = start from beginning)
    public final Integer mockTrackStartSec;
    // End time in seconds for track replay (null = play until end)
    public final Integer mockTrackEndSec;
    // 360° video file name (e.g., "360squaw072925.mp4"). Should be in assets folder. null = use passthrough
    public final String video360File;
    // Time offset in milliseconds: how much to add to GPS time to get video time (video_time = gps_time + offset)
    // Positive = video starts after GPS time, Negative = video starts before GPS time
    public final Integer videoGpsOffsetMs;
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
    // Show wingsuit/canopy 3D models?
    public final boolean showWingsuitCanopy;
    // Show speed chart panel?
    public final boolean showSpeedChart;
    public final LatLngAlt portalLocation;
    // Render options: DEFAULT_SHADER, LOD_SHADER
    public final ShaderType shader;

    public VROptions(String name, String mockTrack, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showWingsuitCanopy, boolean showTarget, boolean showSpeedChart, LatLngAlt portalLocation,
                     String mockSensor, Integer mockTrackStartSec, Integer mockTrackEndSec, String video360File, Integer videoGpsOffsetMs) {
        this.name = name;
        this.mockTrack = mockTrack;
        this.mockSensor = mockSensor;
        this.mockTrackStartSec = mockTrackStartSec;
        this.mockTrackEndSec = mockTrackEndSec;
        this.video360File = video360File;
        this.videoGpsOffsetMs = videoGpsOffsetMs;
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

    // Backward compatibility constructor (no sensor data or video)
    public VROptions(String name, String mockTrack, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showWingsuitCanopy, boolean showTarget, boolean showSpeedChart, LatLngAlt portalLocation) {
        this(name, mockTrack, sourceModel, destination, shader, roomMovement, showDirectionArrow, showWingsuitCanopy, showTarget, showSpeedChart, portalLocation, null, null, null, null, null);
    }

    // Backward compatibility constructor (no video)
    public VROptions(String name, String mockTrack, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showWingsuitCanopy, boolean showTarget, boolean showSpeedChart, LatLngAlt portalLocation,
                     String mockSensor, Integer mockTrackStartSec, Integer mockTrackEndSec) {
        this(name, mockTrack, sourceModel, destination, shader, roomMovement, showDirectionArrow, showWingsuitCanopy, showTarget, showSpeedChart, portalLocation, mockSensor, mockTrackStartSec, mockTrackEndSec, null, null);
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
    public static final float ENVIRONMENT_INTENSITY = 0.0f;  // Disabled IBL to test spotlight issue

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

    // Check if this configuration uses 360 video instead of passthrough
    public boolean has360Video() {
        return video360File != null;
    }

    // Get the filename of the 360 video file (stored in device Movies folder)
    public String get360VideoPath() {
        return video360File;
    }
}
