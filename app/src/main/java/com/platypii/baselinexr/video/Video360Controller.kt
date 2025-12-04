package com.platypii.baselinexr.video

import android.content.Context
import android.media.MediaPlayer
import android.os.Environment
import android.util.Log
import android.view.Surface
import com.platypii.baselinexr.Services
import com.platypii.baselinexr.VROptions
import com.platypii.baselinexr.replay.PlaybackTimeline
import com.platypii.baselinexr.replay.ReplayManager
import java.io.File

/**
 * Simplified 360° video controller using Meta Spatial SDK's VideoSurfacePanelRegistration
 * No manual GL operations needed - SDK provides the Surface directly
 */
class Video360Controller(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var videoGpsOffsetMs: Long = 0
    private var currentOptions: VROptions? = null
    private var isInitialized = false
    private var lastSeekTime: Long = 0  // Track last seek to avoid seek loops
    private var videoDurationMs: Long = 0  // Video duration for end detection
    private var isPaused = false  // Track if we paused for sleep
    private var pausePosition = 0  // Position when paused for sleep
    private var isStopped = true  // Track if we're in stopped state (don't restore positions)
    private var pendingPlayAfterSeek = false  // Track if we should start playing after seek completes
    var syncDisabledUntil: Long = 0  // Timestamp until which GPS sync is disabled (allows fresh start without being overridden)
    private var onSeekCompleteCallback: (() -> Unit)? = null  // Callback when seek completes
    
    /** Callback when video is prepared (duration known). Used by HudPanelController to reinitialize PlayControls. */
    var onVideoPrepared: ((Long) -> Unit)? = null
    
    /** Callback to request playback start. Called after video is prepared. */
    var onRequestPlay: (() -> Unit)? = null

    /**
     * Initialize 360 video system with given VR options
     * @param options VROptions containing video configuration
     */
    fun initialize(options: VROptions) {
        if (!options.has360Video()) {
            Log.d(TAG, "No 360 video configured, skipping initialization")
            return
        }

        currentOptions = options
        videoGpsOffsetMs = options.videoGpsOffsetMs?.toLong() ?: 0L

        Log.d(TAG, "Initializing 360 video system")
        Log.d(TAG, "Video file: ${options.get360VideoPath()}")
        Log.d(TAG, "GPS offset: ${videoGpsOffsetMs}ms")

        isInitialized = true
    }

    /**
     * Setup video with SDK-provided Surface
     * Called by VideoSurfacePanelRegistration surfaceConsumer
     * @param surface Surface provided by Meta Spatial SDK
     */
    fun setupVideoSurface(surface: Surface) {
        Log.d(TAG, "setupVideoSurface called, isInitialized=$isInitialized")

        if (!isInitialized) {
            Log.w(TAG, "Cannot setup video - not initialized")
            return
        }

        val videoPath = currentOptions?.get360VideoPath()
        Log.d(TAG, "Video path from options: $videoPath")
        if (videoPath == null) {
            Log.e(TAG, "No video path configured")
            return
        }

        try {
            // Create MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                // Load video from device Movies folder
                val videoFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), videoPath)
                if (!videoFile.exists()) {
                    Log.e(TAG, "Video file not found: ${videoFile.absolutePath}")
                    return
                }

                setDataSource(videoFile.absolutePath)
                setSurface(surface)  // SDK provides the Surface - no GL operations needed!

                setOnPreparedListener { mp ->
                    Log.d(TAG, "Video prepared. Duration: ${mp.duration}ms")
                    videoDurationMs = mp.duration.toLong()
                    
                    // Initialize PlaybackTimeline so we can calculate delays
                    try {
                        initializePlaybackTimeline(videoDurationMs)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize PlaybackTimeline", e)
                    }
                    
                    // Notify listeners that video is prepared (for PlayControls reinitialization)
                    onVideoPrepared?.invoke(videoDurationMs)
                    
                    // Seek video to position 0 (ready to play full video)
                    mp.seekTo(0)
                    
                    // Auto-start playback via callback to ensure proper state tracking
                    // This coordinates video + GPS with the correct timing offsets
                    Log.i(TAG, "Video prepared - requesting playback start")
                    onRequestPlay?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    true
                }
                
                setOnCompletionListener { mp ->
                    Log.d(TAG, "Video playback completed")
                    // Signal to ReplayManager that video has ended
                    ReplayManager.onVideoCompleted()
                }
                
                setOnSeekCompleteListener { mp ->
                    Log.d(TAG, "Seek completed, position=${mp.currentPosition}ms, pendingPlayAfterSeek=$pendingPlayAfterSeek")
                    if (pendingPlayAfterSeek) {
                        pendingPlayAfterSeek = false
                        mp.start()
                        Log.d(TAG, "Started playback after seek complete")
                    }
                    // Call the callback if set
                    onSeekCompleteCallback?.let { callback ->
                        onSeekCompleteCallback = null  // Clear after use
                        callback()
                    }
                }

                prepareAsync()
                isLooping = false
            }

            Log.d(TAG, "Video surface setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup video", e)
        }
    }

    /**
     * Start video playback
     */
    fun play() {
        isStopped = false
        mediaPlayer?.let { mp ->
            Log.d(TAG, "play() called, isPlaying=${mp.isPlaying}, currentPosition=${mp.currentPosition}ms")
            mp.start()
            Log.d(TAG, "play() after start(), isPlaying=${mp.isPlaying}")
        } ?: Log.e(TAG, "play() called but mediaPlayer is null!")
    }

    /**
     * Pause video playback
     */
    fun pause() {
        mediaPlayer?.let { mp ->
            pausePosition = mp.currentPosition
            mp.pause()
            // Note: isPaused is ONLY for sleep/wake, not user pause
            // MediaPlayer remembers position when paused, just call play() to resume
        }
        Log.d(TAG, "Video paused at position ${mediaPlayer?.currentPosition}ms")
    }
    
    /**
     * Stop video playback and reset to beginning.
     * Clears all pause state and seeks to first frame.
     */
    fun stop() {
        mediaPlayer?.let { mp ->
            mp.pause()
            mp.seekTo(0)
        }
        // Clear all pause state and mark as stopped
        pausePosition = 0
        isPaused = false
        isStopped = true
        Log.d(TAG, "Video stopped and reset to beginning")
    }
    
    /**
     * Pause for headset sleep - stops frame updates to surface
     * Note: Does NOT auto-resume on wake - ReplayController manages that
     */
    fun onSleep() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                pausePosition = mp.currentPosition
                mp.pause()
                isPaused = true
                Log.d(TAG, "Video paused for sleep at position ${pausePosition}ms")
            }
        }
    }
    
    /**
     * Resume from headset wake - only resumes if was playing before sleep
     * Called by ReplayController after checking if playback should resume
     */
    fun onWake() {
        // Don't auto-resume here - ReplayController decides when to resume
        // Just log the state
        if (isPaused) {
            Log.d(TAG, "Video woke from sleep, staying paused at position ${pausePosition}ms (ReplayController controls resume)")
        }
    }
    
    /**
     * Resume playback if it was paused by sleep
     * Called by ReplayController when user presses play
     * Does NOT restore position if video was stopped (isStopped=true)
     */
    fun resumeFromSleep() {
        if (isPaused && !isStopped) {
            mediaPlayer?.let { mp ->
                mp.seekTo(pausePosition)
                mp.start()
                isPaused = false
                Log.d(TAG, "Video resumed from sleep-pause at position ${pausePosition}ms")
            }
        } else if (isPaused && isStopped) {
            // Was stopped, don't restore position, just clear the paused flag
            isPaused = false
            Log.d(TAG, "Video was stopped, clearing isPaused without seeking")
        }
    }

    /**
     * Update video synchronization with video time.
     * 
     * VIDEO SYNC DESIGN:
     * - PlaybackTimeline handles GPS→Video time conversion centrally
     * - This method receives the target video time directly (ms from video start)
     * - Seeks only when drift exceeds threshold to avoid jitter
     * 
     * @param videoTimeMs Target video time in milliseconds from video start (0 = first frame)
     */
    fun updateSync(videoTimeMs: Long) {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) return
        
        // Check if sync is temporarily disabled (during fresh start)
        val now = System.currentTimeMillis()
        if (now < syncDisabledUntil) {
            Log.d(TAG, "Sync disabled for ${syncDisabledUntil - now}ms more (fresh start)")
            return
        }

        // Check current position vs target
        val currentPos = player.currentPosition.toLong()
        val drift = kotlin.math.abs(currentPos - videoTimeMs)

        // Avoid seeking too frequently - add cooldown period
        val timeSinceLastSeek = now - lastSeekTime

        // Only seek if:
        // 1. Drift is significant (> 500ms)
        // 2. We haven't seeked recently (> 1000ms cooldown to avoid seek loops)
        if (drift > 500 && timeSinceLastSeek > 1000) {
            Log.d(TAG, "Video drift ${drift}ms, seeking to ${videoTimeMs}ms (last seek ${timeSinceLastSeek}ms ago)")
            try {
                player.seekTo(videoTimeMs.toInt())
                lastSeekTime = now
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seek video", e)
            }
        }
    }

    /**
     * Check if 360 video is active
     */
    fun isActive(): Boolean {
        return isInitialized && currentOptions?.has360Video() == true
    }
    
    /**
     * Seek to a specific position
     */
    fun seekTo(positionMs: Int) {
        pendingPlayAfterSeek = false
        onSeekCompleteCallback = null
        mediaPlayer?.seekTo(positionMs)
        Log.d(TAG, "Video seeking to ${positionMs}ms")
    }
    
    /**
     * Seek to a specific position and call callback when seek completes.
     * This is useful for coordinating video with GPS - the callback can
     * start both video and GPS playback simultaneously.
     * 
     * @param positionMs Position to seek to
     * @param onComplete Callback invoked when seek is complete
     */
    fun seekWithCallback(positionMs: Int, onComplete: () -> Unit) {
        isStopped = false  // Clear stopped state since we're preparing for playback
        pendingPlayAfterSeek = false
        onSeekCompleteCallback = onComplete
        // Disable sync to prevent GPS from overriding our position
        syncDisabledUntil = System.currentTimeMillis() + 3000
        mediaPlayer?.seekTo(positionMs)
        Log.d(TAG, "Video seeking to ${positionMs}ms with callback")
    }

    /**
     * Seek to a specific position and start playing when seek completes.
     * This ensures video starts from the correct position rather than
     * the position before the async seek completes.
     * Also temporarily disables GPS sync to prevent the stale GPS time from 
     * immediately overriding the fresh start position.
     */
    fun seekToAndPlay(positionMs: Int) {
        isStopped = false  // Clear stopped state since we're starting playback
        mediaPlayer?.let { mp ->
            pendingPlayAfterSeek = true
            // Disable sync for 3 seconds to allow GPS to restart fresh
            // Without this, the stale GPS time would immediately seek the video away from position 0
            syncDisabledUntil = System.currentTimeMillis() + 3000
            mp.seekTo(positionMs)
            Log.d(TAG, "Video seeking to ${positionMs}ms, will play when seek completes (sync disabled for 3s)")
        }
    }
    
    /**
     * Get current playback position in milliseconds
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    /**
     * Get video duration in milliseconds.
     * Returns cached duration or queries MediaPlayer if available.
     */
    fun getDuration(): Long {
        // Try to get from MediaPlayer if we have one and cached value is 0
        if (videoDurationMs == 0L) {
            mediaPlayer?.let { mp ->
                if (mp.duration > 0) {
                    videoDurationMs = mp.duration.toLong()
                }
            }
        }
        return videoDurationMs
    }
    
    /**
     * Check if video playback has completed
     */
    fun isCompleted(): Boolean {
        val mp = mediaPlayer ?: return false
        return mp.currentPosition >= mp.duration - 100  // Within 100ms of end
    }
    
    /**
     * Check if video is currently playing
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
    
    /**
     * Initialize PlaybackTimeline when video is prepared.
     * This ensures the timeline is ready before video starts playing.
     * Will reinitialize if previously initialized without video data.
     */
    private fun initializePlaybackTimeline(videoDurationMs: Long) {
        Log.i(TAG, "initializePlaybackTimeline called with videoDuration=${videoDurationMs}ms")
        
        // Allow re-initialization if we now have video data and previously didn't
        if (PlaybackTimeline.isInitialized && PlaybackTimeline.hasVideo) {
            Log.d(TAG, "PlaybackTimeline already initialized with video data")
            return
        }
        
        // Use getMockLocationProviderIfConfigured to get track data before playback starts
        val mockProvider = Services.location.getMockLocationProviderIfConfigured()
        if (mockProvider == null) {
            Log.w(TAG, "Cannot initialize PlaybackTimeline - no mock provider configured (useMock not set?)")
            return
        }
        
        val gpsTrackStartMs = mockProvider.getTrackStartTime()
        val gpsTrackDuration = mockProvider.getTrackDuration()
        val gpsTrackEndMs = gpsTrackStartMs + gpsTrackDuration
        
        Log.i(TAG, "GPS track data: start=$gpsTrackStartMs, duration=$gpsTrackDuration, end=$gpsTrackEndMs")
        
        if (gpsTrackStartMs <= 0 || gpsTrackEndMs <= gpsTrackStartMs) {
            Log.w(TAG, "Cannot initialize PlaybackTimeline - no valid GPS track (start=$gpsTrackStartMs, end=$gpsTrackEndMs)")
            return
        }
        
        Log.i(TAG, "Reinitializing PlaybackTimeline with video data (duration=${videoDurationMs}ms)")
        PlaybackTimeline.initialize(
            gpsTrackStartMs = gpsTrackStartMs,
            gpsTrackEndMs = gpsTrackEndMs,
            videoDurationMs = videoDurationMs
        )
        Log.i(TAG, "PlaybackTimeline initialized from video onPrepared")
    }

    /**
     * Clean up all resources
     */
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null

        isInitialized = false
        currentOptions = null

        Log.d(TAG, "Video controller released")
    }

    companion object {
        private const val TAG = "Video360Controller"
    }
}
