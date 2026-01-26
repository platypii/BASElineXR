package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptionsList {

    // THIS IS THE VROPTIONS TO USE IN LIVE FLIGHT!!
    public static final VROptions EIGER = new VROptions(
            "Env Eiger",
            "branded",
            null, // current dz
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            new LatLngAlt(46.5785, 7.99, 3040.0)
    );

    public static final VROptions ZILLA = new VROptions(
            "Env Zilla",
            "seb_godz",
            null, // current dz
            VROptions.ShaderType.FOG_SHADER,
            false,
            true,
            true,
            null
    );

    public static final VROptions KPOW_7500 = new VROptions(
            "Env Kpow+7500",
            "kpow",
            new LatLngAlt(47.2375, -123.1458, 2584.0), // kpow + 7500
            VROptions.ShaderType.LOD_SHADER,
            false,
            false,
            true,
            null
    );

    // Squaw Peak, Utah - terrain from USGS LiDAR
    // Terrain origin (SW): 40.252778, -111.623204
    // Terrain center: 40.270890, -111.605728
    // Elevation range: 1676m - 3113m
    // Track data: exit 40.2712,-111.6170,2375m -> landing 40.2673,-111.6359,1509m
    public static final VROptions SQUAW_PEAK = new VROptions(
            "Squaw Peak",
            "kpow",
            new LatLngAlt(40.2673, -111.6359, 500.0), // landing area
            VROptions.ShaderType.FOG_SHADER,
            false,  // use terrain model
            true,
            true,
            new LatLngAlt(40.2712, -111.6170, 2375.0) // exit point
    );

    // All modes for cycling
    public static final VROptions[] ALL_MODES = {
            EIGER, ZILLA, KPOW_7500, SQUAW_PEAK
    };

    public static VROptions getByName(String name) {
        for (VROptions mode : ALL_MODES) {
            if (mode.name.equals(name)) return mode;
        }
        return ZILLA; // default
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
