package com.platypii.baselinexr.bluetooth;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BluetoothPreferences {

    private static final String PREF_BT_ENABLED = "bluetooth_enabled";
    private static final String PREF_BT_DEVICE_ID = "bluetooth_id";
    private static final String PREF_BT_DEVICE_NAME = "bluetooth_name";
    private static final String PREF_BT_BLE = "bluetooth_ble";
    private static final String PREF_FLYSIGHT_PINNED_MAC = "flysight_pinned_mac";
    private static final String PREF_FLYSIGHT_DEVICE_NAME = "flysight_device_name";

    // Android shared preferences for bluetooth
    public boolean preferenceEnabled = true;
    @Nullable
    public String preferenceDeviceId = null;
    @Nullable
    public String preferenceDeviceName = null;
    public boolean preferenceBle = true;
    
    // FlySight device pinning - stores the known-good MAC address and name
    @Nullable
    public String flysightPinnedMac = null;
    @Nullable
    public String flysightDeviceName = null;

    public void load(@NonNull SharedPreferences prefs) {
        preferenceEnabled = prefs.getBoolean(PREF_BT_ENABLED, preferenceEnabled);
        preferenceDeviceId = prefs.getString(PREF_BT_DEVICE_ID, preferenceDeviceId);
        preferenceDeviceName = prefs.getString(PREF_BT_DEVICE_NAME, preferenceDeviceName);
        preferenceBle = prefs.getBoolean(PREF_BT_BLE, true);
        flysightPinnedMac = prefs.getString(PREF_FLYSIGHT_PINNED_MAC, null);
        flysightDeviceName = prefs.getString(PREF_FLYSIGHT_DEVICE_NAME, null);
    }

    /**
     * Pin a FlySight device by MAC address. This ensures only the known-good device is connected.
     * Call this after successfully receiving data from a FlySight device.
     */
    public void pinFlysightDevice(@NonNull Context context, @NonNull String macAddress, @Nullable String deviceName) {
        flysightPinnedMac = macAddress;
        flysightDeviceName = deviceName;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor edit = prefs.edit();
        edit.putString(PREF_FLYSIGHT_PINNED_MAC, macAddress);
        edit.putString(PREF_FLYSIGHT_DEVICE_NAME, deviceName);
        edit.apply();
    }

    /**
     * Forget the pinned FlySight device. Use this to allow pairing with a different device.
     */
    public void forgetFlysightDevice(@NonNull Context context) {
        flysightPinnedMac = null;
        flysightDeviceName = null;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor edit = prefs.edit();
        edit.remove(PREF_FLYSIGHT_PINNED_MAC);
        edit.remove(PREF_FLYSIGHT_DEVICE_NAME);
        edit.apply();
    }

    /**
     * Check if we should connect to this FlySight device.
     * If no device is pinned, accept any FlySight. If pinned, only accept the pinned MAC.
     */
    public boolean shouldConnectToFlysight(@NonNull String macAddress) {
        if (flysightPinnedMac == null) {
            // No pinned device, accept any FlySight
            return true;
        }
        // Only connect to the pinned device
        return flysightPinnedMac.equals(macAddress);
    }

}
