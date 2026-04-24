package com.platypii.baselinexr;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class Adjustments {
    private static final String TAG = "GpsToWorldTransform";
    private static final String PREF_NAME = "BASElineXRPrefs";
    private static final String KEY_YAW_ADJUSTMENT = "yawAdjustment";
    private static final String KEY_NORTH_ADJUSTMENT = "northAdjustment";
    private static final String KEY_EAST_ADJUSTMENT = "eastAdjustment";

    // Yaw adjustment in radians for north orientation reset
    public static float yawAdjustment = 0.0f; // radians
    public static float northAdjustment = 0.0f;
    public static float eastAdjustment = 0.0f;

    public static void loadAdjustments(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        yawAdjustment = prefs.getFloat(KEY_YAW_ADJUSTMENT, 0.0f);
        northAdjustment = prefs.getFloat(KEY_NORTH_ADJUSTMENT, 0.0f);
        eastAdjustment = prefs.getFloat(KEY_EAST_ADJUSTMENT, 0.0f);
        Log.d(TAG, "Loaded yawAdjustment: " + Math.toDegrees(yawAdjustment) + " degrees");
        Log.d(TAG, "Loaded northAdjustment: " + northAdjustment);
        Log.d(TAG, "Loaded eastAdjustment: " + eastAdjustment);
    }

    public static void saveAdjustments(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putFloat(KEY_YAW_ADJUSTMENT, yawAdjustment)
            .putFloat(KEY_NORTH_ADJUSTMENT, northAdjustment)
            .putFloat(KEY_EAST_ADJUSTMENT, eastAdjustment)
            .apply();
        Log.d(TAG, "Saved yawAdjustment: " + Math.toDegrees(yawAdjustment) + " degrees");
        Log.d(TAG, "Saved northAdjustment: " + northAdjustment);
        Log.d(TAG, "Saved eastAdjustment: " + eastAdjustment);
    }

    public static void saveYawAdjustment(Context context) {
        // Normalize yawAdjustment to +/- Math.PI range
        while (yawAdjustment > 2 * Math.PI) {
            yawAdjustment -= 2 * (float)Math.PI;
        }
        while (yawAdjustment < 0) {
            yawAdjustment += 2 * (float)Math.PI;
        }
        saveAdjustments(context);
    }

    public static double yawAdjustmentDegrees() {
        return Math.toDegrees(yawAdjustment);
    }

}