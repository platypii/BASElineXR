package com.platypii.baselinexr.ahrs

import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.GrabbablePanel
import com.platypii.baselinexr.HudOptions
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.location.PlaybackTimeline
import com.platypii.baselinexr.location.RotationEstimator
import com.platypii.baselinexr.measurements.MImuData
import com.platypii.baselinexr.measurements.MLocation
import com.platypii.baselinexr.measurements.MMagData
import com.platypii.baselinexr.util.PubSub

/**
 * System for AHRS (Attitude and Heading Reference System).
 * 
 * Subscribes to sensor updates, runs the FusionAhrs algorithm,
 * updates the UI panel, and implements RotationEstimator interface
 * for HeadModelSystem integration.
 */
class AhrsSystem : SystemBase(), RotationEstimator {
    private val TAG = "AhrsSystem"

    private var initialized = false
    private var panelEntity: Entity? = null
    private var grabbablePanel: GrabbablePanel? = null

    // AHRS adapter (handles calibration, transforms, and algorithm)
    private val ahrsAdapter = FusionAhrsAdapter()

    // Last IMU for predict() and dt calculation
    @Volatile private var lastImu: MImuData? = null
    @Volatile private var lastMag: MMagData? = null
    @Volatile private var hasInitializedOrientation = false

    // PubSub subscribers
    private var imuSubscriber: PubSub.Subscriber<MImuData>? = null
    private var magSubscriber: PubSub.Subscriber<MMagData>? = null

    // UI update throttling - update UI at ~30Hz instead of 400Hz
    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastUiUpdateTime = 0L
    private val uiUpdateIntervalMs = 33L  // ~30 Hz
    private var uiUpdatePending = false

    // Activity reference
    private var activity: BaselineActivity? = null

    // UI Views
    private var gainSlider: SeekBar? = null
    private var gainValue: TextView? = null
    private var accelRejectSlider: SeekBar? = null
    private var accelRejectValue: TextView? = null
    private var magRejectSlider: SeekBar? = null
    private var magRejectValue: TextView? = null
    private var accelStatus: TextView? = null
    private var accelError: TextView? = null
    private var magStatus: TextView? = null
    private var magError: TextView? = null
    private var ahrsState: TextView? = null
    private var biasX: TextView? = null
    private var biasY: TextView? = null
    private var biasZ: TextView? = null
    private var biasStatus: TextView? = null
    private var biasProgress: TextView? = null
    private var gyroMagnitude: TextView? = null
    private var gyroThreshold: TextView? = null
    private var headingView: TextView? = null
    private var pitchView: TextView? = null
    private var rollView: TextView? = null
    
    // Playback controls
    private var btnPlay: Button? = null
    private var btnPause: Button? = null
    private var btnReset: Button? = null
    private var playbackTimeView: TextView? = null
    private var playbackSlider: SeekBar? = null
    private var speedSpinner: Spinner? = null
    private var isUserSeekingSlider = false
    
    // Playback timeline reference
    private val timeline = PlaybackTimeline.getInstance()

    // Current settings
    private var currentGain = 0.5f
    private var currentAccelReject = 10f
    private var currentMagReject = 10f

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
        val panel = composition.tryGetNodeByName("AhrsPanel")
        if (panel?.entity != null) {
            panelEntity = panel.entity

            val panelOffset = Vector3(1.5f, 0f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, panelOffset)

            panel.entity.setComponent(Visible(HudOptions.showAhrs))

            initialized = true
            Log.i(TAG, "AHRS panel initialized")
            
            // Load saved calibration on startup
            loadSavedCalibration(act)

            if (HudOptions.showAhrs) {
                subscribeToSensorUpdates()
            }
        } else {
            Log.w(TAG, "AhrsPanel node not found in scene")
        }
    }
    
    /**
     * Load saved magnetometer calibration on startup.
     */
    private fun loadSavedCalibration(context: android.content.Context) {
        val savedCalibration = com.platypii.baselinexr.calibration.CalibrationStorage.loadCalibration(context)
        if (savedCalibration != null) {
            com.platypii.baselinexr.calibration.CalibrationStorage.applyToAdapter(ahrsAdapter, savedCalibration)
            Log.i(TAG, "Loaded and applied saved calibration: ${savedCalibration.calibrationType}")
        } else {
            Log.i(TAG, "No saved calibration found")
        }
    }

    /**
     * Set up references to the panel views.
     * Called from BaselineActivity after layout inflation.
     */
    fun setViews(
        gainSlider: SeekBar?, gainValue: TextView?,
        accelRejectSlider: SeekBar?, accelRejectValue: TextView?,
        magRejectSlider: SeekBar?, magRejectValue: TextView?,
        accelStatus: TextView?, accelError: TextView?,
        magStatus: TextView?, magError: TextView?,
        ahrsState: TextView?,
        biasX: TextView?, biasY: TextView?, biasZ: TextView?,
        biasStatus: TextView?, biasProgress: TextView?,
        gyroMagnitude: TextView?, gyroThreshold: TextView?,
        headingView: TextView?, pitchView: TextView?, rollView: TextView?
    ) {
        this.gainSlider = gainSlider
        this.gainValue = gainValue
        this.accelRejectSlider = accelRejectSlider
        this.accelRejectValue = accelRejectValue
        this.magRejectSlider = magRejectSlider
        this.magRejectValue = magRejectValue
        this.accelStatus = accelStatus
        this.accelError = accelError
        this.magStatus = magStatus
        this.magError = magError
        this.ahrsState = ahrsState
        this.biasX = biasX
        this.biasY = biasY
        this.biasZ = biasZ
        this.biasStatus = biasStatus
        this.biasProgress = biasProgress
        this.gyroMagnitude = gyroMagnitude
        this.gyroThreshold = gyroThreshold
        this.headingView = headingView
        this.pitchView = pitchView
        this.rollView = rollView

        // Set up slider listeners
        setupSliderListeners()
    }

    /**
     * Set up references to playback control views.
     * Called from BaselineActivity after layout inflation.
     */
    fun setPlaybackViews(
        btnPlay: Button?, btnPause: Button?, btnReset: Button?,
        playbackTimeView: TextView?, playbackSlider: SeekBar?,
        speedSpinner: Spinner?
    ) {
        this.btnPlay = btnPlay
        this.btnPause = btnPause
        this.btnReset = btnReset
        this.playbackTimeView = playbackTimeView
        this.playbackSlider = playbackSlider
        this.speedSpinner = speedSpinner

        // Set up playback control listeners
        setupPlaybackControls()
    }

    private fun setupPlaybackControls() {
        btnPlay?.setOnClickListener {
            timeline.resume()
            updatePlaybackButtonStates()
        }

        btnPause?.setOnClickListener {
            timeline.pause()
            updatePlaybackButtonStates()
        }

        btnReset?.setOnClickListener {
            // Restart playback from beginning
            timeline.restart()
            // Restart the providers to pick up from new timeline position
            Services.sensor.seekTo(0)
            Services.location.seekTo(0)
            // Reset AHRS state
            ahrsAdapter.reset()
            hasInitializedOrientation = false
            updatePlaybackButtonStates()
        }

        playbackSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isUserSeekingSlider) {
                    // User is dragging - update time display but don't seek yet
                    val duration = timeline.trackDuration
                    val position = (progress / 1000f) * duration
                    playbackTimeView?.text = String.format("%.1fs / %.1fs", position / 1000f, duration / 1000f)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeekingSlider = true
                // Pause while seeking
                timeline.pause()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeekingSlider = false
                // Seek to final position
                val progress = seekBar?.progress ?: 0
                val duration = timeline.trackDuration
                val positionMs = ((progress / 1000f) * duration).toLong()
                timeline.seekTo(positionMs)
                // Restart the providers to pick up from new timeline position
                Services.sensor.seekTo(positionMs)
                Services.location.seekTo(positionMs)
                // Reset AHRS state since we're jumping to a new position
                ahrsAdapter.reset()
                hasInitializedOrientation = false
                // Resume playback after seek
                timeline.resume()
                updatePlaybackButtonStates()
            }
        })

        // Set up speed spinner
        speedSpinner?.let { spinner ->
            val speeds = listOf("0.25x", "0.5x", "1x", "2x", "4x")
            val speedValues = listOf(0.25f, 0.5f, 1f, 2f, 4f)
            
            spinner.adapter = ArrayAdapter(
                activity!!,
                android.R.layout.simple_spinner_item,
                speeds
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            
            // Default to 1x speed
            spinner.setSelection(2)
            
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    timeline.setPlaybackSpeed(speedValues[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Initial button state
        updatePlaybackButtonStates()
    }

    private fun updatePlaybackButtonStates() {
        val isPlaying = timeline.isPlaying
        btnPlay?.isEnabled = !isPlaying
        btnPause?.isEnabled = isPlaying
    }

    private fun updatePlaybackTimeDisplay() {
        if (isUserSeekingSlider) return  // Don't update while user is seeking
        
        val position = timeline.playbackPosition
        val duration = timeline.trackDuration
        
        playbackTimeView?.text = String.format("%.1fs / %.1fs", position / 1000f, duration / 1000f)
        
        // Update slider position
        if (duration > 0) {
            val progress = ((position.toFloat() / duration) * 1000).toInt().coerceIn(0, 1000)
            playbackSlider?.progress = progress
        }
        
        // Update button states
        updatePlaybackButtonStates()
    }

    private fun setupSliderListeners() {
        gainSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Map 0-100 to 0.01-1.0
                currentGain = (progress.coerceAtLeast(1) / 100f)
                gainValue?.text = String.format("%.2f", currentGain)
                updateAhrsSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        accelRejectSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentAccelReject = progress.toFloat()
                accelRejectValue?.text = progress.toString()
                updateAhrsSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        magRejectSlider?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentMagReject = progress.toFloat()
                magRejectValue?.text = progress.toString()
                updateAhrsSettings()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateAhrsSettings() {
        ahrsAdapter.updateAhrsSettings(
            FusionAhrs.FusionAhrsSettings(
                gain = currentGain,
                gyroscopeRange = 2000f,
                accelerationRejection = currentAccelReject,
                magneticRejection = currentMagReject,
                recoveryTriggerPeriod = 5.0f  // 5 seconds
            )
        )
    }

    private fun subscribeToSensorUpdates() {
        // Subscribe on background thread - AHRS processing doesn't need main thread
        // UI updates are throttled and posted to main thread separately
        imuSubscriber = PubSub.Subscriber { imu ->
            onImuUpdate(imu)
        }
        Services.sensor.imuUpdates.subscribe(imuSubscriber!!)

        magSubscriber = PubSub.Subscriber { mag ->
            onMagUpdate(mag)
        }
        Services.sensor.magUpdates.subscribe(magSubscriber!!)

        Log.i(TAG, "Subscribed to sensor updates (background thread)")
    }

    private fun unsubscribeFromSensorUpdates() {
        imuSubscriber?.let { Services.sensor.imuUpdates.unsubscribe(it) }
        magSubscriber?.let { Services.sensor.magUpdates.unsubscribe(it) }
        imuSubscriber = null
        magSubscriber = null
        Log.i(TAG, "Unsubscribed from sensor updates")
    }

    private fun onMagUpdate(mag: MMagData) {
        lastMag = mag
        ahrsAdapter.updateMag(mag.magX, mag.magY, mag.magZ)

        // Try to initialize orientation if we haven't yet
        if (!hasInitializedOrientation && lastImu != null) {
            initializeOrientation()
        }
    }

    private fun onImuUpdate(imu: MImuData) {
        val prevImu = lastImu
        lastImu = imu

        // Calculate dt
        val dt = if (prevImu != null) {
            val dtMs = imu.millis - prevImu.millis
            if (dtMs > 0 && dtMs < 1000) {
                dtMs / 1000f
            } else {
                0.0025f  // Default to ~400Hz
            }
        } else {
            0.0025f
        }

        // Try to initialize orientation if we haven't yet
        if (!hasInitializedOrientation) {
            initializeOrientation()
            return
        }

        // Update AHRS
        ahrsAdapter.updateIMU(
            dt,
            imu.gyroX, imu.gyroY, imu.gyroZ,
            imu.accelX, imu.accelY, imu.accelZ
        )

        // Schedule throttled UI update
        scheduleUiUpdate()
    }

    /**
     * Schedule a UI update at throttled rate (~30Hz instead of 400Hz)
     */
    private fun scheduleUiUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUiUpdateTime >= uiUpdateIntervalMs && !uiUpdatePending) {
            uiUpdatePending = true
            uiHandler.post {
                uiUpdatePending = false
                lastUiUpdateTime = System.currentTimeMillis()
                updateDisplay()
            }
        }
    }

    private fun initializeOrientation() {
        val imu = lastImu ?: return
        val mag = lastMag

        if (mag != null) {
            ahrsAdapter.initFromAccelMag(
                imu.accelX, imu.accelY, imu.accelZ,
                mag.magX, mag.magY, mag.magZ
            )
            hasInitializedOrientation = true
            Log.i(TAG, "Initialized AHRS from accel+mag")
        } else {
            ahrsAdapter.initFromAccelOnly(imu.accelX, imu.accelY, imu.accelZ)
            hasInitializedOrientation = true
            Log.i(TAG, "Initialized AHRS from accel only (6-DOF)")
        }
    }

    private fun updateDisplay() {
        // Update playback controls
        updatePlaybackTimeDisplay()
        
        // Get internal states
        val states = ahrsAdapter.getInternalStates()
        val flags = ahrsAdapter.getFlags()
        val biasState = ahrsAdapter.getBiasState()
        val euler = ahrsAdapter.getEulerAngles()

        // Accel status
        if (states.accelerometerIgnored) {
            accelStatus?.text = "⚠️ IGN"
            accelStatus?.setTextColor(0xFFFF6B6B.toInt())
        } else {
            accelStatus?.text = "✓ OK"
            accelStatus?.setTextColor(0xFF6BFF6B.toInt())
        }
        accelError?.text = String.format("%.1f", states.accelerationError)

        // Mag status
        if (states.magnetometerIgnored) {
            magStatus?.text = "⚠️ IGN"
            magStatus?.setTextColor(0xFFFF6B6B.toInt())
        } else {
            magStatus?.text = "✓ OK"
            magStatus?.setTextColor(0xFF6BFF6B.toInt())
        }
        magError?.text = String.format("%.1f", states.magneticError)

        // AHRS state (initializing/stable)
        if (flags.initialising) {
            val rampedGain = ahrsAdapter.getRampedGain()
            val progress = ((10f - rampedGain) / (10f - currentGain) * 100).coerceIn(0f, 100f)
            ahrsState?.text = String.format("Init:%.0f%%", progress)
        } else {
            val stateFlags = mutableListOf<String>()
            if (states.accelerationRecoveryTrigger > 0) {
                stateFlags.add("A:${(states.accelerationRecoveryTrigger * 100).toInt()}%")
            }
            if (states.magneticRecoveryTrigger > 0) {
                stateFlags.add("M:${(states.magneticRecoveryTrigger * 100).toInt()}%")
            }
            ahrsState?.text = if (stateFlags.isEmpty()) "Stable" else stateFlags.joinToString(" ")
        }

        // Runtime bias
        biasX?.text = String.format("%.3f", biasState.biasX)
        biasY?.text = String.format("%.3f", biasState.biasY)
        biasZ?.text = String.format("%.3f", biasState.biasZ)

        when {
            biasState.isCalibrating -> {
                biasStatus?.text = "🔄 Updating"
                biasStatus?.setTextColor(0xFF6BFF6B.toInt())
            }
            biasState.progress > 0 -> {
                biasStatus?.text = "⏳ Stationary"
                biasStatus?.setTextColor(0xFFFFFF6B.toInt())
            }
            else -> {
                biasStatus?.text = "⏸️ Moving"
                biasStatus?.setTextColor(0xFF888888.toInt())
            }
        }
        biasProgress?.text = if (biasState.progress > 0) "(${(biasState.progress * 100).toInt()}%)" else ""

        // Gyro magnitude
        gyroMagnitude?.text = String.format("%.1f", biasState.gyroMagnitude)
        val magColor = if (biasState.gyroMagnitude < biasState.stationaryThreshold) 0xFF6BFF6B.toInt() else 0xFFFF6B6B.toInt()
        gyroMagnitude?.setTextColor(magColor)
        gyroThreshold?.text = String.format("%.1f", biasState.stationaryThreshold)

        // Orientation
        var heading = euler[0]
        if (heading < 0) heading += 360f
        headingView?.text = String.format("%.1f°", heading)
        pitchView?.text = String.format("%.1f°", euler[1])
        rollView?.text = String.format("%.1f°", euler[2])
    }

    /**
     * Toggle panel visibility.
     */
    fun updateVisibility() {
        panelEntity?.setComponent(Visible(HudOptions.showAhrs))

        if (HudOptions.showAhrs) {
            subscribeToSensorUpdates()
        } else {
            unsubscribeFromSensorUpdates()
        }
        
        // Also update 3D visualization
        systemManager.tryFindSystem<AhrsVisualizationSystem>()?.updateVisibility()
    }

    // =========================================================================
    // RotationEstimator Interface Implementation
    // =========================================================================

    override fun updateImu(imu: MImuData) {
        // Already handled by subscription
    }

    override fun updateMag(mag: MMagData) {
        // Already handled by subscription
    }

    override fun updateGps(gps: MLocation) {
        // Not used yet - could be used for GPS-aided heading
    }

    override fun predict(currentTimeMillis: Long): Quaternion {
        // Get current quaternion from AHRS
        val q = ahrsAdapter.getQuaternion()
        // Note: Meta SDK Quaternion uses (x, y, z, w) order
        return Quaternion(q[1], q[2], q[3], q[0])
    }

    override fun getHeadingRad(): Float {
        val euler = ahrsAdapter.getEulerAngles()
        return euler[0] * (Math.PI / 180f).toFloat()
    }

    override fun getPitchRad(): Float {
        val euler = ahrsAdapter.getEulerAngles()
        return euler[1] * (Math.PI / 180f).toFloat()
    }

    override fun getRollRad(): Float {
        val euler = ahrsAdapter.getEulerAngles()
        return euler[2] * (Math.PI / 180f).toFloat()
    }

    override fun getLastImuUpdate(): MImuData? = lastImu

    override fun reset() {
        ahrsAdapter.reset()
        hasInitializedOrientation = false
        lastImu = null
        lastMag = null
    }

    /**
     * Get the underlying AHRS adapter for direct access
     */
    fun getAdapter(): FusionAhrsAdapter = ahrsAdapter

    override fun destroy() {
        unsubscribeFromSensorUpdates()
        super.destroy()
    }
}
