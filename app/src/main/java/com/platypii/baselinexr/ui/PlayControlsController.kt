package com.platypii.baselinexr.ui

import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import com.platypii.baselinexr.R
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.VROptions
import com.platypii.baselinexr.replay.ReplayController
import java.text.SimpleDateFormat
import java.util.*

/**
 * Controller for the play controls popup.
 * 
 * This is separate from the menu system and handles:
 * - Seekbar timeline with GPS/video markers
 * - Play/pause button
 * - Stop button
 * 
 * The popup is shown/hidden via the play controls button in the top bar.
 */
class PlayControlsController(
    private val view: View,
    private val replayController: ReplayController?
) {
    companion object {
        private const val TAG = "PlayControlsController"
        private const val SEEKBAR_MAX = 1000
        private const val SEEKBAR_PADDING_DP = 16
    }
    
    // UI elements - using new IDs from play_controls.xml
    private val seekBar: SeekBar? = view.findViewById(R.id.playControlsSeekBar)
    private val seekbarFrame: FrameLayout? = view.findViewById(R.id.playControlsSeekbarFrame)
    private val gpsLabelsFrame: FrameLayout? = view.findViewById(R.id.playControlsGpsLabelsFrame)
    private val videoLabelsFrame: FrameLayout? = view.findViewById(R.id.playControlsVideoLabelsFrame)
    
    // Markers
    private val gpsStartMarker: View? = view.findViewById(R.id.playControlsGpsStartMarker)
    private val gpsEndMarker: View? = view.findViewById(R.id.playControlsGpsEndMarker)
    private val videoStartMarker: View? = view.findViewById(R.id.playControlsVideoStartMarker)
    private val videoEndMarker: View? = view.findViewById(R.id.playControlsVideoEndMarker)
    
    // Labels
    private val gpsStartLabel: TextView? = view.findViewById(R.id.playControlsGpsStartLabel)
    private val gpsEndLabel: TextView? = view.findViewById(R.id.playControlsGpsEndLabel)
    private val videoStartLabel: TextView? = view.findViewById(R.id.playControlsVideoStartLabel)
    private val videoEndLabel: TextView? = view.findViewById(R.id.playControlsVideoEndLabel)
    
    // Tooltip and duration
    private val currentTimeLabel: TextView? = view.findViewById(R.id.playControlsTimeLabel)
    private val currentDateLabel: TextView? = view.findViewById(R.id.playControlsDateLabel)
    private val currentTimeTooltip: View? = view.findViewById(R.id.playControlsTooltip)
    private val elapsedTimeLabel: TextView? = view.findViewById(R.id.playControlsElapsedLabel)
    private val totalDurationLabel: TextView? = view.findViewById(R.id.playControlsTotalLabel)
    
    // Buttons
    private val playPauseButton: Button? = view.findViewById(R.id.playControlsPlayPauseButton)
    private val stopButton: Button? = view.findViewById(R.id.playControlsStopButton)
    
    // Timeline configuration
    private var timelineStartMs: Long = 0
    private var timelineEndMs: Long = 0
    private var timelineDurationMs: Long = 0
    private var gpsStartMs: Long = 0
    private var gpsEndMs: Long = 0
    private var videoStartGpsMs: Long = 0
    private var videoEndGpsMs: Long = 0
    private var videoGpsOffsetMs: Long = 0
    
    private var hasVideo: Boolean = false
    private var isUserSeeking: Boolean = false
    private var layoutReady: Boolean = false
    private var isVisible: Boolean = false
    
    // Time formatters
    private val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    
    // Callbacks
    var onSeek: ((gpsTimeMs: Long, videoTimeMs: Long?) -> Unit)? = null
    var onPlayPause: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    
    init {
        setupButtons()
    }
    
    private fun setupButtons() {
        playPauseButton?.setOnClickListener {
            android.util.Log.i(TAG, "Play/Pause button clicked")
            onPlayPause?.invoke()
        }
        
        stopButton?.setOnClickListener {
            android.util.Log.i(TAG, "Stop button clicked")
            onStop?.invoke()
        }
    }
    
    /**
     * Called externally when Play/Pause button is clicked (via HudSystem input handling).
     * 
     * Has 2-second debounce to prevent VR controller click-hold behavior from
     * triggering twice. The Meta Quest controller fires click events on both
     * press and release (~1 second apart), so we ignore rapid successive clicks.
     */
    private var lastPlayPauseTime = 0L
    private val playPauseDebounceMs = 2000L
    
    fun onPlayPauseClick() {
        val now = System.currentTimeMillis()
        if (now - lastPlayPauseTime < playPauseDebounceMs) {
            android.util.Log.d(TAG, "onPlayPauseClick() ignored - debounce")
            return
        }
        lastPlayPauseTime = now
        android.util.Log.i(TAG, "onPlayPauseClick()")
        onPlayPause?.invoke()
    }
    
    /**
     * Called externally when Stop button is clicked (via HudSystem input handling)
     */
    fun onStopClick() {
        android.util.Log.i(TAG, "onStopClick() called externally")
        onStop?.invoke()
    }
    
    /**
     * Initialize the seekbar with timeline data.
     */
    fun initialize(
        gpsTrackStartMs: Long,
        gpsTrackEndMs: Long,
        videoDurationMs: Long,
        recordingTimezone: TimeZone = TimeZone.getDefault()
    ) {
        val options = VROptions.current
        
        // GPS track data is already trimmed when loaded
        gpsStartMs = gpsTrackStartMs
        gpsEndMs = gpsTrackEndMs
        
        // Video timing
        hasVideo = options.has360Video() && videoDurationMs > 0
        videoGpsOffsetMs = options.videoGpsOffsetMs?.toLong() ?: 0L
        
        if (hasVideo) {
            videoStartGpsMs = gpsStartMs - videoGpsOffsetMs
            videoEndGpsMs = gpsStartMs + videoDurationMs - videoGpsOffsetMs
        } else {
            videoStartGpsMs = 0
            videoEndGpsMs = 0
        }
        
        // Calculate unified timeline bounds
        if (hasVideo) {
            timelineStartMs = minOf(gpsStartMs, videoStartGpsMs)
            timelineEndMs = maxOf(gpsEndMs, videoEndGpsMs)
        } else {
            timelineStartMs = gpsStartMs
            timelineEndMs = gpsEndMs
        }
        timelineDurationMs = timelineEndMs - timelineStartMs
        
        // Set up formatters
        timeFormat.timeZone = recordingTimezone
        dateFormat.timeZone = recordingTimezone
        
        // Update duration labels
        totalDurationLabel?.text = formatDuration(timelineDurationMs)
        elapsedTimeLabel?.text = "0:00"
        
        // Set up seekbar listener
        setupSeekBarListener()
        
        // Position markers after layout is ready
        seekbarFrame?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                seekbarFrame.viewTreeObserver.removeOnGlobalLayoutListener(this)
                layoutReady = true
                positionMarkersAndLabels()
            }
        })
        
        android.util.Log.i(TAG, "PlayControls initialized: timeline=${timelineDurationMs/1000}s, " +
            "gps=${(gpsEndMs-gpsStartMs)/1000}s, video=${if(hasVideo) "${(videoEndGpsMs-videoStartGpsMs)/1000}s" else "none"}")
    }
    
    private fun positionMarkersAndLabels() {
        val frameWidth = seekbarFrame?.width ?: 0
        val density = view.resources.displayMetrics.density
        val paddingPx = (SEEKBAR_PADDING_DP * density).toInt()
        val trackWidth = frameWidth - (2 * paddingPx)
        
        android.util.Log.i(TAG, "positionMarkersAndLabels: frameWidth=$frameWidth, trackWidth=$trackWidth, " +
            "timelineDuration=${timelineDurationMs/1000}s, gpsStart=$gpsStartMs, gpsEnd=$gpsEndMs")
        
        if (frameWidth <= 0) {
            android.util.Log.w(TAG, "Frame width is 0, cannot position markers")
            return
        }
        
        if (trackWidth <= 0 || timelineDurationMs <= 0) {
            android.util.Log.w(TAG, "Invalid track width or duration")
            return
        }
        
        fun getMarkerX(gpsTimeMs: Long): Float {
            val fraction = (gpsTimeMs - timelineStartMs).toFloat() / timelineDurationMs
            return paddingPx + (fraction * trackWidth)
        }
        
        fun clampLabelPosition(markerX: Float, labelWidth: Int, containerWidth: Int): Float {
            val halfLabel = labelWidth / 2f
            val desiredLeft = markerX - halfLabel
            val margin = 4 * density
            val minX = margin
            val maxX = containerWidth - labelWidth - margin
            return desiredLeft.coerceIn(minX, maxX)
        }
        
        // Position GPS markers and labels
        val gpsStartX = getMarkerX(gpsStartMs)
        val gpsEndX = getMarkerX(gpsEndMs)
        
        gpsStartMarker?.let { marker ->
            marker.translationX = gpsStartX - 1
            marker.visibility = View.VISIBLE
        }
        gpsEndMarker?.let { marker ->
            marker.translationX = gpsEndX - 1
            marker.visibility = View.VISIBLE
        }
        
        gpsStartLabel?.let { label ->
            label.text = timeFormat.format(Date(gpsStartMs))
            label.visibility = View.VISIBLE
            label.post {
                val containerWidth = gpsLabelsFrame?.width ?: frameWidth
                val clampedX = clampLabelPosition(gpsStartX, label.width, containerWidth)
                label.translationX = clampedX
            }
        }
        gpsEndLabel?.let { label ->
            label.text = timeFormat.format(Date(gpsEndMs))
            label.visibility = View.VISIBLE
            label.post {
                val containerWidth = gpsLabelsFrame?.width ?: frameWidth
                val clampedX = clampLabelPosition(gpsEndX, label.width, containerWidth)
                label.translationX = clampedX
            }
        }
        
        // Position video markers and labels if present
        if (hasVideo) {
            val videoStartX = getMarkerX(videoStartGpsMs)
            val videoEndX = getMarkerX(videoEndGpsMs)
            
            videoStartMarker?.let { marker ->
                marker.translationX = videoStartX - 1
                marker.visibility = View.VISIBLE
            }
            videoEndMarker?.let { marker ->
                marker.translationX = videoEndX - 1
                marker.visibility = View.VISIBLE
            }
            
            videoStartLabel?.let { label ->
                label.text = "▶ 0:00"
                label.visibility = View.VISIBLE
                label.post {
                    val containerWidth = videoLabelsFrame?.width ?: frameWidth
                    val clampedX = clampLabelPosition(videoStartX, label.width, containerWidth)
                    label.translationX = clampedX
                }
            }
            videoEndLabel?.let { label ->
                val videoDuration = videoEndGpsMs - videoStartGpsMs
                label.text = "▶ ${formatDuration(videoDuration)}"
                label.visibility = View.VISIBLE
                label.post {
                    val containerWidth = videoLabelsFrame?.width ?: frameWidth
                    val clampedX = clampLabelPosition(videoEndX, label.width, containerWidth)
                    label.translationX = clampedX
                }
            }
        }
    }
    
    fun updatePosition(currentGpsTimeMs: Long) {
        if (isUserSeeking || timelineDurationMs <= 0 || !isVisible) return
        
        val timelineElapsed = currentGpsTimeMs - timelineStartMs
        val progress = (timelineElapsed.toFloat() / timelineDurationMs).coerceIn(0f, 1f)
        
        seekBar?.progress = (progress * SEEKBAR_MAX).toInt()
        
        updateCurrentTimeDisplay(currentGpsTimeMs, progress)
        
        val gpsElapsed = currentGpsTimeMs - gpsStartMs
        elapsedTimeLabel?.text = formatDuration(gpsElapsed)
    }
    
    private fun updateCurrentTimeDisplay(gpsTimeMs: Long, progress: Float = 0f) {
        val date = Date(gpsTimeMs)
        currentTimeLabel?.text = timeFormat.format(date)
        currentDateLabel?.text = dateFormat.format(date)
        
        // Position tooltip to follow thumb
        currentTimeTooltip?.let { tooltip ->
            val frameWidth = seekbarFrame?.width ?: return@let
            if (frameWidth <= 0) return@let
            
            val density = view.resources.displayMetrics.density
            val paddingPx = (SEEKBAR_PADDING_DP * density)
            val trackWidth = frameWidth - (2 * paddingPx)
            if (trackWidth <= 0) return@let
            
            val tooltipWidth = tooltip.width.takeIf { it > 0 } ?: return@let
            
            val containerWidth = (tooltip.parent as? View)?.width ?: frameWidth
            val containerPadding = 8 * density
            val thumbX = containerPadding + paddingPx + (progress * trackWidth)
            
            val centeredPosition = (containerWidth - tooltipWidth) / 2f
            val desiredPosition = thumbX - (tooltipWidth / 2f)
            
            val margin = 4 * density
            val minPosition = margin
            val maxPosition = containerWidth - tooltipWidth - margin
            val clampedPosition = desiredPosition.coerceIn(minPosition, maxPosition)
            
            tooltip.translationX = clampedPosition - centeredPosition
        }
    }
    
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = kotlin.math.abs(durationMs) / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            String.format("%d:%02d:%02d", hours, mins, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun setupSeekBarListener() {
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val fraction = progress.toFloat() / SEEKBAR_MAX
                    val gpsTimeMs = timelineStartMs + (fraction * timelineDurationMs).toLong()
                    
                    updateCurrentTimeDisplay(gpsTimeMs, fraction)
                    elapsedTimeLabel?.text = formatDuration(gpsTimeMs - gpsStartMs)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                
                val progress = seekBar?.progress ?: 0
                val fraction = progress.toFloat() / SEEKBAR_MAX
                val gpsTimeMs = timelineStartMs + (fraction * timelineDurationMs).toLong()
                
                val videoTimeMs = if (hasVideo) {
                    val gpsTrackStart = gpsStartMs
                    gpsTimeMs - gpsTrackStart + videoGpsOffsetMs
                } else {
                    null
                }
                
                android.util.Log.i(TAG, "User seeked to: gps=$gpsTimeMs, video=$videoTimeMs")
                onSeek?.invoke(gpsTimeMs, videoTimeMs)
            }
        })
    }
    
    fun updatePlayPauseButton(isPaused: Boolean) {
        playPauseButton?.text = if (isPaused) "▶" else "⏸"
    }
    
    fun show() {
        isVisible = true
        android.util.Log.i(TAG, "show() called, layoutReady=$layoutReady")
        
        // Re-position markers when shown (in case layout changed)
        if (layoutReady) {
            positionMarkersAndLabels()
        } else {
            // Wait for layout to be ready
            seekbarFrame?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    seekbarFrame.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    layoutReady = true
                    android.util.Log.i(TAG, "Layout ready in show(), positioning markers")
                    positionMarkersAndLabels()
                }
            })
        }
    }
    
    fun hide() {
        isVisible = false
    }
    
    fun isUserSeeking(): Boolean = isUserSeeking
}
