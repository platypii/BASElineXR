package com.platypii.baselinexr.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.platypii.baselinexr.Adjustments
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.DropzoneOptions
import com.platypii.baselinexr.DropzoneOptionsList
import com.platypii.baselinexr.HudOptions
import com.platypii.baselinexr.MockTrackList
import com.platypii.baselinexr.MockTrackOptions
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
            Services.sensor.restart()
        }

        // Dropzone button cycles through dropzone options (null = Auto)
        val dropzoneButton = rootView?.findViewById<Button>(R.id.dropzone_button)
        dropzoneButton?.text = DropzoneOptions.getCurrentName()
        dropzoneButton?.setOnClickListener {
            DropzoneOptions.current = DropzoneOptionsList.getNextDropzone(DropzoneOptions.current)
            DropzoneOptions.saveCurrentDropzone(activity)
            dropzoneButton.text = DropzoneOptions.getCurrentName()
            activity.miniMapPanel?.updateMinimapImage()
        }

        // Speed chart button toggles speed chart visibility
        val speedChartButton = rootView?.findViewById<Button>(R.id.speed_chart_button)
        speedChartButton?.isSelected = HudOptions.showSpeedChart
        speedChartButton?.setOnClickListener {
            HudOptions.showSpeedChart = !HudOptions.showSpeedChart
            HudOptions.saveHudOptions(activity)
            speedChartButton.isSelected = HudOptions.showSpeedChart
            activity.speedChartSystem?.updateVisibility()
        }

        // Track button cycles through mock tracks
        val trackButton = rootView?.findViewById<Button>(R.id.track_button)
        trackButton?.text = MockTrackList.getDisplayName(MockTrackOptions.current, activity)
        trackButton?.setOnClickListener {
            MockTrackOptions.cycleToNext()
            trackButton.text = MockTrackList.getDisplayName(MockTrackOptions.current, activity)
            Services.location.restart()
            Services.sensor.restart()
        }

        // Sensor data button toggles raw sensor data panel visibility
        val sensorDataButton = rootView?.findViewById<Button>(R.id.sensor_data_button)
        sensorDataButton?.isSelected = HudOptions.showSensorData
        sensorDataButton?.setOnClickListener {
            HudOptions.showSensorData = !HudOptions.showSensorData
            HudOptions.saveHudOptions(activity)
            sensorDataButton.isSelected = HudOptions.showSensorData
            activity.sensorDataSystem?.updateVisibility()
        }

        // Mag calibration button toggles magnetometer calibration panel visibility
        val magCalButton = rootView?.findViewById<Button>(R.id.mag_cal_button)
        magCalButton?.isSelected = HudOptions.showMagCalibration
        magCalButton?.setOnClickListener {
            HudOptions.showMagCalibration = !HudOptions.showMagCalibration
            HudOptions.saveHudOptions(activity)
            magCalButton.isSelected = HudOptions.showMagCalibration
            activity.magCalibrationSystem?.updateVisibility()
        }

        // AHRS button toggles AHRS panel visibility
        val ahrsButton = rootView?.findViewById<Button>(R.id.ahrs_button)
        ahrsButton?.isSelected = HudOptions.showAhrs
        ahrsButton?.setOnClickListener {
            HudOptions.showAhrs = !HudOptions.showAhrs
            HudOptions.saveHudOptions(activity)
            ahrsButton.isSelected = HudOptions.showAhrs
            activity.ahrsSystem?.updateVisibility()
        }

        // Control Point button toggles control point panel visibility
        val controlPointButton = rootView?.findViewById<Button>(R.id.control_point_button)
        controlPointButton?.isSelected = HudOptions.showControlPoint
        controlPointButton?.setOnClickListener {
            HudOptions.showControlPoint = !HudOptions.showControlPoint
            HudOptions.saveHudOptions(activity)
            controlPointButton.isSelected = HudOptions.showControlPoint
            activity.controlPointSystem?.updateVisibility()
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