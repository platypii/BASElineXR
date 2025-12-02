package com.platypii.baselinexr.replay

import android.util.Log
import com.platypii.baselinexr.VROptions

/**
 * Manages the unified playback timeline for synchronized GPS + Video playback.
 * 
 * TIMELINE DESIGN:
 * - The timeline uses GPS timestamps (millis since epoch) as the canonical coordinate system
 * - Video times are converted to/from GPS time using the configured offset
 * - All components (GPS, Video, SeekBar) share this timeline for accurate sync
 * 
 * COORDINATE SYSTEMS:
 * - GPS Time: Original timestamps from the track file (millis since epoch)
 * - Video Time: Milliseconds from video start (0 = first frame)
 * - Elapsed Time: Milliseconds from playback start (0 = first GPS point or video frame, whichever is earlier)
 * 
 * CONVERSION:
 * - Video time → GPS time: gpsTime = gpsStartMs + videoTime - videoGpsOffsetMs
 * - GPS time → Video time: videoTime = gpsTime - gpsStartMs + videoGpsOffsetMs
 * - GPS time → Elapsed: elapsed = gpsTime - timelineStartMs
 * 
 * PAUSE/RESUME:
 * - On pause: store current GPS time position
 * - On resume: restore from stored position (no time drift)
 */
object PlaybackTimeline {
    private const val TAG = "PlaybackTimeline"
    
    // Timeline bounds (in GPS time coordinates - millis since epoch)
    var timelineStartMs: Long = 0       // Earliest point (min of GPS start, video start)
        private set
    var timelineEndMs: Long = 0         // Latest point (max of GPS end, video end)
        private set
    val timelineDurationMs: Long
        get() = timelineEndMs - timelineStartMs
    
    // GPS track bounds
    var gpsStartMs: Long = 0            // First GPS timestamp in track
        private set
    var gpsEndMs: Long = 0              // Last GPS timestamp in track
        private set
    val gpsDurationMs: Long
        get() = gpsEndMs - gpsStartMs
    
    // Video bounds (in GPS time coordinates for unified timeline)
    var videoStartGpsMs: Long = 0       // Video start in GPS time coordinates
        private set
    var videoEndGpsMs: Long = 0         // Video end in GPS time coordinates
        private set
    var videoDurationMs: Long = 0       // Video duration in milliseconds
        private set
    
    // Offset configuration
    var videoGpsOffsetMs: Long = 0      // From VROptions.videoGpsOffsetMs
        private set
    
    var hasVideo: Boolean = false
        private set
    
    // Current playback position (in GPS time coordinates)
    @Volatile
    private var currentGpsTimeMs: Long = 0
    
    // Pause state
    @Volatile
    private var pausedAtGpsTimeMs: Long = 0
    
    var isInitialized: Boolean = false
        private set
    
    /**
     * Initialize the timeline with GPS track and video data.
     * 
     * @param gpsTrackStartMs First GPS timestamp in the track (original, not adjusted)
     * @param gpsTrackEndMs Last GPS timestamp in the track
     * @param videoDurationMs Total video duration in milliseconds (0 if no video)
     */
    fun initialize(
        gpsTrackStartMs: Long,
        gpsTrackEndMs: Long,
        videoDurationMs: Long
    ) {
        val options = VROptions.current
        
        gpsStartMs = gpsTrackStartMs
        gpsEndMs = gpsTrackEndMs
        
        // Video timing - convert to GPS time coordinates
        hasVideo = options.has360Video() && videoDurationMs > 0
        videoGpsOffsetMs = options.videoGpsOffsetMs?.toLong() ?: 0L
        this.videoDurationMs = videoDurationMs
        
        if (hasVideo) {
            // Video time = 0 corresponds to GPS time: gpsStartMs - videoGpsOffsetMs
            // Positive offset = video starts AFTER GPS starts (video is "behind")
            // Negative offset = video starts BEFORE GPS starts (video is "ahead")
            videoStartGpsMs = gpsStartMs - videoGpsOffsetMs
            videoEndGpsMs = videoStartGpsMs + videoDurationMs
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
        
        // Initialize current position to start
        currentGpsTimeMs = gpsStartMs
        pausedAtGpsTimeMs = 0
        
        isInitialized = true
        
        Log.i(TAG, "Timeline initialized: " +
            "timeline=$timelineStartMs-$timelineEndMs (${timelineDurationMs/1000}s), " +
            "gps=$gpsStartMs-$gpsEndMs (${gpsDurationMs/1000}s), " +
            "video=${if(hasVideo) "$videoStartGpsMs-$videoEndGpsMs (${videoDurationMs/1000}s)" else "none"}, " +
            "offset=${videoGpsOffsetMs}ms")
    }
    
    /**
     * Update current playback position from GPS.
     * Called when a new GPS location is emitted.
     * 
     * @param gpsTimeMs The current GPS timestamp (original, from track file)
     */
    fun updatePosition(gpsTimeMs: Long) {
        currentGpsTimeMs = gpsTimeMs
    }
    
    /**
     * Get the current GPS time being played.
     */
    fun getCurrentGpsTimeMs(): Long = currentGpsTimeMs
    
    /**
     * Get the current elapsed time from timeline start.
     */
    fun getElapsedMs(): Long = currentGpsTimeMs - timelineStartMs
    
    /**
     * Get the current video time (milliseconds from video start).
     * Returns null if no video or current position is outside video bounds.
     */
    fun getCurrentVideoTimeMs(): Long? {
        if (!hasVideo) return null
        return gpsTimeToVideoTime(currentGpsTimeMs)
    }
    
    // ==================== CONVERSION METHODS ====================
    
    /**
     * Convert GPS time to video time.
     * @param gpsTimeMs GPS timestamp (millis since epoch)
     * @return Video time in milliseconds from video start, or null if outside video bounds
     */
    fun gpsTimeToVideoTime(gpsTimeMs: Long): Long? {
        if (!hasVideo) return null
        
        // videoTime = gpsTime - gpsStartMs + videoGpsOffsetMs
        val videoTimeMs = gpsTimeMs - gpsStartMs + videoGpsOffsetMs
        
        // Clamp to video bounds (allow slight overshoot for sync tolerance)
        return when {
            videoTimeMs < -500 -> null  // Before video
            videoTimeMs > videoDurationMs + 500 -> null  // After video
            else -> videoTimeMs.coerceIn(0, videoDurationMs)
        }
    }
    
    /**
     * Convert video time to GPS time.
     * @param videoTimeMs Video time in milliseconds from video start
     * @return GPS timestamp (millis since epoch)
     */
    fun videoTimeToGpsTime(videoTimeMs: Long): Long {
        // gpsTime = gpsStartMs + videoTime - videoGpsOffsetMs
        return gpsStartMs + videoTimeMs - videoGpsOffsetMs
    }
    
    /**
     * Convert elapsed time (from timeline start) to GPS time.
     * @param elapsedMs Milliseconds from timeline start
     * @return GPS timestamp (millis since epoch)
     */
    fun elapsedToGpsTime(elapsedMs: Long): Long {
        return timelineStartMs + elapsedMs
    }
    
    /**
     * Convert GPS time to elapsed time (from timeline start).
     * @param gpsTimeMs GPS timestamp (millis since epoch)
     * @return Milliseconds from timeline start
     */
    fun gpsTimeToElapsed(gpsTimeMs: Long): Long {
        return gpsTimeMs - timelineStartMs
    }
    
    // ==================== PAUSE/RESUME ====================
    
    /**
     * Record current position for pause.
     */
    fun onPause() {
        pausedAtGpsTimeMs = currentGpsTimeMs
        Log.i(TAG, "Paused at GPS time: $pausedAtGpsTimeMs (elapsed: ${getElapsedMs()}ms)")
    }
    
    /**
     * Get the GPS time we were paused at.
     */
    fun getPausedGpsTimeMs(): Long = pausedAtGpsTimeMs
    
    /**
     * Get the video time we were paused at.
     */
    fun getPausedVideoTimeMs(): Long? = gpsTimeToVideoTime(pausedAtGpsTimeMs)
    
    // ==================== SEEK ====================
    
    /**
     * Calculate seek targets for both GPS and video.
     * @param targetGpsTimeMs The target GPS timestamp
     * @return Pair of (gpsTimeMs, videoTimeMs) where videoTimeMs may be null
     */
    fun calculateSeekTargets(targetGpsTimeMs: Long): Pair<Long, Long?> {
        val clampedGpsTime = targetGpsTimeMs.coerceIn(gpsStartMs, gpsEndMs)
        val videoTime = gpsTimeToVideoTime(clampedGpsTime)
        return Pair(clampedGpsTime, videoTime)
    }
    
    // ==================== INITIAL START TIMING ====================
    
    /**
     * Calculate how long GPS should wait before starting (if video starts first).
     * 
     * On the unified timeline:
     * - If video starts before GPS (negative offset), GPS should wait
     * - If GPS starts before video (positive offset), GPS starts immediately
     * 
     * @return Delay in milliseconds before GPS should start (0 if GPS starts first)
     */
    fun getGpsStartDelayMs(): Long {
        if (!hasVideo) return 0
        
        // GPS delay = how far GPS start is from timeline start
        // If video starts first, this will be positive
        // If GPS starts first, this will be 0
        val delay = gpsStartMs - timelineStartMs
        Log.i(TAG, "GPS start delay: ${delay}ms (gpsStart=$gpsStartMs, timelineStart=$timelineStartMs)")
        return delay.coerceAtLeast(0)
    }
    
    /**
     * Calculate the initial video position when playback starts.
     * 
     * On the unified timeline:
     * - If GPS starts before video, video starts at position 0
     * - If video starts before GPS, video starts at (gpsStart - videoStart)
     * 
     * @return Initial video position in milliseconds from video start
     */
    fun getInitialVideoPositionMs(): Long {
        if (!hasVideo) return 0
        
        // Video position at GPS start time
        // If GPS starts after video, we need to skip ahead in the video
        val position = gpsStartMs - videoStartGpsMs
        Log.i(TAG, "Initial video position: ${position}ms (gpsStart=$gpsStartMs, videoStart=$videoStartGpsMs)")
        return position.coerceAtLeast(0)
    }
    
    /**
     * Check if video starts before GPS on the timeline.
     * If true, video should start first and GPS should wait.
     */
    fun videoStartsFirst(): Boolean {
        if (!hasVideo) return false
        return videoStartGpsMs < gpsStartMs
    }
    
    /**
     * Check if GPS starts before video on the timeline.
     * If true, GPS starts first and video should seek to catch up.
     */
    fun gpsStartsFirst(): Boolean {
        if (!hasVideo) return true  // No video = GPS is the only thing
        return gpsStartMs <= videoStartGpsMs
    }
    
    /**
     * Reset timeline state (for fresh playback).
     */
    fun reset() {
        currentGpsTimeMs = gpsStartMs
        pausedAtGpsTimeMs = 0
        Log.i(TAG, "Timeline reset to start")
    }
    
    /**
     * Clear all timeline data (on activity destroy).
     */
    fun clear() {
        timelineStartMs = 0
        timelineEndMs = 0
        gpsStartMs = 0
        gpsEndMs = 0
        videoStartGpsMs = 0
        videoEndGpsMs = 0
        videoDurationMs = 0
        videoGpsOffsetMs = 0
        hasVideo = false
        currentGpsTimeMs = 0
        pausedAtGpsTimeMs = 0
        isInitialized = false
        Log.i(TAG, "Timeline cleared")
    }
}
