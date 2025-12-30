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

    public static final DropzoneOptions[] ALL_DROPZONES = {
            SEBASTIAN, KAPOWSIN
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
