package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

import java.util.HashMap;
import java.util.Map;

public class VROptions {

    public static final String shader = null;
//    public static final String shader = "data/shaders/terrain_transparent";
//    public static final String shader = "data/shaders/terrain_bubble";

    // LIVE:
//    public static final String mockTrack = null;
//    public static final String source = "eiger";
//    public static final String dest = "kpow";

    // Eiger mushroom BASE:
//    public static final String mockTrack = "eiger-glacier.csv";
//    public static final String source = "eiger";
//    public static final String dest = "eiger";

    // Eiger skydive:
    public static final String mockTrack = "kpow-prison.csv";
    public static final String source = "eiger";
    public static final String dest = "kpowPrison";

    // Kpow skydive landing:
//    public static final String mockTrack = "kpow-landing.csv";
//    public static final String source = "kpow";
//    public static final String dest = "kpow";

    // Kpow ground level:
//    public static final String mockTrack = "kpow-hangar.csv";
//    public static final String source = "kpow";
//    public static final String dest = "kpow";

    public static Map<String, LatLngAlt> destinations = new HashMap<>();

    static {
        destinations.put("eiger", new LatLngAlt(46.56314640, 7.94727628, 0.0));
        destinations.put("capitolHillFoot", new LatLngAlt(47.59, -122.36, -2100.0));
        destinations.put("capitolHillSummit", new LatLngAlt(47.5967, -122.3818, -3790.0));
        destinations.put("kpow", new LatLngAlt(47.22966825, -123.16380949, 0.0));
        destinations.put("kpow4500", new LatLngAlt(47.22966825, -123.16380949, 1500.0));
        destinations.put("kpowPrison", new LatLngAlt(47.22, -123.225, -250.0));
        destinations.put("kpowLake", new LatLngAlt(47.2375, -123.166, -900.0));
    }

    public static String terrainModel = "terrain/" + source + "_tile.json";

    public static LatLngAlt destination() {
        return VROptions.destinations.get(VROptions.dest);
    }

    public static LatLngAlt target = new LatLngAlt(47.239, -123.143, 84);
}
