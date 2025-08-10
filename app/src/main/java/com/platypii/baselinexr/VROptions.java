package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

import java.util.HashMap;
import java.util.Map;

public class VROptions {

    public final String shader;
    public final String mockTrack;
    public final String source;
    public final String dest;
    public final boolean roomMovement;
    public final boolean showDirectionArrow;
    public final boolean showTarget;

    public VROptions(String shader, String mockTrack, String source, String dest, 
                    boolean roomMovement, boolean showDirectionArrow, boolean showTarget) {
        this.shader = shader;
        this.mockTrack = mockTrack;
        this.source = source;
        this.dest = dest;
        this.roomMovement = roomMovement;
        this.showDirectionArrow = showDirectionArrow;
        this.showTarget = showTarget;
    }

    // Predefined configurations
    public static final VROptions LIVE = new VROptions(
        "data/shaders/terrain_bubble",
        null,
        "eiger",
        "kpowPrisonSouth",
        false,
        true,
        true
    );

    public static final VROptions EIGER_MUSHROOM_BASE = new VROptions(
        null,
        "eiger.csv",
        "eiger",
        "eiger",
        false,
        true,
        true
    );

    public static final VROptions EIGER_SKYDIVE = new VROptions(
        null,
        "kpow-prison.csv",
        "eiger",
        "kpowPrison",
        false,
        true,
        true
    );

    public static final VROptions KPOW_SKYDIVE_LANDING = new VROptions(
        null,
        "kpow-landing.csv",
        "kpow",
        "kpow",
        false,
        true,
        true
    );

    public static final VROptions KPOW_GROUND_LEVEL = new VROptions(
        "data/shaders/terrain_bubble",
        "kpow-student.csv",
        "kpow",
        "kpow",
        false,
        true,
        true
    );

    public static final VROptions GOING_IN_SIMULATOR = new VROptions(
        null,
        "kpow-impact.csv",
        "kpow",
        "kpow4500",
        true,
        false,
        false
    );

    public static final VROptions JANK = new VROptions(
        "data/shaders/terrain_bubble",
        "jank.csv",
        "eiger",
        "kpowPrisonSouth",
        false,
        true,
        true
    );

    // Current active configuration
    public static VROptions current = GOING_IN_SIMULATOR;

    public static Map<String, LatLngAlt> destinations = new HashMap<>();

    static {
        destinations.put("eiger", new LatLngAlt(46.56314640, 7.94727628, 0.0));
        destinations.put("capitolHillFoot", new LatLngAlt(47.59, -122.36, -2100.0));
        destinations.put("capitolHillSummit", new LatLngAlt(47.5967, -122.3818, -3790.0));
        destinations.put("kpow", new LatLngAlt(47.22966825, -123.16380949, 0.0));
        destinations.put("kpow4500", new LatLngAlt(47.22966825, -123.16380949, 1500.0));
        destinations.put("kpowPrison", new LatLngAlt(47.22, -123.225, -250.0));
        destinations.put("kpowPrisonSouth", new LatLngAlt(47.21, -123.225, -250.0));
        destinations.put("kpowLake", new LatLngAlt(47.2375, -123.166, -900.0));
    }

    public String getTerrainModel() {
        return "terrain/" + source + "_tile.json";
    }

    public LatLngAlt getDestination() {
        return VROptions.destinations.get(this.dest);
    }

    // Kpow student field
    public static LatLngAlt target = new LatLngAlt(47.2375, -123.1458, 84);
}
