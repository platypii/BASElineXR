package com.platypii.baselinexr.calibration

import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.core.net.toUri
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.SpatialActivityManager
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.GrabbablePanel
import com.platypii.baselinexr.HudOptions
import com.platypii.baselinexr.R
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.measurements.MMagData
import com.platypii.baselinexr.util.HeadPoseUtil
import com.platypii.baselinexr.util.PubSub
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

/**
 * System for magnetometer calibration with 3D visualization.
 * 
 * Displays:
 * - Red spheres: raw magnetometer samples
 * - Green spheres: calibrated samples
 * - Blue transparent sphere: reference sphere (expected magnitude)
 * 
 * Supports both hard iron (min/max) and soft iron (ellipsoid fit) calibration.
 */
class MagCalibrationSystem : SystemBase() {
    private val TAG = "MagCalibrationSystem"
    
    private var initialized = false
    private var panelEntity: Entity? = null
    private var grabbablePanel: GrabbablePanel? = null
    private var activity: BaselineActivity? = null
    
    // View references
    private var sampleCountView: TextView? = null
    private var offsetXView: TextView? = null
    private var offsetYView: TextView? = null
    private var offsetZView: TextView? = null
    private var magnitudeView: TextView? = null
    private var sphericityView: TextView? = null
    private var qualityView: TextView? = null
    private var softIronMatrixView: TextView? = null
    private var calibrationTypeView: TextView? = null
    private var calibrationStatusView: TextView? = null
    private var hardIronButton: Button? = null
    private var softIronButton: Button? = null
    private var clearButton: Button? = null
    private var applyHardIronButton: Button? = null
    private var applyFullButton: Button? = null
    
    // Mag data storage (from loaded track or live)
    private val magSamples = mutableListOf<MMagData>()
    
    // Live mag ring buffer (60 seconds at 10 Hz = 600 samples)
    private val magRingBuffer = MagRingBuffer(600)
    
    // Live mag subscriber
    private var magSubscriber: PubSub.Subscriber<MMagData>? = null
    private var isLiveMode = false
    
    // Current calibration result
    private var calibrationResult: MagCalibrationResult? = null
    
    // Samples used for calibrated visualization (snapshot for position updates)
    private var calibratedVizSamples = listOf<MMagData>()
    
    // 3D visualization entities (track mode)
    private val rawSphereEntities = mutableListOf<Entity>()
    private val calibratedSphereEntities = mutableListOf<Entity>()
    private var referenceSphereEntity: Entity? = null
    
    // Live sphere pool (pre-allocated, reused)
    private val liveSpherePool = mutableListOf<Entity>()
    private val acceptedMagPoints = mutableListOf<FloatArray>() // mag xyz of spatially filtered accepted samples
    private var activeSphereCount = 0
    private var liveSpherePoolInitialized = false
    private val pendingMagSamples = ConcurrentLinkedQueue<MMagData>()
    
    // Visualization settings
    companion object {
        /** Scale factor to convert gauss to world units (meters) */
        private const val MAG_TO_WORLD_SCALE = 0.5f
        
        /** Size of each sample sphere */
        private const val SAMPLE_SPHERE_SCALE = 0.002f
        
        /** Maximum samples to visualize for track mode (for performance) */
        private const val MAX_VISUAL_SAMPLES = 500
        
        /** Number of pre-allocated sphere entities for live mode */
        private const val LIVE_SPHERE_POOL_SIZE = 100
        
        /** Minimum Euclidean distance (gauss) between accepted visualization points */
        private const val MIN_DISTANCE_GAUSS = 0.005f
        
        /** Subsample step for large datasets */
        private fun subsampleStep(totalSamples: Int): Int {
            return if (totalSamples <= MAX_VISUAL_SAMPLES) 1
            else (totalSamples + MAX_VISUAL_SAMPLES - 1) / MAX_VISUAL_SAMPLES
        }
    }
    
    override fun execute() {
        if (!initialized) {
            val act = SpatialActivityManager.getVrActivity<BaselineActivity>()
            if (!act.glxfLoaded) return
            initializePanel(act)
        }
        
        if (initialized) {
            grabbablePanel?.setupInteraction()
            grabbablePanel?.updatePosition()
            
            // In live mode, process pending mag samples into sphere pool
            if (isLiveMode && HudOptions.showMagCalibration) {
                processLiveMagSamples()
            }
            
            updateVisualizationPosition()
        }
    }
    
    private fun initializePanel(act: BaselineActivity) {
        this.activity = act
        
        val composition = act.glXFManager.getGLXFInfo(BaselineActivity.GLXF_SCENE)
        val panel = composition.tryGetNodeByName("MagCalibrationPanel")
        if (panel?.entity != null) {
            panelEntity = panel.entity
            
            // Position to the right of the sensor data panel
            val panelOffset = Vector3(1.6f, -0.5f, 3f)
            grabbablePanel = GrabbablePanel(systemManager, panel.entity, panelOffset)
            
            // Set initial visibility
            panel.entity.setComponent(Visible(HudOptions.showMagCalibration))
            
            initialized = true
            Log.i(TAG, "Mag calibration panel initialized")
            
            // Start live subscription or load track data
            if (HudOptions.showMagCalibration) {
                startLiveSubscription()
            }
        } else {
            Log.w(TAG, "MagCalibrationPanel node not found in scene")
        }
    }
    
    /**
     * Set up view references from the panel layout.
     */
    fun setViews(
        sampleCount: TextView?,
        offsetX: TextView?, offsetY: TextView?, offsetZ: TextView?,
        magnitude: TextView?, sphericity: TextView?, quality: TextView?,
        softIronMatrix: TextView?, calibrationType: TextView?, calibrationStatus: TextView?,
        hardIronBtn: Button?, softIronBtn: Button?, clearBtn: Button?,
        applyHardIronBtn: Button?, applyFullBtn: Button?
    ) {
        sampleCountView = sampleCount
        offsetXView = offsetX
        offsetYView = offsetY
        offsetZView = offsetZ
        magnitudeView = magnitude
        sphericityView = sphericity
        qualityView = quality
        softIronMatrixView = softIronMatrix
        calibrationTypeView = calibrationType
        calibrationStatusView = calibrationStatus
        hardIronButton = hardIronBtn
        softIronButton = softIronBtn
        clearButton = clearBtn
        applyHardIronButton = applyHardIronBtn
        applyFullButton = applyFullBtn
        
        // Set up button click handlers
        hardIronButton?.setOnClickListener {
            runHardIronCalibration()
        }
        
        softIronButton?.setOnClickListener {
            runSoftIronCalibration()
        }
        
        clearButton?.setOnClickListener {
            clearCalibration()
        }
        
        applyHardIronButton?.setOnClickListener {
            applyHardIronOnly()
        }
        
        applyFullButton?.setOnClickListener {
            applyFullCalibration()
        }
        
        // Update status from saved calibration
        updateCalibrationStatusFromAdapter()
    }
    
    /**
     * Load magnetometer data from the currently loaded track.
     */
    private fun loadMagDataFromTrack() {
        val sensorData = Services.sensor.getLastSensorDataSet()
        if (sensorData != null && sensorData.hasMag()) {
            magSamples.clear()
            magSamples.addAll(sensorData.magData)
            Log.i(TAG, "Loaded ${magSamples.size} mag samples from track")
            updateSampleCountDisplay()
            createRawVisualization()
        } else {
            Log.w(TAG, "No mag data available from current track")
            sampleCountView?.text = "No data"
        }
    }
    
    /**
     * Start subscribing to live magnetometer data from BLE/sensor service.
     * Samples are added to the ring buffer and queued for spatial-filter visualization.
     */
    private fun startLiveSubscription() {
        if (magSubscriber != null) return // Already subscribed
        
        initializeLiveSpherePool()
        
        magSubscriber = PubSub.Subscriber<MMagData> { mag ->
            magRingBuffer.add(mag)
            pendingMagSamples.offer(mag)
        }
        Services.sensor.magUpdates.subscribe(magSubscriber!!)
        isLiveMode = true
        
        Log.i(TAG, "Started live mag subscription (buffer capacity=${magRingBuffer.capacity}, pool=$LIVE_SPHERE_POOL_SIZE)")
    }
    
    /**
     * Stop live magnetometer subscription.
     */
    private fun stopLiveSubscription() {
        magSubscriber?.let { Services.sensor.magUpdates.unsubscribe(it) }
        magSubscriber = null
        isLiveMode = false
        Log.i(TAG, "Stopped live mag subscription")
    }
    
    /**
     * Initialize the pre-allocated pool of sphere entities for live visualization.
     * All start invisible; they are revealed as samples are accepted by the spatial filter.
     */
    private fun initializeLiveSpherePool() {
        if (liveSpherePoolInitialized) return
        for (i in 0 until LIVE_SPHERE_POOL_SIZE) {
            val entity = Entity.create(
                Mesh("sphere_red.gltf".toUri()),
                Transform(Pose(Vector3(0f))),
                Scale(Vector3(SAMPLE_SPHERE_SCALE)),
                Visible(false)
            )
            liveSpherePool.add(entity)
        }
        liveSpherePoolInitialized = true
        Log.i(TAG, "Initialized live sphere pool: $LIVE_SPHERE_POOL_SIZE entities")
    }
    
    /**
     * Drain pending mag samples from the queue, apply spatial filter,
     * and assign accepted samples to the next available pool entity.
     * Called from execute() on the main/render thread.
     */
    private fun processLiveMagSamples() {
        var processed = false
        var sample = pendingMagSamples.poll()
        while (sample != null) {
            processed = true
            
            val point = floatArrayOf(sample.magX, sample.magY, sample.magZ)
            
            // Spatial dedup: only accept if far enough from all existing accepted points
            if (activeSphereCount < LIVE_SPHERE_POOL_SIZE && isPointAccepted(point)) {
                acceptedMagPoints.add(point)
                liveSpherePool[activeSphereCount].setComponent(Visible(true))
                activeSphereCount++
            }
            
            sample = pendingMagSamples.poll()
        }
        
        if (processed) {
            updateSampleCountDisplay()
        }
    }
    
    /**
     * Check if a point passes the spatial deduplication filter.
     * Returns true if the point is at least MIN_DISTANCE_GAUSS away from all accepted points.
     */
    private fun isPointAccepted(point: FloatArray): Boolean {
        for (accepted in acceptedMagPoints) {
            val dx = point[0] - accepted[0]
            val dy = point[1] - accepted[1]
            val dz = point[2] - accepted[2]
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq < MIN_DISTANCE_GAUSS * MIN_DISTANCE_GAUSS) return false
        }
        return true
    }
    
    /**
     * Reset the live sphere pool: hide all entities, clear accepted points.
     */
    private fun resetLiveSpherePool() {
        for (i in 0 until activeSphereCount) {
            liveSpherePool[i].setComponent(Visible(false))
        }
        activeSphereCount = 0
        acceptedMagPoints.clear()
        pendingMagSamples.clear()
    }
    
    /**
     * Destroy all pre-allocated pool entities (for cleanup).
     */
    private fun destroyLiveSpherePool() {
        for (entity in liveSpherePool) {
            entity.destroy()
        }
        liveSpherePool.clear()
        activeSphereCount = 0
        acceptedMagPoints.clear()
        pendingMagSamples.clear()
        liveSpherePoolInitialized = false
    }
    
    /**
     * Run hard iron calibration on the loaded data.
     */
    private fun runHardIronCalibration() {
        val samples = if (isLiveMode) magRingBuffer.toList() else magSamples
        if (samples.size < 10) {
            Log.w(TAG, "Not enough samples for calibration: ${samples.size}")
            return
        }
        
        try {
            calibrationResult = MagCalibrator.calibrateHardIron(samples)
            Log.i(TAG, "Hard iron calibration complete: $calibrationResult")
            updateCalibrationDisplay()
            createCalibratedVisualization()
        } catch (e: Exception) {
            Log.e(TAG, "Hard iron calibration failed", e)
        }
    }
    
    /**
     * Run soft iron calibration on the loaded data.
     */
    private fun runSoftIronCalibration() {
        val samples = if (isLiveMode) magRingBuffer.toList() else magSamples
        if (samples.size < 10) {
            Log.w(TAG, "Not enough samples for calibration: ${samples.size}")
            return
        }
        
        try {
            calibrationResult = MagCalibrator.calibrateSoftIron(samples)
            Log.i(TAG, "Soft iron calibration complete: $calibrationResult")
            updateCalibrationDisplay()
            createCalibratedVisualization()
        } catch (e: Exception) {
            Log.e(TAG, "Soft iron calibration failed", e)
        }
    }
    
    /**
     * Apply hard iron calibration only to AHRS adapter.
     * Clears any soft iron matrix (like the reference implementation).
     */
    private fun applyHardIronOnly() {
        val result = calibrationResult
        if (result == null) {
            Log.w(TAG, "No calibration to apply")
            return
        }
        
        val adapter = activity?.ahrsSystem?.getAdapter()
        if (adapter == null) {
            Log.e(TAG, "AHRS adapter not available")
            return
        }
        
        // Apply hard iron offset only
        adapter.setMagCalibration(
            com.platypii.baselinexr.ahrs.FusionAhrsAdapter.MagCalibration(
                offsetX = result.offsetX,
                offsetY = result.offsetY,
                offsetZ = result.offsetZ,
                scaleX = 1f,
                scaleY = 1f,
                scaleZ = 1f
            )
        )
        
        // Clear soft iron matrix (important: match reference implementation)
        adapter.setSoftIronMatrix(null)
        
        // Save calibration and update status
        CalibrationStorage.saveCalibration(activity!!, result, false)
        updateCalibrationStatus("Hard Iron Applied", "#88FF88")
        
        // Send hard iron calibration to FlySight via BLE
        sendHardIronToFlySight(result)
        
        Log.i(TAG, "Applied hard iron calibration: offset=(${result.offsetX}, ${result.offsetY}, ${result.offsetZ})")
    }
    
    /**
     * Send hard iron calibration offsets to FlySight 2 via BLE control point.
     * Offsets are converted from gauss to milligauss (int16_t) on the device side.
     */
    private fun sendHardIronToFlySight(result: MagCalibrationResult) {
        val controlPoint = Services.bluetooth?.flysightProtocol?.controlPoint
        if (controlPoint == null) {
            Log.w(TAG, "Cannot send hard iron to FlySight: not connected")
            updateCalibrationStatus("Hard Iron Applied (BLE N/A)", "#FFFF88")
            return
        }
        val ok = controlPoint.setMagHardIron(result.offsetX, result.offsetY, result.offsetZ)
        if (ok) {
            Log.i(TAG, "Sent hard iron to FlySight: (${result.offsetX}, ${result.offsetY}, ${result.offsetZ})")
            updateCalibrationStatus("Hard Iron Sent to FS", "#88FF88")
        } else {
            Log.e(TAG, "Failed to send hard iron to FlySight")
            updateCalibrationStatus("Hard Iron BLE Failed", "#FF8888")
        }
    }
    
    /**
     * Apply full calibration (hard iron + soft iron) to AHRS adapter.
     */
    private fun applyFullCalibration() {
        val result = calibrationResult
        if (result == null) {
            Log.w(TAG, "No calibration to apply")
            return
        }
        
        val adapter = activity?.ahrsSystem?.getAdapter()
        if (adapter == null) {
            Log.e(TAG, "AHRS adapter not available")
            return
        }
        
        // Apply hard iron offset
        adapter.setMagCalibration(
            com.platypii.baselinexr.ahrs.FusionAhrsAdapter.MagCalibration(
                offsetX = result.offsetX,
                offsetY = result.offsetY,
                offsetZ = result.offsetZ,
                scaleX = 1f,
                scaleY = 1f,
                scaleZ = 1f
            )
        )
        
        // Apply soft iron matrix if this is a soft iron calibration
        if (result.calibrationType == "soft_iron") {
            adapter.setSoftIronMatrix(result.softIronMatrix)
            CalibrationStorage.saveCalibration(activity!!, result, true)
            updateCalibrationStatus("Full Cal Applied", "#88FF88")
            Log.i(TAG, "Applied full calibration: hard iron + soft iron matrix")
        } else {
            // No soft iron matrix for hard iron calibration
            adapter.setSoftIronMatrix(null)
            CalibrationStorage.saveCalibration(activity!!, result, false)
            updateCalibrationStatus("Hard Iron Applied", "#88FF88")
            Log.i(TAG, "Applied hard iron calibration (no soft iron matrix)")
        }
    }
    
    /**
     * Update calibration status display.
     */
    private fun updateCalibrationStatus(text: String, colorHex: String) {
        calibrationStatusView?.text = text
        calibrationStatusView?.setTextColor(android.graphics.Color.parseColor(colorHex))
    }
    
    /**
     * Update status based on current adapter calibration.
     */
    private fun updateCalibrationStatusFromAdapter() {
        val adapter = activity?.ahrsSystem?.getAdapter()
        if (adapter != null) {
            val magCal = adapter.getMagCalibration()
            if (magCal.offsetX != 0f || magCal.offsetY != 0f || magCal.offsetZ != 0f) {
                val hasSoftIron = adapter.getSoftIronMatrix() != null
                if (hasSoftIron) {
                    updateCalibrationStatus("Full Cal Applied", "#88FF88")
                } else {
                    updateCalibrationStatus("Hard Iron Applied", "#88FF88")
                }
            } else {
                updateCalibrationStatus("Not Applied", "#FF8888")
            }
        }
    }
    
    /**
     * Clear calibration and visualization.
     */
    private fun clearCalibration() {
        calibrationResult = null
        clearVisualization()
        updateCalibrationDisplay()
        
        // Reset live pool or reload track data
        if (isLiveMode) {
            resetLiveSpherePool()
            magRingBuffer.clear()
        } else if (magSamples.isNotEmpty()) {
            createRawVisualization()
        }
    }
    
    /**
     * Update the sample count display.
     */
    private fun updateSampleCountDisplay() {
        val totalCount = if (isLiveMode) magRingBuffer.size() else magSamples.size
        if (isLiveMode) {
            sampleCountView?.text = "$totalCount samples ($activeSphereCount viz)"
        } else {
            sampleCountView?.text = "$totalCount samples"
        }
    }
    
    /**
     * Update the calibration result display.
     */
    private fun updateCalibrationDisplay() {
        val result = calibrationResult
        if (result == null) {
            offsetXView?.text = "---"
            offsetYView?.text = "---"
            offsetZView?.text = "---"
            magnitudeView?.text = "---"
            sphericityView?.text = "---"
            qualityView?.text = "---"
            softIronMatrixView?.text = "---"
            calibrationTypeView?.text = "None"
            return
        }
        
        offsetXView?.text = String.format("%+.4f", result.offsetX)
        offsetYView?.text = String.format("%+.4f", result.offsetY)
        offsetZView?.text = String.format("%+.4f", result.offsetZ)
        magnitudeView?.text = String.format("%.4f G", result.referenceMagnitude)
        sphericityView?.text = String.format("%.1f%%", result.sphericity * 100)
        qualityView?.text = String.format("%.1f%%", result.quality)
        calibrationTypeView?.text = if (result.calibrationType == "hard_iron") "Hard Iron" else "Soft Iron"
        
        if (result.calibrationType == "soft_iron") {
            softIronMatrixView?.text = result.formatSoftIronMatrix()
        } else {
            softIronMatrixView?.text = "Identity (hard iron only)"
        }
    }
    
    /**
     * Create 3D visualization of raw magnetometer samples.
     */
    private fun createRawVisualization() {
        // Clear existing raw spheres
        for (entity in rawSphereEntities) {
            entity.destroy()
        }
        rawSphereEntities.clear()
        
        if (magSamples.isEmpty()) return
        
        val step = subsampleStep(magSamples.size)
        var count = 0
        
        for (i in magSamples.indices step step) {
            if (count >= MAX_VISUAL_SAMPLES) break
            
            val sample = magSamples[i]
            val pos = Vector3(
                sample.magX * MAG_TO_WORLD_SCALE,
                sample.magY * MAG_TO_WORLD_SCALE,
                sample.magZ * MAG_TO_WORLD_SCALE
            )
            
            val entity = Entity.create(
                Mesh("sphere_red.gltf".toUri()),
                Transform(Pose(pos)),
                Scale(Vector3(SAMPLE_SPHERE_SCALE)),
                Visible(HudOptions.showMagCalibration)
            )
            rawSphereEntities.add(entity)
            count++
        }
        
        Log.i(TAG, "Created $count raw sample spheres (step=$step)")
    }
    
    /**
     * Create 3D visualization of calibrated samples and reference sphere.
     */
    private fun createCalibratedVisualization() {
        val result = calibrationResult ?: return
        
        // Clear existing calibrated spheres
        for (entity in calibratedSphereEntities) {
            entity.destroy()
        }
        calibratedSphereEntities.clear()
        
        // Destroy old reference sphere
        referenceSphereEntity?.destroy()
        referenceSphereEntity = null
        
        val samples = if (isLiveMode) magRingBuffer.toList() else magSamples
        if (samples.isEmpty()) return
        
        // Cache samples for position updates
        calibratedVizSamples = samples.toList()
        
        val step = subsampleStep(samples.size)
        var count = 0
        
        for (i in samples.indices step step) {
            if (count >= MAX_VISUAL_SAMPLES) break
            
            val sample = samples[i]
            val corrected = result.apply(sample.magX, sample.magY, sample.magZ)
            
            val pos = Vector3(
                corrected[0] * MAG_TO_WORLD_SCALE,
                corrected[1] * MAG_TO_WORLD_SCALE,
                corrected[2] * MAG_TO_WORLD_SCALE
            )
            
            val entity = Entity.create(
                Mesh("sphere_green.gltf".toUri()),
                Transform(Pose(pos)),
                Scale(Vector3(SAMPLE_SPHERE_SCALE)),
                Visible(HudOptions.showMagCalibration)
            )
            calibratedSphereEntities.add(entity)
            count++
        }
        
        // Create reference sphere at expected magnitude
        val refRadius = result.referenceMagnitude * MAG_TO_WORLD_SCALE
        // The sphere.gltf has radius ~6.8 units, so scale accordingly
        val refScale = refRadius / 6.8f
        
        referenceSphereEntity = Entity.create(
            Mesh("sphere_reference.gltf".toUri()),
            Transform(Pose(Vector3(0f))),
            Scale(Vector3(refScale)),
            Visible(HudOptions.showMagCalibration)
        )
        
        Log.i(TAG, "Created $count calibrated sample spheres and reference sphere (radius=${refRadius})")
    }
    
    /**
     * Clear all visualization entities.
     */
    private fun clearVisualization() {
        for (entity in rawSphereEntities) {
            entity.destroy()
        }
        rawSphereEntities.clear()
        
        for (entity in calibratedSphereEntities) {
            entity.destroy()
        }
        calibratedSphereEntities.clear()
        
        referenceSphereEntity?.destroy()
        referenceSphereEntity = null
        
        // Reset live sphere pool (hide, don't destroy)
        if (liveSpherePoolInitialized) {
            resetLiveSpherePool()
        }
    }
    
    /**
     * Update visualization position to be in front of the user.
     */
    private fun updateVisualizationPosition() {
        if (!HudOptions.showMagCalibration) return
        
        // Get head position
        val headPose = HeadPoseUtil.getHeadPose(systemManager) ?: return
        if (headPose == Pose()) return
        
        // Position visualization in front of and below the user
        val visualizationCenter = headPose.t + Vector3(0f, -0.3f, 1.5f)
        
        // Update live sphere pool positions
        for (i in 0 until activeSphereCount) {
            val point = acceptedMagPoints[i]
            val localPos = Vector3(
                point[0] * MAG_TO_WORLD_SCALE,
                point[1] * MAG_TO_WORLD_SCALE,
                point[2] * MAG_TO_WORLD_SCALE
            )
            liveSpherePool[i].setComponent(Transform(Pose(visualizationCenter + localPos)))
        }
        
        // Update track mode sphere positions
        for ((index, entity) in rawSphereEntities.withIndex()) {
            if (index >= magSamples.size) break
            val sampleIndex = index * subsampleStep(magSamples.size)
            if (sampleIndex >= magSamples.size) break
            
            val sample = magSamples[sampleIndex]
            val localPos = Vector3(
                sample.magX * MAG_TO_WORLD_SCALE,
                sample.magY * MAG_TO_WORLD_SCALE,
                sample.magZ * MAG_TO_WORLD_SCALE
            )
            entity.setComponent(Transform(Pose(visualizationCenter + localPos)))
        }
        
        val result = calibrationResult
        if (result != null && calibratedVizSamples.isNotEmpty()) {
            for ((index, entity) in calibratedSphereEntities.withIndex()) {
                if (index >= calibratedVizSamples.size) break
                val sampleIndex = index * subsampleStep(calibratedVizSamples.size)
                if (sampleIndex >= calibratedVizSamples.size) break
                
                val sample = calibratedVizSamples[sampleIndex]
                val corrected = result.apply(sample.magX, sample.magY, sample.magZ)
                val localPos = Vector3(
                    corrected[0] * MAG_TO_WORLD_SCALE,
                    corrected[1] * MAG_TO_WORLD_SCALE,
                    corrected[2] * MAG_TO_WORLD_SCALE
                )
                entity.setComponent(Transform(Pose(visualizationCenter + localPos)))
            }
            
            referenceSphereEntity?.setComponent(Transform(Pose(visualizationCenter)))
        }
    }
    
    /**
     * Toggle visibility of the calibration panel and visualization.
     */
    fun updateVisibility() {
        panelEntity?.setComponent(Visible(HudOptions.showMagCalibration))
        
        // Update track mode sphere visibility
        for (entity in rawSphereEntities) {
            entity.setComponent(Visible(HudOptions.showMagCalibration))
        }
        for (entity in calibratedSphereEntities) {
            entity.setComponent(Visible(HudOptions.showMagCalibration))
        }
        referenceSphereEntity?.setComponent(Visible(HudOptions.showMagCalibration))
        
        // Update live sphere pool visibility
        if (liveSpherePoolInitialized) {
            for (i in 0 until activeSphereCount) {
                liveSpherePool[i].setComponent(Visible(HudOptions.showMagCalibration))
            }
        }
        
        if (HudOptions.showMagCalibration) {
            startLiveSubscription()
        } else {
            stopLiveSubscription()
            clearVisualization()
        }
    }
    
    /**
     * Get the current calibration result (for export or other uses).
     */
    fun getCalibrationResult(): MagCalibrationResult? = calibrationResult
    
    /**
     * Clean up resources.
     */
    fun cleanup() {
        stopLiveSubscription()
        clearVisualization()
        destroyLiveSpherePool()
        magRingBuffer.clear()
        panelEntity = null
        grabbablePanel = null
        initialized = false
    }
}
