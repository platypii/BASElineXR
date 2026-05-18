package com.platypii.baselinexr

import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.bluetooth.Flysight2ControlPoint
import com.platypii.baselinexr.events.BluetoothEvent
import com.platypii.baselinexr.events.FlysightModeEvent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * System for managing the FlySight Control Point panel.
 * Provides UI for sending control point commands and viewing responses.
 */
class ControlPointSystem : SystemBase(), Flysight2ControlPoint.ControlPointListener {
    private val TAG = "ControlPointSystem"
    
    private var initialized = false
    private var panelEntity: Entity? = null
    private var grabbablePanel: GrabbablePanel? = null
    
    // View references
    private var connectionStatus: TextView? = null
    private var deviceMode: TextView? = null
    private var firmwareVersion: TextView? = null
    private var deviceId: TextView? = null
    private var pinnedDevice: TextView? = null
    private var unpinButton: Button? = null
    private var dividerStatus: TextView? = null
    private var responseLog: TextView? = null
    
    private var setSleepButton: Button? = null
    private var setActiveButton: Button? = null
    private var showRawButton: Button? = null
    private var setDividersButton: Button? = null
    private var getDividersButton: Button? = null
    private var getFwButton: Button? = null
    private var getDeviceIdButton: Button? = null
    private var gnssModelLabel: TextView? = null
    private var gnssModelPrevButton: Button? = null
    private var gnssModelNextButton: Button? = null
    private var gnssModelSetButton: Button? = null
    private var gnssRateLabel: TextView? = null
    private var gnssRatePrevButton: Button? = null
    private var gnssRateNextButton: Button? = null
    private var gnssRateSetButton: Button? = null

    private var gnssModelIndex = 7
    private var gnssRateIndex = 1  // Default: 10 Hz
    
    // Sensor configuration UI elements
    data class SensorRow(
        val sensorId: Int,
        var odrText: TextView? = null,
        var dividerText: TextView? = null,
        var divDecButton: Button? = null,
        var divIncButton: Button? = null,
        var rateText: TextView? = null,
        var setButton: Button? = null
    )
    private val sensorRows = Array(Flysight2ControlPoint.SENSOR_COUNT) { SensorRow(it) }
    
    // Track current divider index for each sensor (index into DIVIDER_VALUES)
    private val dividerIndices = IntArray(Flysight2ControlPoint.SENSOR_COUNT) { 1 }  // Default to index 1 = value 1
    
    // Track divider values received from device
    private val dividerValues = IntArray(Flysight2ControlPoint.SENSOR_COUNT) { -1 }  // -1 = unknown
    
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
        val panel = composition.tryGetNodeByName("ControlPointPanel")
        if (panel?.entity != null) {
            panelEntity = panel.entity
            
            // Position on the right side of the screen
            val controlPointPanelOffset = Vector3(1.6f, -0.5f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, controlPointPanelOffset)
            
            // Set initial visibility
            panel.entity.setComponent(Visible(HudOptions.showControlPoint))
            
            initialized = true
            Log.i(TAG, "Control point panel initialized")
            
            // Register as control point listener if visible
            if (HudOptions.showControlPoint) {
                registerAsListener()
            }
            
            // Register for FlySight mode events
            EventBus.getDefault().register(this)
        } else {
            Log.w(TAG, "ControlPointPanel node not found in scene")
        }
    }
    
    /**
     * Set up references to the panel views.
     * Called from BaselineActivity after layout inflation.
     */
    fun setViews(
        connectionStatus: TextView?,
        deviceMode: TextView?,
        firmwareVersion: TextView?,
        deviceId: TextView?,
        pinnedDevice: TextView?,
        unpinButton: Button?,
        dividerStatus: TextView?,
        responseLog: TextView?,
        setSleepButton: Button?,
        setActiveButton: Button?,
        showRawButton: Button?,
        setDividersButton: Button?,
        getDividersButton: Button?,
        getFwButton: Button?,
        getDeviceIdButton: Button?,
        gnssModelLabel: TextView?,
        gnssModelPrevButton: Button?,
        gnssModelNextButton: Button?,
        gnssModelSetButton: Button?,
        gnssRateLabel: TextView?,
        gnssRatePrevButton: Button?,
        gnssRateNextButton: Button?,
        gnssRateSetButton: Button?,
        // Sensor config views: odr, divider text, dec button, inc button, rate, set button
        baroOdr: TextView?, baroDivider: TextView?, baroDivDec: Button?, baroDivInc: Button?, baroRate: TextView?, baroSet: Button?,
        humOdr: TextView?, humDivider: TextView?, humDivDec: Button?, humDivInc: Button?, humRate: TextView?, humSet: Button?,
        accelOdr: TextView?, accelDivider: TextView?, accelDivDec: Button?, accelDivInc: Button?, accelRate: TextView?, accelSet: Button?,
        gyroOdr: TextView?, gyroDivider: TextView?, gyroDivDec: Button?, gyroDivInc: Button?, gyroRate: TextView?, gyroSet: Button?,
        magOdr: TextView?, magDivider: TextView?, magDivDec: Button?, magDivInc: Button?, magRate: TextView?, magSet: Button?
    ) {
        this.connectionStatus = connectionStatus
        this.deviceMode = deviceMode
        this.firmwareVersion = firmwareVersion
        this.deviceId = deviceId
        this.pinnedDevice = pinnedDevice
        this.unpinButton = unpinButton
        this.dividerStatus = dividerStatus
        this.responseLog = responseLog
        this.setSleepButton = setSleepButton
        this.setActiveButton = setActiveButton
        this.showRawButton = showRawButton
        this.setDividersButton = setDividersButton
        this.getDividersButton = getDividersButton
        this.getFwButton = getFwButton
        this.getDeviceIdButton = getDeviceIdButton
        this.gnssModelLabel = gnssModelLabel
        this.gnssModelPrevButton = gnssModelPrevButton
        this.gnssModelNextButton = gnssModelNextButton
        this.gnssModelSetButton = gnssModelSetButton
        this.gnssRateLabel = gnssRateLabel
        this.gnssRatePrevButton = gnssRatePrevButton
        this.gnssRateNextButton = gnssRateNextButton
        this.gnssRateSetButton = gnssRateSetButton
        
        // Set up sensor row references
        sensorRows[Flysight2ControlPoint.SENSOR_BARO].apply {
            odrText = baroOdr; dividerText = baroDivider; divDecButton = baroDivDec; divIncButton = baroDivInc; rateText = baroRate; setButton = baroSet
        }
        sensorRows[Flysight2ControlPoint.SENSOR_HUM].apply {
            odrText = humOdr; dividerText = humDivider; divDecButton = humDivDec; divIncButton = humDivInc; rateText = humRate; setButton = humSet
        }
        sensorRows[Flysight2ControlPoint.SENSOR_ACCEL].apply {
            odrText = accelOdr; dividerText = accelDivider; divDecButton = accelDivDec; divIncButton = accelDivInc; rateText = accelRate; setButton = accelSet
        }
        sensorRows[Flysight2ControlPoint.SENSOR_GYRO].apply {
            odrText = gyroOdr; dividerText = gyroDivider; divDecButton = gyroDivDec; divIncButton = gyroDivInc; rateText = gyroRate; setButton = gyroSet
        }
        sensorRows[Flysight2ControlPoint.SENSOR_MAG].apply {
            odrText = magOdr; dividerText = magDivider; divDecButton = magDivDec; divIncButton = magDivInc; rateText = magRate; setButton = magSet
        }
        
        // Set up button click listeners
        setSleepButton?.setOnClickListener { onSetSleepClick() }
        setActiveButton?.setOnClickListener { onSetActiveClick() }
        showRawButton?.setOnClickListener { onShowRawClick() }
        unpinButton?.setOnClickListener { onUnpinClick() }
        setDividersButton?.setOnClickListener { onSetDividersClick() }
        getDividersButton?.setOnClickListener { onGetDividersClick() }
        getFwButton?.setOnClickListener { onGetFwClick() }
        getDeviceIdButton?.setOnClickListener { onGetDeviceIdClick() }
        gnssModelSetButton?.setOnClickListener { onSetGnssModelClick() }
        gnssRateSetButton?.setOnClickListener { onSetGnssRateClick() }

        setupGnssControls()
        // Set up sensor configuration UI
        setupSensorControls()
        
        // Update connection status and pinned device display
        updateConnectionStatus()
        updatePinnedDeviceDisplay()
        updateShowRawButton()
    }

    private fun setupGnssControls() {
        updateGnssModelLabel()
        updateGnssRateLabel()

        gnssModelPrevButton?.setOnClickListener {
            if (gnssModelIndex > 0) {
                gnssModelIndex--
                updateGnssModelLabel()
            }
        }
        gnssModelNextButton?.setOnClickListener {
            if (gnssModelIndex < Flysight2ControlPoint.GNSS_DYNAMIC_MODEL_VALUES.size - 1) {
                gnssModelIndex++
                updateGnssModelLabel()
            }
        }
        gnssRatePrevButton?.setOnClickListener {
            if (gnssRateIndex > 0) {
                gnssRateIndex--
                updateGnssRateLabel()
            }
        }
        gnssRateNextButton?.setOnClickListener {
            if (gnssRateIndex < Flysight2ControlPoint.GNSS_RATE_VALUES_MS.size - 1) {
                gnssRateIndex++
                updateGnssRateLabel()
            }
        }
    }

    private fun updateGnssModelLabel() {
        gnssModelLabel?.text = Flysight2ControlPoint.GNSS_DYNAMIC_MODEL_LABELS.getOrElse(gnssModelIndex) { "?" }
    }

    private fun updateGnssRateLabel() {
        gnssRateLabel?.text = Flysight2ControlPoint.GNSS_RATE_LABELS.getOrElse(gnssRateIndex) { "?" }
    }
    
    private fun setupSensorControls() {
        for (row in sensorRows) {
            val sensorId = row.sensorId
            
            // Display default ODR for this sensor
            updateOdrDisplay(sensorId)
            
            // Initialize divider display
            dividerIndices[sensorId] = 1  // Default to index 1 = value 1
            updateDividerDisplay(sensorId)
            
            // Dec button
            row.divDecButton?.setOnClickListener {
                if (dividerIndices[sensorId] > 0) {
                    dividerIndices[sensorId]--
                    updateDividerDisplay(sensorId)
                }
            }
            
            // Inc button
            row.divIncButton?.setOnClickListener {
                if (dividerIndices[sensorId] < Flysight2ControlPoint.DIVIDER_VALUES.size - 1) {
                    dividerIndices[sensorId]++
                    updateDividerDisplay(sensorId)
                }
            }
            
            // Set button
            row.setButton?.setOnClickListener { onSetSensorDivider(sensorId) }
        }
    }
    
    private fun updateDividerDisplay(sensorId: Int) {
        val row = sensorRows.getOrNull(sensorId) ?: return
        val dividerIndex = dividerIndices[sensorId]
        val label = Flysight2ControlPoint.DIVIDER_LABELS.getOrElse(dividerIndex) { "1" }
        row.dividerText?.text = label
        
        // Update rate display
        updateSensorRateDisplay(sensorId)
    }
    
    private fun updateOdrDisplay(sensorId: Int) {
        val row = sensorRows.getOrNull(sensorId) ?: return
        val defaultOdrHz = Flysight2ControlPoint.getDefaultOdrHz(sensorId)
        row.odrText?.text = if (defaultOdrHz >= 1) {
            String.format("%.0fHz", defaultOdrHz)
        } else {
            String.format("%.1fHz", defaultOdrHz)
        }
    }
    
    private fun updateSensorRateDisplay(sensorId: Int) {
        val row = sensorRows.getOrNull(sensorId) ?: return
        val dividerIndex = dividerIndices[sensorId]
        val divider = Flysight2ControlPoint.DIVIDER_VALUES.getOrElse(dividerIndex) { 1 }
        
        val rateText = if (divider == 0) {
            "Auto"
        } else {
            // Calculate rate = defaultOdrHz / divider
            val defaultOdrHz = Flysight2ControlPoint.getDefaultOdrHz(sensorId)
            val rate = defaultOdrHz / divider
            if (rate >= 1) String.format("%.0fHz", rate) else String.format("%.1fHz", rate)
        }
        row.rateText?.text = rateText
    }
    
    private fun onSetSensorDivider(sensorId: Int) {
        val dividerIndex = dividerIndices[sensorId]
        val divider = Flysight2ControlPoint.DIVIDER_VALUES.getOrElse(dividerIndex) { 1 }
        
        Log.i(TAG, "Set Divider for sensor $sensorId to $divider")
        val controlPoint = Services.bluetooth?.flysightProtocol?.controlPoint
        if (controlPoint != null) {
            controlPoint.setBleDivider(sensorId, divider)
            appendLog("SET ${Flysight2ControlPoint.SENSOR_NAMES[sensorId]}=$divider")
        } else {
            appendLog("Error: Not connected")
        }
    }
    
    private fun onSetDividersClick() {
        Log.i(TAG, "Set Dividers button clicked")
        val controlPoint = Services.bluetooth?.flysightProtocol?.controlPoint
        if (controlPoint != null) {
            controlPoint.configureAllDividers(1)
            appendLog("Sent: SET_DIVIDER(all=1)")
        } else {
            appendLog("Error: Not connected")
        }
    }
    
    private fun onGetDividersClick() {
        Log.i(TAG, "Get Dividers button clicked")
        val controlPoint = Services.bluetooth?.flysightProtocol?.controlPoint
        if (controlPoint != null) {
            // Request all dividers
            for (i in 0..4) {
                controlPoint.getBleDivider(i)
            }
            appendLog("Sent: GET_DIVIDER(0-4)")
        } else {
            appendLog("Error: Not connected")
        }
    }
    
    private fun onGetFwClick() {
        Log.i(TAG, "Get FW Version button clicked")
        val ok = Services.bluetooth?.flysightProtocol?.controlPoint?.getFirmwareVersion() ?: false
        if (!ok) {
            appendLog("FW version: not connected")
        }
    }
    
    private fun onGetDeviceIdClick() {
        Log.i(TAG, "Get Device ID button clicked")
        val ok = Services.bluetooth?.flysightProtocol?.controlPoint?.getDeviceId() ?: false
        if (!ok) {
            appendLog("Device ID: not connected")
        }
    }
    
    private fun registerAsListener() {
        Services.bluetooth?.flysightProtocol?.controlPoint?.setListener(this)
        Log.i(TAG, "Registered as control point listener")
    }
    
    private fun unregisterAsListener() {
        Services.bluetooth?.flysightProtocol?.controlPoint?.setListener(null)
        Log.i(TAG, "Unregistered as control point listener")
    }
    
    override fun onControlPointResponse(opcode: Int, status: Int, data: ByteArray?) {
        Log.i(TAG, "onControlPointResponse: opcode=0x${opcode.toString(16)} status=$status dataLen=${data?.size ?: 0}")
        activity?.runOnUiThread {
            val statusStr = when (status) {
                Flysight2ControlPoint.CP_STATUS_SUCCESS -> "OK"
                Flysight2ControlPoint.CP_STATUS_NOT_SUPPORTED -> "NOT_SUPPORTED"
                Flysight2ControlPoint.CP_STATUS_INVALID_PARAM -> "INVALID_PARAM"
                Flysight2ControlPoint.CP_STATUS_FAILED -> "FAILED"
                Flysight2ControlPoint.CP_STATUS_NOT_PERMITTED -> "NOT_PERMITTED"
                Flysight2ControlPoint.CP_STATUS_BUSY -> "BUSY"
                else -> "UNKNOWN($status)"
            }
            
            when (opcode) {
                Flysight2ControlPoint.SD_CMD_SET_BLE_DIVIDER.toInt() -> {
                    appendLog("SET_DIVIDER: $statusStr")
                }
                Flysight2ControlPoint.SD_CMD_GET_BLE_DIVIDER.toInt() -> {
                    if (data != null && data.size >= 3) {
                        val sensorId = data[0].toInt() and 0xFF
                        val divider = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
                        dividerValues[sensorId] = divider
                        updateDividerDisplay()
                        updateSensorDividerFromDevice(sensorId, divider)
                        appendLog("GET ${Flysight2ControlPoint.SENSOR_NAMES.getOrElse(sensorId) { "?" }}=$divider")
                    }
                }
                Flysight2ControlPoint.DS_CMD_GET_FW_VERSION.toInt() -> {
                    if (data != null) {
                        val version = String(data)
                        firmwareVersion?.text = version
                        appendLog("FW: $version")
                    }
                }
                Flysight2ControlPoint.DS_CMD_GET_DEVICE_ID.toInt() -> {
                    if (data != null) {
                        val id = data.joinToString("") { "%02X".format(it) }
                        deviceId?.text = id
                        appendLog("ID: $id")
                    }
                }
                Flysight2ControlPoint.DS_CMD_REQUEST_SLEEP.toInt() -> {
                    appendLog("REQUEST_SLEEP: $statusStr")
                    if (status == Flysight2ControlPoint.CP_STATUS_SUCCESS) {
                        Services.bluetooth?.flysightProtocol?.requestModeRead()
                    }
                }
                Flysight2ControlPoint.DS_CMD_REQUEST_ACTIVE.toInt() -> {
                    appendLog("REQUEST_ACTIVE: $statusStr")
                    // Read mode to trigger UI update
                    if (status == Flysight2ControlPoint.CP_STATUS_SUCCESS) {
                        Services.bluetooth?.flysightProtocol?.requestModeRead()
                    }
                }
                Flysight2ControlPoint.SD_CMD_SET_GNSS_MODEL.toInt() -> {
                    appendLog("SET_GNSS_MODEL: $statusStr")
                }
                Flysight2ControlPoint.SD_CMD_SET_GNSS_RATE.toInt() -> {
                    appendLog("SET_GNSS_RATE: $statusStr")
                }
                else -> {
                    appendLog("Response 0x${opcode.toString(16)}: $statusStr")
                }
            }
        }
    }
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onFlysightModeEvent(event: FlysightModeEvent) {
        val modeName = FlysightModeEvent.modeName(event.mode)
        deviceMode?.text = modeName
        appendLog("Mode: $modeName")
    }
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBluetoothEvent(event: BluetoothEvent) {
        // Refresh status displays when connection state changes
        updateConnectionStatus()
        updatePinnedDeviceDisplay()
    }
    
    private fun updateDividerDisplay() {
        val sb = StringBuilder("Dividers: ")
        val names = arrayOf("B", "H", "A", "G", "M")
        for (i in 0 until Flysight2ControlPoint.SENSOR_COUNT) {
            if (i > 0) sb.append(" ")
            sb.append(names[i]).append("=")
            if (dividerValues[i] >= 0) {
                sb.append(dividerValues[i])
            } else {
                sb.append("?")
            }
        }
        dividerStatus?.text = sb.toString()
    }
    
    private fun updateSensorDividerFromDevice(sensorId: Int, divider: Int) {
        // Find the index in DIVIDER_VALUES that matches this divider
        val index = Flysight2ControlPoint.DIVIDER_VALUES.indexOf(divider)
        if (index >= 0) {
            dividerIndices[sensorId] = index
            updateDividerDisplay(sensorId)
        } else {
            // Value not in our predefined list - show as-is
            Log.w(TAG, "Divider value $divider not in predefined list for sensor $sensorId")
            val row = sensorRows.getOrNull(sensorId)
            row?.dividerText?.text = divider.toString()
        }
    }
    
    private fun updateConnectionStatus() {
        val isConnected = Services.bluetooth?.flysightProtocol?.controlPoint != null
        if (isConnected) {
            connectionStatus?.text = "Connected"
            connectionStatus?.setTextColor(0xFF88FF88.toInt())
        } else {
            connectionStatus?.text = "Disconnected"
            connectionStatus?.setTextColor(0xFFFF8888.toInt())
        }
    }
    
    private fun updatePinnedDeviceDisplay() {
        val prefs = Services.bluetooth?.preferences
        val name = prefs?.flysightDeviceName
        val mac = prefs?.flysightPinnedMac
        
        when {
            name != null && mac != null -> {
                // Format: "FlySight (AA:BB:...)"
                val shortMac = if (mac.length > 8) "${mac.substring(0, 8)}..." else mac
                pinnedDevice?.text = "$name ($shortMac)"
                pinnedDevice?.setTextColor(0xFF88FF88.toInt())
            }
            name != null -> {
                pinnedDevice?.text = name
                pinnedDevice?.setTextColor(0xFF88FF88.toInt())
            }
            mac != null -> {
                pinnedDevice?.text = mac
                pinnedDevice?.setTextColor(0xFF88FF88.toInt())
            }
            else -> {
                pinnedDevice?.text = "None"
                pinnedDevice?.setTextColor(0xFFAAAA88.toInt())
            }
        }
    }
    
    private fun onSetSleepClick() {
        Log.i(TAG, "Set Sleep button clicked")
        val ok = Services.bluetooth?.flysightProtocol?.controlPoint?.setMode(FlysightModeEvent.MODE_SLEEP) ?: false
        if (!ok) {
            appendLog("Set Sleep: not connected")
        }
    }
    
    private fun onSetActiveClick() {
        Log.i(TAG, "Set Active button clicked")
        val ok = Services.bluetooth?.flysightProtocol?.controlPoint?.setMode(FlysightModeEvent.MODE_ACTIVE) ?: false
        if (!ok) {
            appendLog("Set Active: not connected")
        }
    }

    private fun onSetGnssModelClick() {
        val model = Flysight2ControlPoint.GNSS_DYNAMIC_MODEL_VALUES.getOrElse(gnssModelIndex) { -1 }
        val label = Flysight2ControlPoint.GNSS_DYNAMIC_MODEL_LABELS.getOrElse(gnssModelIndex) { "?" }
        if (model < 0) {
            appendLog("GNSS model: invalid selection")
            return
        }
        val ok = Services.bluetooth?.flysightProtocol?.controlPoint?.setGnssModel(model) ?: false
        if (ok) {
            appendLog("Sent: GNSS model=$label")
        } else {
            appendLog("GNSS model: not connected")
        }
    }

    private fun onSetGnssRateClick() {
        val rateMs = Flysight2ControlPoint.GNSS_RATE_VALUES_MS.getOrElse(gnssRateIndex) { -1 }
        val label = Flysight2ControlPoint.GNSS_RATE_LABELS.getOrElse(gnssRateIndex) { "?" }
        if (rateMs < 0) {
            appendLog("GNSS rate: invalid selection")
            return
        }
        val ok = Services.bluetooth?.flysightProtocol?.controlPoint?.setGnssRateMs(rateMs) ?: false
        if (ok) {
            appendLog("Sent: GNSS rate=$label (${rateMs}ms)")
        } else {
            appendLog("GNSS rate: not connected")
        }
    }
    
    private fun onShowRawClick() {
        Log.i(TAG, "Show Raw button clicked")
        HudOptions.showRawSensor = !HudOptions.showRawSensor
        activity?.let { HudOptions.saveHudOptions(it) }
        updateShowRawButton()
    }
    
    private fun onUnpinClick() {
        Log.i(TAG, "Unpin button clicked")
        val prefs = Services.bluetooth?.preferences
        val act = activity
        if (prefs != null && prefs.flysightPinnedMac != null && act != null) {
            prefs.forgetFlysightDevice(act)
            appendLog("Device unpinned")
            updatePinnedDeviceDisplay()
            // Restart bluetooth to scan for any device
            Services.bluetooth?.restart(act)
        } else {
            appendLog("No device pinned")
        }
    }
    
    private fun updateShowRawButton() {
        showRawButton?.let { button ->
            if (HudOptions.showRawSensor) {
                button.text = "Hide"
                button.setBackgroundColor(0xFF884466.toInt())
            } else {
                button.text = "Show"
                button.setBackgroundColor(0xFF664488.toInt())
            }
        }
    }
    
    private fun appendLog(message: String) {
        responseLog?.let { log ->
            val current = log.text.toString()
            val lines = current.split("\n").toMutableList()
            lines.add(message)
            // Keep only last 5 lines
            while (lines.size > 5) {
                lines.removeAt(0)
            }
            log.text = lines.joinToString("\n")
        }
    }
    
    /**
     * Toggle panel visibility.
     * Called from HudPanelController when button is pressed.
     */
    fun updateVisibility() {
        panelEntity?.setComponent(Visible(HudOptions.showControlPoint))
        
        if (HudOptions.showControlPoint) {
            // Reset divider values
            for (i in 0 until Flysight2ControlPoint.SENSOR_COUNT) dividerValues[i] = -1
            updateDividerDisplay()
            updateConnectionStatus()
            updatePinnedDeviceDisplay()
            registerAsListener()
        } else {
            unregisterAsListener()
        }
    }
    
    fun cleanup() {
        unregisterAsListener()
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
        panelEntity = null
        grabbablePanel = null
        initialized = false
    }
}
