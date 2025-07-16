package com.platypii.baselinexr.bluetooth;

import android.bluetooth.le.ScanRecord;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.location.LocationCheck;
import com.platypii.baselinexr.location.NMEAException;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.Exceptions;
import com.platypii.baselinexr.util.PubSub;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.GattStatus;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class Flysight2Protocol extends BleProtocol {
    private static final String TAG = "FlysightProtocol";
    private final PubSub<MLocation> locationUpdates;

    // Flysight services
    private static final UUID flysightService0 = UUID.fromString("00000000-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID flysightService1 = UUID.fromString("00000001-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID flysightService2 = UUID.fromString("00000002-cc7a-482a-984a-7f2ed5b3e58f");
    // Flysight characteristics
    private static final UUID flysightCharacteristicGNSS = UUID.fromString("00000000-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicTX = UUID.fromString("00000001-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicRX = UUID.fromString("00000002-8e22-4541-9d4c-21edae82ed19");

    private static final long gpsEpochMilliseconds = 315964800000L - 18000L; // January 6, 1980 - 18s
    private static final long millisecondsPerWeek = 604800000L;

    public Flysight2Protocol(@NonNull PubSub<MLocation> locationUpdates) {
        this.locationUpdates = locationUpdates;
    }

    @Override
    public boolean canParse(@NonNull BluetoothPeripheral peripheral, @Nullable ScanRecord record) {
        // TODO: Check address
        return peripheral.getName().equals("FlySight");
    }

    @Override
    public void onServicesDiscovered(@NonNull BluetoothPeripheral peripheral) {
        Log.i(TAG, "flysight services discovered " + peripheral.getCurrentMtu());
        peripheral.requestMtu(256);
    }

    @Override
    public void onMtuChanged(@NonNull BluetoothPeripheral peripheral, int mtu, @NonNull GattStatus status) {
        Log.i(TAG, "flysight mtu changed " + mtu);
        // Subscribe to flysight service
        peripheral.setNotify(flysightService1, flysightCharacteristicGNSS, true);
    }

    @Override
    public void processBytes(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value) {
        try {
            final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
            final int tow = buf.getInt(0); // gps time of week
            final double lng = buf.getInt(4) * 1e-7;
            final double lat = buf.getInt(8) * 1e-7;
            final double alt = buf.getInt(12) * 1e-3;
            final double vN = buf.getInt(16) * 1e-3;
            final double vE = buf.getInt(20) * 1e-3;
            final double climb = buf.getInt(24) * -1e-3;

            // Calculate gps week from current system time
            final long now = System.currentTimeMillis();
            final long gpsTime = now - gpsEpochMilliseconds;
            final long gpsWeek = gpsTime / millisecondsPerWeek;
            // TODO: Check if near the start or end of the week
            // Calculate epoch time from time-of-week
            final long millis = gpsWeek * millisecondsPerWeek + tow + gpsEpochMilliseconds;

            final int locationError = LocationCheck.validate(lat, lng);
            if (locationError == LocationCheck.VALID) {
                final MLocation loc = new MLocation(
                        millis, lat, lng, alt, climb, vN, vE,
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN, -1, -1
                );
                Log.i(TAG, "flysight -> app: gps " + loc);
                // Update listeners
                locationUpdates.post(loc);
            } else {
                Log.w(TAG, LocationCheck.message[locationError] + ": " + lat + "," + lng);
                Exceptions.report(new NMEAException(LocationCheck.message[locationError] + ": " + lat + "," + lng));
            }
        } catch (Exception e) {
            Exceptions.report(e);
        }
    }
}
