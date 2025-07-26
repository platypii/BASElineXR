package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.location.LocationStatus
import com.platypii.baselinexr.util.Convert

class HudSystem(private val gpsTransform: GpsToWorldTransform) : SystemBase() {
  private var initialized = false
  private var grabbablePanel: GrabbablePanel? = null
  
  // HUD content references
  private var latlngLabel: TextView? = null
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

    // Location updates are set up via setupLocationUpdates() called from BaselineActivity
  }

  private fun initializePanel(activity: BaselineActivity) {
    val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
    val panel = composition.tryGetNodeByName("Panel")
    if (panel?.entity != null) {
      grabbablePanel = GrabbablePanel(systemManager, panel.entity)
      initialized = true
    }
  }


  fun setupLocationUpdates(activity: BaselineActivity, latlngLabel: TextView?, speedLabel: TextView?) {
    this.latlngLabel = latlngLabel
    this.speedLabel = speedLabel
    
    if (latlngLabel != null && speedLabel != null && !locationSubscriptionInitialized) {
      // Set initial values
      latlngLabel.text = Services.location.dataSource()
      
      // Subscribe to location updates
      Services.location.locationUpdates.subscribeMain { loc ->
        LocationStatus.updateStatus(activity)
        val provider = Services.location.dataSource()
        latlngLabel.text = provider + " " + loc.toStringSimple()
        latlngLabel.setCompoundDrawablesWithIntrinsicBounds(LocationStatus.icon, 0, 0, 0)
        speedLabel.text = Convert.speed(loc.groundSpeed()) + "  " + Convert.distance(loc.altitude_gps)

        // Update the origin to the latest GPS location
        gpsTransform.setOrigin(loc)
      }
      
      // Update status periodically
      LocationStatus.updateStatus(activity)
      latlngLabel.text = LocationStatus.message
      latlngLabel.setCompoundDrawablesWithIntrinsicBounds(LocationStatus.icon, 0, 0, 0)
      
      locationSubscriptionInitialized = true
    }
  }

}
