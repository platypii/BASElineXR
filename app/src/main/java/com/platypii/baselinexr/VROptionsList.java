package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

import java.util.HashMap;
import java.util.Map;

public class VROptionsList {

    public static final VROptions LIVE = new VROptions(
        "Live",
        "data/shaders/terrain_cinema",
        null,
        "eiger",
        "kpowSouth",
        false,
        true,
        true,
        false
    );

    public static final VROptions EIGER_BASE = new VROptions(
        "Eiger Base",
        null,
        "eiger.csv",
        "eiger",
        "eiger",
        false,
        true,
        true,
        false
    );

    public static final VROptions EIGER_SKYDIVE = new VROptions(
            "Eiger Skydive",
            null,
            "kpow-prison.csv",
            "eiger",
            "kpowPrison",
            false,
            true,
            true,
            false
    );

    public static final VROptions EIGER_SKYDIVE_LAKE = new VROptions(
            "Eiger Skydive",
            null,
            "kpow.csv",
            "eiger",
            "kpowLake",
            false,
            true,
            true,
            false
    );

    public static final VROptions KPOW_SKYDIVE_LANDING = new VROptions(
        "Kpow Landing",
        null,
        "kpow-landing.csv",
        "kpow",
        "kpow",
        false,
        true,
        true,
        false
    );

    public static final VROptions KPOW_GROUND_LEVEL = new VROptions(
        "Kpow Ground",
        "data/shaders/terrain_bubble",
        "kpow-student.csv",
        "kpow",
        "kpow",
        false,
        true,
        true,
        false
    );

    public static final VROptions GOING_IN_SIMULATOR = new VROptions(
        "Going-In",
        null,
        "kpow-impact.csv",
        "kpow",
        "kpow4500",
        true,
        false,
        false,
        false
    );

    public static final VROptions JANK = new VROptions(
        "Jank",
        "data/shaders/terrain_bubble",
        "jank-longer.csv",
        "eiger",
        "kpowSouth",
        false,
        true,
        false,
        false
    );

    public static final VROptions PORTAL_RUN = new VROptions(
            "Portal Run",
            null,
            "kpow-north-run.csv",
            "eiger",
            "kpowNorthRun",
            false,
            true,
            true,
            true
    );

    public static Map<String, LatLngAlt> destinations = new HashMap<>();

    static {
        // Small offset to align mushroom:
        destinations.put("eiger", new LatLngAlt(46.57731, 8.0053, 3973.0));
        destinations.put("capitolHillFoot", new LatLngAlt(47.60453, -122.30197620, 1860.0));
        destinations.put("capitolHillSummit", new LatLngAlt(47.61123, -122.32377620, 170.0));
        destinations.put("kpow", new LatLngAlt(47.2375, -123.1458, 84.0));
        destinations.put("kpow4500", new LatLngAlt(47.2375, -123.1458, 1584.0)); // 84 + 1500
        destinations.put("kpowPrison", new LatLngAlt(47.2338, -123.16697620, 3800.0));
        destinations.put("kpowSouth", new LatLngAlt(47.22453, -123.1669762, 3710.0));
        destinations.put("kpowNorthRun", new LatLngAlt(47.2288, -123.112, 3710.0));
        destinations.put("kpowLake", new LatLngAlt(47.25203, -123.10797620, 3060.0));
    }
}