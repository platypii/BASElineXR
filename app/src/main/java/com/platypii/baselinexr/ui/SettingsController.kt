package com.platypii.baselinexr.ui

import android.content.res.ColorStateList
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.platypii.baselinexr.AtmosphereSettings
import com.platypii.baselinexr.R
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.VROptions
import com.platypii.baselinexr.bluetooth.BluetoothService
import com.platypii.baselinexr.bluetooth.FlysightTimeSync
import com.platypii.baselinexr.location.MockSensorProvider
import com.platypii.baselinexr.measurements.MBaroData
import com.platypii.baselinexr.measurements.MHumidityData
import com.platypii.baselinexr.measurements.MImuData
import com.platypii.baselinexr.measurements.MMagData
import com.platypii.baselinexr.measurements.MSensorData
import com.platypii.baselinexr.measurements.MTimeSync
import com.platypii.baselinexr.replay.PlaybackTimeline
import com.platypii.baselinexr.util.PubSub

/**
 * Controller for the Settings menu UI.
 * Handles tab navigation between Atmosphere, Sensor, and Filter sections.
 * 
 * For live BLE mode: subscribes to sensor PubSub channels for real-time updates with rate calculation.
 * For mock/replay mode: polls MockSensorProvider to display sample counts and current values.
 */
class SettingsController(private val rootView: View) {
    private val TAG = "SettingsController"

    // Tab buttons
    private var tabAtmosphere: Button? = null
    private var tabSensor: Button? = null
    private var tabFilter: Button? = null

    // Section containers
    private var sectionAtmosphere: View? = null
    private var sectionSensor: View? = null
    private var sectionFilter: View? = null

    // Current tab
    private var currentTab = "atmosphere"

    // ========== Atmosphere Section Views ==========
    private var btnShowAtmospheric: Button? = null
    private var btnTempMode: Button? = null
    private var btnOffsetMinus: Button? = null
    private var btnOffsetPlus: Button? = null
    private var tempOffsetValue: TextView? = null
    private var tempOffsetCelsius: TextView? = null
    private var calculatedTempValue: TextView? = null
    private var tempOffsetContainer: LinearLayout? = null
    private var deviceTempContainer: LinearLayout? = null
    private var deviceTempValue: TextView? = null

    // ========== Sensor Section Views ==========
    private var sensorStatusSummary: TextView? = null
    // IMU
    private var imuRate: TextView? = null
    private var imuAccelValue: TextView? = null
    private var imuGyroValue: TextView? = null
    private var imuTempValue: TextView? = null
    // MAG
    private var magRate: TextView? = null
    private var magFieldValue: TextView? = null
    private var magTempValue: TextView? = null
    // BARO
    private var baroRate: TextView? = null
    private var baroPressureValue: TextView? = null
    private var baroTempValue: TextView? = null
    // HUM
    private var humRate: TextView? = null
    private var humHumidityValue: TextView? = null
    private var humTempValue: TextView? = null
    // TIME
    private var timeRate: TextView? = null
    private var timeOffsetValue: TextView? = null
    private var timeGpsValue: TextView? = null

    // ========== Rate Calculation ==========
    private val imuTimestamps = RateCalculator()
    private val magTimestamps = RateCalculator()
    private val baroTimestamps = RateCalculator()
    private val humTimestamps = RateCalculator()
    private val timeTimestamps = RateCalculator()

    // ========== PubSub Subscribers ==========
    private var imuSubscriber: PubSub.Subscriber<MImuData>? = null
    private var magSubscriber: PubSub.Subscriber<MMagData>? = null
    private var baroSubscriber: PubSub.Subscriber<MBaroData>? = null
    private var humSubscriber: PubSub.Subscriber<MHumidityData>? = null
    private var timeSubscriber: PubSub.Subscriber<MTimeSync>? = null

    // Colors
    private val COLOR_TAB_ACTIVE = 0xFF0066AA.toInt()
    private val COLOR_TAB_INACTIVE = 0xFF444444.toInt()

    // Mock mode detection
    private val isMockMode = VROptions.current.mockSensor != null

    init {
        setupUI()
        // In live mode, subscribe to BLE sensor updates
        // In mock mode, updates happen in update() called by HudPanelController
        if (!isMockMode) {
            subscribeToSensorUpdates()
        }
        Log.i(TAG, "SettingsController initialized, isMockMode=$isMockMode")
    }

    private fun setupUI() {
        // Find tab buttons
        tabAtmosphere = rootView.findViewById(R.id.tab_atmosphere)
        tabSensor = rootView.findViewById(R.id.tab_sensor)
        tabFilter = rootView.findViewById(R.id.tab_filter)

        // Find section containers
        sectionAtmosphere = rootView.findViewById(R.id.section_atmosphere)
        sectionSensor = rootView.findViewById(R.id.section_sensor)
        sectionFilter = rootView.findViewById(R.id.section_filter)

        // Setup tab listeners
        tabAtmosphere?.setOnClickListener {
            Log.i("BXRINPUT", "Atmosphere tab clicked")
            showSection("atmosphere")
        }
        tabSensor?.setOnClickListener {
            Log.i("BXRINPUT", "Sensor tab clicked")
            showSection("sensor")
        }
        tabFilter?.setOnClickListener {
            Log.i("BXRINPUT", "Filter tab clicked")
            showSection("filter")
        }

        // Setup Atmosphere section
        setupAtmosphereSection()

        // Setup Sensor section views
        setupSensorSection()

        // Initialize with atmosphere tab
        showSection("atmosphere")
    }

    private fun showSection(section: String) {
        currentTab = section

        // Update tab button colors
        tabAtmosphere?.backgroundTintList = ColorStateList.valueOf(
            if (section == "atmosphere") COLOR_TAB_ACTIVE else COLOR_TAB_INACTIVE
        )
        tabSensor?.backgroundTintList = ColorStateList.valueOf(
            if (section == "sensor") COLOR_TAB_ACTIVE else COLOR_TAB_INACTIVE
        )
        tabFilter?.backgroundTintList = ColorStateList.valueOf(
            if (section == "filter") COLOR_TAB_ACTIVE else COLOR_TAB_INACTIVE
        )

        // Show/hide sections
        sectionAtmosphere?.visibility = if (section == "atmosphere") View.VISIBLE else View.GONE
        sectionSensor?.visibility = if (section == "sensor") View.VISIBLE else View.GONE
        sectionFilter?.visibility = if (section == "filter") View.VISIBLE else View.GONE
    }

    // ========== Atmosphere Section ==========

    private fun setupAtmosphereSection() {
        // Find views within atmosphere section
        btnShowAtmospheric = rootView.findViewById(R.id.btn_show_atmospheric)
        btnTempMode = rootView.findViewById(R.id.btn_temp_mode)
        btnOffsetMinus = rootView.findViewById(R.id.btn_offset_minus)
        btnOffsetPlus = rootView.findViewById(R.id.btn_offset_plus)
        tempOffsetValue = rootView.findViewById(R.id.temp_offset_value)
        tempOffsetCelsius = rootView.findViewById(R.id.temp_offset_celsius)
        calculatedTempValue = rootView.findViewById(R.id.calculated_temp_value)
        tempOffsetContainer = rootView.findViewById(R.id.temp_offset_container)
        deviceTempContainer = rootView.findViewById(R.id.device_temp_container)
        deviceTempValue = rootView.findViewById(R.id.device_temp_value)

        // Setup button listeners
        btnShowAtmospheric?.setOnClickListener {
            Log.i("BXRINPUT", "Show Atmospheric button clicked!")
            val newValue = !AtmosphereSettings.showAtmosphericPanel
            AtmosphereSettings.setShowAtmosphericPanel(newValue)
            updateShowAtmosphericButton()
        }

        btnTempMode?.setOnClickListener {
            Log.i("BXRINPUT", "Temperature Mode button clicked!")
            val newValue = !AtmosphereSettings.useTemperatureOffset
            AtmosphereSettings.setUseTemperatureOffset(newValue)
            updateTempModeUI()
        }

        btnOffsetMinus?.setOnClickListener {
            Log.i("BXRINPUT", "Offset Minus button clicked!")
            AtmosphereSettings.decrementOffset()
            updateOffsetDisplay()
        }

        btnOffsetPlus?.setOnClickListener {
            Log.i("BXRINPUT", "Offset Plus button clicked!")
            AtmosphereSettings.incrementOffset()
            updateOffsetDisplay()
        }

        // Initialize UI state
        updateShowAtmosphericButton()
        updateTempModeUI()
        updateOffsetDisplay()
    }

    private fun updateShowAtmosphericButton() {
        if (AtmosphereSettings.showAtmosphericPanel) {
            btnShowAtmospheric?.text = "ON"
            btnShowAtmospheric?.backgroundTintList = ColorStateList.valueOf(0xFF00AA00.toInt())
        } else {
            btnShowAtmospheric?.text = "OFF"
            btnShowAtmospheric?.backgroundTintList = ColorStateList.valueOf(0xFFAA0000.toInt())
        }
    }

    private fun updateTempModeUI() {
        if (AtmosphereSettings.useTemperatureOffset) {
            btnTempMode?.text = "OFFSET"
            btnTempMode?.backgroundTintList = ColorStateList.valueOf(0xFF0066AA.toInt())
            tempOffsetContainer?.visibility = View.VISIBLE
            deviceTempContainer?.visibility = View.GONE
            updateOffsetDisplay()
        } else {
            btnTempMode?.text = "DEVICE"
            btnTempMode?.backgroundTintList = ColorStateList.valueOf(0xFFAA6600.toInt())
            tempOffsetContainer?.visibility = View.GONE
            deviceTempContainer?.visibility = View.VISIBLE
            updateDeviceTempDisplay()
        }
    }

    private fun updateOffsetDisplay() {
        val offsetC = AtmosphereSettings.temperatureOffsetC
        val offsetF = AtmosphereSettings.getTemperatureOffsetF()

        // Format offset with sign
        val signF = if (offsetF >= 0) "+" else ""
        val signC = if (offsetC >= 0) "+" else ""

        tempOffsetValue?.text = "$signF${String.format("%.0f", offsetF)}°F"
        tempOffsetCelsius?.text = "($signC${String.format("%.0f", offsetC)}°C)"

        // Update calculated temperature at current altitude
        updateCalculatedTemp()
    }

    private fun updateCalculatedTemp() {
        val loc = Services.location.lastLoc
        if (loc != null) {
            val calculatedTempF = AtmosphereSettings.getCalculatedTemperatureF(loc.altitude_gps.toFloat())
            calculatedTempValue?.text = String.format("%.1f°F", calculatedTempF)
        } else {
            calculatedTempValue?.text = "---"
        }
    }

    private fun updateDeviceTempDisplay() {
        // Try to get sensor data using GPS time from last location (not system time)
        val gpsTimeMs = Services.location.lastLoc?.millis ?: System.currentTimeMillis()
        val sensorData: MSensorData? = Services.location.getSensorProvider()?.getSensorAtTime(gpsTimeMs)

        if (sensorData != null && !sensorData.baroTemp.isNaN() && sensorData.baroTemp != 0f) {
            val tempC = sensorData.baroTemp
            val tempF = tempC * 9f / 5f + 32f
            deviceTempValue?.text = String.format("%.1f°F", tempF)
            deviceTempValue?.setTextColor(0xFF00FFFF.toInt())
        } else if (sensorData != null && !sensorData.humidityTemp.isNaN() && sensorData.humidityTemp != 0f) {
            val tempC = sensorData.humidityTemp
            val tempF = tempC * 9f / 5f + 32f
            deviceTempValue?.text = String.format("%.1f°F", tempF)
            deviceTempValue?.setTextColor(0xFF00FFFF.toInt())
        } else {
            // No sensor data - show ISA + offset as fallback
            val loc = Services.location.lastLoc
            if (loc != null) {
                val fallbackTempF = AtmosphereSettings.getCalculatedTemperatureF(loc.altitude_gps.toFloat())
                deviceTempValue?.text = String.format("%.1f°F (ISA+offset)", fallbackTempF)
                deviceTempValue?.setTextColor(0xFFFFAA00.toInt())
            } else {
                deviceTempValue?.text = "No sensor data"
                deviceTempValue?.setTextColor(0xFFFFAA00.toInt())
            }
        }
    }

    // ========== Sensor Section ==========

    private fun setupSensorSection() {
        sensorStatusSummary = rootView.findViewById(R.id.sensor_status_summary)
        // IMU
        imuRate = rootView.findViewById(R.id.imu_rate)
        imuAccelValue = rootView.findViewById(R.id.imu_accel_value)
        imuGyroValue = rootView.findViewById(R.id.imu_gyro_value)
        imuTempValue = rootView.findViewById(R.id.imu_temp_value)
        // MAG
        magRate = rootView.findViewById(R.id.mag_rate)
        magFieldValue = rootView.findViewById(R.id.mag_field_value)
        magTempValue = rootView.findViewById(R.id.mag_temp_value)
        // BARO
        baroRate = rootView.findViewById(R.id.baro_rate)
        baroPressureValue = rootView.findViewById(R.id.baro_pressure_value)
        baroTempValue = rootView.findViewById(R.id.baro_temp_value)
        // HUM
        humRate = rootView.findViewById(R.id.hum_rate)
        humHumidityValue = rootView.findViewById(R.id.hum_humidity_value)
        humTempValue = rootView.findViewById(R.id.hum_temp_value)
        // TIME
        timeRate = rootView.findViewById(R.id.time_rate)
        timeOffsetValue = rootView.findViewById(R.id.time_offset_value)
        timeGpsValue = rootView.findViewById(R.id.time_gps_value)
    }

    private fun subscribeToSensorUpdates() {
        val bluetooth: BluetoothService? = Services.bluetooth

        if (bluetooth == null) {
            Log.w(TAG, "BluetoothService not available for sensor subscriptions")
            return
        }

        // Subscribe to IMU updates on main thread for UI updates
        imuSubscriber = PubSub.Subscriber { imu ->
            imuTimestamps.addSample()
            updateImuDisplay(imu)
        }
        bluetooth.imuUpdates.subscribeMain(imuSubscriber!!)

        // Subscribe to MAG updates
        magSubscriber = PubSub.Subscriber { mag ->
            magTimestamps.addSample()
            updateMagDisplay(mag)
        }
        bluetooth.magUpdates.subscribeMain(magSubscriber!!)

        // Subscribe to BARO updates
        baroSubscriber = PubSub.Subscriber { baro ->
            baroTimestamps.addSample()
            updateBaroDisplay(baro)
        }
        bluetooth.baroUpdates.subscribeMain(baroSubscriber!!)

        // Subscribe to HUM updates
        humSubscriber = PubSub.Subscriber { hum ->
            humTimestamps.addSample()
            updateHumDisplay(hum)
        }
        bluetooth.humidityUpdates.subscribeMain(humSubscriber!!)

        // Subscribe to TIME updates
        timeSubscriber = PubSub.Subscriber { time ->
            timeTimestamps.addSample()
            updateTimeDisplay(time)
        }
        bluetooth.timeSyncUpdates.subscribeMain(timeSubscriber!!)

        Log.i(TAG, "Subscribed to all sensor PubSub channels")
    }

    private fun updateImuDisplay(imu: MImuData) {
        imuRate?.text = String.format("%.1f Hz", imuTimestamps.getRate())
        imuAccelValue?.text = String.format("X: %.3f g  Y: %.3f g  Z: %.3f g", imu.accelX, imu.accelY, imu.accelZ)
        imuGyroValue?.text = String.format("X: %.1f °/s  Y: %.1f °/s  Z: %.1f °/s", imu.gyroX, imu.gyroY, imu.gyroZ)
        imuTempValue?.text = String.format("%.2f °C", imu.temperature)
        updateStatusSummary()
    }

    private fun updateMagDisplay(mag: MMagData) {
        magRate?.text = String.format("%.1f Hz", magTimestamps.getRate())
        magFieldValue?.text = String.format("X: %.4f G  Y: %.4f G  Z: %.4f G", mag.magX, mag.magY, mag.magZ)
        magTempValue?.text = String.format("%.2f °C", mag.temperature)
        updateStatusSummary()
    }

    private fun updateBaroDisplay(baro: MBaroData) {
        baroRate?.text = String.format("%.1f Hz", baroTimestamps.getRate())
        val pressureInHg = baro.pressure * 0.0002953
        baroPressureValue?.text = String.format("%.1f Pa  (%.2f inHg)", baro.pressure, pressureInHg)
        baroTempValue?.text = String.format("%.2f °C", baro.temperature)
        updateStatusSummary()
    }

    private fun updateHumDisplay(hum: MHumidityData) {
        humRate?.text = String.format("%.1f Hz", humTimestamps.getRate())
        humHumidityValue?.text = String.format("%.1f %%", hum.humidity)
        humTempValue?.text = String.format("%.2f °C", hum.temperature)
        updateStatusSummary()
    }

    private fun updateTimeDisplay(time: MTimeSync) {
        timeRate?.text = String.format("%.1f Hz", timeTimestamps.getRate())
        val offsetMs = FlysightTimeSync.getOffset()
        timeOffsetValue?.text = String.format("%d ms", offsetMs)
        timeGpsValue?.text = String.format("%d", time.getSyncUnixMillis())
        updateStatusSummary()
    }

    private fun updateStatusSummary() {
        val hasImu = imuTimestamps.getRate() > 0
        val hasMag = magTimestamps.getRate() > 0
        val hasBaro = baroTimestamps.getRate() > 0
        val hasHum = humTimestamps.getRate() > 0
        val hasTime = timeTimestamps.getRate() > 0

        val sensorCount = listOf(hasImu, hasMag, hasBaro, hasHum, hasTime).count { it }

        val status = when {
            sensorCount == 5 -> "All sensors active"
            sensorCount > 0 -> "$sensorCount/5 sensors active"
            else -> "No sensor data (check BLE connection)"
        }

        sensorStatusSummary?.text = status
        sensorStatusSummary?.setTextColor(
            when {
                sensorCount == 5 -> 0xFF00FF00.toInt()
                sensorCount > 0 -> 0xFFFFAA00.toInt()
                else -> 0xFFFF0000.toInt()
            }
        )
    }

    /**
     * Call this periodically to update dynamic values.
     * For mock mode, also updates sensor display.
     */
    fun update() {
        if (AtmosphereSettings.useTemperatureOffset) {
            updateCalculatedTemp()
        } else {
            updateDeviceTempDisplay()
        }
        
        // Update mock sensor display if in mock mode and sensor tab is visible
        if (isMockMode && currentTab == "sensor") {
            updateMockSensorDisplay()
        }
    }

    // ========== Mock Sensor Display ==========

    private fun updateMockSensorDisplay() {
        val provider = Services.location.getSensorProvider()
        
        if (provider == null) {
            sensorStatusSummary?.text = "Provider is null"
            sensorStatusSummary?.setTextColor(0xFFFF0000.toInt())
            return
        }
        
        if (provider !is MockSensorProvider) {
            sensorStatusSummary?.text = "Not MockSensorProvider (got ${provider.javaClass.simpleName})"
            sensorStatusSummary?.setTextColor(0xFFFF0000.toInt())
            return
        }

        val dataSet = provider.getSensorDataSet()
        
        // Get interpolated playback time from MockLocationProvider for smooth sensor updates
        // This uses wall-clock elapsed time rather than discrete GPS point emissions
        val mockGps = Services.location.getMockLocationProvider()
        val interpolatedTime = mockGps?.getInterpolatedGpsTimeMs() ?: 0L
        val timelineTime = PlaybackTimeline.getCurrentGpsTimeMs()
        
        // Use interpolated time if available and looks valid, otherwise fall back to timeline
        // A valid interpolated time should be > 0 and not just the track start boundary
        val playbackTimeMs = when {
            interpolatedTime > 0 && interpolatedTime != mockGps?.getTrackStartTime() -> interpolatedTime
            timelineTime > 0 -> timelineTime
            else -> interpolatedTime // Last resort: use whatever we have
        }
        
        // Debug: Log time info to diagnose lookup issues
        val timeRange = dataSet.getTimeRange()
        if (timeRange != null) {
            Log.d(TAG, "Sensor lookup: interpolated=$interpolatedTime, timeline=$timelineTime, using=$playbackTimeMs, " +
                    "dataRange=[${timeRange[0]}, ${timeRange[1]}], imuCount=${dataSet.imuData.size}")
        } else {
            Log.d(TAG, "Sensor lookup: interpolated=$interpolatedTime, timeline=$timelineTime, dataRange=null, imuCount=${dataSet.imuData.size}")
        }
        
        // Get current values at playback time
        val imu = provider.getImuAtTime(playbackTimeMs)
        val mag = provider.getMagAtTime(playbackTimeMs)
        val baro = provider.getBaroAtTime(playbackTimeMs)
        val hum = provider.getHumidityAtTime(playbackTimeMs)

        // Update IMU display with sample count
        val imuCount = dataSet.imuData.size
        imuRate?.text = if (imuCount > 0) "$imuCount samples" else "No data"
        if (imu != null) {
            imuAccelValue?.text = String.format("X: %.3f g  Y: %.3f g  Z: %.3f g", imu.accelX, imu.accelY, imu.accelZ)
            imuGyroValue?.text = String.format("X: %.1f °/s  Y: %.1f °/s  Z: %.1f °/s", imu.gyroX, imu.gyroY, imu.gyroZ)
            imuTempValue?.text = String.format("%.2f °C", imu.temperature)
        } else {
            imuAccelValue?.text = "---"
            imuGyroValue?.text = "---"
            imuTempValue?.text = "---"
        }

        // Update MAG display with sample count
        val magCount = dataSet.magData.size
        magRate?.text = if (magCount > 0) "$magCount samples" else "No data"
        if (mag != null) {
            magFieldValue?.text = String.format("X: %.4f G  Y: %.4f G  Z: %.4f G", mag.magX, mag.magY, mag.magZ)
            magTempValue?.text = String.format("%.2f °C", mag.temperature)
        } else {
            magFieldValue?.text = "---"
            magTempValue?.text = "---"
        }

        // Update BARO display with sample count
        val baroCount = dataSet.baroData.size
        baroRate?.text = if (baroCount > 0) "$baroCount samples" else "No data"
        if (baro != null) {
            val pressureInHg = baro.pressure * 0.0002953
            baroPressureValue?.text = String.format("%.1f Pa  (%.2f inHg)", baro.pressure, pressureInHg)
            baroTempValue?.text = String.format("%.2f °C", baro.temperature)
        } else {
            baroPressureValue?.text = "---"
            baroTempValue?.text = "---"
        }

        // Update HUM display with sample count
        val humCount = dataSet.humidityData.size
        humRate?.text = if (humCount > 0) "$humCount samples" else "No data"
        if (hum != null) {
            humHumidityValue?.text = String.format("%.1f %%", hum.humidity)
            humTempValue?.text = String.format("%.2f °C", hum.temperature)
        } else {
            humHumidityValue?.text = "---"
            humTempValue?.text = "---"
        }

        // Update TIME display with sample count
        val timeCount = dataSet.timeSyncData.size
        timeRate?.text = if (timeCount > 0) "$timeCount samples" else "No data"
        if (timeCount > 0) {
            val lastTimeSync = dataSet.timeSyncData.lastOrNull()
            if (lastTimeSync != null) {
                // For mock mode, show device time as "offset" (it's really the device timestamp)
                timeOffsetValue?.text = String.format("Week %d", lastTimeSync.gpsWeek)
                timeGpsValue?.text = String.format("%d", lastTimeSync.getSyncUnixMillis())
            }
        } else {
            timeOffsetValue?.text = "---"
            timeGpsValue?.text = "---"
        }

        // Update status summary for mock mode
        updateStatusSummaryMock(imuCount, magCount, baroCount, humCount, timeCount)
    }

    private fun updateStatusSummaryMock(imuCount: Int, magCount: Int, baroCount: Int, humCount: Int, timeCount: Int) {
        val sensorCounts = listOf(imuCount, magCount, baroCount, humCount, timeCount)
        val sensorsWithData = sensorCounts.count { it > 0 }
        val totalSamples = sensorCounts.sum()
        val playbackTimeMs = PlaybackTimeline.getCurrentGpsTimeMs()
        
        // Check if playback is active
        val playbackActive = playbackTimeMs > 0

        val status = when {
            !playbackActive && sensorsWithData > 0 -> "Mock: Waiting for playback ($totalSamples samples loaded)"
            sensorsWithData == 5 -> "Mock: $totalSamples total samples"
            sensorsWithData > 0 -> "Mock: $sensorsWithData/5 sensors ($totalSamples samples)"
            else -> "Mock: No sensor data loaded"
        }

        sensorStatusSummary?.text = status
        sensorStatusSummary?.setTextColor(
            when {
                !playbackActive && sensorsWithData > 0 -> 0xFFFFAA00.toInt() // Orange when waiting
                sensorsWithData == 5 -> 0xFF00AAFF.toInt() // Cyan for mock mode
                sensorsWithData > 0 -> 0xFFFFAA00.toInt()
                else -> 0xFFFF0000.toInt()
            }
        )
    }

    /**
     * Clean up subscriptions when controller is destroyed
     */
    fun destroy() {
        // Unsubscribe from BLE PubSub channels (live mode only)
        val bluetooth: BluetoothService? = Services.bluetooth

        if (bluetooth != null) {
            imuSubscriber?.let { bluetooth.imuUpdates.unsubscribeMain(it) }
            magSubscriber?.let { bluetooth.magUpdates.unsubscribeMain(it) }
            baroSubscriber?.let { bluetooth.baroUpdates.unsubscribeMain(it) }
            humSubscriber?.let { bluetooth.humidityUpdates.unsubscribeMain(it) }
            timeSubscriber?.let { bluetooth.timeSyncUpdates.unsubscribeMain(it) }
        }

        imuSubscriber = null
        magSubscriber = null
        baroSubscriber = null
        humSubscriber = null
        timeSubscriber = null

        Log.i(TAG, "Cleaned up sensor subscriptions")
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
    }
}
