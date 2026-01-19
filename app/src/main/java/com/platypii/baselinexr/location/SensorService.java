package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.MockTrackOptions;
import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.util.PubSub;
import com.platypii.baselinexr.util.PubSub.Subscriber;

/**
 * Meta sensor provider that uses mock or bluetooth sensor source.
 * Parallel to LocationService but for IMU/MAG/BARO sensor data.
 */
public class SensorService implements Subscriber<MImuData> {
    private static final String TAG = "SensorService";

    // What data source to pull from
    private static final int SENSOR_NONE = 0;
    private static final int SENSOR_MOCK = 1;
    private static final int SENSOR_BLUETOOTH = 2;
    private int sensorMode = SENSOR_NONE;

    // Generation counter to detect stale delayed threads
    private int startGeneration = 0;

    // Context for restart functionality
    private Context appContext;

    @NonNull
    private final MockSensorProvider mockSensorProvider;

    // Future: BluetoothSensorProvider

    // Rotation estimator for orientation
    public final RotationEstimator rotationEstimator = new SimpleRotationEstimator();

    // PubSub for re-publishing sensor updates (after processing)
    public final PubSub<MImuData> imuUpdates = new PubSub<>();
    public final PubSub<MMagData> magUpdates = new PubSub<>();
    public final PubSub<MBaroData> baroUpdates = new PubSub<>();

    // Subscribers for internal routing
    private final Subscriber<MMagData> magSubscriber = this::onMagUpdate;
    private final Subscriber<MBaroData> baroSubscriber = this::onBaroUpdate;

    public SensorService() {
        mockSensorProvider = new MockSensorProvider();
    }

    @Override
    public void apply(MImuData imu) {
        // Update rotation estimator
        rotationEstimator.updateImu(imu);

        // Re-post to subscribers
        imuUpdates.post(imu);
    }

    private void onMagUpdate(MMagData mag) {
        // Update rotation estimator
        rotationEstimator.updateMag(mag);

        // Re-post to subscribers
        magUpdates.post(mag);
    }

    private void onBaroUpdate(MBaroData baro) {
        // Re-post to subscribers
        baroUpdates.post(baro);
    }

    @NonNull
    public String providerName() {
        return TAG;
    }

    @NonNull
    public String dataSource() {
        if (sensorMode == SENSOR_MOCK) {
            return mockSensorProvider.dataSource();
        } else if (sensorMode == SENSOR_BLUETOOTH) {
            return "Bluetooth";
        } else {
            return "None";
        }
    }

    public void start(@NonNull Context context) {
        this.appContext = context;
        if (sensorMode != SENSOR_NONE) {
            Log.e(TAG, "Sensor service already started");
        }
        startGeneration++;
        final int myGeneration = startGeneration;

        // Check if mock mode should be used
        final boolean useMock = MockTrackOptions.current != null;

        if (useMock) {
            sensorMode = SENSOR_MOCK;
            // Start sensor provider on background thread (it will wait for PlaybackTimeline if needed)
            new Thread(() -> {
                if (sensorMode == SENSOR_MOCK && startGeneration == myGeneration) {
                    mockSensorProvider.start(context);
                    // Subscribe to sensor updates
                    mockSensorProvider.imuUpdates.subscribe(this);
                    mockSensorProvider.magUpdates.subscribe(magSubscriber);
                    mockSensorProvider.baroUpdates.subscribe(baroSubscriber);
                    Log.i(TAG, "Mock sensor provider started");
                }
            }).start();
        } else {
            // Future: Bluetooth sensor mode
            sensorMode = SENSOR_NONE;
            Log.i(TAG, "No sensor source available (live mode not implemented yet)");
        }
    }

    public void stop() {
        if (sensorMode == SENSOR_MOCK) {
            mockSensorProvider.imuUpdates.unsubscribe(this);
            mockSensorProvider.magUpdates.unsubscribe(magSubscriber);
            mockSensorProvider.baroUpdates.unsubscribe(baroSubscriber);
            mockSensorProvider.stop();
        }
        // Future: stop bluetooth sensor

        sensorMode = SENSOR_NONE;
        rotationEstimator.reset();
    }

    /**
     * Restart the sensor service with the current options.
     */
    public void restart() {
        if (appContext == null) {
            Log.e(TAG, "Cannot restart: no context available");
            return;
        }
        Log.i(TAG, "Restarting sensor service");
        stop();
        start(appContext);
    }

    /**
     * Check if sensor data is available
     */
    public boolean hasSensorData() {
        return sensorMode == SENSOR_MOCK && mockSensorProvider.hasSensorData();
    }

    /**
     * Get the mock sensor provider (for inspection)
     */
    @Nullable
    public MockSensorProvider getMockSensorProvider() {
        return sensorMode == SENSOR_MOCK ? mockSensorProvider : null;
    }
}
