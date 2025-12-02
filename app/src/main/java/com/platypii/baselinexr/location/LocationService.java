package com.platypii.baselinexr.location;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.AtmosphereSettings;
import com.platypii.baselinexr.VROptions;
import com.platypii.baselinexr.bluetooth.BluetoothService;
import com.platypii.baselinexr.jarvis.FlightComputer;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.replay.ReplayManager;
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
    private static final boolean useMock = VROptions.current.mockTrack != null || VROptions.current.mockSensor != null;

    @NonNull
    private final BluetoothService bluetooth;

    @NonNull
    private final LocationProviderAndroid locationProviderAndroid;
    @NonNull
    public final LocationProviderBluetooth locationProviderBluetooth;
    @NonNull
    private final MockLocationProvider locationProviderMock;
    
    // Sensor data provider (compass, IMU, barometer) - synchronized with GPS
    @NonNull
    public final MockSensorProvider sensorProvider;

    // Motion estimator for sophisticated position prediction
    public final MotionEstimator motionEstimator = new KalmanFilter3D();
    
    /**
     * Get the mock location provider for GPS track playback.
     * Used for seekbar position tracking during replay.
     */
    @Nullable
    public MockLocationProvider getMockLocationProvider() {
        return locationMode == LOCATION_MOCK ? locationProviderMock : null;
    }

    public LocationService(@NonNull BluetoothService bluetooth) {
        this.bluetooth = bluetooth;
        locationProviderAndroid = new LocationProviderAndroid();
        locationProviderBluetooth = new LocationProviderBluetooth(bluetooth);
        locationProviderMock = new MockLocationProvider();
        sensorProvider = new MockSensorProvider();
    }


    @Override
    public void apply(MLocation loc) {
        // Initialize temperature offset on first GPS fix
        if (!TemperatureEstimator.INSTANCE.getHasInitializedOffset()) {
            // Only initialize if we have valid GPS data
            if (loc.millis > 0 && !Double.isNaN(loc.latitude) && !Double.isNaN(loc.longitude)) {
                initializeTemperatureOffset(loc);
            } else {
                Log.w(TAG, "Skipping temperature init - invalid GPS data: millis=" + loc.millis + 
                    ", lat=" + loc.latitude + ", lon=" + loc.longitude);
            }
        }

        // Update motion estimator with new GPS data
        motionEstimator.update(loc);

        // Re-post location update
        updateLocation(loc);
    }

    /**
     * Initialize the temperature offset using GPS-based estimation.
     * Called once on the first GPS fix.
     */
    private void initializeTemperatureOffset(MLocation loc) {
        // Get the original GPS time (before playback time adjustment)
        long originalGpsTime = getOriginalGpsTime(loc.millis);
        Log.i(TAG, "Temperature estimation: adjusted time=" + loc.millis + ", original time=" + originalGpsTime);

        // Create a location with the original timestamp for estimation
        MLocation originalLoc = new MLocation(
            originalGpsTime, loc.latitude, loc.longitude, loc.altitude_gps,
            loc.climb, loc.vN, loc.vE, loc.hAcc, loc.pdop, loc.hdop, loc.vdop,
            loc.satellitesUsed, loc.satellitesInView
        );

        // Calculate and set the estimated offset using original recording time
        float estimatedOffset = TemperatureEstimator.INSTANCE.calculateIsaOffset(originalLoc);
        AtmosphereSettings.INSTANCE.setTemperatureOffsetC(estimatedOffset);

        Log.i(TAG, String.format("Initialized temperature offset from GPS: %.2f°C (%.2f°F)",
            estimatedOffset, estimatedOffset * 9f / 5f));

        // TEMPORARY DEBUG: Log 24-hour temperature profile using original time
        TemperatureEstimator.INSTANCE.logDailyTemperatureProfile(originalLoc);
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
            return;
        }
        // MOCK LOCATION
        if (useMock) {
            // Check if replay is in progress (started but not completed)
            // If so, don't auto-start from Services.start() - let ReplayController manage it
            if (ReplayManager.INSTANCE.getHasStarted() && 
                !ReplayManager.INSTANCE.getGpsCompleted() && 
                !ReplayManager.INSTANCE.isReadyToRestart()) {
                // GPS playback is in progress or was manually stopped - don't interfere
                Log.i(TAG, "Replay in progress or stopped, skipping auto GPS start");
                return;
            }
            
            locationMode = LOCATION_MOCK;
            // Delay location updates because it crashes
            new Thread(() -> {
                try {
                    Thread.sleep(4000);
                    Log.i(TAG, String.format("TIMESYNC: Starting GPS provider after 4s delay at time=%d", System.currentTimeMillis()));
                    locationProviderMock.start(context);
                    locationProviderMock.locationUpdates.subscribe(this);
                    
                    // Start sensor data provider if mockSensor is specified
                    // Use GPS track start time as reference to ensure timestamps align
                    if (VROptions.current.mockSensor != null) {
                        Log.i(TAG, String.format("TIMESYNC: Starting sensor provider at time=%d", System.currentTimeMillis()));
                        long trackStartTime = locationProviderMock.getTrackStartTime();
                        sensorProvider.start(context, trackStartTime);
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

    /**
     * Get the original track start time (first GPS timestamp from the recording).
     * This is the actual GPS time before any time shifting for playback.
     * Returns 0 if not in mock mode or no track loaded.
     */
    public long getOriginalTrackStartTime() {
        if (locationMode == LOCATION_MOCK) {
            return locationProviderMock.getTrackStartTime();
        }
        return 0;
    }

    /**
     * Get the original GPS time for a given adjusted location timestamp.
     * Converts from playback-adjusted time back to original recording time.
     * @param adjustedMillis The adjusted timestamp (as seen in lastLoc.millis during playback)
     * @return Original GPS time from the recording, or adjustedMillis if not in mock mode
     */
    public long getOriginalGpsTime(long adjustedMillis) {
        if (locationMode == LOCATION_MOCK) {
            long trackStartTime = locationProviderMock.getTrackStartTime();
            if (trackStartTime > 0) {
                // Reverse the time adjustment: original = adjusted - timeDelta
                // where timeDelta = systemStartTime - trackStartTime
                long timeDelta = MockLocationProvider.systemStartTime - trackStartTime;
                return adjustedMillis - timeDelta;
            }
        }
        return adjustedMillis;
    }

    /**
     * Returns the number of milliseconds since the last fix.
     * Returns 0 when motion estimator is frozen (paused/stopped playback).
     * This keeps GPS data appearing "fresh" so systems don't hide/fade.
     */
    @Override
    public long lastFixDuration() {
        if (motionEstimator.isFrozen()) {
            return 0;  // Always fresh when frozen
        }
        return locationProvider().lastFixDuration();
    }

    /**
     * Get the effective current time for freshness calculations (GPS time base).
     * 
     * TIME SYNC DESIGN:
     * MockLocationProvider shifts GPS timestamps forward after pause/resume by adjusting
     * systemStartTime. This keeps GPS timestamps synchronized with wall clock time.
     * Therefore, we return raw GPS time here - no pause adjustment needed.
     * 
     * Charts use this for freshness: (getEffectiveCurrentTime() - loc.millis <= window)
     * Since both sides use the same time base, this check works correctly.
     */
    public long getEffectiveCurrentTime() {
        return TimeOffset.phoneToGpsTime(System.currentTimeMillis());
    }

    /**
     * Get the effective current time for prediction calculations (phone time base).
     * See getEffectiveCurrentTime() for TIME SYNC DESIGN explanation.
     */
    public long getEffectivePhoneTime() {
        return System.currentTimeMillis();
    }

    public float refreshRate() {
        return locationProvider().refreshRate.refreshRate;
    }
    
    /**
     * Check if we're in mock/replay mode
     */
    public boolean isMockMode() {
        return locationMode == LOCATION_MOCK;
    }
    
    /**
     * Pause mock GPS playback (can resume later)
     */
    public void pauseMockPlayback() {
        if (locationMode == LOCATION_MOCK) {
            Log.i(TAG, "Pausing mock GPS playback");
            locationProviderMock.pause();
            sensorProvider.pause();
        }
    }

    /**
     * Resume mock GPS playback from paused position
     */
    public void resumeMockPlayback() {
        if (locationMode == LOCATION_MOCK) {
            Log.i(TAG, "Resuming mock GPS playback");
            locationProviderMock.resume();
            sensorProvider.resume();
        }
    }
    
    /**
     * Stop mock GPS playback
     */
    public void stopMockPlayback() {
        if (locationMode == LOCATION_MOCK) {
            Log.i(TAG, "Stopping mock GPS playback");
            locationProviderMock.stop();
            sensorProvider.stop();
        }
    }
    
    /**
     * Seek mock GPS playback to a specific timestamp.
     * @param targetGpsTimeMs The target GPS timestamp to seek to
     * @param resumeAfterSeek Whether to resume playback after seeking
     */
    public void seekMockPlayback(long targetGpsTimeMs, boolean resumeAfterSeek) {
        if (locationMode == LOCATION_MOCK) {
            Log.i(TAG, "Seeking mock GPS playback to " + targetGpsTimeMs);
            locationProviderMock.seekTo(targetGpsTimeMs, resumeAfterSeek);
            // TODO: Seek sensor data to match GPS time
        }
    }
    
    /**
     * Start mock GPS playback (from beginning)
     */
    public void startMockPlayback(@NonNull Context context) {
        if (useMock) {
            Log.i(TAG, "Starting mock GPS playback, locationMode=" + locationMode);
            if (locationMode == LOCATION_MOCK) {
                // Already in mock mode - restart
                locationProviderMock.restart(context);
                if (VROptions.current.mockSensor != null) {
                    long trackStartTime = locationProviderMock.getTrackStartTime();
                    sensorProvider.restart(context, trackStartTime);
                }
            } else {
                // Not in mock mode yet - initialize and start
                Log.i(TAG, "Not in mock mode, calling start()");
                locationMode = LOCATION_MOCK;
                locationProviderMock.restart(context);
                locationProviderMock.locationUpdates.subscribe(this);
                if (VROptions.current.mockSensor != null) {
                    long trackStartTime = locationProviderMock.getTrackStartTime();
                    sensorProvider.start(context, trackStartTime);
                }
            }
        }
    }
    
    /**
     * Start mock GPS playback with a delay.
     * Used when video starts before GPS on the shared timeline.
     * The delay is applied to systemStartTime so GPS timestamps align correctly.
     * 
     * @param context Application context
     * @param delayMs Milliseconds to delay GPS start (shifts systemStartTime forward)
     */
    public void startMockPlaybackWithDelay(@NonNull Context context, long delayMs) {
        if (useMock) {
            Log.i(TAG, "Starting mock GPS playback with delay: " + delayMs + "ms, locationMode=" + locationMode);
            if (locationMode == LOCATION_MOCK) {
                // Already in mock mode - restart with delay
                locationProviderMock.restartWithDelay(context, delayMs);
                if (VROptions.current.mockSensor != null) {
                    long trackStartTime = locationProviderMock.getTrackStartTime();
                    sensorProvider.restart(context, trackStartTime);
                }
            } else {
                // Not in mock mode yet - initialize and start with delay
                Log.i(TAG, "Not in mock mode, initializing mock mode with delay");
                locationMode = LOCATION_MOCK;
                locationProviderMock.restartWithDelay(context, delayMs);
                locationProviderMock.locationUpdates.subscribe(this);
                if (VROptions.current.mockSensor != null) {
                    long trackStartTime = locationProviderMock.getTrackStartTime();
                    sensorProvider.start(context, trackStartTime);
                }
            }
        }
    }
    
    /**
     * Check if mock GPS playback has completed
     */
    public boolean isMockPlaybackCompleted() {
        if (locationMode == LOCATION_MOCK) {
            return locationProviderMock.isCompleted();
        }
        return true;
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
            sensorProvider.stop();
        }
        locationMode = LOCATION_NONE;
        super.stop();
    }

}
