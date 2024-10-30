package com.platypii.baselinexr.measurements;

import static com.platypii.baselinexr.util.Numbers.isReal;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.util.Convert;
import com.platypii.baselinexr.util.Numbers;

public class LatLngAlt {

    public final double lat;
    public final double lng;
    public final double alt;

    public LatLngAlt(double lat, double lng, double alt) {
        this.lat = lat;
        this.lng = lng;
        this.alt = alt;
    }

    @NonNull
    @Override
    public String toString() {
        return formatLatLngAlt(lat, lng, alt);
    }

    @NonNull
    public static String formatLatLng(double lat, double lng) {
        if (isReal(lat) && isReal(lng)) {
            return Numbers.format6.format(lat) + ", " + Numbers.format6.format(lng);
        } else {
            return "";
        }
    }

    @NonNull
    public static String formatLatLngAlt(double lat, double lng, double alt) {
        if (isReal(lat) && isReal(lng) && isReal(alt)) {
            return Numbers.format6.format(lat) + ", " + Numbers.format6.format(lng) + ", " + Convert.distance(alt);
        } else if (isReal(alt)) {
            return Convert.distance(alt);
        } else {
            return "";
        }
    }
}
