package com.platypii.baselinexr

import android.util.Log
import android.widget.TextView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.measurements.MBaroData
import com.platypii.baselinexr.measurements.MImuData
import com.platypii.baselinexr.measurements.MMagData
import com.platypii.baselinexr.util.PubSub

/**
 * System for managing the Raw Sensor Data panel.
 * Displays real-time gyroscope, accelerometer, and magnetometer data.
 */
class SensorDataSystem : SystemBase() {
    private val TAG = "SensorDataSystem"
    
    private var initialized = false
    private var panelEntity: Entity? = null
    private var grabbablePanel: GrabbablePanel? = null
    
    // View references
    private var gyroX: TextView? = null
    private var gyroY: TextView? = null
    private var gyroZ: TextView? = null
    private var accelX: TextView? = null
    private var accelY: TextView? = null
    private var accelZ: TextView? = null
    private var magX: TextView? = null
    private var magY: TextView? = null
    private var magZ: TextView? = null
    private var imuRateView: TextView? = null
    private var magRateView: TextView? = null
    private var sampleCountView: TextView? = null
    
    // Rate calculation
    private val imuRateCalc = RateCalculator()
    private val magRateCalc = RateCalculator()
    private var imuSampleCount = 0L
    private var magSampleCount = 0L
    
    // PubSub subscribers
    private var imuSubscriber: PubSub.Subscriber<MImuData>? = null
    private var magSubscriber: PubSub.Subscriber<MMagData>? = null
    
    // UI update throttling - update at ~30Hz instead of 400Hz
    private var lastImuUiUpdate = 0L
    private var lastMagUiUpdate = 0L
    private val uiUpdateIntervalMs = 33L  // ~30 Hz
    
    // Activity reference for UI updates
    private var activity: BaselineActivity? = null
    
    override fun execute() {
        if (!initialized) {
            val act = SpatialActivityManager.getVrActivity<BaselineActivity>()
            if (!act.glxfLoaded) return
            initializePanel(act)
        }
        
        if (initialized) {
            grabbablePanel?.setupInteraction()
            grabbablePanel?.updatePosition()
        }
    }
    
    private fun initializePanel(act: BaselineActivity) {
        this.activity = act
        
        val composition = act.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val panel = composition.tryGetNodeByName("SensorDataPanel")
        if (panel?.entity != null) {
            panelEntity = panel.entity
            
            // Position on the left side of the screen
            val sensorPanelOffset = Vector3(-1.6f, -0.5f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, sensorPanelOffset)
            
            // Set initial visibility
            panel.entity.setComponent(Visible(HudOptions.showSensorData))
            
            initialized = true
            Log.i(TAG, "Sensor data panel initialized")
            
            // Subscribe to sensor updates if visible
            if (HudOptions.showSensorData) {
                subscribeToSensorUpdates()
            }
        } else {
            Log.w(TAG, "SensorDataPanel node not found in scene")
        }
    }
    
    /**
     * Set up references to the panel views.
     * Called from BaselineActivity after layout inflation.
     */
    fun setViews(
        gyroX: TextView?, gyroY: TextView?, gyroZ: TextView?,
        accelX: TextView?, accelY: TextView?, accelZ: TextView?,
        magX: TextView?, magY: TextView?, magZ: TextView?,
        imuRateView: TextView?, magRateView: TextView?, sampleCountView: TextView?
    ) {
        this.gyroX = gyroX
        this.gyroY = gyroY
        this.gyroZ = gyroZ
        this.accelX = accelX
        this.accelY = accelY
        this.accelZ = accelZ
        this.magX = magX
        this.magY = magY
        this.magZ = magZ
        this.imuRateView = imuRateView
        this.magRateView = magRateView
        this.sampleCountView = sampleCountView
    }
    
    private fun subscribeToSensorUpdates() {
        // Subscribe to SensorService's republished updates
        imuSubscriber = PubSub.Subscriber { imu ->
            imuRateCalc.addSample()
            imuSampleCount++
            // Throttle UI updates to ~30Hz
            val now = System.currentTimeMillis()
            if (now - lastImuUiUpdate >= uiUpdateIntervalMs) {
                lastImuUiUpdate = now
                updateImuDisplay(imu)
            }
        }
        Services.sensor.imuUpdates.subscribeMain(imuSubscriber!!)
        
        magSubscriber = PubSub.Subscriber { mag ->
            magRateCalc.addSample()
            magSampleCount++
            // Throttle UI updates to ~30Hz
            val now = System.currentTimeMillis()
            if (now - lastMagUiUpdate >= uiUpdateIntervalMs) {
                lastMagUiUpdate = now
                updateMagDisplay(mag)
            }
        }
        Services.sensor.magUpdates.subscribeMain(magSubscriber!!)
        
        Log.i(TAG, "Subscribed to sensor updates")
    }
    
    private fun unsubscribeFromSensorUpdates() {
        imuSubscriber?.let { Services.sensor.imuUpdates.unsubscribeMain(it) }
        magSubscriber?.let { Services.sensor.magUpdates.unsubscribeMain(it) }
        imuSubscriber = null
        magSubscriber = null
        Log.i(TAG, "Unsubscribed from sensor updates")
    }
    
    private fun updateImuDisplay(imu: MImuData) {
        // Gyroscope values (already in deg/s)
        gyroX?.text = String.format("%+8.2f", imu.gyroX)
        gyroY?.text = String.format("%+8.2f", imu.gyroY)
        gyroZ?.text = String.format("%+8.2f", imu.gyroZ)
        
        // Accelerometer values (already in g)
        accelX?.text = String.format("%+7.3f", imu.accelX)
        accelY?.text = String.format("%+7.3f", imu.accelY)
        accelZ?.text = String.format("%+7.3f", imu.accelZ)
        
        // Update rate and sample count
        imuRateView?.text = String.format("%.1f Hz", imuRateCalc.getRate())
        sampleCountView?.text = String.format("%d IMU / %d MAG", imuSampleCount, magSampleCount)
    }
    
    private fun updateMagDisplay(mag: MMagData) {
        // Magnetometer values (in gauss)
        magX?.text = String.format("%+8.4f", mag.magX)
        magY?.text = String.format("%+8.4f", mag.magY)
        magZ?.text = String.format("%+8.4f", mag.magZ)
        
        // Update MAG rate
        magRateView?.text = String.format("%.1f Hz", magRateCalc.getRate())
    }
    
    /**
     * Toggle panel visibility.
     * Called from HudPanelController when button is pressed.
     */
    fun updateVisibility() {
        panelEntity?.setComponent(Visible(HudOptions.showSensorData))
        
        if (HudOptions.showSensorData) {
            // Reset counters when showing panel
            imuSampleCount = 0
            magSampleCount = 0
            imuRateCalc.reset()
            magRateCalc.reset()
            subscribeToSensorUpdates()
        } else {
            unsubscribeFromSensorUpdates()
        }
    }
    
    fun cleanup() {
        unsubscribeFromSensorUpdates()
        panelEntity = null
        grabbablePanel = null
        initialized = false
    }
    
    /**
     * Helper class to calculate update rate from timestamps
     */
    private class RateCalculator {
        private val timestamps = mutableListOf<Long>()
        private val windowMs = 2000L // Calculate rate over 2 second window

        @Synchronized
        fun addSample() {
            val now = System.currentTimeMillis()
            timestamps.add(now)
            // Remove old timestamps outside the window
            val cutoff = now - windowMs
            timestamps.removeAll { it < cutoff }
        }

        @Synchronized
        fun getRate(): Float {
            if (timestamps.size < 2) return 0f
            val oldest = timestamps.first()
            val newest = timestamps.last()
            val durationMs = newest - oldest
            return if (durationMs > 0) {
                (timestamps.size - 1) * 1000f / durationMs
            } else {
                0f
            }
        }

        @Synchronized
        fun reset() {
            timestamps.clear()
        }
    }
}
