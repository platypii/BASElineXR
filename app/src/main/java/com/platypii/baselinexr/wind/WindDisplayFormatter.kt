package com.platypii.baselinexr.wind

/**
 * Utility class for consistent wind data formatting across the application
 */
object WindDisplayFormatter {
    
    /**
     * Format wind estimation for display
     */
    fun formatWindEstimation(wind: WindEstimation?, prefix: String = "Wind"): String {
        return if (wind != null) {
            String.format("$prefix: %.1f mph @ %.0f° (R²=%.4f)", 
                wind.windMagnitude * 2.23694, 
                wind.windDirection, 
                wind.confidence)
        } else {
            "$prefix: -- mph @ --° (R²=--)"
        }
    }
    
    /**
     * Format wind estimation for short display (no R² value)
     */
    fun formatWindEstimationShort(wind: WindEstimation?, prefix: String = "Wind"): String {
        return if (wind != null) {
            String.format("$prefix: %.1f MPH @ %.0f°", 
                wind.windMagnitude * 2.23694, 
                wind.windDirection)
        } else {
            "$prefix: -- MPH @ --°"
        }
    }
    
    /**
     * Format aircraft speed for display
     */
    fun formatAircraftSpeed(circleFit: LeastSquaresCircleFit.CircleFitResult?, prefix: String = "Aircraft Speed"): String {
        return if (circleFit != null && circleFit.aircraftSpeed > 0) {
            String.format("$prefix: %.1f mph", circleFit.aircraftSpeed * 2.23694)
        } else {
            "$prefix: -- mph"
        }
    }
    
    /**
     * Get appropriate color for wind type display
     */
    fun getWindColor(windType: String): Int {
        return when (windType.lowercase()) {
            "gps" -> 0xFF66BB6A.toInt() // Green
            "sustained" -> 0xFF64B5F6.toInt() // Blue  
            else -> 0xFFCCCCCC.toInt() // Gray
        }
    }
    
    /**
     * Get appropriate background color for wind type display
     */
    fun getWindBackgroundColor(windType: String): Int {
        return when (windType.lowercase()) {
            "gps" -> 0xFF0D1F0D.toInt() // Dark green
            "sustained" -> 0xFF0D1621.toInt() // Dark blue
            else -> 0xFF333333.toInt() // Dark gray
        }
    }
}