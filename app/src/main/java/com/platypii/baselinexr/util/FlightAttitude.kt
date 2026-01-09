package com.platypii.baselinexr.util

/**
 * Shared flight attitude data that can be passed between visualization systems.
 * Contains the raw calculated values before model-specific adjustments are applied.
 * 
 * Each visualization system can then apply its own model-specific adjustments
 * when building the final quaternion rotation.
 * 
 * @param headingRad GPS heading in radians (North=0, East=π/2)
 * @param pitchRad Pitch angle in radians (negative = descending/nose down)
 * @param rollRad Roll angle in radians (positive = right wing down)
 * @param aoaRad Angle of attack in radians (positive = nose up relative to velocity)
 * @param betaRad Sideslip angle in radians (heading component of control rotation, combined with AOA)
 * @param yawAdjustment User yaw adjustment in radians (from Adjustments)
 */
data class FlightAttitude(
    val headingRad: Float,
    val pitchRad: Float,
    val rollRad: Float,
    val aoaRad: Float,
    val betaRad: Float = 0f,
    val yawAdjustment: Float = 0f
)
