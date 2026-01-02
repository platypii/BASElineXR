package com.platypii.baselinexr.location;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.MockTrackOptions;
import com.platypii.baselinexr.bluetooth.BluetoothService;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.PubSub.Subscriber;

/**
 * Meta location provider that uses bluetooth or android location source
 */
public class LocationService extends LocationProvider implements Subscriber<MLocation> {
    private static final String TAG = "LocationService";

    // What data source to pull from
    private static final int LOCATION_NONE = 0;
    private static final int LOCATION_ANDROID = 1;
    private static final int LOCATION_BLUETOOTH = 2;
    private static final int LOCATION_MOCK = 3;
    private int locationMode = LOCATION_NONE;

    // Generation counter to detect stale delayed threads
    private int startGeneration = 0;

    // Context for restart functionality
    private Context appContext;

    @NonNull
    private final BluetoothService bluetooth;

    @NonNull
    private final LocationProviderAndroid locationProviderAndroid;
    @NonNull
    public final LocationProviderBluetooth locationProviderBluetooth;
    @NonNull
    private final MockLocationProvider locationProviderMock;

    // Motion estimator for sophisticated position prediction
    public final MotionEstimator motionEstimator = new KalmanFilter3D();

    public LocationService(@NonNull BluetoothService bluetooth) {
        this.bluetooth = bluetooth;
        locationProviderAndroid = new LocationProviderAndroid();
        locationProviderBluetooth = new LocationProviderBluetooth(bluetooth);
        locationProviderMock = new MockLocationProvider();
    }


    @Override
    public void apply(MLocation loc) {
        // Update motion estimator with new GPS data
        motionEstimator.update(loc);

        // Re-post location update
        updateLocation(loc);
    }

    @NonNull
    @Override
    protected String providerName() {
        return TAG;
    }

    @NonNull
    @Override
    public String dataSource() {
        // TODO: Baro?
        if (locationMode == LOCATION_ANDROID) {
            return Build.MANUFACTURER + " " + Build.MODEL;
        } else if (locationMode == LOCATION_BLUETOOTH) {
            return locationProviderBluetooth.dataSource();
        } else if (locationMode == LOCATION_MOCK) {
            return locationProviderMock.dataSource();
        } else {
            return "None";
        }
    }

    @Override
    public void start(@NonNull Context context) {
        this.appContext = context;
        if (locationMode != LOCATION_NONE) {
            Log.e(TAG, "Location service already started");
        }
        startGeneration++;
        final int myGeneration = startGeneration;
        // Check if mock mode should be used (evaluated dynamically based on current MockTrackOptions)
        final boolean useMock = MockTrackOptions.current != null;
        // MOCK LOCATION
        if (useMock) {
            locationMode = LOCATION_MOCK;
            // Delay location updates because it crashes
            new Thread(() -> {
                try {
                    Thread.sleep(4000);
                    if (locationMode == LOCATION_MOCK && startGeneration == myGeneration) {
                        locationProviderMock.start(context);
                        locationProviderMock.locationUpdates.subscribe(this);
                    }
                } catch (InterruptedException ignored) {
                }
            }).start();
        } else if (bluetooth.preferences.preferenceEnabled) {
            // Start bluetooth location service
            locationMode = LOCATION_BLUETOOTH;
            locationProviderBluetooth.start(context);
            locationProviderBluetooth.locationUpdates.subscribe(this);
        } else {
            // Start android location service
            locationMode = LOCATION_ANDROID;
            locationProviderAndroid.start(context);
            locationProviderAndroid.locationUpdates.subscribe(this);
        }
    }

    private LocationProvider locationProvider() {
        if (locationMode == LOCATION_BLUETOOTH) {
            return locationProviderBluetooth;
        } else if (locationMode == LOCATION_MOCK) {
            return locationProviderMock;
        } else {
            return locationProviderAndroid;
        }
    }

    @Override
    public long lastFixDuration() {
        return locationProvider().lastFixDuration();
    }

    public float refreshRate() {
        return locationProvider().refreshRate.refreshRate;
    }

    @Override
    public void stop() {
        if (locationMode == LOCATION_ANDROID) {
            // Stop android location service
            locationProviderAndroid.locationUpdates.unsubscribe(this);
            locationProviderAndroid.stop();
        } else if (locationMode == LOCATION_BLUETOOTH) {
            // Stop bluetooth location service
            locationProviderBluetooth.locationUpdates.unsubscribe(this);
            locationProviderBluetooth.stop();
        } else if (locationMode == LOCATION_MOCK) {
            // Only unsubscribe if mock was actually started (it has a 4s delay)
            if (locationProviderMock.started) {
                locationProviderMock.locationUpdates.unsubscribe(this);
            }
            locationProviderMock.stop();
        }
        locationMode = LOCATION_NONE;
        super.stop();
    }

    /**
     * Restart the location service with the current VROptions.
     * This is useful when switching between Live and Replay modes.
     */
    public void restart() {
        if (appContext == null) {
            Log.e(TAG, "Cannot restart: no context available");
            return;
        }
        Log.i(TAG, "Restarting location service");
        stop();
        start(appContext);
    }

}
