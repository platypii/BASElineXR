package com.platypii.baselinexr;

import android.content.Context;
import android.content.SharedPreferences;

import com.meta.spatial.core.Vector3;
import com.platypii.baselinexr.measurements.LatLngAlt;

public class VROptions {

    // Current active configuration
    public static VROptions current = VROptionsList.ZILLA;

    public enum ShaderType {
        DEFAULT_SHADER,
        LOD_SHADER,
        FOG_SHADER
    }

    // Configuration name (displayed in HUD)
    public final String name;
    // 3D terrain model, options: eiger, kpow, branded (with BASElineXR logo on summit)
    public final String sourceModel;
    // Place the source point-of-interest (summit) at the destination location
    private final LatLngAlt destination;
    // If true, walking around the room moves in the map
    public final boolean roomMovement;
    // Show direction arrow below for alignment?
    public final boolean showDirectionArrow;
    // Show target reticle on the dropzone location?
    public final boolean showTarget;
    public final LatLngAlt portalLocation;
    // Render options: DEFAULT_SHADER, LOD_SHADER
    public final ShaderType shader;

    public VROptions(String name, String sourceModel, LatLngAlt destination, ShaderType shader,
                     boolean roomMovement, boolean showDirectionArrow, boolean showTarget, LatLngAlt portalLocation) {
        this.name = name;
        this.sourceModel = sourceModel;
        this.destination = destination;
        this.roomMovement = roomMovement;
        this.showDirectionArrow = showDirectionArrow;
        this.showTarget = showTarget;
        this.portalLocation = portalLocation;
        this.shader = shader;
    }

    // Offset distance when clicking NSEW
    public static float offsetDistance = 300; // meters

    // Default lighting constants
    public static final Vector3 AMBIENT_COLOR = new Vector3(1.4f);
    public static final Vector3 SUN_COLOR = new Vector3(1f);
    public static final Vector3 SUN_DIRECTION = new Vector3(-4f, 10f, -2f).normalize();
    public static final float ENVIRONMENT_INTENSITY = 0.01f;

    // Map the short "sourceModel" string to the asset location
    public String getTerrainModel() {
        return "terrain/" + sourceModel + "_tile.json";
    }

    /**
     * Get the offset-adjusted destination for terrain placement.
     * If destination is null, uses the current dropzone's landing zone.
     * Returns null if both are unavailable (waiting for GPS auto-detect).
     */
    public LatLngAlt getDestination() {
        LatLngAlt dest = destination;
        if (dest == null && DropzoneOptions.current != null) {
            dest = DropzoneOptions.current.landingZone;
        }
        if (dest == null) {
            return null;
        }
        // Apply north/east adjustments
        return GeoUtils.applyOffset(dest,
                new Vector3(Adjustments.eastAdjustment, 0, Adjustments.northAdjustment));
    }

    // Load saved mode from SharedPreferences
    public static void loadCurrentMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("BASElineXRPrefs", Context.MODE_PRIVATE);
        String modeName = prefs.getString("vrOptionsMode", "Live Seb Godz");
        current = VROptionsList.getByName(modeName);
    }

    // Save current mode to SharedPreferences
    public static void saveCurrentMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("BASElineXRPrefs", Context.MODE_PRIVATE);
        prefs.edit().putString("vrOptionsMode", current.name).apply();
    }
}
