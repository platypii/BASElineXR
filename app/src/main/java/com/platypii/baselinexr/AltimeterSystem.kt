package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.util.Convert

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
        // Set initial value
        altitudeLabel?.text = "--- ft"
    }
    
    fun onLocation(loc: com.platypii.baselinexr.measurements.MLocation) {
        val altitudeText = Convert.distance(loc.altitude_gps)
        altitudeLabel?.text = altitudeText
    }

    fun cleanup() {
        grabbablePanel = null
        altitudeLabel = null
        initialized = false
    }
}