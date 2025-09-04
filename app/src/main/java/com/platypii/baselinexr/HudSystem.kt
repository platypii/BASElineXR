package com.platypii.baselinexr

import android.widget.TextView
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.location.LocationStatus
import com.platypii.baselinexr.util.Convert
import com.platypii.baselinexr.util.GpsFreshnessColor

class HudSystem : SystemBase() {
  private var initialized = false
  private var grabbablePanel: GrabbablePanel? = null
  private var extraControlsVisible = false

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
      val hudOffset = Vector3(-0.2f, 1.4f, 3.6f)
      grabbablePanel = GrabbablePanel(systemManager, panel.entity, hudOffset)
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
    val refreshRate = Services.location.refreshRate()
    latlngLabel?.setCompoundDrawablesWithIntrinsicBounds(LocationStatus.icon, 0, 0, 0)
    if (loc != null) {
      latlngLabel?.text = provider + " " + loc.toStringSimple() + " (" + VROptions.current.name + ")"
      speedLabel?.text = Convert.speed(loc.groundSpeed()) + "  " + Convert.distance(loc.altitude_gps) + "  " + String.format("%.1f Hz", refreshRate)
    } else {
      latlngLabel?.text = LocationStatus.message
      speedLabel?.text = ""
    }
    val millisecondsSinceLastFix = Services.location.lastFixDuration()
    val color = GpsFreshnessColor.getColorForFreshness(millisecondsSinceLastFix)
    speedLabel?.setTextColor(color)
  }

  fun setExtraControlsVisible(visible: Boolean) {
    extraControlsVisible = visible
    val offset = if (visible) Vector3(0.2f, -1.4f, -2f) else Vector3(-0.2f, 1.4f, 2f)
    grabbablePanel?.moveByOffset(offset)

    // Enlarge DirectionArrow when extra controls are visible
    val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
    activity.directionArrowSystem?.setEnlarged(visible)
  }

  fun cleanup() {
    grabbablePanel = null
    latlngLabel = null
    speedLabel = null
    initialized = false
  }
}
