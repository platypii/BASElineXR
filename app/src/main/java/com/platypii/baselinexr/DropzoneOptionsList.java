package com.platypii.baselinexr;

import com.platypii.baselinexr.measurements.LatLngAlt;

public class DropzoneOptionsList {

    public static final DropzoneOptions SEBASTIAN = new DropzoneOptions(
            "Sebastian",
            new LatLngAlt(27.8165, -80.499, 5),
            27.7957,    // latMin
            27.8315,    // latMax
            -80.5334,   // lngMin
            -80.4552,   // lngMax
            R.drawable.minimap_sebastian
    );

    public static final DropzoneOptions KAPOWSIN = new DropzoneOptions(
            "Kapowsin",
            new LatLngAlt(47.2375, -123.1458, 84),
            47.214,     // latMin
            47.2637,    // latMax
            -123.2033,  // lngMin
            -123.0856,  // lngMax
            R.drawable.minimap_kpow
    );

    public static final DropzoneOptions OGDEN = new DropzoneOptions(
            "Ogden",
            new LatLngAlt(41.1999, -112.0018, 1340),
            41.186030,   // latMin
            41.213530,   // latMax
            -112.020010, // lngMin
            -111.983510, // lngMax
            R.drawable.minimap_ogden
    );

    public static final DropzoneOptions TOOELE = new DropzoneOptions(
            "Tooele",
            new LatLngAlt(40.6109, -112.3481, 1378),
            40.5447,    // latMin
            40.6771,    // latMax
            -112.4505,  // lngMin
            -112.2456,  // lngMax
            R.drawable.minimap_tooele
    );

    public static final DropzoneOptions[] ALL_DROPZONES = {
            KAPOWSIN, SEBASTIAN, OGDEN, TOOELE
    };

    public static DropzoneOptions getByName(String name) {
        for (DropzoneOptions dz : ALL_DROPZONES) {
            if (dz.name.equals(name)) return dz;
        }
        return SEBASTIAN; // default fallback
    }

    public static DropzoneOptions getNextDropzone(DropzoneOptions current) {
        for (int i = 0; i < ALL_DROPZONES.length; i++) {
            if (ALL_DROPZONES[i] == current) {
                return ALL_DROPZONES[(i + 1) % ALL_DROPZONES.length];
            }
        }
        return ALL_DROPZONES[0];
    }
}
