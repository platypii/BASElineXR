package com.platypii.baselinexr

import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.charts.SpeedChartLive

class SpeedChartSystem : SystemBase() {
    private var initialized = false
    private var panelEntity: Entity? = null
    private var grabbablePanel: GrabbablePanel? = null
    private var speedChartLive: SpeedChartLive? = null

    override fun execute() {
        if (!initialized) {
            val activity = SpatialActivityManager.getVrActivity<BaselineActivity>()
            if (!activity.glxfLoaded) return
            initializePanel(activity)
        }

        if (initialized) {
            grabbablePanel?.setupInteraction()
            grabbablePanel?.updatePosition()
        }
    }

    private fun initializePanel(activity: BaselineActivity) {
        val composition = activity.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val panel = composition.tryGetNodeByName("SpeedChartPanel")
        if (panel?.entity != null) {
            panelEntity = panel.entity
            // Position on the right side of the screen
            val speedChartOffset = Vector3(1.6f, -0.8f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, speedChartOffset)
            panel.entity.setComponent(Visible(HudOptions.showSpeedChart))
            initialized = true
        }
    }

    fun setSpeedChart(speedChartLive: SpeedChartLive?) {
        this.speedChartLive = speedChartLive
        if (speedChartLive != null && HudOptions.showSpeedChart) {
            speedChartLive.start(Services.location)
        }
    }

    fun updateVisibility() {
        panelEntity?.setComponent(Visible(HudOptions.showSpeedChart))
        if (HudOptions.showSpeedChart) {
            speedChartLive?.start(Services.location)
        } else {
            speedChartLive?.stop()
        }
    }

    fun cleanup() {
        speedChartLive?.stop()
        panelEntity = null
        grabbablePanel = null
        speedChartLive = null
        initialized = false
    }
}