package com.platypii.baselinexr.replay

import android.content.Context
import android.util.Log
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.VROptions
import com.platypii.baselinexr.video.Video360Controller

/**
 * Controls synchronized playback of GPS track and 360 video.
 * GPS is the master clock - video follows GPS timing.
 * 
 * States:
 * - STOPPED: Not playing, at start position
 * - PLAYING: Actively playing GPS + video
 * - PAUSED: Frozen at current position, can resume
 * - COMPLETED: Reached end of track
 */
class ReplayController(private val context: Context) {
    
    companion object {
        private const val TAG = "ReplayController"
    }
    
    enum class PlaybackState {
        STOPPED,    // At beginning, not started
        PLAYING,    // Actively playing
        PAUSED,     // Frozen, can resume
        COMPLETED   // Finished playing
    }
    
    /** Current playback state */
    @Volatile
    var state: PlaybackState = PlaybackState.STOPPED
        private set
    
    /** Video controller reference */
    var videoController: Video360Controller? = null
    
    /**
     * Called when GPS playback starts (from MockLocationProvider)
     * This syncs our state with the actual playback state
     */
    fun onGpsStarted() {
        Log.i(TAG, "GPS playback started notification received")
        if (state != PlaybackState.PLAYING) {
            setState(PlaybackState.PLAYING)
        }
    }
    
    /** Whether we're in replay mode (has mock track configured) */
    val isReplayMode: Boolean
        get() = VROptions.current.mockTrack != null || VROptions.current.mockSensor != null

    /**
     * Start or resume playback
     */
    fun play() {
        Log.i(TAG, "play() called, current state: $state")
        
        when (state) {
            PlaybackState.STOPPED, PlaybackState.COMPLETED -> {
                // Start fresh from beginning
                startFresh()
            }
            PlaybackState.PAUSED -> {
                // Resume from current position
                resume()
            }
            PlaybackState.PLAYING -> {
                Log.d(TAG, "Already playing, ignoring")
            }
        }
    }
    
    /**
     * Pause playback (can resume later)
     */
    fun pause() {
        Log.i(TAG, "pause() called, current state: $state")
        
        if (state != PlaybackState.PLAYING) {
            Log.d(TAG, "Not playing, ignoring pause")
            return
        }
        
        // Cancel any pending scheduled GPS/video starts
        cancelScheduledGpsStart()
        cancelScheduledVideoStart()
        
        // Freeze motion estimator to stop position extrapolation
        Services.location.motionEstimator.freeze()
        
        // Record current position in PlaybackTimeline
        PlaybackTimeline.onPause()
        
        // Pause GPS
        Services.location.pauseMockPlayback()
        
        // Pause video
        videoController?.pause()
        
        setState(PlaybackState.PAUSED)
    }
    
    /**
     * Stop playback and reset to beginning.
     * Always goes to STOPPED state (not PAUSED), clears all pause positions,
     * resets video to first frame.
     * Waits for PLAY button to start again.
     */
    fun stop() {
        Log.i(TAG, "stop() called, current state: $state")
        
        // Cancel any pending scheduled GPS/video starts
        cancelScheduledGpsStart()
        cancelScheduledVideoStart()
        
        // Freeze motion estimator to stop position extrapolation
        Services.location.motionEstimator.freeze()
        
        // Stop and reset video to first frame, clear pause position
        videoController?.let { vc ->
            vc.stop()  // Stops playback and resets to beginning
        }
        
        // Stop GPS playback (don't reset - startFresh will handle restart)
        Services.location.stopMockPlayback()
        
        // Reset enhanced flight mode state
        Services.flightComputer?.enhancedFlightMode?.reset()
        
        setState(PlaybackState.STOPPED)
        Log.i(TAG, "Playback stopped - waiting for PLAY")
    }
    
    /**
     * Toggle between play and pause
     */
    fun togglePlayPause() {
        Log.i(TAG, "togglePlayPause() called, current state: $state")
        
        when (state) {
            PlaybackState.PLAYING -> pause()
            PlaybackState.PAUSED, PlaybackState.STOPPED, PlaybackState.COMPLETED -> play()
        }
    }
    
    /**
     * Called when headset goes to sleep - auto pause
     */
    fun onSleep() {
        Log.i(TAG, "onSleep() called, current state: $state")
        if (state == PlaybackState.PLAYING) {
            // Pause GPS
            Services.location.pauseMockPlayback()
            // Pause video (don't use videoController?.pause() as onSleep handles it)
            setState(PlaybackState.PAUSED)
        }
    }
    
    /**
     * Called when headset wakes up - don't auto resume, user controls via buttons
     */
    fun onWake() {
        Log.i(TAG, "onWake() called, current state: $state")
        // Don't auto-resume - keep paused state, user can resume via play button
    }
    
    // Handler for scheduling GPS start after delay
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var scheduledGpsStartRunnable: Runnable? = null
    private var scheduledVideoStartRunnable: Runnable? = null

    /**
     * Schedule GPS to start after a delay.
     * Cancels any previously scheduled start.
     */
    private fun scheduleGpsStart(delayMs: Long) {
        // Cancel any existing scheduled start
        scheduledGpsStartRunnable?.let { handler.removeCallbacks(it) }
        
        scheduledGpsStartRunnable = Runnable {
            Log.i(TAG, "Scheduled GPS start triggered")
            if (state == PlaybackState.PLAYING) {
                // Disable video sync BEFORE starting GPS to prevent the first GPS point
                // from triggering a sync before we're ready
                videoController?.syncDisabledUntil = System.currentTimeMillis() + 2000
                
                // Get current video position and calculate corresponding GPS time
                val currentVideoPos = videoController?.getCurrentPosition()?.toLong() ?: 0
                val correspondingGpsTime = PlaybackTimeline.videoTimeToGpsTime(currentVideoPos)
                
                Log.i(TAG, "GPS start: videoPos=$currentVideoPos -> gpsTime=$correspondingGpsTime (gpsStart=${PlaybackTimeline.gpsStartMs})")
                
                if (correspondingGpsTime >= PlaybackTimeline.gpsStartMs) {
                    // Video has progressed into GPS range - seek GPS to match
                    Log.i(TAG, "Video in GPS range, seeking GPS to $correspondingGpsTime")
                    Services.location.seekMockPlayback(correspondingGpsTime, true)
                } else {
                    // Still before GPS range (timing variance) - start from beginning
                    Log.i(TAG, "Video still before GPS range, starting from beginning")
                    Services.location.startMockPlayback(context)
                }
            }
            scheduledGpsStartRunnable = null
        }
        
        Log.i(TAG, "Scheduling GPS start in ${delayMs}ms")
        handler.postDelayed(scheduledGpsStartRunnable!!, delayMs)
    }
    
    /**
     * Cancel any scheduled GPS start (called on pause/stop)
     */
    private fun cancelScheduledGpsStart() {
        scheduledGpsStartRunnable?.let {
            handler.removeCallbacks(it)
            scheduledGpsStartRunnable = null
            Log.i(TAG, "Cancelled scheduled GPS start")
        }
    }
    
    /**
     * Schedule video to start after a delay.
     * Cancels any previously scheduled start.
     */
    private fun scheduleVideoStart(delayMs: Long) {
        // Cancel any existing scheduled start
        scheduledVideoStartRunnable?.let { handler.removeCallbacks(it) }
        
        scheduledVideoStartRunnable = Runnable {
            Log.i(TAG, "Scheduled video start triggered")
            if (state == PlaybackState.PLAYING) {
                videoController?.play()
            }
            scheduledVideoStartRunnable = null
        }
        
        Log.i(TAG, "Scheduling video start in ${delayMs}ms")
        handler.postDelayed(scheduledVideoStartRunnable!!, delayMs)
    }
    
    /**
     * Cancel any scheduled video start (called on pause/stop)
     */
    private fun cancelScheduledVideoStart() {
        scheduledVideoStartRunnable?.let {
            handler.removeCallbacks(it)
            scheduledVideoStartRunnable = null
            Log.i(TAG, "Cancelled scheduled video start")
        }
    }

    /**
     * Seek to a specific position in the replay.
     * Coordinates GPS and video seeking.
     * 
     * Handles three zones:
     * 1. Before GPS starts (video-only): Stop GPS, schedule to start later
     * 2. Within GPS range: Normal seek
     * 3. After GPS ends (video-only): Stop GPS, let video continue alone
     * 
     * @param gpsTimeMs The target GPS timestamp to seek to
     * @param videoTimeMs The corresponding video time (null if no video)
     * @param resumeAfterSeek Whether to resume playback after seeking
     */
    fun seekTo(gpsTimeMs: Long, videoTimeMs: Long?, resumeAfterSeek: Boolean = false) {
        Log.i(TAG, "seekTo() called: gps=$gpsTimeMs, video=$videoTimeMs, resume=$resumeAfterSeek, state=$state")
        
        // Cancel any pending scheduled GPS/video starts
        cancelScheduledGpsStart()
        cancelScheduledVideoStart()
        
        // Always freeze motion estimator during seek - will be unfrozen by onGpsStarted when GPS emits
        Services.location.motionEstimator.freeze()
        
        // Clear cached predictions (soft reset - preserves filter state for natural recovery)
        Services.location.motionEstimator.softReset()
        
        // Reset flight computer state (may have jumped between flight phases)
        Services.flightComputer?.enhancedFlightMode?.reset()
        
        // Update PlaybackTimeline with new position
        PlaybackTimeline.updatePosition(gpsTimeMs)
        
        // Check which zone we're seeking to
        val gpsStartMs = PlaybackTimeline.gpsStartMs
        val gpsEndMs = PlaybackTimeline.gpsEndMs
        val seekingBeforeGps = PlaybackTimeline.hasVideo && gpsTimeMs < gpsStartMs
        val seekingAfterGps = PlaybackTimeline.hasVideo && gpsTimeMs > gpsEndMs
        
        when {
            seekingBeforeGps -> {
                Log.i(TAG, "Seeking to video-only zone BEFORE GPS (gps=$gpsTimeMs < gpsStart=$gpsStartMs)")
                
                // Motion estimator stays frozen - no GPS data in this zone
                
                // Stop GPS playback - it will be restarted when video reaches GPS start point
                Services.location.getMockLocationProvider()?.let { provider ->
                    if (provider.isStarted) {
                        Log.i(TAG, "Stopping GPS playback for video-only seek (before)")
                        provider.stop()
                    }
                }
                
                // Calculate delay until GPS should start
                // GPS starts when video reaches position = videoGpsOffsetMs
                val currentVideoPos = videoTimeMs ?: 0L
                val videoPositionWhereGpsStarts = PlaybackTimeline.videoGpsOffsetMs
                val msUntilGpsStart = (videoPositionWhereGpsStarts - currentVideoPos).coerceAtLeast(0)
                Log.i(TAG, "GPS will start in ${msUntilGpsStart}ms (video needs to reach ${videoPositionWhereGpsStarts}ms, currently at ${currentVideoPos}ms)")
                
                // Schedule GPS to start after delay (if resuming playback)
                if (resumeAfterSeek) {
                    scheduleGpsStart(msUntilGpsStart)
                }
            }
            seekingAfterGps -> {
                Log.i(TAG, "Seeking to video-only zone AFTER GPS (gps=$gpsTimeMs > gpsEnd=$gpsEndMs)")
                
                // Motion estimator stays frozen - GPS track has ended
                
                // Stop GPS playback - GPS track has ended, only video continues
                Services.location.getMockLocationProvider()?.let { provider ->
                    if (provider.isStarted) {
                        Log.i(TAG, "Stopping GPS playback for video-only seek (after)")
                        provider.stop()
                    }
                }
                // No scheduling needed - GPS is done for this playback
            }
            else -> {
                // Normal seek - within GPS track range
                Log.i(TAG, "Seeking within GPS range ($gpsStartMs <= $gpsTimeMs <= $gpsEndMs)")
                // Motion estimator stays frozen during seek - caller's resume() will unfreeze it
                Services.location.seekMockPlayback(gpsTimeMs, resumeAfterSeek)
            }
        }
        
        // Seek video if present
        videoTimeMs?.let { videoTime ->
            videoController?.let { vc ->
                if (resumeAfterSeek) {
                    vc.seekToAndPlay(videoTime.toInt())
                } else {
                    vc.seekTo(videoTime.toInt())
                }
            }
        }
        
        // Clear completed flag in ReplayManager if seeking back
        if (ReplayManager.gpsCompleted || ReplayManager.videoCompleted) {
            ReplayManager.prepareForRestart()
        }
        
        // Update state if needed
        if (resumeAfterSeek && state != PlaybackState.PLAYING) {
            setState(PlaybackState.PLAYING)
        }
        
        Log.i(TAG, "Seek complete")
    }
    
    /**
     * Lightweight seek for drag preview - emits GPS point and updates video frame
     * without resetting motion estimator or flight computer state.
     * 
     * This is called at 20fps during seekbar drag to show the current position.
     * Motion estimator is frozen by PlayControlsController during the drag.
     * 
     * @param gpsTimeMs The target GPS timestamp
     * @param videoTimeMs The corresponding video time (null if no video)
     */
    fun seekPreview(gpsTimeMs: Long, videoTimeMs: Long?) {
        // Update PlaybackTimeline
        PlaybackTimeline.updatePosition(gpsTimeMs)
        
        // Only seek GPS if we're within the GPS track range (not video-only zones)
        val gpsStartMs = PlaybackTimeline.gpsStartMs
        val gpsEndMs = PlaybackTimeline.gpsEndMs
        val inGpsRange = gpsTimeMs >= gpsStartMs && gpsTimeMs <= gpsEndMs
        
        if (!PlaybackTimeline.hasVideo || inGpsRange) {
            // Seek GPS to emit point (without resuming playback)
            Services.location.seekMockPlayback(gpsTimeMs, false)
        }
        
        // Seek video to show frame (paused)
        videoTimeMs?.let { videoTime ->
            videoController?.seekTo(videoTime.toInt())
        }
    }
    
    /**
     * Get the GPS track start time (first point timestamp).
     */
    fun getTrackStartTimeMs(): Long {
        return Services.location.getMockLocationProvider()?.getTrackStartTime() ?: 0L
    }
    
    /**
     * Get the GPS track end time (last point timestamp).
     */
    fun getTrackEndTimeMs(): Long {
        val provider = Services.location.getMockLocationProvider() ?: return 0L
        return provider.getTrackStartTime() + provider.getTrackDuration()
    }
    
    /**
     * Get the current GPS timestamp being played.
     */
    fun getCurrentGpsTimeMs(): Long {
        return Services.location.getMockLocationProvider()?.getCurrentGpsTimeMs() ?: 0L
    }
    
    /**
     * Get the GPS track duration in milliseconds.
     */
    fun getTrackDurationMs(): Long {
        return Services.location.getMockLocationProvider()?.getTrackDuration() ?: 0L
    }
    
    /**
     * Ensure PlaybackTimeline is initialized before use.
     * Called by startFresh() in case play is pressed before popup was opened.
     */
    private fun ensurePlaybackTimelineInitialized() {
        if (PlaybackTimeline.isInitialized) return
        
        val mockProvider = Services.location.getMockLocationProvider() ?: return
        val gpsTrackStartMs = mockProvider.getTrackStartTime()
        val gpsTrackEndMs = gpsTrackStartMs + mockProvider.getTrackDuration()
        
        if (gpsTrackStartMs <= 0 || gpsTrackEndMs <= gpsTrackStartMs) {
            Log.w(TAG, "Cannot initialize PlaybackTimeline - no valid GPS track")
            return
        }
        
        val videoDurationMs = videoController?.getDuration() ?: 0L
        
        PlaybackTimeline.initialize(
            gpsTrackStartMs = gpsTrackStartMs,
            gpsTrackEndMs = gpsTrackEndMs,
            videoDurationMs = videoDurationMs
        )
        Log.i(TAG, "PlaybackTimeline initialized from startFresh()")
    }
    
    /**
     * Start playback from beginning.
     * 
     * TIMELINE COORDINATION:
     * Both GPS and video ALWAYS play in their entirety from position 0.
     * The timeline spans from the earliest start to the latest end.
     * 
     * - If video starts first on timeline: Video starts at 0 immediately,
     *   GPS starts at 0 after gpsDelayMs
     * - If GPS starts first on timeline: GPS starts at 0 immediately,
     *   video starts at 0 after videoDelayMs
     * 
     * Neither is ever truncated - we just delay the start of whichever one
     * comes second on the timeline.
     */
    private fun startFresh() {
        Log.i(TAG, "Starting fresh playback from beginning")
        
        // Reset ReplayManager state so GPS and video don't think they're already completed
        ReplayManager.prepareForRestart()
        
        // Reset motion estimator completely for fresh start - clears accumulated Kalman state
        Services.location.motionEstimator.reset()
        
        // Reset video sync state in BaselineActivity so PlaybackTimeline position is reset
        (context as? BaselineActivity)?.resetVideoSyncState()
        
        // Ensure PlaybackTimeline is initialized before we use it
        ensurePlaybackTimelineInitialized()
        
        // Calculate timing from PlaybackTimeline
        val gpsDelayMs = PlaybackTimeline.getGpsStartDelayMs()
        val videoDelayMs = PlaybackTimeline.getVideoStartDelayMs()
        
        Log.i(TAG, "Timeline start: gpsDelay=${gpsDelayMs}ms, videoDelay=${videoDelayMs}ms, " +
            "videoStartsFirst=${PlaybackTimeline.videoStartsFirst()}")
        
        if (!PlaybackTimeline.hasVideo) {
            // NO VIDEO: GPS only, start immediately from position 0
            Log.i(TAG, "No video - starting GPS immediately")
            Services.location.startMockPlayback(context)
            setState(PlaybackState.PLAYING)
            return
        }
        
        // We have video - both always play from position 0, but one may be delayed
        // Seek video to 0 first, then coordinate the starts
        videoController?.seekWithCallback(0) {
            Log.i(TAG, "Video seek to 0 complete, coordinating starts")
            
            if (PlaybackTimeline.videoStartsFirst()) {
                // VIDEO STARTS FIRST: Start video now, delay GPS
                Log.i(TAG, "Video starts first - playing video now, GPS in ${gpsDelayMs}ms")
                
                // Freeze motion estimator until GPS starts (no GPS data during video-only period)
                if (gpsDelayMs > 0) {
                    Services.location.motionEstimator.freeze()
                }
                
                videoController?.play()
                
                if (gpsDelayMs > 0) {
                    // Use centralized scheduling so it can be cancelled on pause/stop
                    scheduleGpsStart(gpsDelayMs)
                } else {
                    Services.location.startMockPlayback(context)
                }
            } else {
                // GPS STARTS FIRST: Start GPS now, delay video
                Log.i(TAG, "GPS starts first - starting GPS now, video in ${videoDelayMs}ms")
                Services.location.startMockPlayback(context)
                
                if (videoDelayMs > 0) {
                    // Use centralized scheduling so it can be cancelled on pause/stop
                    scheduleVideoStart(videoDelayMs)
                } else {
                    videoController?.play()
                }
            }
        }
        
        // Set state to PLAYING after everything is kicked off
        setState(PlaybackState.PLAYING)
    }
    
    /**
     * Resume from paused position.
     * Also handles the case where GPS was never started (e.g., we were in video-only zone
     * and seeked into GPS range) - in that case, starts GPS from current position.
     */
    fun resume() {
        Log.i(TAG, "Resuming playback from state: $state")
        
        // Unfreeze motion estimator to allow position extrapolation
        Services.location.motionEstimator.unfreeze()
        Log.i(TAG, "Motion estimator unfrozen")
        
        // Check if GPS needs to be started vs resumed
        val provider = Services.location.getMockLocationProvider()
        val gpsNeedsStart = provider != null && !provider.isStarted
        
        if (gpsNeedsStart) {
            // GPS was never started - check if we're in video-only zone or GPS range
            val currentGpsTime = PlaybackTimeline.getCurrentGpsTimeMs()
            val gpsStartTime = PlaybackTimeline.gpsStartMs
            val gpsEndTime = PlaybackTimeline.gpsEndMs
            
            Log.i(TAG, "GPS not started - current position: $currentGpsTime, gpsRange=[$gpsStartTime, $gpsEndTime]")
            
            if (currentGpsTime < gpsStartTime) {
                // We're in video-only zone BEFORE GPS - schedule GPS to start later
                // Calculate based on video position, not GPS time
                val currentVideoPos = videoController?.getCurrentPosition()?.toLong() ?: 0
                val videoPositionWhereGpsStarts = PlaybackTimeline.videoGpsOffsetMs
                val msUntilGpsStart = (videoPositionWhereGpsStarts - currentVideoPos).coerceAtLeast(0)
                Log.i(TAG, "In video-only zone before GPS - scheduling GPS start in ${msUntilGpsStart}ms (video at ${currentVideoPos}ms, GPS starts at video ${videoPositionWhereGpsStarts}ms)")
                
                // Freeze motion estimator until GPS starts
                Services.location.motionEstimator.freeze()
                
                // Schedule GPS to start when video reaches GPS start point
                scheduleGpsStart(msUntilGpsStart)
            } else if (currentGpsTime > gpsEndTime) {
                // We're in video-only zone AFTER GPS - don't start GPS at all
                Log.i(TAG, "In video-only zone after GPS - GPS will not start")
            } else {
                // We're in GPS range - start GPS from current position
                Log.i(TAG, "In GPS range - starting GPS from current position")
                
                // Cancel any pending scheduled GPS/video starts - we're starting GPS manually
                cancelScheduledGpsStart()
                cancelScheduledVideoStart()
                
                // Use seekTo with resumeAfterSeek=true to start GPS from current position
                Services.location.seekMockPlayback(currentGpsTime, true)
            }
        } else {
            // Normal resume - GPS was already started
            Services.location.resumeMockPlayback()
            Log.i(TAG, "GPS resumed")
        }
        
        // Resume video - just call play(), MediaPlayer remembers position when paused
        videoController?.play()
        Log.i(TAG, "Video play() called")
        
        setState(PlaybackState.PLAYING)
        Log.i(TAG, "State set to PLAYING, resume complete")
    }
    
    private fun setState(newState: PlaybackState) {
        if (state != newState) {
            val oldState = state
            state = newState
            Log.i(TAG, "State changed: $oldState -> $newState")
        }
    }
}
