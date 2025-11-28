package com.platypii.baselinexr.video

import android.content.Context
import android.media.MediaPlayer
import android.os.Environment
import android.util.Log
import android.view.Surface
import com.platypii.baselinexr.VROptions
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
                    mp.start()  // Start playback immediately
                    Log.d(TAG, "Video playback started")
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    true
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
        mediaPlayer?.start()
    }

    /**
     * Pause video playback
     */
    fun pause() {
        mediaPlayer?.pause()
    }

    /**
     * Update video synchronization with GPS time
     * @param gpsTimeMs GPS time in milliseconds since track start
     */
    fun updateSync(gpsTimeMs: Long) {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) return

        // Convert GPS time to video time
        val videoTimeMs = gpsTimeMs + videoGpsOffsetMs

        // Check current position
        val currentPos = player.currentPosition.toLong()
        val drift = kotlin.math.abs(currentPos - videoTimeMs)

        // Avoid seeking too frequently - add cooldown period
        val now = System.currentTimeMillis()
        val timeSinceLastSeek = now - lastSeekTime

        // Only seek if:
        // 1. Drift is significant (> 500ms instead of 100ms for more tolerance)
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
