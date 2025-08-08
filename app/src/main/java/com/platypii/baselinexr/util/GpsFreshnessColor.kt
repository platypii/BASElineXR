package com.platypii.baselinexr.util

import android.graphics.Color

object GpsFreshnessColor {
    private const val FADE_START_MS = 1000L
    private const val FADE_END_MS = 6000L
    
    /**
     * Returns color based on GPS fix freshness, fading from white to red
     * between 5-10 seconds since last fix
     */
    fun getColorForFreshness(millisecondsSinceLastFix: Long): Int {
        return when {
            millisecondsSinceLastFix < FADE_START_MS -> Color.WHITE
            millisecondsSinceLastFix > FADE_END_MS -> Color.RED
            else -> {
                // Linear interpolation between white and red
                val fadeProgress = (millisecondsSinceLastFix - FADE_START_MS).toFloat() / (FADE_END_MS - FADE_START_MS)
                val red = 255
                val greenBlue = (255 * (1 - fadeProgress)).toInt()
                Color.rgb(red, greenBlue, greenBlue)
            }
        }
    }
}