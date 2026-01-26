package com.platypii.baselinexr;

import android.content.Context;
import android.content.SharedPreferences;

public class HudOptions {

    private static final String PREF_NAME = "BASElineXRPrefs";
    private static final String KEY_SHOW_SPEED_CHART = "hud_showSpeedChart";
    private static final String KEY_SHOW_SENSOR_DATA = "hud_showSensorData";
    private static final String KEY_SHOW_MAG_CALIBRATION = "hud_showMagCalibration";
    private static final String KEY_SHOW_AHRS = "hud_showAhrs";

    // Show speed chart panel?
    public static boolean showSpeedChart = true;
    
    // Show raw sensor data panel?
    public static boolean showSensorData = false;
    
    // Show magnetometer calibration panel?
    public static boolean showMagCalibration = false;
    
    // Show AHRS panel?
    public static boolean showAhrs = false;

    // Load saved HUD options from SharedPreferences
    public static void loadHudOptions(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        showSpeedChart = prefs.getBoolean(KEY_SHOW_SPEED_CHART, false);
        showSensorData = prefs.getBoolean(KEY_SHOW_SENSOR_DATA, false);
        showMagCalibration = prefs.getBoolean(KEY_SHOW_MAG_CALIBRATION, false);
        showAhrs = prefs.getBoolean(KEY_SHOW_AHRS, false);
    }

    // Save HUD options to SharedPreferences
    public static void saveHudOptions(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putBoolean(KEY_SHOW_SPEED_CHART, showSpeedChart)
            .putBoolean(KEY_SHOW_SENSOR_DATA, showSensorData)
            .putBoolean(KEY_SHOW_MAG_CALIBRATION, showMagCalibration)
            .putBoolean(KEY_SHOW_AHRS, showAhrs)
            .apply();
    }
}
