package com.platypii.baselinexr;

import android.content.Context;
import android.content.SharedPreferences;

public class HudOptions {

    private static final String PREF_NAME = "BASElineXRPrefs";
    private static final String KEY_SHOW_SPEED_CHART = "hud_showSpeedChart";

    // Show speed chart panel?
    public static boolean showSpeedChart = true;

    // Load saved HUD options from SharedPreferences
    public static void loadHudOptions(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        showSpeedChart = prefs.getBoolean(KEY_SHOW_SPEED_CHART, false);
    }

    // Save HUD options to SharedPreferences
    public static void saveHudOptions(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SHOW_SPEED_CHART, showSpeedChart).apply();
    }
}
