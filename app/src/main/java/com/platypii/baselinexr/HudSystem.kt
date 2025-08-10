package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.location.LocationStatus
import com.platypii.baselinexr.util.Convert
import com.platypii.baselinexr.util.GpsFreshnessColor

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

    updateLocation()
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

    updateLocation()
  }
  
  private fun updateLocation() {
    val provider = Services.location.dataSource()
    val loc = Services.location.lastLoc
    latlngLabel?.setCompoundDrawablesWithIntrinsicBounds(LocationStatus.icon, 0, 0, 0)
    if (loc != null) {
      latlngLabel?.text = provider + " " + loc.toStringSimple() + " (" + VROptions.current.sourceModel + " -> " + VROptions.current.dest + ")"
      speedLabel?.text = Convert.speed(loc.groundSpeed()) + "  " + Convert.distance(loc.altitude_gps)
    } else {
      latlngLabel?.text = LocationStatus.message
      speedLabel?.text = ""
    }
    val millisecondsSinceLastFix = Services.location.lastFixDuration()
    val color = GpsFreshnessColor.getColorForFreshness(millisecondsSinceLastFix)
    speedLabel?.setTextColor(color)
  }

  fun cleanup() {
    grabbablePanel = null
    latlngLabel = null
    speedLabel = null
    initialized = false
  }
}
