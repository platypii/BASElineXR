package com.platypii.baselinexr.bluetooth;

import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_CONNECTED;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_CONNECTING;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STARTING;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STATES;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STOPPED;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STOPPING;
import static com.platypii.baselinexr.bluetooth.BluetoothUtil.getDeviceName;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.events.BluetoothEvent;
import com.platypii.baselinexr.location.NMEA;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Thread that reads from bluetooth input stream, and turns into NMEA sentences
 */
class BluetoothRunnable implements Stoppable {
    private static final String TAG = "BluetoothRunnable";

    private static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static final int reconnectDelayMin = 500; // milliseconds
    private static final int reconnectDelayMax = 2000; // milliseconds
    private static int reconnectDelay = reconnectDelayMin; // milliseconds

    @NonNull
    private final BluetoothService service;
    @NonNull
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;

    // Bluetooth state
    int bluetoothState = BT_STOPPED;

    BluetoothRunnable(@NonNull BluetoothService service, @NonNull BluetoothAdapter bluetoothAdapter) {
        this.service = service;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    @Override
    public void run() {
        Log.i(TAG, "Bluetooth thread starting");
        setState(BT_STARTING);

        // Reconnect loop
        while (bluetoothState != BT_STOPPING) {
            // Connect to bluetooth GPS
            setState(BT_CONNECTING);
            final boolean isConnected = connect();
            if (bluetoothState == BT_CONNECTING && isConnected) {
                setState(BT_CONNECTED);
                reconnectDelay = reconnectDelayMin;

                // Start processing NMEA sentences
                processSentences();
            }
            // Are we restarting or stopping?
            if (bluetoothState != BT_STOPPING) {
                setState(BT_CONNECTING);
                // Sleep before reconnect
                try {
                    Thread.sleep(reconnectDelay);
                    // Exponential backoff
                    reconnectDelay = Math.min(reconnectDelayMax, (int)(reconnectDelay * 1.1));
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Bluetooth thread interrupted");
                }
                Log.i(TAG, "Reconnecting to bluetooth device");
            } else {
                Log.d(TAG, "Bluetooth thread about to stop");
            }
        }

        // Bluetooth service stopped
        setState(BT_STOPPED);
    }

    /**
     * Connect to gps receiver.
     * Precondition: bluetooth enabled and preferenceDeviceId != null
     *
     * @return true iff bluetooth socket was connect successfully
     */
    private boolean connect() {
        try {
            if (!bluetoothAdapter.isEnabled()) {
                Log.w(TAG, "Bluetooth is not enabled");
                return false;
            } else if (service.preferences.preferenceDeviceId == null) {
                Log.w(TAG, "Cannot connect: bluetooth device not selected");
                return false;
            }
            // Get bluetooth device
            final BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(service.preferences.preferenceDeviceId);
            UUID uuid = DEFAULT_UUID;
            final ParcelUuid[] uuids = bluetoothDevice.getUuids();
            if (uuids != null && uuids.length > 0) {
                uuid = uuids[0].getUuid();
            }
            // Connect to bluetooth device
            String deviceName = getDeviceName(bluetoothDevice);
            if (deviceName.isEmpty()) deviceName = uuid.toString();
            Log.i(TAG, "Connecting to bluetooth device: " + deviceName);
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
                bluetoothSocket.connect();

                // Connected to bluetooth device
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to bluetooth device: " + e.getMessage());
                return false;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to connect to bluetooth: permission exception", e);
            return false;
        }
    }

    /**
     * Pipe bluetooth socket into nmea listeners
     */
    private void processSentences() {
        try {
            final InputStream is = bluetoothSocket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while (bluetoothState == BT_CONNECTED && (line = reader.readLine()) != null) {
                // Update listeners
                service.nmeaUpdates.post(new NMEA(System.currentTimeMillis(), line));
            }
        } catch (IOException e) {
            if (bluetoothState == BT_CONNECTED) {
                Log.e(TAG, "Error reading from bluetooth socket", e);
            }
        } finally {
            Log.d(TAG, "Bluetooth thread shutting down");
        }
    }

    private void setState(int state) {
        if (bluetoothState != state) {
            Log.d(TAG, "Bluetooth state: " + BT_STATES[bluetoothState] + " -> " + BT_STATES[state]);
        }
        if (bluetoothState == BT_STOPPING && state == BT_CONNECTING) {
            Log.e(TAG, "Illegal bluetooth state transition: " + BT_STATES[bluetoothState] + " -> " + BT_STATES[state]);
        }
        if (bluetoothState == state && state != BT_CONNECTING) {
            // Only allowed self-transition is connecting -> connecting
            Log.e(TAG, "Null state transition: " + BT_STATES[bluetoothState] + " -> " + BT_STATES[state]);
        }
        if (bluetoothState != state) {
            bluetoothState = state;
            EventBus.getDefault().post(new BluetoothEvent());
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping bluetooth runnable");
        setState(BT_STOPPING);
        // Close bluetooth socket
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Exception closing bluetooth socket", e);
            }
        }
    }
}
