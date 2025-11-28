package com.platypii.baselinexr.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.EGL14
import android.os.Environment
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.IOException

/**
 * Manages 360° video playback with GPS time synchronization
 * Uses Android MediaPlayer with SurfaceTexture for video decoding
 */
class Video360Player(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    // Time synchronization
    private var gpsOffsetMs: Int = 0  // video_time = gps_time + offset
    private var isLoaded = false
    private var isPaused = false

    // Callback for texture updates
    var onTextureAvailable: ((SurfaceTexture) -> Unit)? = null

    /**
     * Load a 360° video from device storage (Movies folder)
     * @param videoFilename Filename of the video (e.g., "360squaw072925.mp4")
     * @param offsetMs Time offset in milliseconds (video time - GPS time)
     */
    fun loadVideo(videoFilename: String, offsetMs: Int) {
        Log.d(TAG, "Loading 360 video: $videoFilename with offset ${offsetMs}ms")

        this.gpsOffsetMs = offsetMs

        try {
            // Release any existing player
            release()

            // Get video file from Movies folder
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val videoFile = File(moviesDir, videoFilename)

            if (!videoFile.exists()) {
                Log.e(TAG, "Video file not found: ${videoFile.absolutePath}")
                return
            }

            Log.d(TAG, "Loading video from: ${videoFile.absolutePath}")

            // Create new MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                // Open video from external storage
                setDataSource(videoFile.absolutePath)

                // Configure for video playback
                setLooping(false)
                setVolume(0f, 0f) // Mute audio for now

                // Prepare asynchronously
                setOnPreparedListener { mp ->
                    Log.d(TAG, "Video prepared. Duration: ${mp.duration}ms")
                    isLoaded = true
                    isPaused = true // Start paused
                }

                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    false
                }

                setOnCompletionListener {
                    Log.d(TAG, "Video playback completed")
                }

                prepareAsync()
            }

        } catch (e: IOException) {
            Log.e(TAG, "Failed to load video: $videoFilename", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to read video: $videoFilename", e)
        }
    }

    /**
     * Create and attach SurfaceTexture for video rendering
     * This should be called when OpenGL texture is ready
     */
    fun createSurfaceTexture(textureId: Int) {
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener {
                // Notify that a new frame is available
                onTextureAvailable?.invoke(it)
            }
        }

        surface = Surface(surfaceTexture)
        mediaPlayer?.setSurface(surface)

        Log.d(TAG, "SurfaceTexture created and attached to MediaPlayer")
    }

    /**
     * Seek video to match GPS time
     * @param gpsTimeMs GPS time in milliseconds since track start
     */
    fun seekToGpsTime(gpsTimeMs: Long) {
        if (!isLoaded) return

        // Convert GPS time to video time using offset
        val videoTimeMs = gpsTimeMs + gpsOffsetMs

        // Clamp to valid range
        val duration = mediaPlayer?.duration ?: 0
        val clampedTime = videoTimeMs.coerceIn(0L, duration.toLong())

        mediaPlayer?.seekTo(clampedTime.toInt())
    }

    /**
     * Start or resume video playback
     */
    fun play() {
        if (!isLoaded) return

        mediaPlayer?.start()
        isPaused = false
        Log.d(TAG, "Video playback started")
    }

    /**
     * Pause video playback
     */
    fun pause() {
        if (!isLoaded) return

        mediaPlayer?.pause()
        isPaused = true
        Log.d(TAG, "Video playback paused")
    }

    /**
     * Update video position based on GPS time
     * Call this frequently during replay to keep video synced
     */
    fun updateSync(gpsTimeMs: Long) {
        if (!isLoaded || isPaused) return

        val videoTimeMs = gpsTimeMs + gpsOffsetMs
        val currentVideoTime = mediaPlayer?.currentPosition ?: 0

        // If drift is too large (>100ms), seek to correct position
        val drift = Math.abs(currentVideoTime - videoTimeMs)
        if (drift > 100) {
            Log.d(TAG, "Video drift detected: ${drift}ms, seeking to sync")
            seekToGpsTime(gpsTimeMs)
        }
    }

    /**
     * Update the surface texture (call this each frame)
     * @return true if texture was updated
     */
    fun updateTexture(): Boolean {
        // Check if we have a valid EGL context
        val display = EGL14.eglGetCurrentDisplay()
        val context = EGL14.eglGetCurrentContext()

        return try {
            if (display === EGL14.EGL_NO_DISPLAY || context === EGL14.EGL_NO_CONTEXT) {
                Log.w(TAG, "No EGL context current - display=$display, context=$context")
                return false
            }

            surfaceTexture?.updateTexImage()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update texture - display=$display, context=$context", e)
            false
        }
    }

    /**
     * Get current video playback position in milliseconds
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    /**
     * Get video duration in milliseconds
     */
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    /**
     * Check if video is currently playing
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    /**
     * Release all resources
     */
    fun release() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null

        surface?.release()
        surface = null

        surfaceTexture?.release()
        surfaceTexture = null

        isLoaded = false
        isPaused = false

        Log.d(TAG, "Video player released")
    }

    companion object {
        private const val TAG = "Video360Player"
    }
}
