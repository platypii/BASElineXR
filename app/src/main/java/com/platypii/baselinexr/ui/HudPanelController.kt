package com.platypii.baselinexr.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.platypii.baselinexr.Adjustments
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R

class HudPanelController(private val activity: BaselineActivity) {
    
    fun setupPanel(rootView: View?) {
        val exitButton = rootView?.findViewById<Button>(R.id.exit_button)
        exitButton?.setOnClickListener({
            activity.finish()
        })

        // Add click listener to hudPanel to toggle extraControls visibility
        val hudPanel = rootView?.findViewById<android.widget.LinearLayout>(R.id.hudPanel)
        val extraControls = rootView?.findViewById<android.widget.LinearLayout>(R.id.extraControls)
        hudPanel?.setOnClickListener({
            extraControls?.let { controls ->
                if (controls.visibility == View.VISIBLE) {
                    controls.visibility = View.GONE
                } else {
                    controls.visibility = View.VISIBLE
                }
            }
        })

        val yawPlusButton = rootView?.findViewById<Button>(R.id.yaw_plus_button)
        yawPlusButton?.setOnClickListener({
            // Increment yaw adjustment by 5 degrees (convert to radians)
            Adjustments.yawAdjustment += Math.toRadians(5.0)
            Adjustments.saveYawAdjustment(activity)
        })

        val yawMinusButton = rootView?.findViewById<Button>(R.id.yaw_minus_button)
        yawMinusButton?.setOnClickListener({
            // Decrement yaw adjustment by 5 degrees (convert to radians)
            Adjustments.yawAdjustment -= Math.toRadians(5.0)
            Adjustments.saveYawAdjustment(activity)
        })

        val fwdButton = rootView?.findViewById<Button>(R.id.fwd_button)
        fwdButton?.setOnClickListener({
            activity.handleForwardOrientationButton()
        })

        val tailButton = rootView?.findViewById<Button>(R.id.tail_button)
        tailButton?.setOnClickListener({
            activity.handleTailOrientationButton()
        })

        val northButton = rootView?.findViewById<Button>(R.id.north_button)
        northButton?.setOnClickListener({
            Adjustments.northAdjustment += 500.0
            Adjustments.saveAdjustments(activity)
        })

        val southButton = rootView?.findViewById<Button>(R.id.south_button)
        southButton?.setOnClickListener({
            Adjustments.northAdjustment -= 500.0
            Adjustments.saveAdjustments(activity)
        })

        val eastButton = rootView?.findViewById<Button>(R.id.east_button)
        eastButton?.setOnClickListener({
            Adjustments.eastAdjustment += 500.0
            Adjustments.saveAdjustments(activity)
        })

        val westButton = rootView?.findViewById<Button>(R.id.west_button)
        westButton?.setOnClickListener({
            Adjustments.eastAdjustment -= 500.0
            Adjustments.saveAdjustments(activity)
        })

        val centerButton = rootView?.findViewById<Button>(R.id.center_button)
        centerButton?.setOnClickListener({
            Adjustments.northAdjustment = 0.0
            Adjustments.eastAdjustment = 0.0
            Adjustments.saveAdjustments(activity)
        })

        // Set up HUD references
        val latlngLabel = rootView?.findViewById<TextView>(R.id.lat_lng)
        val speedLabel = rootView?.findViewById<TextView>(R.id.speed)
        activity.hudSystem?.setLabels(latlngLabel, speedLabel)
    }
}