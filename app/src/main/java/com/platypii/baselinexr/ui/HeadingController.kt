package com.platypii.baselinexr.ui

import android.view.View
import android.widget.Button
import com.platypii.baselinexr.Adjustments
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.VROptions

/**
 * Controller for heading adjustment controls
 */
class HeadingController(private val activity: BaselineActivity) {
    
    fun setupControls(rootView: View?) {
        val yawPlusButton = rootView?.findViewById<Button>(R.id.yaw_plus_button)
        yawPlusButton?.setOnClickListener {
            // Increment yaw adjustment by 5 degrees (convert to radians)
            Adjustments.yawAdjustment -= Math.toRadians(5.0).toFloat()
            Adjustments.saveYawAdjustment(activity)
        }

        val yawMinusButton = rootView?.findViewById<Button>(R.id.yaw_minus_button)
        yawMinusButton?.setOnClickListener {
            // Decrement yaw adjustment by 5 degrees (convert to radians)
            Adjustments.yawAdjustment += Math.toRadians(5.0).toFloat()
            Adjustments.saveYawAdjustment(activity)
        }

        val fwdButton = rootView?.findViewById<Button>(R.id.fwd_button)
        fwdButton?.setOnClickListener {
            activity.handleOrientationButton(true)
        }

        val tailButton = rootView?.findViewById<Button>(R.id.tail_button)
        tailButton?.setOnClickListener {
            activity.handleOrientationButton(false)
        }

        val northButton = rootView?.findViewById<Button>(R.id.north_button)
        northButton?.setOnClickListener {
            Adjustments.northAdjustment += VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        }

        val southButton = rootView?.findViewById<Button>(R.id.south_button)
        southButton?.setOnClickListener {
            Adjustments.northAdjustment -= VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        }

        val eastButton = rootView?.findViewById<Button>(R.id.east_button)
        eastButton?.setOnClickListener {
            Adjustments.eastAdjustment += VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        }

        val westButton = rootView?.findViewById<Button>(R.id.west_button)
        westButton?.setOnClickListener {
            Adjustments.eastAdjustment -= VROptions.offsetDistance
            Adjustments.saveAdjustments(activity)
        }

        val centerButton = rootView?.findViewById<Button>(R.id.center_button)
        centerButton?.setOnClickListener {
            Adjustments.northAdjustment = 0f
            Adjustments.eastAdjustment = 0f
            Adjustments.saveAdjustments(activity)
        }
    }
}