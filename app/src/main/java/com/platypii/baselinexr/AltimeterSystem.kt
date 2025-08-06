package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.util.Convert
import com.platypii.baselinexr.util.GpsFreshnessColor

class AltimeterSystem : SystemBase() {
    private var initialized = false
    private var grabbablePanel: GrabbablePanel? = null

    // Altimeter content references
    private var altitudeLabel: TextView? = null

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

        updateLocation()
    }

    private fun initializePanel(activity: BaselineActivity) {
        val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val panel = composition.tryGetNodeByName("AltimeterPanel")
        if (panel?.entity != null) {
            // Position altimeter on the right side, slightly lower than main HUD
            val altimeterOffset = Vector3(-1.2f, -2.2f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, altimeterOffset)
            initialized = true
        }
    }

    fun setLabel(altitudeLabel: TextView?) {
        this.altitudeLabel = altitudeLabel
        updateLocation()
    }

    private fun updateLocation() {
        val loc = Services.location.lastLoc
        if (loc != null) {
            altitudeLabel?.text = Convert.distance(loc.altitude_gps)
        } else {
            altitudeLabel?.text = "--- ft"
        }

        val millisecondsSinceLastFix = Services.location.lastFixDuration()
        val color = GpsFreshnessColor.getColorForFreshness(millisecondsSinceLastFix)
        altitudeLabel?.setTextColor(color)
    }

    fun cleanup() {
        grabbablePanel = null
        altitudeLabel = null
        initialized = false
    }
}