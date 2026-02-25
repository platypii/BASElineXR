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
    private var pinnedMac: TextView? = null
    private var dividerStatus: TextView? = null
    private var responseLog: TextView? = null
    
    private var forgetDeviceButton: Button? = null
    private var setDividersButton: Button? = null
    private var getDividersButton: Button? = null
    private var getFwButton: Button? = null
    private var getDeviceIdButton: Button? = null
    
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
        pinnedMac: TextView?,
        dividerStatus: TextView?,
        responseLog: TextView?,
        forgetDeviceButton: Button?,
        setDividersButton: Button?,
        getDividersButton: Button?,
        getFwButton: Button?,
        getDeviceIdButton: Button?,
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
        this.pinnedMac = pinnedMac
        this.dividerStatus = dividerStatus
        this.responseLog = responseLog
        this.forgetDeviceButton = forgetDeviceButton
        this.setDividersButton = setDividersButton
        this.getDividersButton = getDividersButton
        this.getFwButton = getFwButton
        this.getDeviceIdButton = getDeviceIdButton
        
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
        forgetDeviceButton?.setOnClickListener { onForgetDeviceClick() }
        setDividersButton?.setOnClickListener { onSetDividersClick() }
        getDividersButton?.setOnClickListener { onGetDividersClick() }
        getFwButton?.setOnClickListener { onGetFwClick() }
        getDeviceIdButton?.setOnClickListener { onGetDeviceIdClick() }
        
        // Set up sensor configuration UI
        setupSensorControls()
        
        // Update connection status and pinned MAC display
        updateConnectionStatus()
        updatePinnedMacDisplay()
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
    
    // DS_Control_Point (0x07) functionality is DISABLED
    // Subscribing to it or sending commands triggers Quest OS pairing popup
    // even on bonded devices. The connection also becomes unstable.
    // See: https://github.com/platypii/BASElineXR/issues/XXX
    
    private fun onGetFwClick() {
        Log.i(TAG, "Get FW Version button clicked - DISABLED")
        appendLog("FW version: disabled (causes popup)")
    }
    
    private fun onGetDeviceIdClick() {
        Log.i(TAG, "Get Device ID button clicked - DISABLED")
        appendLog("Device ID: disabled (causes popup)")
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
    
    private fun updatePinnedMacDisplay() {
        val prefs = Services.bluetooth?.preferences
        val mac = prefs?.flysightPinnedMac
        if (mac != null) {
            pinnedMac?.text = mac
            pinnedMac?.setTextColor(0xFF88FF88.toInt())
            forgetDeviceButton?.isEnabled = true
        } else {
            pinnedMac?.text = "None"
            pinnedMac?.setTextColor(0xFFAAAA88.toInt())
            forgetDeviceButton?.isEnabled = false
        }
    }
    
    private fun onForgetDeviceClick() {
        Log.i(TAG, "Forget device button clicked")
        Services.bluetooth?.flysightProtocol?.forgetPinnedDevice()
        updatePinnedMacDisplay()
        appendLog("Forgot pinned device")
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
            updatePinnedMacDisplay()
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
