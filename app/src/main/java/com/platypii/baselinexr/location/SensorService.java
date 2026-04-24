package com.platypii.baselinexr.location;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.MockTrackOptions;
import com.platypii.baselinexr.bluetooth.BluetoothService;
import com.platypii.baselinexr.bluetooth.Flysight2Protocol;
import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumData;
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

    // Bluetooth service reference (set on start)
    @Nullable
    private BluetoothService bluetooth;

    // PubSub for re-publishing sensor updates (after processing)
    public final PubSub<MImuData> imuUpdates = new PubSub<>();
    public final PubSub<MMagData> magUpdates = new PubSub<>();
    public final PubSub<MBaroData> baroUpdates = new PubSub<>();
    public final PubSub<MHumData> humUpdates = new PubSub<>();

    // Subscribers for internal routing
    private final Subscriber<MMagData> magSubscriber = this::onMagUpdate;
    private final Subscriber<MBaroData> baroSubscriber = this::onBaroUpdate;
    private final Subscriber<MHumData> humSubscriber = this::onHumUpdate;

    public SensorService() {
        mockSensorProvider = new MockSensorProvider();
    }

    @Override
    public void apply(MImuData imu) {
        Log.v(TAG, "SensorService received IMU: " + imu);
        // Re-post to subscribers (AhrsSystem handles rotation estimation)
        imuUpdates.post(imu);
    }

    private void onMagUpdate(MMagData mag) {
        Log.v(TAG, "SensorService received MAG: " + mag);
        // Re-post to subscribers (AhrsSystem handles rotation estimation)
        magUpdates.post(mag);
    }

    private void onBaroUpdate(MBaroData baro) {
        // Re-post to subscribers
        baroUpdates.post(baro);
    }

    private void onHumUpdate(MHumData hum) {
        // Re-post to subscribers
        humUpdates.post(hum);
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

    public void start(@NonNull Context context, @Nullable BluetoothService bluetoothService) {
        this.appContext = context;
        this.bluetooth = bluetoothService;
        if (sensorMode != SENSOR_NONE) {
            Log.e(TAG, "Sensor service already started");
        }
        startGeneration++;
        final int myGeneration = startGeneration;

        // Check if mock mode should be used
        final boolean useMock = MockTrackOptions.current != null;

        if (useMock) {
            sensorMode = SENSOR_MOCK;
            // Subscribe FIRST, before starting playback (to avoid race condition)
            mockSensorProvider.imuUpdates.subscribe(this);
            mockSensorProvider.magUpdates.subscribe(magSubscriber);
            mockSensorProvider.baroUpdates.subscribe(baroSubscriber);
            
            // Start sensor provider (will wait for GPS timeline, then start playback thread)
            // This can block for up to 2 seconds waiting for timeline, but that's OK
            // since GPS service starts first and initializes the timeline quickly
            mockSensorProvider.start(context);
            Log.i(TAG, "Mock sensor provider started");
        } else if (bluetoothService != null) {
            // Live bluetooth sensor mode
            sensorMode = SENSOR_BLUETOOTH;
            Flysight2Protocol protocol = bluetoothService.flysightProtocol;
            // Subscribe to FlySight sensor data streams
            protocol.imuUpdates.subscribe(this);
            protocol.magUpdates.subscribe(magSubscriber);
            protocol.baroUpdates.subscribe(baroSubscriber);
            protocol.humUpdates.subscribe(humSubscriber);
            Log.i(TAG, "Bluetooth sensor provider started, subscribed to protocol.imuUpdates/magUpdates/baroUpdates/humUpdates");
        } else {
            sensorMode = SENSOR_NONE;
            Log.i(TAG, "No sensor source available");
        }
    }

    /**
     * Start sensor service (convenience method without bluetooth)
     */
    public void start(@NonNull Context context) {
        start(context, null);
    }

    public void stop() {
        if (sensorMode == SENSOR_MOCK) {
            mockSensorProvider.imuUpdates.unsubscribe(this);
            mockSensorProvider.magUpdates.unsubscribe(magSubscriber);
            mockSensorProvider.baroUpdates.unsubscribe(baroSubscriber);
            mockSensorProvider.stop();
        } else if (sensorMode == SENSOR_BLUETOOTH && bluetooth != null) {
            Flysight2Protocol protocol = bluetooth.flysightProtocol;
            protocol.imuUpdates.unsubscribe(this);
            protocol.magUpdates.unsubscribe(magSubscriber);
            protocol.baroUpdates.unsubscribe(baroSubscriber);
            protocol.humUpdates.unsubscribe(humSubscriber);
        }

        sensorMode = SENSOR_NONE;
        bluetooth = null;
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
        BluetoothService savedBluetooth = bluetooth;
        stop();
        start(appContext, savedBluetooth);
    }

    /**
     * Seek to a specific position in the playback.
     * This restarts the provider from the current timeline position.
     * @param positionMs Position in milliseconds from track start (timeline already updated by caller)
     */
    public void seekTo(long positionMs) {
        if (sensorMode != SENSOR_MOCK) {
            Log.w(TAG, "seekTo only works in mock mode");
            return;
        }
        if (appContext == null) {
            Log.e(TAG, "Cannot seek: no context available");
            return;
        }
        Log.i(TAG, "Seeking sensor playback to " + positionMs + "ms");
        // Stop current playback thread (but don't unsubscribe or change mode)
        mockSensorProvider.stop();
        // Restart from current timeline position (doesn't reset timeline)
        mockSensorProvider.restartFromCurrentPosition(appContext);
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

    /**
     * Get the loaded sensor data set (for calibration and analysis)
     */
    @Nullable
    public com.platypii.baselinexr.tracks.SensorDataSet getLastSensorDataSet() {
        return sensorMode == SENSOR_MOCK ? mockSensorProvider.getSensorData() : null;
    }
}
