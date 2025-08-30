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
    private var horizontalSpeedLabel: TextView? = null
    private var verticalSpeedLabel: TextView? = null
    private var glideLabel: TextView? = null
    private var ldRatioLabel: TextView? = null

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

    fun setLabels(altitudeLabel: TextView?, horizontalSpeedLabel: TextView?, verticalSpeedLabel: TextView?, glideLabel: TextView?, ldRatioLabel: TextView?) {
        this.altitudeLabel = altitudeLabel
        this.horizontalSpeedLabel = horizontalSpeedLabel
        this.verticalSpeedLabel = verticalSpeedLabel
        this.glideLabel = glideLabel
        this.ldRatioLabel = ldRatioLabel
        updateFlightStats()
    }

    private fun updateFlightStats() {
        val loc = Services.location.lastLoc
        val millisecondsSinceLastFix = Services.location.lastFixDuration()
        
        if (loc != null) {
            // Altitude
            altitudeLabel?.text = Convert.distance(loc.altitude_gps - VROptions.target.alt)
            
            // Horizontal Speed
            val groundSpeed = loc.groundSpeed()
            horizontalSpeedLabel?.text = " H: ${Convert.speed(groundSpeed)}"
            
            // Vertical Speed
            val climb = loc.climb
            if (!climb.isNaN()) {
                verticalSpeedLabel?.text = " V: ${Convert.speed(-climb)}" // Negative climb for fall rate display
            } else {
                verticalSpeedLabel?.text = " V: --- mph"
            }
            
            // Glide ratio
            if (!climb.isNaN()) {
                glideLabel?.text = " GR: " + Convert.glide(groundSpeed, climb, 1, false)
            } else {
                glideLabel?.text = " GR: ---"
            }

            // L/D ratio from motion estimator
            val ld = Services.location.motionEstimator.ld()
            if (!ld.isNaN()) {
                ldRatioLabel?.text = " LD: ${String.format("%.1f", ld)}"
            } else {
                ldRatioLabel?.text = " LD: ---"
            }
        } else {
            altitudeLabel?.text = "--- ft"
            horizontalSpeedLabel?.text = " H: --- mph"
            verticalSpeedLabel?.text = " V: --- mph"
            glideLabel?.text = " GR: ---"
            ldRatioLabel?.text = " LD: ---"
        }

        // Set color based on GPS freshness
        val color = GpsFreshnessColor.getColorForFreshness(millisecondsSinceLastFix)
        altitudeLabel?.setTextColor(color)
        
        // Hide speed-related labels if GPS data is stale (3+ seconds)
        if (millisecondsSinceLastFix >= 3000) {
            horizontalSpeedLabel?.text = ""
            verticalSpeedLabel?.text = ""
            glideLabel?.text = ""
            ldRatioLabel?.text = ""
        } else {
            horizontalSpeedLabel?.setTextColor(color)
            verticalSpeedLabel?.setTextColor(color)
            glideLabel?.setTextColor(color)
            ldRatioLabel?.setTextColor(color)
        }
    }

    fun cleanup() {
        grabbablePanel = null
        altitudeLabel = null
        horizontalSpeedLabel = null
        verticalSpeedLabel = null
        glideLabel = null
        ldRatioLabel = null
        initialized = false
    }
}