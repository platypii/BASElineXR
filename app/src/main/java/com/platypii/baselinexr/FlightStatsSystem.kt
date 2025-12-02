package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.location.KalmanFilter3D
import com.platypii.baselinexr.util.Convert
import com.platypii.baselinexr.util.GpsFreshnessColor
import kotlin.math.sqrt

class FlightStatsSystem : SystemBase() {
    private var initialized = false
    private var grabbablePanel: GrabbablePanel? = null

    // Flight stats content references
    private var altitudeLabel: TextView? = null
    private var otherStatsLabel: TextView? = null

    override fun execute() {
        val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
        if (!activity.glxfLoaded) return

        if (!initialized) {
            initializePanel(activity)
        }

        if (initialized) {
            grabbablePanel?.setupInteraction()
            grabbablePanel?.updatePosition()
        }

        updateFlightStats()
    }

    private fun initializePanel(activity: BaselineActivity) {
        val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val panel = composition.tryGetNodeByName("FlightStatsPanel")
        if (panel?.entity != null) {
            // Position on bottom left where the altimeter was
            val flightStatsOffset = Vector3(-1.6f, -0.8f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, flightStatsOffset)
            initialized = true
        }
    }

    fun setLabels(altitudeLabel: TextView?, otherStatsLabel: TextView?) {
        this.altitudeLabel = altitudeLabel
        this.otherStatsLabel = otherStatsLabel
        updateFlightStats()
    }

    private fun updateFlightStats() {
        val loc = Services.location.lastLoc
        val millisecondsSinceLastFix = Services.location.lastFixDuration()

        if (loc != null) {
            // Use high-speed predicted data for position and velocity when available
            val (altitude, groundSpeed, climb) = getHighSpeedData(loc)

            // Altitude with 90Hz updates
            altitudeLabel?.text = Convert.distance(altitude - VROptions.dropzone.alt)

            // Combine other stats into single string
            val horizontalSpeedText = "H: ${Convert.speed(groundSpeed)}"

            val verticalSpeedText = if (!climb.isNaN()) {
                "V: ${Convert.speed(-climb)}" // Negative climb for fall rate display
            } else {
                "V: --- mph"
            }

            val glideText = if (!climb.isNaN()) {
                "GR: " + Convert.glide(groundSpeed, climb, 1, false)
            } else {
                "GR: ---"
            }

            val ld = Services.location.motionEstimator.ld()
            val ldText = if (!ld.isNaN()) {
                "LD: ${String.format("%.1f", ld)}"
            } else {
                "LD: ---"
            }

            otherStatsLabel?.text = "$horizontalSpeedText\n$verticalSpeedText\n$glideText\n$ldText"
        } else {
            altitudeLabel?.text = "--- ft"
            otherStatsLabel?.text = "H: --- mph\nV: --- mph\nGR: ---\nLD: ---"
        }

        // Set color based on GPS freshness
        val color = GpsFreshnessColor.getColorForFreshness(millisecondsSinceLastFix)
        altitudeLabel?.setTextColor(color)

        // Hide speed-related stats if GPS data is stale (3+ seconds)
        if (millisecondsSinceLastFix >= 3000) {
            otherStatsLabel?.text = ""
        } else {
            otherStatsLabel?.setTextColor(color)
        }
    }

    /**
     * Get position and velocity data using high-speed predicted state (90Hz) when available,
     * fallback to GPS data for backwards compatibility
     */
    private fun getHighSpeedData(loc: com.platypii.baselinexr.measurements.MLocation): Triple<Double, Double, Double> {
        return if (Services.location.motionEstimator is KalmanFilter3D) {
            val kf3d = Services.location.motionEstimator as KalmanFilter3D
            val predictedState = kf3d.getCachedPredictedState(System.currentTimeMillis())

            // Get predicted position delta and add to GPS baseline
            val positionDelta =   predictedState.position().y -kf3d.state.position().y
            val predictedAltitude = loc.altitude_gps + positionDelta

            // Calculate ground speed from predicted velocity components
            val groundSpeed = sqrt(
                predictedState.velocity().x * predictedState.velocity().x +
                        predictedState.velocity().z * predictedState.velocity().z
            )
            val climb = predictedState.velocity().y

            Triple(predictedAltitude, groundSpeed, climb)
        } else {
            // Fallback to GPS data
            Triple(loc.altitude_gps, loc.groundSpeed(), loc.climb)
        }
    }

    fun cleanup() {
        grabbablePanel = null
        altitudeLabel = null
        otherStatsLabel = null
        initialized = false
    }
}