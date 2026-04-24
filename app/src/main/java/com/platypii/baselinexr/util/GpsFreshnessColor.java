package com.platypii.baselinexr.util;

import android.graphics.Color;

public class GpsFreshnessColor {
    private static final long FADE_START_MS = 1000L;
    private static final long FADE_END_MS = 6000L;

    /**
     * Returns color based on GPS fix freshness, fading from white to red
     * between 1-6 seconds since last fix
     */
    public static int getColorForFreshness(long millisecondsSinceLastFix) {
        if (millisecondsSinceLastFix < FADE_START_MS) {
            return Color.WHITE;
        } else if (millisecondsSinceLastFix > FADE_END_MS) {
            return Color.RED;
        } else {
            // Linear interpolation between white and red
            float fadeProgress = (float) (millisecondsSinceLastFix - FADE_START_MS) / (FADE_END_MS - FADE_START_MS);
            int red = 255;
            int greenBlue = (int) (255 * (1 - fadeProgress));
            return Color.rgb(red, greenBlue, greenBlue);
        }
    }
}
