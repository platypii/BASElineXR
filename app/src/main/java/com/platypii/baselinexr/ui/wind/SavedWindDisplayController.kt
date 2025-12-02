package com.platypii.baselinexr.ui.wind

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.wind.SavedWindSystem
import com.platypii.baselinexr.wind.WindSystem

/**
 * Controller for the Saved Wind keyframe display (read-only keyframe editor style)
 */
class SavedWindDisplayController(private val activity: BaselineActivity) {

    private var keyframeListLayout: LinearLayout? = null
    private var keyframeCountText: TextView? = null
    private var currentInterpolatedWindText: TextView? = null
    private var keyframeScrollView: ScrollView? = null

    /**
     * Initialize the saved wind display with the given view
     */
    fun initialize(rootView: View?) {
        keyframeListLayout = rootView?.findViewById(R.id.keyframe_list)
        keyframeCountText = rootView?.findViewById(R.id.keyframe_count)
        currentInterpolatedWindText = rootView?.findViewById(R.id.current_interpolated_wind)
        keyframeScrollView = rootView?.findViewById(R.id.keyframe_scroll)

        setupScrollControls(rootView)
        populateKeyframeList()
    }

    private fun setupScrollControls(rootView: View?) {
        val scrollAmount = 80 // pixels to scroll per click

        rootView?.findViewById<Button>(R.id.keyframe_scroll_up_button)?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Keyframe scroll up clicked!")
            keyframeScrollView?.smoothScrollBy(0, -scrollAmount)
        }

        rootView?.findViewById<Button>(R.id.keyframe_scroll_down_button)?.setOnClickListener {
            android.util.Log.i("BXRINPUT", "Keyframe scroll down clicked!")
            keyframeScrollView?.smoothScrollBy(0, scrollAmount)
        }
    }

    /**
     * Populate the keyframe list from the SavedWindSystem
     */
    private fun populateKeyframeList() {
        keyframeListLayout?.removeAllViews()

        val savedWindSystem = WindSystem.getInstance().getSavedWindSystem()
        if (savedWindSystem == null) {
            android.util.Log.w("SavedWindDisplayController", "SavedWindSystem not available")
            showNoDataMessage()
            return
        }

        val keyframes = savedWindSystem.keyframes
        if (keyframes.isEmpty()) {
            showNoDataMessage()
            return
        }

        keyframeCountText?.text = "${keyframes.size} keyframes"

        val inflater = LayoutInflater.from(activity)

        // Display keyframes from highest to lowest altitude (typical for skydiving)
        val sortedKeyframes = keyframes.sortedByDescending { it.altitude }

        for (keyframe in sortedKeyframes) {
            val itemView = inflater.inflate(R.layout.wind_keyframe_item, keyframeListLayout, false)

            // Altitude (convert meters to feet)
            val altitudeFt = keyframe.altitude * 3.28084
            itemView.findViewById<TextView>(R.id.keyframe_altitude)?.text = 
                String.format("%.0fft", altitudeFt)

            // Wind speed (assuming m/s, convert to mph)
            val speedMph = keyframe.windspeed * 2.23694
            itemView.findViewById<TextView>(R.id.keyframe_speed)?.text = 
                String.format("%.1f mph", speedMph)

            // Wind direction
            itemView.findViewById<TextView>(R.id.keyframe_direction)?.text = 
                String.format("%.0f°", keyframe.direction)

            // Inclination
            itemView.findViewById<TextView>(R.id.keyframe_inclination)?.text = 
                String.format("%.1f°", keyframe.inclination)

            keyframeListLayout?.addView(itemView)
        }
    }

    private fun showNoDataMessage() {
        keyframeCountText?.text = "No keyframes"

        val messageView = TextView(activity)
        messageView.text = "No wind keyframes loaded.\nCheck savedwind.csv in assets."
        messageView.setTextColor(0xFFAAAAAA.toInt())
        messageView.textSize = 12f
        messageView.setPadding(16, 32, 16, 32)
        messageView.gravity = android.view.Gravity.CENTER

        keyframeListLayout?.addView(messageView)
    }

    /**
     * Update the current interpolated wind display based on current altitude
     */
    fun updateCurrentInterpolatedWind() {
        val savedWindSystem = WindSystem.getInstance().getSavedWindSystem()
        if (savedWindSystem == null) {
            currentInterpolatedWindText?.text = "No data"
            return
        }

        // Get current altitude
        val currentAltitude = Services.location?.lastLoc?.altitude_gps ?: 0.0

        val interpolated = savedWindSystem.getWindAtAltitude(currentAltitude)
        if (interpolated == null) {
            currentInterpolatedWindText?.text = "-- mph @ --° (incl: --°)"
            return
        }

        // Convert units
        val speedMph = interpolated.windspeed * 2.23694
        val altitudeFt = currentAltitude * 3.28084

        currentInterpolatedWindText?.text = String.format(
            "%.0fft: %.1f mph @ %.0f° (incl: %.1f°)",
            altitudeFt,
            speedMph,
            interpolated.direction,
            interpolated.inclination
        )
    }

    /**
     * Refresh the display (call when returning to this view)
     */
    fun refresh() {
        populateKeyframeList()
        updateCurrentInterpolatedWind()
    }
}
