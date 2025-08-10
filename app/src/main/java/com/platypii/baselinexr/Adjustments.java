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
    public static double yawAdjustment = 0.0; // radians
    public static double northAdjustment = 0.0;
    public static double eastAdjustment = 0.0;

    public static void loadAdjustments(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        yawAdjustment = Double.longBitsToDouble(prefs.getLong(KEY_YAW_ADJUSTMENT, 
            Double.doubleToLongBits(0.0)));
        northAdjustment = Double.longBitsToDouble(prefs.getLong(KEY_NORTH_ADJUSTMENT,
            Double.doubleToLongBits(0.0)));
        eastAdjustment = Double.longBitsToDouble(prefs.getLong(KEY_EAST_ADJUSTMENT,
            Double.doubleToLongBits(0.0)));
        Log.d(TAG, "Loaded yawAdjustment: " + Math.toDegrees(yawAdjustment) + " degrees");
        Log.d(TAG, "Loaded northAdjustment: " + northAdjustment);
        Log.d(TAG, "Loaded eastAdjustment: " + eastAdjustment);
    }

    public static void saveAdjustments(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putLong(KEY_YAW_ADJUSTMENT, Double.doubleToLongBits(yawAdjustment))
            .putLong(KEY_NORTH_ADJUSTMENT, Double.doubleToLongBits(northAdjustment))
            .putLong(KEY_EAST_ADJUSTMENT, Double.doubleToLongBits(eastAdjustment))
            .apply();
        Log.d(TAG, "Saved yawAdjustment: " + Math.toDegrees(yawAdjustment) + " degrees");
        Log.d(TAG, "Saved northAdjustment: " + northAdjustment);
        Log.d(TAG, "Saved eastAdjustment: " + eastAdjustment);
    }

    public static void saveYawAdjustment(Context context) {
        // Normalize yawAdjustment to +/- Math.PI range
        while (yawAdjustment > 2 * Math.PI) {
            yawAdjustment -= 2 * Math.PI;
        }
        while (yawAdjustment < 0) {
            yawAdjustment += 2 * Math.PI;
        }
        saveAdjustments(context);
    }

}