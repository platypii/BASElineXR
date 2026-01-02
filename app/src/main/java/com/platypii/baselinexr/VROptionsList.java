package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptionsList {

    // THIS IS THE VROPTIONS TO USE IN LIVE FLIGHT!!
    public static final VROptions LIVE = new VROptions(
            "Live Eiger",
            null,
            "branded",
            null, // current dz
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            new LatLngAlt(46.5785, 7.99, 3040.0)
    );

    public static final VROptions LIVE_ZILLA = new VROptions(
            "Live Zilla",
            null,
            "seb_godz",
            null, // current dz
            VROptions.ShaderType.FOG_SHADER,
            false,
            true,
            true,
            null
    );

//    public static final VROptions LIVE_KPOW_7500 = new VROptions(
//            "Live kpow + 7500",
//            null,
//            "kpow",
//            new LatLngAlt(47.2375, -123.1458, 2584.0), // kpow + 7500
//            VROptions.ShaderType.LOD_SHADER,
//            false,
//            true,
//            true,
//            true,
//            null
//    );

    public static final VROptions EIGER_BASE = new VROptions(
            "Eiger Base",
            "eiger.csv",
            "eiger",
            new LatLngAlt(46.57731, 8.0053, 3973.0), // eiger
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
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
            new LatLngAlt(46.5785, 7.99, 3050.0)
    );

    public static final VROptions SEB_SKYDIVE = new VROptions(
            "Seb Skydive",
            "seb_ff.csv",
            "seb_godz",
            new LatLngAlt(DropzoneOptionsList.SEBASTIAN.landingZone.lat, DropzoneOptionsList.SEBASTIAN.landingZone.lng, 10),
//            new LatLngAlt( 27.814, -80.512, 3800.0), // west of seb
            VROptions.ShaderType.FOG_SHADER,
            false,
            true,
            true,
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
            new LatLngAlt(46.57835, 7.984, 2670.5)
    );

    // All modes for cycling
    public static final VROptions[] ALL_MODES = {
            LIVE, LIVE_ZILLA,
            EIGER_BASE, EIGER_SKYDIVE, SEB_SKYDIVE,
            GOING_IN_SIMULATOR, PORTAL_RUN
    };

    public static VROptions getByName(String name) {
        for (VROptions mode : ALL_MODES) {
            if (mode.name.equals(name)) return mode;
        }
        return LIVE_ZILLA; // default
    }

    public static VROptions getNextMode(VROptions current) {
        for (int i = 0; i < ALL_MODES.length; i++) {
            if (ALL_MODES[i] == current) {
                return ALL_MODES[(i + 1) % ALL_MODES.length];
            }
        }
        return ALL_MODES[0];
    }

}
