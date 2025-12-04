package com.platypii.baselinexr.replay

/**
 * Manages replay state for synchronized 360 video + GPS track playback.
 * 
 * Design:
 * - In replay mode, video and GPS play through independently
 * - When BOTH have completed, the system is ready to restart
 * - On next activity start (headset wake), if both completed, restart both together
 * - Live mode (no mockTrack/mockSensor) works as before with no special handling
 */
object ReplayManager {
    private const val TAG = "ReplayManager"
    
    /** Whether we're in replay mode (mockTrack or mockSensor configured) */
    val isReplayMode: Boolean
        get() = com.platypii.baselinexr.VROptions.current.mockTrack != null || 
                com.platypii.baselinexr.VROptions.current.mockSensor != null
    
    /** Whether we have 360 video configured */
    val hasVideo: Boolean
        get() = com.platypii.baselinexr.VROptions.current.has360Video()
    
    /** Track completion status */
    @Volatile
    var gpsCompleted: Boolean = false
        private set
    
    /** Video completion status */
    @Volatile
    var videoCompleted: Boolean = false
        private set
    
    /** Whether replay has started at least once */
    @Volatile
    var hasStarted: Boolean = false
        private set
    
    /** Callback when GPS starts (to sync ReplayController state) */
    var onGpsStartedCallback: (() -> Unit)? = null
    
    /** Callback when playback completes (both GPS and video if present) */
    var onPlaybackCompletedCallback: (() -> Unit)? = null
    
    /**
     * Check if everything has completed and we're ready to restart
     */
    fun isReadyToRestart(): Boolean {
        if (!isReplayMode) return false
        if (!hasStarted) return false
        
        // If we have video, both must be complete. Otherwise just GPS.
        return if (hasVideo) {
            gpsCompleted && videoCompleted
        } else {
            gpsCompleted
        }
    }
    
    /**
     * Mark GPS playback as started
     */
    fun onGpsStarted() {
        android.util.Log.i(TAG, "GPS playback started")
        hasStarted = true
        gpsCompleted = false
        // Unfreeze motion estimator when GPS starts - allows extrapolation
        com.platypii.baselinexr.Services.location.motionEstimator.unfreeze()
        onGpsStartedCallback?.invoke()
    }
    
    /**
     * Mark GPS playback as completed
     */
    fun onGpsCompleted() {
        android.util.Log.i("BXRINPUT", "GPS playback completed - gpsCompleted=true, videoCompleted=$videoCompleted, hasVideo=$hasVideo")
        gpsCompleted = true
        // Freeze motion estimator when GPS ends - prevents extrapolation while video continues
        com.platypii.baselinexr.Services.location.motionEstimator.freeze()
        checkReadyToRestart()
    }
    
    /**
     * Mark video playback as started
     */
    fun onVideoStarted() {
        android.util.Log.i("BXRINPUT", "Video playback started")
        videoCompleted = false
    }
    
    /**
     * Mark video playback as completed
     */
    fun onVideoCompleted() {
        android.util.Log.i("BXRINPUT", "Video playback completed - gpsCompleted=$gpsCompleted, videoCompleted=true")
        videoCompleted = true
        checkReadyToRestart()
    }
    
    /**
     * Check if both components are done and notify listener
     */
    private fun checkReadyToRestart() {
        val ready = isReadyToRestart()
        android.util.Log.i("BXRINPUT", "checkReadyToRestart: ready=$ready, gpsCompleted=$gpsCompleted, videoCompleted=$videoCompleted, hasVideo=$hasVideo")
        if (ready) {
            android.util.Log.i(TAG, "Both GPS and video completed - auto-stopping and resetting")
            onPlaybackCompletedCallback?.invoke()
        }
    }
    
    /**
     * Prepare for restart (called before restarting playback)
     */
    fun prepareForRestart() {
        android.util.Log.i(TAG, "Preparing for restart")
        gpsCompleted = false
        videoCompleted = false
        // hasStarted stays true
    }
    
    /**
     * Reset all state (called on activity destroy)
     */
    fun reset() {
        android.util.Log.i(TAG, "Replay manager reset")
        gpsCompleted = false
        videoCompleted = false
        hasStarted = false
        onGpsStartedCallback = null
        onPlaybackCompletedCallback = null
    }
}
