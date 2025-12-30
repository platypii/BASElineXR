package com.platypii.baselinexr.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.platypii.baselinexr.Adjustments
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.DropzoneOptions
import com.platypii.baselinexr.DropzoneOptionsList
import com.platypii.baselinexr.R
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.VROptions
import com.platypii.baselinexr.VROptionsList

class HudPanelController(private val activity: BaselineActivity) {
    
    fun setupPanel(rootView: View?) {
        val exitButton = rootView?.findViewById<Button>(R.id.exit_button)
        exitButton?.setOnClickListener({
            activity.finish()
        })

        // Config button toggles configControls visibility
        val configButton = rootView?.findViewById<Button>(R.id.config_button)
        val configControls = rootView?.findViewById<android.widget.GridLayout>(R.id.configControls)
        val extraControls = rootView?.findViewById<android.widget.GridLayout>(R.id.extraControls)
        configButton?.setOnClickListener {
            configControls?.let { controls ->
                if (controls.visibility == View.VISIBLE) {
                    controls.visibility = View.GONE
                    activity.hudSystem?.setExtraControlsVisible(false)
                } else {
                    controls.visibility = View.VISIBLE
                    extraControls?.visibility = View.GONE
                    activity.hudSystem?.setExtraControlsVisible(true)
                }
            }
        }

        // Mode button cycles through VROptions modes
        val modeButton = rootView?.findViewById<Button>(R.id.mode_button)
        modeButton?.text = VROptions.current.name  // Show current mode on startup
        modeButton?.setOnClickListener {
            VROptions.current = VROptionsList.getNextMode(VROptions.current)
            VROptions.saveCurrentMode(activity)
            modeButton.text = VROptions.current.name
            activity.terrainSystem?.reload()
            // Restart location service to switch between Live and Replay modes
            Services.location.restart()
        }

        // Dropzone button cycles through dropzone options
        val dropzoneButton = rootView?.findViewById<Button>(R.id.dropzone_button)
        dropzoneButton?.text = DropzoneOptions.current.name
        dropzoneButton?.setOnClickListener {
            DropzoneOptions.current = DropzoneOptionsList.getNextDropzone(DropzoneOptions.current)
            DropzoneOptions.saveCurrentDropzone(activity)
            dropzoneButton.text = DropzoneOptions.current.name
            activity.miniMapPanel?.updateMinimapImage()
        }

        // Add click listener to hudPanel to toggle extraControls visibility
        val hudPanel = rootView?.findViewById<android.widget.LinearLayout>(R.id.hudPanel)
        hudPanel?.setOnClickListener({
            extraControls?.let { controls ->
                if (controls.visibility == View.VISIBLE) {
                    controls.visibility = View.GONE
                    activity.hudSystem?.setExtraControlsVisible(false)
                } else {
                    controls.visibility = View.VISIBLE
                    configControls?.visibility = View.GONE
                    activity.hudSystem?.setExtraControlsVisible(true)
                }
            }
        })

        val yawPlusButton = rootView?.findViewById<Button>(R.id.yaw_plus_button)
        yawPlusButton?.setOnClickListener({
            // Increment yaw adjustment by 5 degrees (convert to radians)
            Adjustments.yawAdjustment -= Math.toRadians(5.0).toFloat()
            Adjustments.saveYawAdjustment(activity)
        })

        val yawMinusButton = rootView?.findViewById<Button>(R.id.yaw_minus_button)
        yawMinusButton?.setOnClickListener({
            // Decrement yaw adjustment by 5 degrees (convert to radians)
            Adjustments.yawAdjustment += Math.toRadians(5.0).toFloat()
            Adjustments.saveYawAdjustment(activity)
        })

        val fwdButton = rootView?.findViewById<Button>(R.id.fwd_button)
        fwdButton?.setOnClickListener({
            activity.handleOrientationButton(true)
        })

        val tailButton = rootView?.findViewById<Button>(R.id.tail_button)
        tailButton?.setOnClickListener({
            activity.handleOrientationButton(false)
        })

        val northButton = rootView?.findViewById<Button>(R.id.north_button)
        northButton?.setOnClickListener({
            Adjustments.northAdjustment += VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        })

        val southButton = rootView?.findViewById<Button>(R.id.south_button)
        southButton?.setOnClickListener({
            Adjustments.northAdjustment -= VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        })

        val eastButton = rootView?.findViewById<Button>(R.id.east_button)
        eastButton?.setOnClickListener({
            Adjustments.eastAdjustment += VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        })

        val westButton = rootView?.findViewById<Button>(R.id.west_button)
        westButton?.setOnClickListener({
            Adjustments.eastAdjustment -= VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        })

        val centerButton = rootView?.findViewById<Button>(R.id.center_button)
        centerButton?.setOnClickListener({
            Adjustments.northAdjustment = 0f
            Adjustments.eastAdjustment = 0f
            Adjustments.saveAdjustments(activity)
        })

        // Set up HUD references
        val latlngLabel = rootView?.findViewById<TextView>(R.id.lat_lng)
        val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
        activity.hudSystem?.setLabels(latlngLabel, speedLabel)
    }
}