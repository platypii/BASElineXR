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
    
    /**
     * Seek to a specific position in the replay.
     * Coordinates GPS and video seeking.
     * 
     * @param gpsTimeMs The target GPS timestamp to seek to
     * @param videoTimeMs The corresponding video time (null if no video)
     * @param resumeAfterSeek Whether to resume playback after seeking
     */
    fun seekTo(gpsTimeMs: Long, videoTimeMs: Long?, resumeAfterSeek: Boolean = false) {
        Log.i(TAG, "seekTo() called: gps=$gpsTimeMs, video=$videoTimeMs, resume=$resumeAfterSeek, state=$state")
        
        // Reset motion estimator state (position jump invalidates velocity estimates)
        Services.location.motionEstimator.freeze()
        Services.location.motionEstimator.unfreeze()
        
        // Reset flight computer state (may have jumped between flight phases)
        Services.flightComputer?.enhancedFlightMode?.reset()
        
        // Update PlaybackTimeline with new position
        PlaybackTimeline.updatePosition(gpsTimeMs)
        
        // Seek GPS
        Services.location.seekMockPlayback(gpsTimeMs, resumeAfterSeek)
        
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
     * Uses PlaybackTimeline to calculate proper start positions/delays based on
     * the configured videoGpsOffsetMs:
     * 
     * - If video starts first (offset < 0): Video at 0, GPS delayed by offset
     * - If GPS starts first (offset >= 0): GPS at 0, video seeks to catch up
     */
    private fun startFresh() {
        Log.i(TAG, "Starting fresh playback from beginning")
        
        // Reset ReplayManager state so GPS and video don't think they're already completed
        ReplayManager.prepareForRestart()
        
        // Unfreeze motion estimator to allow position extrapolation
        Services.location.motionEstimator.unfreeze()
        
        // Reset video sync state in BaselineActivity so PlaybackTimeline position is reset
        (context as? BaselineActivity)?.resetVideoSyncState()
        
        // Ensure PlaybackTimeline is initialized before we use it
        ensurePlaybackTimelineInitialized()
        
        // Calculate timing from PlaybackTimeline
        val gpsDelayMs = PlaybackTimeline.getGpsStartDelayMs()
        val initialVideoPositionMs = PlaybackTimeline.getInitialVideoPositionMs()
        
        Log.i(TAG, "Timeline start: gpsDelay=${gpsDelayMs}ms, videoPosition=${initialVideoPositionMs}ms, " +
            "videoStartsFirst=${PlaybackTimeline.videoStartsFirst()}")
        
        if (PlaybackTimeline.hasVideo && PlaybackTimeline.videoStartsFirst()) {
            // VIDEO STARTS FIRST: Video plays from 0, GPS waits then starts
            Log.i(TAG, "Video starts first - starting video at 0, GPS will wait ${gpsDelayMs}ms")
            
            // Start video from position 0
            videoController?.seekToAndPlay(0)
            
            // Delay GPS start to match timeline
            if (gpsDelayMs > 0) {
                // Adjust GPS systemStartTime to account for the delay
                // This makes GPS "think" it started earlier, so timestamps align
                Services.location.startMockPlaybackWithDelay(context, gpsDelayMs)
            } else {
                Services.location.startMockPlayback(context)
            }
        } else {
            // GPS STARTS FIRST (or no video): GPS starts immediately, video seeks to catch up
            Log.i(TAG, "GPS starts first - starting GPS, video seeks to ${initialVideoPositionMs}ms")
            
            // Start GPS from beginning
            Services.location.startMockPlayback(context)
            
            // Start video at the calculated position to sync with GPS
            videoController?.let { vc ->
                if (initialVideoPositionMs > 0) {
                    vc.seekToAndPlay(initialVideoPositionMs.toInt())
                } else {
                    vc.seekToAndPlay(0)
                }
            }
        }
        
        // Set state to PLAYING after everything is started
        setState(PlaybackState.PLAYING)
    }
    
    /**
     * Resume from paused position
     */
    private fun resume() {
        Log.i(TAG, "Resuming playback from PAUSED state")
        
        // Unfreeze motion estimator to allow position extrapolation
        Services.location.motionEstimator.unfreeze()
        Log.i(TAG, "Motion estimator unfrozen")
        
        // Resume GPS playback
        Services.location.resumeMockPlayback()
        Log.i(TAG, "GPS resumed")
        
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
