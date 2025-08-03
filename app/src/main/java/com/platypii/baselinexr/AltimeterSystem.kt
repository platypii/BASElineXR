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
    private var locationSubscriptionInitialized = false
    private var locationSubscriber: ((com.platypii.baselinexr.measurements.MLocation) -> Unit)? = null

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

    fun setupLocationUpdates(activity: BaselineActivity, altitudeLabel: TextView?) {
        this.altitudeLabel = altitudeLabel
        
        if (altitudeLabel != null && !locationSubscriptionInitialized) {
            // Set initial value
            altitudeLabel.text = "--- ft"
            
            // Subscribe to location updates
            locationSubscriber = { loc ->
                val altitudeText = Convert.distance(loc.altitude_gps)
                altitudeLabel.text = altitudeText
            }
            Services.location.locationUpdates.subscribeMain(locationSubscriber!!)
            
            locationSubscriptionInitialized = true
        }
    }

    fun cleanup() {
        locationSubscriber?.let { subscriber ->
            Services.location.locationUpdates.unsubscribeMain(subscriber)
            locationSubscriber = null
        }
        grabbablePanel = null
        altitudeLabel = null
        locationSubscriptionInitialized = false
        initialized = false
    }
}