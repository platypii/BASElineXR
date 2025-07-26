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
    private var locationSubscriptionInitialized = false

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
        val panel = composition.tryGetNodeByName("SpeedometerPanel")
        if (panel?.entity != null) {
            // Position speedometer on the left side, slightly lower than main HUD
            val speedometerOffset = Vector3(1.2f, -2.5f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, speedometerOffset)
            initialized = true
        }
    }

    fun setupLocationUpdates(activity: BaselineActivity, speedLabel: TextView?) {
        this.speedLabel = speedLabel
        
        if (speedLabel != null && !locationSubscriptionInitialized) {
            // Set initial value
            speedLabel.text = "--- mph"
            
            // Subscribe to location updates
            Services.location.locationUpdates.subscribeMain { loc ->
                val speedText = Convert.speed(loc.groundSpeed())
                speedLabel.text = speedText
            }
            
            locationSubscriptionInitialized = true
        }
    }
}