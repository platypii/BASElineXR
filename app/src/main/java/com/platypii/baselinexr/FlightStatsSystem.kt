package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.util.Convert
import com.platypii.baselinexr.util.GpsFreshnessColor

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
            // Altitude (relative to landing zone, or raw GPS if no dropzone)
            val groundAlt = DropzoneOptions.current?.landingZone?.alt ?: 0.0
            altitudeLabel?.text = Convert.distance(loc.altitude_gps - groundAlt)

            // Combine other stats into single string
            val groundSpeed = loc.groundSpeed()
            val horizontalSpeedText = "H: ${Convert.speed(groundSpeed)}"

            val climb = loc.climb
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

    fun cleanup() {
        grabbablePanel = null
        altitudeLabel = null
        otherStatsLabel = null
        initialized = false
    }
}