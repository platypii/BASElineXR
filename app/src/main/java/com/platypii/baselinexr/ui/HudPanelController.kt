
package com.platypii.baselinexr.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.ui.wind.WindEstimationController
import com.platypii.baselinexr.wind.WindDataPoint
import com.platypii.baselinexr.wind.WindSystem


// Wind input source selection
private enum class WindInputSource { ESTIMATION, NO_WIND, SAVED }
private var windInputSource: WindInputSource = WindInputSource.ESTIMATION
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

        // Wind input source radio group
        val windInputGroup = rootView?.findViewById<android.widget.RadioGroup>(R.id.wind_input_source_group)
        val windEstimationRadio = rootView?.findViewById<android.widget.RadioButton>(R.id.wind_input_estimation)
        val windNoWindRadio = rootView?.findViewById<android.widget.RadioButton>(R.id.wind_input_nowind)
        val windSavedRadio = rootView?.findViewById<android.widget.RadioButton>(R.id.wind_input_saved)

        windInputGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.wind_input_estimation -> setWindInputSource(WindInputSource.ESTIMATION)
                R.id.wind_input_nowind -> setWindInputSource(WindInputSource.NO_WIND)
                R.id.wind_input_saved -> setWindInputSource(WindInputSource.SAVED)
            }
        }
        // Set initial state
        windEstimationRadio?.isChecked = true
        setWindInputSource(WindInputSource.ESTIMATION)

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

        // Wind system toggle button (in wind estimation controls)
        val windSystemToggleButton = rootView?.findViewById<Button>(R.id.wind_system_toggle_button)
        windSystemToggleButton?.setOnClickListener {
            toggleWindSystemEnabled(windSystemToggleButton)
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

            // Update wind system toggle button to show current state
            val windSystemToggleButton = rootView?.findViewById<Button>(R.id.wind_system_toggle_button)
            updateWindSystemToggleButton(windSystemToggleButton)

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

    private fun updateWindSystemToggleButton(button: Button?) {
        if (button != null) {
            val windSystem = WindSystem.getInstance()
            val isEnabled = windSystem.isEnabled()

            if (isEnabled) {
                button.text = "Wind: ON"
                button.setTextColor(android.graphics.Color.WHITE)
            } else {
                button.text = "Wind: OFF"
                button.setTextColor(android.graphics.Color.RED)
            }
        }
    }

    private fun toggleWindSystemEnabled(button: Button) {
        try {
            val windSystem = WindSystem.getInstance()
            val currentlyEnabled = windSystem.isEnabled()
            val newEnabled = !currentlyEnabled

            windSystem.setEnabled(newEnabled)

            // Update button appearance
            updateWindSystemToggleButton(button)

            android.util.Log.d("HudPanelController", "Wind system toggled to: ${if (newEnabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            android.util.Log.e("HudPanelController", "Error toggling wind system: ${e.message}", e)
        }
    }

    fun cleanup() {
        windEstimationController.cleanup()
    }
    /**
     * Set wind input source and update WindSystem mode
     */
    private fun setWindInputSource(source: WindInputSource) {
        windInputSource = source
        val windSystem = com.platypii.baselinexr.wind.WindSystem.getInstance()
        when (source) {
            WindInputSource.ESTIMATION -> windSystem.setWindMode(com.platypii.baselinexr.wind.WindSystem.WindMode.ESTIMATION)
            WindInputSource.NO_WIND -> windSystem.setWindMode(com.platypii.baselinexr.wind.WindSystem.WindMode.NO_WIND)
            WindInputSource.SAVED -> windSystem.setWindMode(com.platypii.baselinexr.wind.WindSystem.WindMode.SAVED)
        }
    }
}