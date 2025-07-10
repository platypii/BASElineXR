package com.platypii.baselinexr.bluetooth;

import static com.platypii.baselinexr.RequestCodes.RC_BLUE_ENABLE;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_CONNECTED;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_CONNECTING;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_SCANNING;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STARTING;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STATES;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STOPPED;
import static com.platypii.baselinexr.bluetooth.BluetoothState.BT_STOPPING;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.location.NMEA;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.Exceptions;
import com.platypii.baselinexr.util.PubSub;

import java.util.HashSet;
import java.util.Set;

/**
 * Class to manage a bluetooth GPS receiver.
 * Note: instantiating this class will not automatically start bluetooth. Call startAsync to connect.
 */
public class BluetoothService {
    private static final String TAG = "Bluetooth";

    public final PubSub<NMEA> nmeaUpdates = new PubSub<>();
    public final PubSub<MLocation> locationUpdates = new PubSub<>();

    // BLE subsystem
    public final BleService ble = new BleService(
            new Flysight2Protocol(locationUpdates)
    );

    // Android shared preferences for bluetooth
    public final BluetoothPreferences preferences = new BluetoothPreferences();

    @Nullable
    private BluetoothAdapter bluetoothAdapter;
    @Nullable
    private BluetoothRunnable bluetoothRunnable;
    @Nullable
    private Thread bluetoothThread;

    public void start(@NonNull Activity activity) {
        if (BluetoothState.started(getState())) {
            Exceptions.report(new IllegalStateException("Bluetooth started twice " + BT_STATES[getState()]));
            return;
        }
        if (getState() == BT_STOPPED) {
            bluetoothAdapter = getAdapter(activity);
            if (preferences.preferenceBle) {
                // Start bluetooth LE
                ble.start(activity);
            } else {
                // Start bluetooth thread
                if (bluetoothRunnable != null) {
                    Log.e(TAG, "Bluetooth thread already started");
                }
                if (bluetoothAdapter != null) {
                    bluetoothRunnable = new BluetoothRunnable(BluetoothService.this, bluetoothAdapter);
                    bluetoothThread = new Thread(bluetoothRunnable);
                    bluetoothThread.start();
                }
            }
        } else {
            Exceptions.report(new IllegalStateException("Bluetooth already started: " + BT_STATES[getState()]));
        }
    }

    /**
     * Get bluetooth adapter, request bluetooth if needed
     */
    @Nullable
    private BluetoothAdapter getAdapter(@NonNull Activity activity) {
        // TODO: Make sure this doesn't take too long
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device not supported
            Log.e(TAG, "Bluetooth not supported");
        } else if (!bluetoothAdapter.isEnabled()) {
            // Turn on bluetooth
            // TODO: Handle result?
            final Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                activity.startActivityForResult(enableBluetoothIntent, RC_BLUE_ENABLE);
            } catch (SecurityException e) {
                Exceptions.report(e);
            }
        }
        return bluetoothAdapter;
    }

    /**
     * Return list of bonded devices
     */
    @NonNull
    public Set<BluetoothDevice> getBondedDevices() {
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            try {
                return bluetoothAdapter.getBondedDevices();
            } catch (SecurityException e) {
                Log.w(TAG, "Tried to get devices, but bluetooth permission denied", e);
                return new HashSet<>();
            }
        } else {
            Log.w(TAG, "Tried to get devices, but bluetooth is not enabled");
            return new HashSet<>();
        }
    }

    public int getState() {
        if (preferences.preferenceBle) {
            return ble.bluetoothState;
        } else {
            return bluetoothRunnable != null ? bluetoothRunnable.bluetoothState : BT_STOPPED;
        }
    }

    /**
     * Return a string with the gps location device name
     */
    @NonNull
    public String getDeviceName() {
        if (!preferences.preferenceEnabled) {
            return "Phone";
        } else if (preferences.preferenceDeviceName != null) {
            return preferences.preferenceDeviceName;
        } else {
            return "";
        }
    }

    public boolean isEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Return a human-readable string for the bluetooth state
     */
    @NonNull
    public String getStatusMessage(@NonNull Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                return "Bluetooth permission required";
//            }
        }
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            // Hardware disabled
            return "BT disabled";
        } else if (!preferences.preferenceEnabled) {
            // Bluetooth preference disabled
            return "BT disabled";
        } else if (preferences.preferenceDeviceId == null) {
            // Bluetooth preference enabled, but device not selected
            return "BT device not selected";
        } else {
            switch (getState()) {
                case BT_STOPPED:
                    return "BT stopped";
                case BT_STARTING:
                    return "BT starting";
                case BT_CONNECTING:
                    return "BT connecting";
                case BT_CONNECTED:
                    return "BT connected";
                case BT_STOPPING:
                    return "BT stopping";
                case BT_SCANNING:
                    return "BT scanning";
                default:
                    return "";
            }
        }
    }

    public synchronized void stop() {
        if (bluetoothRunnable != null && bluetoothThread != null) {
            Log.i(TAG, "Stopping bluetooth service");
            bluetoothRunnable.stop();
            try {
                bluetoothThread.join(1000);

                // Thread is dead, clean up
                bluetoothRunnable = null;
                bluetoothThread = null;
                if (getState() != BT_STOPPED) {
                    Log.e(TAG, "Unexpected bluetooth state: state should be STOPPED when thread has stopped");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Bluetooth thread interrupted while waiting for it to die", e);
            }
            Log.i(TAG, "Stopped bluetooth service");
        }
        // Stop bluetooth LE
        ble.stop();
    }

    /**
     * Restart bluetooth.
     * If bluetooth is stopped, just start it.
     */
    public synchronized void restart(@NonNull Activity activity) {
        Log.i(TAG, "Restarting bluetooth service");
        // Stop first
        stop();
        if (getState() != BT_STOPPED) {
            Exceptions.report(new IllegalStateException("Error restarting bluetooth: not stopped: " + BT_STATES[getState()]));
        }
        start(activity);
    }
}
