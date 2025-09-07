package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptionsList {

    // THIS IS THE VROPTIONS TO USE IN LIVE FLIGHT!!
    public static final VROptions LIVE = new VROptions(
            "Live",
            null,
            "branded",
            // summit over the water tower:
//            new LatLngAlt(47.238, -123.156, 3800.0),
            // copy destination from target landing zone
            new LatLngAlt(VROptions.dropzone.lat, VROptions.dropzone.lng, 3800),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            new LatLngAlt(46.5785, 7.99, 3040.0)
    );

    // Same as live but with the eiger more eastward toward island lake
    public static final VROptions LIVE_EAST = new VROptions(
            "Live East",
            null,
            "branded",
            // ideal for a kpow east jump run:
            new LatLngAlt(47.236, -123.132, 3710.0),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            null
    );

    public static final VROptions EIGER_BASE = new VROptions(
            "Eiger Base",
            "eiger.csv",
            "eiger",
            new LatLngAlt(46.57731, 8.0053, 3973.0), // eiger
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            null
    );

    public static final VROptions EIGER_SKYDIVE = new VROptions(
            "Eiger Skydive",
            "kpow-prison.csv",
            "branded",
            new LatLngAlt(47.2338, -123.167, 3800.0), // kpow prison
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            new LatLngAlt(46.5785, 7.99, 3050.0)
    );

    public static final VROptions EIGER_SKYDIVE_LAKE = new VROptions(
            "Eiger Skydive",
            "kpow.csv",
            "eiger",
            new LatLngAlt(47.25203, -123.107976, 3060.0), // island lake
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            null
    );

    public static final VROptions KPOW_SKYDIVE_LANDING = new VROptions(
            "Kpow Landing",
            "kpow-landing.csv",
            "kpow",
            new LatLngAlt(47.2375, -123.1458, 84.0), // kpow
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            null
    );

    public static final VROptions KPOW_GROUND_LEVEL = new VROptions(
            "Kpow Ground",
            "kpow-student.csv",
            "kpow",
            new LatLngAlt(47.2375, -123.1458, 84.0), // kpow
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            null
    );

    public static final VROptions GOING_IN_SIMULATOR = new VROptions(
            "Going-In",
            "kpow-impact.csv",
            "kpow",
            new LatLngAlt(47.2375, -123.1458, 1584.0), // kpow + 4500
            VROptions.ShaderType.LOD_SHADER,
            true,
            false,
            false,
            false,
            null
    );

    public static final VROptions PORTAL_RUN = new VROptions(
            "Portal Run",
            "portal-run.csv",
            "eiger",
            new LatLngAlt(47.2288, -123.112, 3710.0), // kpow portal run
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            new LatLngAlt(46.57835, 7.984, 2670.5)
    );

}
