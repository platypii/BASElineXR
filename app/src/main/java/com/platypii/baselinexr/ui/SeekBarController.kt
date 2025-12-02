package com.platypii.baselinexr.ui

import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import com.platypii.baselinexr.R
import com.platypii.baselinexr.VROptions
import com.platypii.baselinexr.replay.ReplayController
import java.text.SimpleDateFormat
import java.util.*

/**
 * Controller for the replay seekbar timeline.
 * 
 * The timeline spans from the earliest event (GPS start or video start) to the 
 * latest event (GPS end or video end). Vertical markers show the exact positions
 * of GPS start/end and video start/end on the timeline.
 * 
 * Timeline coordinate system uses GPS timestamps (millis since epoch).
 * Video times are converted to GPS time using: gps_time = video_time - videoGpsOffsetMs
 */
class SeekBarController(
    private val view: View,
    private val replayController: ReplayController?
) {
    companion object {
        private const val TAG = "SeekBarController"
        private const val SEEKBAR_MAX = 1000  // 0.1% precision
        private const val SEEKBAR_PADDING_DP = 16  // Must match layout padding
    }
    
    // UI elements
    private val seekBar: SeekBar? = view.findViewById(R.id.replaySeekBar)
    private val seekbarFrame: FrameLayout? = view.findViewById(R.id.seekbarFrame)
    private val gpsLabelsFrame: FrameLayout? = view.findViewById(R.id.gpsLabelsFrame)
    private val videoLabelsFrame: FrameLayout? = view.findViewById(R.id.videoLabelsFrame)
    
    // Markers (vertical lines on seekbar)
    private val gpsStartMarker: View? = view.findViewById(R.id.gpsStartMarker)
    private val gpsEndMarker: View? = view.findViewById(R.id.gpsEndMarker)
    private val videoStartMarker: View? = view.findViewById(R.id.videoStartMarker)
    private val videoEndMarker: View? = view.findViewById(R.id.videoEndMarker)
    
    // Labels - GPS above seekbar, Video below seekbar
    private val gpsStartLabel: TextView? = view.findViewById(R.id.gpsStartLabel)
    private val gpsEndLabel: TextView? = view.findViewById(R.id.gpsEndLabel)
    private val videoStartLabel: TextView? = view.findViewById(R.id.videoStartLabel)
    private val videoEndLabel: TextView? = view.findViewById(R.id.videoEndLabel)
    
    // Tooltip and duration
    private val currentTimeLabel: TextView? = view.findViewById(R.id.currentTimeLabel)
    private val currentDateLabel: TextView? = view.findViewById(R.id.currentDateLabel)
    private val currentTimeTooltip: View? = view.findViewById(R.id.currentTimeTooltip)
    private val elapsedTimeLabel: TextView? = view.findViewById(R.id.elapsedTimeLabel)
    private val totalDurationLabel: TextView? = view.findViewById(R.id.totalDurationLabel)
    
    // Timeline configuration (all times in GPS millis since epoch)
    private var timelineStartMs: Long = 0      // Earliest point on timeline
    private var timelineEndMs: Long = 0        // Latest point on timeline
    private var timelineDurationMs: Long = 0   // Total duration
    
    private var gpsStartMs: Long = 0           // GPS track start time
    private var gpsEndMs: Long = 0             // GPS track end time
    private var videoStartGpsMs: Long = 0      // Video start in GPS time coordinates
    private var videoEndGpsMs: Long = 0        // Video end in GPS time coordinates
    private var videoGpsOffsetMs: Long = 0     // Offset: gps_time = video_time - offset
    
    private var hasVideo: Boolean = false
    private var isUserSeeking: Boolean = false
    private var layoutReady: Boolean = false
    
    // Time formatters
    private val timeFormat = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    
    // Callback when user seeks to a new position
    var onSeek: ((gpsTimeMs: Long, videoTimeMs: Long?) -> Unit)? = null
    
    /**
     * Initialize the seekbar with timeline data.
     * 
     * @param gpsTrackStartMs First GPS timestamp in the track
     * @param gpsTrackEndMs Last GPS timestamp in the track  
     * @param videoDurationMs Total video duration in milliseconds (0 if no video)
     * @param recordingTimezone Timezone for display (defaults to device timezone)
     */
    fun initialize(
        gpsTrackStartMs: Long,
        gpsTrackEndMs: Long,
        videoDurationMs: Long,
        recordingTimezone: TimeZone = TimeZone.getDefault()
    ) {
        val options = VROptions.current
        
        // GPS track data is already trimmed when loaded by FlySightDataLoader
        // gpsTrackStartMs/EndMs already reflect the trimmed track
        gpsStartMs = gpsTrackStartMs
        gpsEndMs = gpsTrackEndMs
        
        // Video timing - convert video times to GPS time coordinates
        // The offset is relative to the GPS start (playback start)
        hasVideo = options.has360Video() && videoDurationMs > 0
        videoGpsOffsetMs = options.videoGpsOffsetMs?.toLong() ?: 0L
        
        if (hasVideo) {
            // Video time = 0 corresponds to GPS time: gpsStartMs - offset
            // (offset positive = video starts after GPS playback start)
            videoStartGpsMs = gpsStartMs - videoGpsOffsetMs
            videoEndGpsMs = gpsStartMs + videoDurationMs - videoGpsOffsetMs
            
            android.util.Log.i(TAG, "Video timing: videoStart=$videoStartGpsMs, videoEnd=$videoEndGpsMs, " +
                "gpsStart=$gpsStartMs, offset=$videoGpsOffsetMs, duration=${videoDurationMs}ms")
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
        
        // Set up formatters with recording timezone
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
        
        android.util.Log.i(TAG, "SeekBar initialized: " +
            "timeline=$timelineStartMs-$timelineEndMs (${timelineDurationMs/1000}s), " +
            "gps=$gpsStartMs-$gpsEndMs (${(gpsEndMs-gpsStartMs)/1000}s), " +
            "video=${if(hasVideo) "$videoStartGpsMs-$videoEndGpsMs" else "none"}")
    }
    
    /**
     * Position the vertical markers and labels based on timeline positions.
     * Uses edge detection to keep labels within visible bounds.
     */
    private fun positionMarkersAndLabels() {
        val frameWidth = seekbarFrame?.width ?: return
        val density = view.resources.displayMetrics.density
        val paddingPx = (SEEKBAR_PADDING_DP * density).toInt()
        val trackWidth = frameWidth - (2 * paddingPx)
        
        if (trackWidth <= 0 || timelineDurationMs <= 0) return
        
        // Helper to calculate X position for a given GPS time (relative to seekbar frame)
        fun getMarkerX(gpsTimeMs: Long): Float {
            val fraction = (gpsTimeMs - timelineStartMs).toFloat() / timelineDurationMs
            return paddingPx + (fraction * trackWidth)
        }
        
        // Helper to position label with edge clamping
        // markerX is the marker position (includes seekbar padding offset)
        // Returns the translation X that keeps the label fully visible
        fun clampLabelPosition(markerX: Float, labelWidth: Int, containerWidth: Int): Float {
            // Desired center: markerX, label spans from (markerX - labelWidth/2) to (markerX + labelWidth/2)
            val halfLabel = labelWidth / 2f
            val desiredLeft = markerX - halfLabel
            
            // Add small margin from edges (4dp)
            val edgeMargin = 4 * density
            val minX = edgeMargin
            val maxX = containerWidth - labelWidth - edgeMargin
            
            return desiredLeft.coerceIn(minX, maxX)
        }
        
        // Position GPS markers and labels (above seekbar)
        val gpsStartX = getMarkerX(gpsStartMs)
        val gpsEndX = getMarkerX(gpsEndMs)
        
        gpsStartMarker?.let { marker ->
            marker.translationX = gpsStartX - 1  // Center the 2dp line
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
        
        // Position video markers and labels (below seekbar) if present
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
        
        android.util.Log.d(TAG, "Markers positioned: gps=$gpsStartX-$gpsEndX, " +
            "video=${if(hasVideo) "${getMarkerX(videoStartGpsMs)}-${getMarkerX(videoEndGpsMs)}" else "n/a"}, " +
            "trackWidth=$trackWidth, frameWidth=$frameWidth")
    }
    
    /**
     * Update the seekbar position based on current playback time.
     * @param currentGpsTimeMs The current GPS timestamp being played (raw, from track data)
     */
    fun updatePosition(currentGpsTimeMs: Long) {
        if (isUserSeeking || timelineDurationMs <= 0) return
        
        // Calculate position on timeline (0.0 to 1.0)
        // currentGpsTimeMs is the raw GPS time from track data
        val timelineElapsed = currentGpsTimeMs - timelineStartMs
        val progress = (timelineElapsed.toFloat() / timelineDurationMs).coerceIn(0f, 1f)
        
        seekBar?.progress = (progress * SEEKBAR_MAX).toInt()
        
        // Update current time display and position tooltip
        updateCurrentTimeDisplay(currentGpsTimeMs, progress)
        
        // Elapsed time is relative to GPS playback start (gpsStartMs), not timeline start
        // This shows how far into the GPS track we are
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
            
            // The tooltip's parent container width (same as seekbar container)
            val containerWidth = (tooltip.parent as? View)?.width ?: frameWidth
            
            // Calculate thumb center X position (in container coordinates)
            // Account for container's 8dp padding
            val containerPadding = 8 * density
            val thumbX = containerPadding + paddingPx + (progress * trackWidth)
            
            // Tooltip starts centered (at containerWidth/2 - tooltipWidth/2)
            // We want it centered on thumbX
            val centeredPosition = (containerWidth - tooltipWidth) / 2f
            val desiredPosition = thumbX - (tooltipWidth / 2f)
            
            // Clamp to keep tooltip within container (with small margin)
            val margin = 4 * density
            val minPosition = margin
            val maxPosition = containerWidth - tooltipWidth - margin
            val clampedPosition = desiredPosition.coerceIn(minPosition, maxPosition)
            
            // translationX is offset from the centered position
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
                    elapsedTimeLabel?.text = formatDuration(gpsTimeMs - timelineStartMs)
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
                
                // Calculate corresponding video time if applicable
                // video_time = gps_time - gpsTrackStart + videoGpsOffsetMs
                val videoTimeMs = if (hasVideo) {
                    // gps_time = gpsTrackStart + video_time - offset
                    // Therefore: video_time = gps_time - gpsTrackStart + offset
                    val gpsTrackStart = gpsStartMs  // Use trimmed GPS start
                    gpsTimeMs - gpsTrackStart + videoGpsOffsetMs
                } else {
                    null
                }
                
                android.util.Log.i(TAG, "User seeked to: gps=$gpsTimeMs, video=$videoTimeMs")
                onSeek?.invoke(gpsTimeMs, videoTimeMs)
            }
        })
    }
    
    fun isUserSeeking(): Boolean = isUserSeeking
}
