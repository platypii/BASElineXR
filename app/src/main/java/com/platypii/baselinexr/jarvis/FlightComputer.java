package com.platypii.baselinexr.jarvis;

import android.util.Log;
import androidx.annotation.NonNull;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.PubSub.Subscriber;

/**
 * Situational awareness engine with AutoStop integration
 */
public class FlightComputer implements Subscriber<MLocation> {
    private static final String TAG = "FlightComputer";

    // Public state
    public int flightMode = FlightMode.MODE_UNKNOWN;

    // AutoStop system for landing detection
    private final AutoStop autoStop = new AutoStop();

    // Enhanced flight mode detection (for testing/logging only)
    private final EnhancedFlightMode enhancedFlightMode = new EnhancedFlightMode();
    private int lastEnhancedMode = EnhancedFlightMode.MODE_UNKNOWN;

    /**
     * Return a human readable flight mode
     */
    public String getModeString() {
        return FlightMode.getModeString(flightMode);
    }

    /**
     * Get enhanced flight mode (for testing/logging)
     */
    public int getEnhancedMode() {
        return enhancedFlightMode.getMode();
    }

    /**
     * Get enhanced flight mode instance (for testing/logging)
     */
    public EnhancedFlightMode getEnhancedFlightMode() {
        return enhancedFlightMode;
    }

    public void start() {
        // Start listening for updates
        Services.location.locationUpdates.subscribe(this);
        // Reset enhanced flight mode
        enhancedFlightMode.reset();
        // Start AutoStop system for landing detection
        autoStop.start();
    }

    @Override
    public void apply(@NonNull MLocation loc) {
        // Update flight mode using basic detection
        flightMode = FlightMode.getMode(loc);

        // Update enhanced flight mode (for testing/logging only)
        enhancedFlightMode.update(loc);

        // Log enhanced mode changes
        int enhancedMode = enhancedFlightMode.getMode();
        if (enhancedMode != lastEnhancedMode) {
            Log.i(TAG, "Enhanced mode: " + enhancedFlightMode.getModeString());
            lastEnhancedMode = enhancedMode;
        }

        // Update AutoStop system for landing detection
        autoStop.update(loc);
    }

    public void stop() {
        // Stop updates
        Services.location.locationUpdates.unsubscribe(this);
        // Stop AutoStop system
        autoStop.stop();
    }

}
