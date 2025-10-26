package com.platypii.baselinexr.wind

/**
 * Cached circle fit results to avoid redundant calculations
 */
data class WindCalculationCache(
    val dataPoints: List<WindDataPoint>,
    val dataHash: Int,
    val gpsCircleFit: LeastSquaresCircleFit.CircleFitResult?,
    val sustainedCircleFit: LeastSquaresCircleFit.CircleFitResult?,
    val gpsWindEstimation: WindEstimation?,
    val sustainedWindEstimation: WindEstimation?
) {
    companion object {
        /**
         * Calculate hash for data points to detect changes
         */
        fun calculateDataHash(dataPoints: List<WindDataPoint>): Int {
            var hash = dataPoints.size
            if (dataPoints.isNotEmpty()) {
                // Use first, middle, and last points for hash to detect changes
                hash = 31 * hash + dataPoints.first().hashCode()
                if (dataPoints.size > 2) {
                    hash = 31 * hash + dataPoints[dataPoints.size / 2].hashCode()
                }
                hash = 31 * hash + dataPoints.last().hashCode()
            }
            return hash
        }
        
        /**
         * Create cache from data points by running circle fits
         */
        fun createFromData(dataPoints: List<WindDataPoint>): WindCalculationCache {
            val dataHash = calculateDataHash(dataPoints)
            
            var gpsCircleFit: LeastSquaresCircleFit.CircleFitResult? = null
            var sustainedCircleFit: LeastSquaresCircleFit.CircleFitResult? = null
            var gpsWindEstimation: WindEstimation? = null
            var sustainedWindEstimation: WindEstimation? = null
            
            if (dataPoints.size >= 3) {
                try {
                    gpsCircleFit = LeastSquaresCircleFit.fitCircleToGPSVelocities(dataPoints)
                    if (gpsCircleFit != null && gpsCircleFit.pointCount >= 3) {
                        gpsWindEstimation = WindEstimation(
                            gpsCircleFit.getWindE(),
                            gpsCircleFit.getWindN(),
                            gpsCircleFit.rSquared,
                            gpsCircleFit.pointCount
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WindCalculationCache", "Failed to fit GPS circle: ${e.message}")
                }
                
                try {
                    sustainedCircleFit = LeastSquaresCircleFit.fitCircleToSustainedVelocities(dataPoints)
                    if (sustainedCircleFit != null && sustainedCircleFit.pointCount >= 3) {
                        sustainedWindEstimation = WindEstimation(
                            sustainedCircleFit.getWindE(),
                            sustainedCircleFit.getWindN(),
                            sustainedCircleFit.rSquared,
                            sustainedCircleFit.pointCount
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WindCalculationCache", "Failed to fit sustained circle: ${e.message}")
                }
            }
            
            return WindCalculationCache(
                dataPoints, dataHash, gpsCircleFit, sustainedCircleFit, 
                gpsWindEstimation, sustainedWindEstimation
            )
        }
    }
    
    /**
     * Check if this cache is valid for the given data points
     */
    fun isValidFor(newDataPoints: List<WindDataPoint>): Boolean {
        val newHash = calculateDataHash(newDataPoints)
        return dataHash == newHash
    }
}