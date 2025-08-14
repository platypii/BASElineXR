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
        "kpowPrisonSouth",
        false,
        true,
        true
    );

    public static final VROptions EIGER_MUSHROOM_BASE = new VROptions(
        "Eiger Base",
        null,
        "eiger.csv",
        "eiger",
        "eiger",
        false,
        true,
        true
    );

    public static final VROptions EIGER_SKYDIVE = new VROptions(
        "Eiger Skydive",
        null,
        "kpow-prison.csv",
        "eiger",
        "kpowPrison",
        false,
        true,
        true
    );

    public static final VROptions KPOW_SKYDIVE_LANDING = new VROptions(
        "Kpow Landing",
        null,
        "kpow-landing.csv",
        "kpow",
        "kpow",
        false,
        true,
        true
    );

    public static final VROptions KPOW_GROUND_LEVEL = new VROptions(
        "Kpow Ground",
        "data/shaders/terrain_bubble",
        "kpow-student.csv",
        "kpow",
        "kpow",
        false,
        true,
        true
    );

    public static final VROptions GOING_IN_SIMULATOR = new VROptions(
        "Going-In",
        null,
        "kpow-impact.csv",
        "kpow",
        "kpow4500",
        true,
        false,
        false
    );

    public static final VROptions JANK = new VROptions(
        "Jank",
        "data/shaders/terrain_bubble",
        "jank-longer.csv",
        "eiger",
        "kpowPrisonSouth",
        false,
        true,
        false
    );

    public static Map<String, LatLngAlt> destinations = new HashMap<>();

    static {
        destinations.put("eiger", new LatLngAlt(46.57731, 8.0053, 3973.0));
        destinations.put("capitolHillFoot", new LatLngAlt(47.60453, -122.30197620, 1860.0));
        destinations.put("capitolHillSummit", new LatLngAlt(47.61123, -122.32377620, 170.0));
        destinations.put("kpow", new LatLngAlt(47.2375, -123.1458, 84.0));
        destinations.put("kpow4500", new LatLngAlt(47.2375, -123.1458, 1584.0)); // 84 + 1500
        destinations.put("kpowPrison", new LatLngAlt(47.23453, -123.16697620, 3710.0));
        destinations.put("kpowPrisonSouth", new LatLngAlt(47.22453, -123.16697620, 3710.0));
        destinations.put("kpowLake", new LatLngAlt(47.25203, -123.10797620, 3060.0));
    }
}