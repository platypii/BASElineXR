package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptionsList {

    // THIS IS THE VROPTIONS TO USE IN LIVE FLIGHT!!
    public static final VROptions LIVE = new VROptions(
            "Live",
            null,
            "branded",
            // ogden over hangars:
//           new LatLngAlt(41.191222, -112.010510,4197.728),
            // copy destination from target landing zone
            new LatLngAlt(41.193, -112.008,4197.728),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            true,
            true,
            new LatLngAlt(41.191222, 7.99, 3440.0),
            null,
            null,
            null
    );

    public static final VROptions LIVEON = new VROptions(
            "LiveON",
            null,
            "branded",
            // ogden over hangars:
//           new LatLngAlt(41.191222, -112.010510,4197.728),
            // copy destination from target landing zone
            new LatLngAlt(41.205, -112.0,4197.728),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            true,
            true,
            new LatLngAlt(41.191222, 7.99, 3440.0),
            null,
            null,
            null
    );

    public static final VROptions LIVEOS = new VROptions(
            "LiveOS",
            null,
            "branded",
            // ogden over hangars:
//           new LatLngAlt(41.191222, -112.010510,4197.728),
            // copy destination from target landing zone
            new LatLngAlt(41.187, -112.0,4197.728),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            true,
            true,
            new LatLngAlt(41.191222, 7.99, 3440.0),
            null,
            null,
            null
    );

    public static final VROptions LIVE_POTM = new VROptions(
            "Livep",
            null,
            "branded",
            // summit over the water tower:
//            new LatLngAlt(47.238, -123.156, 3800.0),
            // copy destination from target landing zone
            new LatLngAlt(40.4750657,-111.8902499,1563.063),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            true,
            true,
            new LatLngAlt(46.5785, 7.99, 3040.0),
            null,
            null,
            null
    );

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
            true,
            false,
            null,
            null,
            null,
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
            true,
            true,
            null,
            null,
            null,
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
            true,
            true,
            new LatLngAlt(46.5785, 7.99, 3050.0),
            null,
            null,
            null
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
            true,
            true,
            new LatLngAlt(47.25203, 7.984, 2670.5),
            null,
            null,
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
            true,
            true,
            null,
            null,
            null,
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
            false,
            true,
            false,
            null,
            null,
            null,
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
            true,
            false,
            true,
            null,
            null,
            null,
            null
    );

    public static final VROptions JANK = new VROptions(
            "Jank",
            "kpow20hz.csv",
            "eiger",
            new LatLngAlt(47.238, -123.156, 3800.0), // water tower
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            null,
            null,
            null,
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
            true,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final VROptions SQUAW = new VROptions(
            "Squaw",
            "squaw.csv",
            "eiger",
            new LatLngAlt(40.2709309,-111.620655,2146.342
            ), // kpow portal run
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final VROptions SQUAW_WITH_SENSORS = new VROptions(
            "Squaw+Sensors",
            null, // mockTrack overridden by mockSensor
            "eiger",
            new LatLngAlt(40.2709309,-111.620655,2146.342),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            "squaw072925", // mockSensor folder (updated path)
            null, // mockTrackStartSec (start from beginning)
            null  // mockTrackEndSec (play until end)
    );

    public static final VROptions SQUAW_SENSORS_CLIP = new VROptions(
            "Squaw Clip",
            null,
            "eiger",
            new LatLngAlt(40.2709309,-111.620655,2146.342),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            "squaw072925", // mockSensor folder (updated path)
            855, // mockTrackStartSec (start at 10 seconds)
            962  // mockTrackEndSec (end at 60 seconds)
    );

    public static final VROptions SQUAW_360_VIDEO = new VROptions(
            "Squaw 360",
            null,
            "eiger",
            new LatLngAlt(40.2709309,-111.620655,2146.342),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            "squaw072925",          // mockSensor folder
            850,                     // mockTrackStartSec
            962,                     // mockTrackEndSec
            "360squawbaselinexr.mp4", // video360File (filename only, in Movies folder)
            1000                        // videoGpsOffsetMs (adjust as needed for sync)
    );

    public static final VROptions CHINA_SENSORS_CLIP = new VROptions(
            "CHINA Clip",
            null,
            "eiger",
            new LatLngAlt(40.2709309,-111.620655,2146.342),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            "china050125",
            1824,
            2100
    );

    public static final VROptions TOOELE_SKYDIVE = new VROptions(
            "Tooele",
            "squaw.csv",
            "eiger",
            new LatLngAlt(40.2709309,-111.620655,2146.342
            ), // kpow portal run
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final VROptions SQUAW_CANOPY = new VROptions(
            "Squaw Canopy",
            "squawcanopy.csv",
            "eiger",
            new LatLngAlt(40.2709309,-111.620655,1946.342
            ),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final VROptions OGDEN_AIRPLANE = new VROptions(
            "Ogden Airplane",
            "airplane.csv",
            "eiger",
            new LatLngAlt(41.187, -112.0,4197.728
            ),
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final VROptions OGDEN_SKYDIVE = new VROptions(
            "Ogden",
            "ogden.csv",
            "eiger",
            new LatLngAlt(41.2148900,-111.9970277,4197.728
            ), //
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final VROptions OGDEN_SKYDIVE_FULL = new VROptions(
            "Ogden Full",
            "ogdenfull.csv",
            "eiger",
            new LatLngAlt(41.2148900,-111.9970277,4197.728
            ), //
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final VROptions CHINA = new VROptions(
            "china",
            "china.csv",
            "eiger",
            new LatLngAlt(27.4065070,103.2274659,2132.344
            ), // kpow portal run
            VROptions.ShaderType.LOD_SHADER,
            false,
            true,
            true,
            false,
            true,
            new LatLngAlt(46.57835, 7.984, 2670.5),
            null,
            null,
            null
    );

    public static final MiniMapOptions MM_KPOW = new MiniMapOptions(
            47.214,    // latMin
            47.2637,   // latMax
            -123.2033, // lngMin
            -123.0856, // lngMax
            R.drawable.minimap_kpow
    );
    //ogden
    public static final MiniMapOptions MM_OGDEN = new MiniMapOptions(
            41.186030,    // latMin
            41.213530,    // latMax
            -112.020010,  // lngMin
            -111.983510,  // lngMax
            R.drawable.minimap_ogden
    );

    public static final MiniMapOptions MM_TOOELE = new MiniMapOptions(
            40.5447, 40.6771, -112.4505, -112.2456,
            R.drawable.minimap_tooele
    );

}
