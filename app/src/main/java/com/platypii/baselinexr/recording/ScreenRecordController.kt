package com.platypii.baselinexr.recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.platypii.baselinexr.R
import java.io.File

/**
 * Controller for screen recording using MediaProjection API.
 * Uses ScreenRecordService for foreground service requirement.
 * 
 * Note: MediaProjection permission cannot be persisted - Android requires
 * user consent each time for security reasons. This is by design.
 */
class ScreenRecordController(private val activity: Activity) {
    
    companion object {
        private const val TAG = "ScreenRecordController"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        private const val DEBOUNCE_MS = 500L  // Prevent double-clicks
    }
    
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var recordButton: Button? = null
    private var lastClickTime = 0L
    private var isRequestingPermission = false
    
    /**
     * Initialize the controller. Call from Activity onCreate.
     */
    fun initialize() {
        mediaProjectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        
        // Listen for recording state changes from service
        ScreenRecordService.onRecordingStateChanged = { isRecording, file ->
            activity.runOnUiThread {
                updateButtonState()
                if (!isRecording && file != null) {
                    Toast.makeText(activity, "Recording saved: ${file.name}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * Set the record button to update its UI state
     */
    fun setRecordButton(button: Button) {
        recordButton = button
        updateButtonState()
        
        button.setOnClickListener {
            toggleRecording()
        }
    }
    
    /**
     * Toggle recording state with debouncing
     */
    fun toggleRecording() {
        // Debounce to prevent double-clicks
        val now = System.currentTimeMillis()
        if (now - lastClickTime < DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring click (debounce)")
            return
        }
        lastClickTime = now
        
        // Don't allow toggling while permission dialog is showing
        if (isRequestingPermission) {
            Log.d(TAG, "Ignoring click (permission dialog showing)")
            return
        }
        
        if (ScreenRecordService.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * Start screen recording - requests permission if needed
     */
    @Suppress("DEPRECATION")
    fun startRecording() {
        if (ScreenRecordService.isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        if (isRequestingPermission) {
            Log.w(TAG, "Already requesting permission")
            return
        }
        
        Log.i(TAG, "Requesting MediaProjection permission")
        isRequestingPermission = true
        val intent = mediaProjectionManager?.createScreenCaptureIntent()
        if (intent != null) {
            activity.startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
        } else {
            Log.e(TAG, "Cannot start recording - not initialized properly")
            isRequestingPermission = false
            Toast.makeText(activity, "Recording not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Stop screen recording
     */
    fun stopRecording() {
        Log.i(TAG, "Stopping recording via service")
        val intent = Intent(activity, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        activity.startService(intent)
        updateButtonState()
    }
    
    /**
     * Handle activity result from MediaProjection permission request.
     * Call this from your Activity's onActivityResult.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE_SCREEN_CAPTURE) {
            return false
        }
        
        // Permission dialog is done
        isRequestingPermission = false
        
        if (resultCode == Activity.RESULT_OK && data != null) {
            Log.i(TAG, "MediaProjection permission granted, starting service")
            
            // Start the foreground service with the projection data
            val serviceIntent = Intent(activity, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_START
                putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenRecordService.EXTRA_RESULT_DATA, data)
            }
            activity.startForegroundService(serviceIntent)
            
            Toast.makeText(activity, "Recording started", Toast.LENGTH_SHORT).show()
            updateButtonState()
        } else {
            Log.w(TAG, "MediaProjection permission denied")
            Toast.makeText(activity, "Screen recording permission denied", Toast.LENGTH_SHORT).show()
        }
        
        return true
    }
    
    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = ScreenRecordService.isRecording
    
    /**
     * Update button appearance based on recording state
     */
    private fun updateButtonState() {
        recordButton?.let { button ->
            Log.d(TAG, "Updating button state: isRecording=${ScreenRecordService.isRecording}")
            if (ScreenRecordService.isRecording) {
                button.text = "⏹ Stop"
                button.setBackgroundResource(R.drawable.button_bg_recording)
            } else {
                button.text = "⏺ Rec"
                button.setBackgroundResource(R.drawable.button_bg)
            }
        }
    }
    
    /**
     * Release resources when done
     */
    fun release() {
        if (ScreenRecordService.isRecording) {
            stopRecording()
        }
        ScreenRecordService.onRecordingStateChanged = null
    }
}
