package com.platypii.baselinexr;

import android.content.Context;
import android.content.SharedPreferences;

import com.platypii.baselinexr.measurements.LatLngAlt;

public class DropzoneOptions {

    // Current active dropzone (null means auto-detect)
    public static DropzoneOptions current = null;

    // Dropzone name (displayed in HUD)
    public final String name;
    // Target landing zone in the real world
    public final LatLngAlt landingZone;
    // Minimap bounds
    public final double latMin;
    public final double latMax;
    public final double lngMin;
    public final double lngMax;
    // Minimap image resource
    public final int drawableResource;

    public DropzoneOptions(String name, LatLngAlt landingZone,
                           double latMin, double latMax, double lngMin, double lngMax,
                           int drawableResource) {
        this.name = name;
        this.landingZone = landingZone;
        this.latMin = latMin;
        this.latMax = latMax;
        this.lngMin = lngMin;
        this.lngMax = lngMax;
        this.drawableResource = drawableResource;
    }

    // Load saved dropzone from SharedPreferences
    public static void loadCurrentDropzone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("BASElineXRPrefs", Context.MODE_PRIVATE);
        String dropzoneName = prefs.getString("dropzoneOptions", "Auto");
        if ("Auto".equals(dropzoneName)) {
            current = null;
        } else {
            current = DropzoneOptionsList.getByName(dropzoneName);
        }
    }

    // Save current dropzone to SharedPreferences
    public static void saveCurrentDropzone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("BASElineXRPrefs", Context.MODE_PRIVATE);
        String name = current == null ? "Auto" : current.name;
        prefs.edit().putString("dropzoneOptions", name).apply();
    }

    /**
     * Get display name for current dropzone
     */
    public static String getCurrentName() {
        return current == null ? "DZ Auto" : current.name;
    }

    /**
     * Auto-detect dropzone from GPS location (only if current is null/auto)
     * @return true if dropzone was detected
     */
    public static boolean autoDetect(double lat, double lng) {
        if (current != null) return false;
        DropzoneOptions detected = DropzoneOptionsList.findByLocation(lat, lng);
        if (detected != null) {
            current = detected;
            return true;
        }
        return false;
    }
}
