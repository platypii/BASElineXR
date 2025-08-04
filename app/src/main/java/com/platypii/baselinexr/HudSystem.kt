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

    // Location updates are handled by BaselineActivity calling onLocation()
  }

  private fun initializePanel(activity: BaselineActivity) {
    val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
    val panel = composition.tryGetNodeByName("Panel")
    if (panel?.entity != null) {
      grabbablePanel = GrabbablePanel(systemManager, panel.entity)
      initialized = true
    }
  }


  fun setLabels(latlngLabel: TextView?, speedLabel: TextView?) {
    this.latlngLabel = latlngLabel
    this.speedLabel = speedLabel
    
    // Set initial values
    latlngLabel?.text = Services.location.dataSource()
    speedLabel?.text = "--- mph"
  }
  
  fun onLocation(loc: com.platypii.baselinexr.measurements.MLocation, activity: BaselineActivity) {
    LocationStatus.updateStatus(activity)
    val provider = Services.location.dataSource()
    latlngLabel?.text = provider + " " + loc.toStringSimple()
    latlngLabel?.setCompoundDrawablesWithIntrinsicBounds(LocationStatus.icon, 0, 0, 0)
    speedLabel?.text = Convert.speed(loc.groundSpeed()) + "  " + Convert.distance(loc.altitude_gps)
  }

  fun cleanup() {
    grabbablePanel = null
    latlngLabel = null
    speedLabel = null
    initialized = false
  }
}
