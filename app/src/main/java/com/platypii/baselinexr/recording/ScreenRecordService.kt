package com.platypii.baselinexr.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.platypii.baselinexr.BaselineActivity
import com.platypii.baselinexr.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground service for screen recording using MediaProjection.
 * Required for Android 10+ to use MediaProjection.
 */
class ScreenRecordService : Service() {
    
    companion object {
        private const val TAG = "ScreenRecordService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_record_channel"
        
        // Recording resolution - square 1:1 aspect ratio to capture full VR view including HUD
        // Quest 3 renders at ~1800x1920 per eye, we use a square format to get all HUD elements
        private const val VIDEO_WIDTH = 2048
        private const val VIDEO_HEIGHT = 2048  // Square 1:1 to capture full FOV
        private const val VIDEO_BITRATE = 20_000_000  // 20 Mbps for higher res
        private const val VIDEO_FRAME_RATE = 30
        
        // Intent extras
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_START = "com.platypii.baselinexr.recording.START"
        const val ACTION_STOP = "com.platypii.baselinexr.recording.STOP"
        
        // Static state for checking from activity
        var isRecording = false
            private set
        var currentOutputFile: File? = null
            private set
            
        // Callback for recording state changes
        var onRecordingStateChanged: ((Boolean, File?) -> Unit)? = null
    }
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                
                if (resultData != null) {
                    startForegroundWithNotification()
                    startRecording(resultCode, resultData)
                } else {
                    Log.e(TAG, "No result data provided")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when screen recording is active"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun startForegroundWithNotification() {
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording Screen")
            .setContentText("Tap to stop recording")
            .setSmallIcon(R.drawable.wingsuit)
            .setOngoing(true)
            .setContentIntent(stopPendingIntent)
            .addAction(Notification.Action.Builder(
                null, "Stop", stopPendingIntent
            ).build())
            .build()
        
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
        
        Log.i(TAG, "Started foreground service with notification")
    }
    
    private fun startRecording(resultCode: Int, resultData: Intent) {
        try {
            Log.i(TAG, "Starting recording...")
            
            // Get MediaProjection
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, resultData)
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to get MediaProjection")
                stopSelf()
                return
            }
            
            // Setup callback for projection ending
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    Handler(Looper.getMainLooper()).post {
                        stopRecording()
                        stopSelf()
                    }
                }
            }, Handler(Looper.getMainLooper()))
            
            // Create output file
            val outputDir = getOutputDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            currentOutputFile = File(outputDir, "BASElineXR_$timestamp.mp4")
            
            Log.i(TAG, "Recording to: ${currentOutputFile?.absolutePath}")
            
            // Setup MediaRecorder
            mediaRecorder = MediaRecorder(this).apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoEncodingBitRate(VIDEO_BITRATE)
                setOutputFile(currentOutputFile?.absolutePath)
                prepare()
            }
            
            // Get screen density
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            
            // Create virtual display
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                VIDEO_WIDTH,
                VIDEO_HEIGHT,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )
            
            // Start recording
            mediaRecorder?.start()
            isRecording = true
            
            Log.i(TAG, "Recording started successfully")
            
            // Notify listeners
            onRecordingStateChanged?.invoke(true, currentOutputFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            stopSelf()
        }
    }
    
    private fun stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not recording")
            return
        }
        
        Log.i(TAG, "Stopping recording...")
        
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        }
        
        val savedFile = currentOutputFile
        cleanup()
        isRecording = false
        
        Log.i(TAG, "Recording saved to: ${savedFile?.absolutePath}")
        
        // Notify listeners
        onRecordingStateChanged?.invoke(false, savedFile)
    }
    
    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        
        mediaRecorder?.release()
        mediaRecorder = null
        
        mediaProjection?.stop()
        mediaProjection = null
    }
    
    private fun getOutputDirectory(): File {
        // Try external Movies directory first
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val baselineDir = File(moviesDir, "BASElineXR")
        
        if (!baselineDir.exists()) {
            baselineDir.mkdirs()
        }
        
        if (baselineDir.canWrite()) {
            return baselineDir
        }
        
        // Fallback to app's external files directory
        val appMoviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (appMoviesDir != null && !appMoviesDir.exists()) {
            appMoviesDir.mkdirs()
        }
        
        return appMoviesDir ?: filesDir
    }
    
    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        if (isRecording) {
            stopRecording()
        }
        cleanup()
        super.onDestroy()
    }
}
