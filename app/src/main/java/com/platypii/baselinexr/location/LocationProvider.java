package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.PubSub;
import com.platypii.baselinexr.util.RefreshRateEstimator;

public abstract class LocationProvider {
    // Duration until location considered stale, in milliseconds
    private static final long LOCATION_TTL = 5000;

    // Moving average of refresh rate in Hz
    public final RefreshRateEstimator refreshRate = new RefreshRateEstimator();

    // History
    public MLocation lastLoc; // last location received

    public final PubSub<MLocation> locationUpdates = new PubSub<>();

    /**
     * Give a useful name to the inherited provider
     */
    @NonNull
    protected abstract String providerName();

    /**
     * String description of the GPS device
     */
    @NonNull
    protected abstract String dataSource();

    /**
     * Start location updates
     *
     * @param context The Application context
     */
    public abstract void start(@NonNull Context context);

    /**
     * Returns the number of milliseconds since the last fix
     */
    public long lastFixDuration() {
        if (lastLoc != null && lastLoc.millis > 0) {
            final long duration = System.currentTimeMillis() - TimeOffset.gpsToPhoneTime(lastLoc.millis);
            if (duration < 0) {
                Log.w(providerName(), "Time since last fix should never be negative delta = " + duration + "ms");
            }
            return duration;
        } else {
            return -1;
        }
    }

    /**
     * Returns whether the last location fix is recent
     */
    public boolean isFresh() {
        return lastLoc != null && lastFixDuration() < LOCATION_TTL;
    }

    /**
     * Children should call updateLocation() when they have new location information
     */
    void updateLocation(@NonNull MLocation loc) {
//        Log.v(providerName(), "LocationProvider.updateLocation(" + loc + ")");

        // Check for duplicate
        if (lastLoc != null && lastLoc.equals(loc)) {
            Log.w(providerName(), "Skipping duplicate location " + loc);
            return;
        }
        // Check for negative time delta between locations
        if (lastLoc != null && loc.millis < lastLoc.millis) {
            Log.e(providerName(), "Non-monotonic time delta: " + loc.millis + " - " + lastLoc.millis + " = " + (loc.millis - lastLoc.millis) + " ms");
        }

        // Store location
        lastLoc = loc;

        // Update gps time offset
        // TODO: What if there are multiple GPS devices giving different times?
        TimeOffset.update(providerName(), lastLoc.millis);

        refreshRate.addSample(lastLoc.millis);

        // Notify listeners (async so the service never blocks!)
        locationUpdates.postAsync(lastLoc);
    }

    public void stop() {
        if (!locationUpdates.isEmpty()) {
            Log.w(providerName(), "Stopping location service, but listeners are still listening");
        }
    }
}
