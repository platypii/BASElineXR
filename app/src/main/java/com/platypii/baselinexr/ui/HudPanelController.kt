package com.platypii.baselinexr.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.ui.wind.WindEstimationController
import com.platypii.baselinexr.wind.WindDataPoint

/**
 * Main controller for the HUD panel UI
 */
class HudPanelController(private val activity: BaselineActivity) {

    private var isWindEstimationMode = false
    private val windEstimationController = WindEstimationController(activity)
    private val headingController = HeadingController(activity)

    fun setupPanel(rootView: View?) {
        val exitButton = rootView?.findViewById<Button>(R.id.exit_button)
        exitButton?.setOnClickListener {
            activity.finish()
        }

        // Initialize controllers
        windEstimationController.initialize(rootView)
        headingController.setupControls(rootView)

        // Add click listener to hudPanel to toggle extraControls visibility
        val hudPanel = rootView?.findViewById<android.widget.LinearLayout>(R.id.hudPanel)
        val extraControls = rootView?.findViewById<android.widget.GridLayout>(R.id.extraControls)
        val windEstimationControls = rootView?.findViewById<android.widget.LinearLayout>(R.id.windEstimationControls)

        hudPanel?.setOnClickListener {
            if (isWindEstimationMode) {
                // Hide wind estimation and show heading controls
                windEstimationControls?.visibility = View.GONE
                extraControls?.visibility = View.VISIBLE
                isWindEstimationMode = false
                windEstimationController.stopCollection()
                activity.hudSystem?.setExtraControlsVisible(true)
            } else {
                extraControls?.let { controls ->
                    if (controls.visibility == View.VISIBLE) {
                        controls.visibility = View.GONE
                        activity.hudSystem?.setExtraControlsVisible(false)
                    } else {
                        controls.visibility = View.VISIBLE
                        activity.hudSystem?.setExtraControlsVisible(true)
                    }
                }
            }
        }

        setupModeToggleButtons(rootView)

        // Set up HUD references
        val latlngLabel = rootView?.findViewById<TextView>(R.id.lat_lng)
        val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
        activity.hudSystem?.setLabels(latlngLabel, speedLabel)
    }

    private fun setupModeToggleButtons(rootView: View?) {
        // Wind estimation button (in heading controls)
        val windEstimationButton = rootView?.findViewById<Button>(R.id.wind_estimation_button)
        windEstimationButton?.setOnClickListener {
            switchToWindEstimationMode(rootView)
        }

        // Mode switch button (in wind estimation controls)
        val windModeSwitchButton = rootView?.findViewById<Button>(R.id.wind_mode_switch_button)
        windModeSwitchButton?.setOnClickListener {
            switchToHeadingMode(rootView)
        }
    }

    private fun switchToWindEstimationMode(rootView: View?) {
        try {
            android.util.Log.d("HudPanelController", "Switching to wind estimation mode")

            val extraControls = rootView?.findViewById<android.widget.GridLayout>(R.id.extraControls)
            val windEstimationControls = rootView?.findViewById<android.widget.LinearLayout>(R.id.windEstimationControls)

            extraControls?.visibility = View.GONE
            windEstimationControls?.visibility = View.VISIBLE
            isWindEstimationMode = true

            windEstimationController.startCollection()

            activity.hudSystem?.setExtraControlsVisible(false)

            android.util.Log.d("HudPanelController", "Wind estimation mode activated successfully")
        } catch (e: Exception) {
            android.util.Log.e("HudPanelController", "Error switching to wind estimation mode: ${e.message}", e)
        }
    }

    private fun switchToHeadingMode(rootView: View?) {
        val extraControls = rootView?.findViewById<android.widget.GridLayout>(R.id.extraControls)
        val windEstimationControls = rootView?.findViewById<android.widget.LinearLayout>(R.id.windEstimationControls)

        windEstimationControls?.visibility = View.GONE
        extraControls?.visibility = View.VISIBLE
        isWindEstimationMode = false

        windEstimationController.stopCollection()
        activity.hudSystem?.setExtraControlsVisible(true)
    }

    /**
     * Add new data point to wind layers
     */
    fun addDataPointToLayers(dataPoint: WindDataPoint) {
        windEstimationController.addDataPointToLayers(dataPoint)
    }

    fun cleanup() {
        windEstimationController.cleanup()
    }
}