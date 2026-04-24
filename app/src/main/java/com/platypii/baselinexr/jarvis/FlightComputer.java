package com.platypii.baselinexr.jarvis;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.PubSub.Subscriber;

/**
 * Situational awareness engine
 */
public class FlightComputer implements Subscriber<MLocation> {
    private static final String TAG = "FlightComputer";

    // Public state
    public int flightMode = FlightMode.MODE_UNKNOWN;

    /**
     * Return a human readable flight mode
     */
    public String getModeString() {
        return FlightMode.getModeString(flightMode);
    }

    public void start() {
        // Start listening for updates
        Services.location.locationUpdates.subscribe(this);
    }

    @Override
    public void apply(@NonNull MLocation loc) {
        // Update flight mode
        flightMode = FlightMode.getMode(loc);
    }

    public void stop() {
        // Stop updates
        Services.location.locationUpdates.unsubscribe(this);
    }

}
