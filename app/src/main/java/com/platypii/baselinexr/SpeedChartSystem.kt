package com.platypii.baselinexr

import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.platypii.baselinexr.charts.SpeedChartLive

class SpeedChartSystem : SystemBase() {
    private var initialized = false
    private var grabbablePanel: GrabbablePanel? = null
    private var speedChartLive: SpeedChartLive? = null

    override fun execute() {
        // Don't show speed chart if disabled in VROptions
        if (!VROptions.current.showSpeedChart) {
            return
        }

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
        val panel = composition.tryGetNodeByName("SpeedChartPanel")
        if (panel?.entity != null) {
            // Position on the right side of the screen
            val speedChartOffset = Vector3(1.6f, -0.8f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, speedChartOffset)
            initialized = true
        }
    }

    fun setSpeedChart(speedChartLive: SpeedChartLive?) {
        this.speedChartLive = speedChartLive
        if (speedChartLive != null) {
            speedChartLive.start(Services.location)
        }
    }

    fun cleanup() {
        speedChartLive?.stop()
        grabbablePanel = null
        speedChartLive = null
        initialized = false
    }
}