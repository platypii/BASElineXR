package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.util.Convert

class SpeedometerSystem : SystemBase() {
    private var initialized = false
    private var grabbablePanel: GrabbablePanel? = null

    // Speedometer content references
    private var speedLabel: TextView? = null

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
        val panel = composition.tryGetNodeByName("SpeedometerPanel")
        if (panel?.entity != null) {
            // Position speedometer on the left side, slightly lower than main HUD
            val speedometerOffset = Vector3(1.2f, -2.2f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, speedometerOffset)
            initialized = true
        }
    }

    fun setLabel(speedLabel: TextView?) {
        this.speedLabel = speedLabel
        updateLocation()
    }

    private fun updateLocation() {
        val loc = Services.location.lastLoc
        val millisecondsSinceLastFix = Services.location.lastFixDuration()

        // Hide speedometer text if GPS data is stale (3+ seconds)
        if (millisecondsSinceLastFix >= 3000) {
            speedLabel?.text = ""
            return
        }

        if (loc != null) {
            speedLabel?.text = Convert.speed(loc.groundSpeed())
        } else {
            speedLabel?.text = "--- mph"
        }
    }

    fun cleanup() {
        grabbablePanel = null
        speedLabel = null
        initialized = false
    }
}