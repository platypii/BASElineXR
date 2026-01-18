package com.platypii.baselinexr.bluetooth;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanRecord;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.location.LocationCheck;
import com.platypii.baselinexr.location.NMEAException;
import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumidityData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MTimeSync;
import com.platypii.baselinexr.util.Exceptions;
import com.platypii.baselinexr.util.PubSub;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.GattStatus;
import com.welie.blessed.WriteType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Flysight2Protocol extends BleProtocol {
    private static final String TAG = "FlysightProtocol";
    
    // PubSub channels for data distribution
    private final PubSub<MLocation> locationUpdates;
    private final PubSub<MImuData> imuUpdates;
    private final PubSub<MMagData> magUpdates;
    private final PubSub<MBaroData> baroUpdates;
    private final PubSub<MHumidityData> humidityUpdates;
    private final PubSub<MTimeSync> timeSyncUpdates;

    // Flysight services
    private static final UUID flysightService0 = UUID.fromString("00000000-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID flysightService1 = UUID.fromString("00000001-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID flysightService2 = UUID.fromString("00000002-cc7a-482a-984a-7f2ed5b3e58f");
    
    // Sensor Data service (0x0003)
    private static final UUID sensorDataService = UUID.fromString("00000003-8e22-4541-9d4c-21edae82ed19");

    // Flysight characteristics (GNSS)
    private static final UUID flysightCharacteristicGNSS = UUID.fromString("00000000-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicTX = UUID.fromString("00000001-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicRX = UUID.fromString("00000002-8e22-4541-9d4c-21edae82ed19");
    
    // Sensor characteristics
    private static final UUID charIMU = UUID.fromString("00000010-8e22-4541-9d4c-21edae82ed19");
    private static final UUID charMag = UUID.fromString("00000011-8e22-4541-9d4c-21edae82ed19");
    private static final UUID charBaro = UUID.fromString("00000012-8e22-4541-9d4c-21edae82ed19");
    private static final UUID charHum = UUID.fromString("00000013-8e22-4541-9d4c-21edae82ed19");
    private static final UUID charTimeSync = UUID.fromString("00000014-8e22-4541-9d4c-21edae82ed19");

    // Flysight commands
    private static final byte[] flysightCommandHeartbeat = new byte[]{(byte) 0xfe};

    private static final long gpsEpochMilliseconds = 315964800000L - 18000L; // January 6, 1980 - 18s
    private static final long millisecondsPerWeek = 604800000L;

    public Flysight2Protocol(@NonNull PubSub<MLocation> locationUpdates,
                             @NonNull PubSub<MImuData> imuUpdates,
                             @NonNull PubSub<MMagData> magUpdates,
                             @NonNull PubSub<MBaroData> baroUpdates,
                             @NonNull PubSub<MHumidityData> humidityUpdates,
                             @NonNull PubSub<MTimeSync> timeSyncUpdates) {
        this.locationUpdates = locationUpdates;
        this.imuUpdates = imuUpdates;
        this.magUpdates = magUpdates;
        this.baroUpdates = baroUpdates;
        this.humidityUpdates = humidityUpdates;
        this.timeSyncUpdates = timeSyncUpdates;
    }

    @Override
    public boolean canParse(@NonNull BluetoothPeripheral peripheral, @Nullable ScanRecord record) {
        //Log.i(TAG, "Found flysight device: " + peripheral.getName() + " " + peripheral.getAddress());
        return peripheral.getName().equals("FlySight");
//        if (isFlysight(peripheral, record)) {
//            Log.i(TAG, "Found flysight device: " + peripheral.getName() + " " + peripheral.getAddress());
//            return true;
//        }
//        return false;
    }

    private boolean isFlysight(@NonNull BluetoothPeripheral peripheral, @Nullable ScanRecord record) {
        if (record != null) {
            // Check services
            final List<ParcelUuid> services = record.getServiceUuids();
            if (services != null) {
                for (ParcelUuid parcelUuid : services) {
                    if (parcelUuid.getUuid().equals(flysightService1)) {
                        return true;
                    }
                }
            }
            // Check manufacturer
//            final byte[] mfg = record.getManufacturerSpecificData(2523);
//            if (mfg != null && mfg.length == 1 && mfg[0] == 0) {
//                return true;
//            }
        }
        if (peripheral.getName().startsWith("KFS")) return true;
        if (peripheral.getName().startsWith("FlySight")) return true;
        return false;
    }

    @Override
    public void onServicesDiscovered(@NonNull BluetoothPeripheral peripheral) {
        Log.i(TAG, "flysight services discovered " + peripheral.getCurrentMtu());
        peripheral.requestMtu(256);
        // Start heartbeat thread
        startHeartbeat(peripheral, flysightService0, flysightCharacteristicRX);
    }

    @Override
    public void onMtuChanged(@NonNull BluetoothPeripheral peripheral, int mtu, @NonNull GattStatus status) {
        Log.i(TAG, "flysight mtu changed " + mtu);
        // Subscribe to GNSS service
        peripheral.setNotify(flysightService1, flysightCharacteristicGNSS, true);
        // Subscribe to sensor streams
        peripheral.setNotify(sensorDataService, charIMU, true);
        peripheral.setNotify(sensorDataService, charMag, true);
        peripheral.setNotify(sensorDataService, charBaro, true);
        peripheral.setNotify(sensorDataService, charHum, true);
        peripheral.setNotify(sensorDataService, charTimeSync, true);
        // Reset time sync on new connection
        FlysightTimeSync.reset();
    }
    
    @Override
    public void onCharacteristicUpdate(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value,
            @NonNull BluetoothGattCharacteristic characteristic, @NonNull GattStatus status) {
        if (status != GattStatus.SUCCESS || value.length == 0) return;
        
        UUID uuid = characteristic.getUuid();
        
        if (uuid.equals(flysightCharacteristicGNSS)) {
            processBytes(peripheral, value);  // existing GNSS handler
        } else if (uuid.equals(charIMU)) {
            processIMU(value);
        } else if (uuid.equals(charMag)) {
            processMag(value);
        } else if (uuid.equals(charBaro)) {
            processBaro(value);
        } else if (uuid.equals(charHum)) {
            processHum(value);
        } else if (uuid.equals(charTimeSync)) {
            processTimeSync(value);
        }
    }

    @Override
    public void processBytes(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value) {
        try {
            // FlySight2 RC pre-2024-11-11 didn't have flag byte
            if (value.length == 29 && value[0] == -80) { // 0xb0
                value = Arrays.copyOfRange(value, 1, value.length);
            }
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

    /**
     * Process IMU characteristic data (30 bytes)
     * Format: time(4) + gyro_xyz(12) + accel_xyz(12) + temp(2)
     * Per BLE_SENSOR_STREAMING_PROTOCOL.md
     */
    private void processIMU(@NonNull byte[] value) {
        if (value.length < 30) {
            Log.w(TAG, "IMU data too short: " + value.length);
            return;
        }
        final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        
        // Device time in milliseconds (unsigned 32-bit)
        long deviceTimeMs = buf.getInt(0) & 0xFFFFFFFFL;
        long gpsMillis = FlysightTimeSync.deviceToGpsTime(deviceTimeMs);
        
        // Gyroscope: signed 32-bit, scale factor 0.001 (raw to °/s)
        float gx = buf.getInt(4) * 0.001f;
        float gy = buf.getInt(8) * 0.001f;
        float gz = buf.getInt(12) * 0.001f;
        
        // Accelerometer: signed 32-bit, scale factor 0.001 (raw to g)
        float ax = buf.getInt(16) * 0.001f;
        float ay = buf.getInt(20) * 0.001f;
        float az = buf.getInt(24) * 0.001f;
        
        // Temperature: signed 16-bit, scale factor 0.01 (raw to °C)
        float temperature = buf.getShort(28) * 0.01f;
        
        MImuData imu = new MImuData(gpsMillis, deviceTimeMs, gx, gy, gz, ax, ay, az, temperature);
        imuUpdates.post(imu);
    }
    
    /**
     * Process Magnetometer characteristic data (12 bytes)
     * Format: time(4) + mag_xyz(6) + temp(2)
     * Per BLE_SENSOR_STREAMING_PROTOCOL.md
     */
    private void processMag(@NonNull byte[] value) {
        if (value.length < 12) {
            Log.w(TAG, "MAG data too short: " + value.length);
            return;
        }
        final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        
        // Device time in milliseconds (unsigned 32-bit)
        long deviceTimeMs = buf.getInt(0) & 0xFFFFFFFFL;
        long gpsMillis = FlysightTimeSync.deviceToGpsTime(deviceTimeMs);
        
        // Magnetometer: signed 16-bit (mGauss), convert to Gauss
        float mx = buf.getShort(4) / 1000.0f;
        float my = buf.getShort(6) / 1000.0f;
        float mz = buf.getShort(8) / 1000.0f;
        
        // Temperature: signed 16-bit, scale factor 0.01 (raw to °C)
        float temperature = buf.getShort(10) * 0.01f;
        
        MMagData mag = new MMagData(gpsMillis, deviceTimeMs, mx, my, mz, temperature);
        magUpdates.post(mag);
    }
    
    /**
     * Process Barometer characteristic data (10 bytes)
     * Format: time(4) + pressure(4) + temp(2)
     * Per BLE_SENSOR_STREAMING_PROTOCOL.md
     */
    private void processBaro(@NonNull byte[] value) {
        if (value.length < 10) {
            Log.w(TAG, "BARO data too short: " + value.length);
            return;
        }
        final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        
        // Device time in milliseconds (unsigned 32-bit)
        long deviceTimeMs = buf.getInt(0) & 0xFFFFFFFFL;
        long gpsMillis = FlysightTimeSync.deviceToGpsTime(deviceTimeMs);
        
        // Pressure: unsigned 32-bit (Pascals)
        long pressureRaw = buf.getInt(4) & 0xFFFFFFFFL;
        float pressure = (float) pressureRaw;  // Pascals
        
        // Temperature: signed 16-bit, scale factor 0.01 (raw to °C)
        float temperature = buf.getShort(8) * 0.01f;
        
        MBaroData baro = new MBaroData(gpsMillis, deviceTimeMs, pressure, temperature);
        baroUpdates.post(baro);
    }
    
    /**
     * Process Humidity characteristic data (8 bytes)
     * Format: time(4) + humidity(2) + temp(2)
     * Per BLE_SENSOR_STREAMING_PROTOCOL.md
     */
    private void processHum(@NonNull byte[] value) {
        if (value.length < 8) {
            Log.w(TAG, "HUM data too short: " + value.length);
            return;
        }
        final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        
        // Device time in milliseconds (unsigned 32-bit)
        long deviceTimeMs = buf.getInt(0) & 0xFFFFFFFFL;
        long gpsMillis = FlysightTimeSync.deviceToGpsTime(deviceTimeMs);
        
        // Humidity: signed 16-bit, scale factor 0.01 (raw to %RH)
        float humidity = buf.getShort(4) * 0.01f;
        
        // Temperature: signed 16-bit, scale factor 0.01 (raw to °C)
        float temperature = buf.getShort(6) * 0.01f;
        
        MHumidityData hum = new MHumidityData(gpsMillis, deviceTimeMs, humidity, temperature);
        humidityUpdates.post(hum);
    }
    
    /**
     * Process Time Sync characteristic data (10 bytes)
     * Format: device_time(4) + gps_tow(4) + gps_week(2)
     * Per BLE_SENSOR_STREAMING_PROTOCOL.md
     */
    private void processTimeSync(@NonNull byte[] value) {
        if (value.length < 10) {
            Log.w(TAG, "TIME data too short: " + value.length);
            return;
        }
        final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        
        // Device time in milliseconds (unsigned 32-bit)
        long deviceTimeMs = buf.getInt(0) & 0xFFFFFFFFL;
        
        // GPS time of week in milliseconds (unsigned 32-bit)
        long gpsTowMs = buf.getInt(4) & 0xFFFFFFFFL;
        
        // GPS week number (unsigned 16-bit)
        int gpsWeek = buf.getShort(8) & 0xFFFF;
        
        // Update global time sync (order: deviceTimeMs, gpsTowMs, gpsWeek)
        FlysightTimeSync.update(deviceTimeMs, gpsTowMs, gpsWeek);
        
        // Create and post time sync measurement (order: deviceTimeMs, gpsTowMs, gpsWeek)
        MTimeSync timeSync = new MTimeSync(deviceTimeMs, gpsTowMs, gpsWeek);
        timeSyncUpdates.post(timeSync);
        
        Log.d(TAG, "Time sync: device=" + deviceTimeMs + " gpsWeek=" + gpsWeek + " tow=" + gpsTowMs);
    }
    
    private void startHeartbeat(@NonNull BluetoothPeripheral peripheral, @NonNull UUID service, @NonNull UUID characteristic) {
        // Start heartbeat thread
        new Thread(() -> {
            try {
                while (true) {
                    // Send heartbeat every 14.5 seconds
                    if (peripheral.writeCharacteristic(service, characteristic, flysightCommandHeartbeat, WriteType.WITHOUT_RESPONSE)) {
                        Thread.sleep(14500);
                    } else {
                        Log.w(TAG, "Failed to send heartbeat, stopping heartbeat thread");
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Log.i(TAG, "Heartbeat thread interrupted");
            }
        }).start();
    }
}
