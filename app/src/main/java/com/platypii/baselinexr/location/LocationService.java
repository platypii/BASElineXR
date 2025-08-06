package com.platypii.baselinexr.location;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.VROptions;
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

    // Mock location provider will replay an existing track
    private static final boolean useMock = VROptions.mockTrack != null;

    @NonNull
    private final BluetoothService bluetooth;

    @NonNull
    private final LocationProviderAndroid locationProviderAndroid;
    @NonNull
    public final LocationProviderBluetooth locationProviderBluetooth;
    @NonNull
    private final MockLocationProvider locationProviderMock;

    // Motion estimator for sophisticated position prediction
    public final MotionEstimator motionEstimator = new MotionEstimator(0.1);

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
        if (locationMode != LOCATION_NONE) {
            Log.e(TAG, "Location service already started");
        }
        // MOCK LOCATION
        if (useMock) {
            locationMode = LOCATION_MOCK;
            // Delay location updates because it crashes
            new Thread(() -> {
                try {
                    Thread.sleep(4000);
                    locationProviderMock.start(context);
                    locationProviderMock.locationUpdates.subscribe(this);
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

    @Override
    public long lastFixDuration() {
        if (locationMode == LOCATION_BLUETOOTH) {
            return locationProviderBluetooth.lastFixDuration();
        } else if (locationMode == LOCATION_MOCK) {
            return locationProviderMock.lastFixDuration();
        } else {
            return locationProviderAndroid.lastFixDuration();
        }
    }

    public float refreshRate() {
        if (bluetooth.preferences.preferenceEnabled) {
            return locationProviderBluetooth.refreshRate.refreshRate;
        } else {
            return locationProviderAndroid.refreshRate.refreshRate;
        }
    }

    public void permissionGranted(@NonNull Context context) {
        if (locationMode == LOCATION_BLUETOOTH) {
            locationProviderBluetooth.start(context);
        } else if (locationMode == LOCATION_ANDROID) {
            locationProviderAndroid.start(context);
        }
    }

    /**
     * Stops and then starts location services, such as when switch bluetooth on or off.
     */
    public synchronized void restart(@NonNull Context context) {
        Log.i(TAG, "Restarting location service");
        stop();
        start(context);
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
            locationProviderMock.locationUpdates.unsubscribe(this);
            locationProviderMock.stop();
        }
        locationMode = LOCATION_NONE;
        super.stop();
    }

}
