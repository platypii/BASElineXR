package com.platypii.baselinexr.bluetooth;

import com.platypii.baselinexr.events.FlysightModeEvent;
import com.platypii.baselinexr.location.LocationCheck;
import com.platypii.baselinexr.location.NMEAException;
import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MTimeSync;
import com.platypii.baselinexr.util.Exceptions;
import com.platypii.baselinexr.util.PubSub;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.le.ScanRecord;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.GattStatus;
import com.welie.blessed.WriteType;
import org.greenrobot.eventbus.EventBus;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Flysight2Protocol extends BleProtocol {
    private static final String TAG = "FlysightProtocol";
    
    // PubSub channels for sensor updates
    private final PubSub<MLocation> locationUpdates;
    public final PubSub<MImuData> imuUpdates = new PubSub<>();
    public final PubSub<MMagData> magUpdates = new PubSub<>();
    public final PubSub<MBaroData> baroUpdates = new PubSub<>();
    public final PubSub<MHumData> humUpdates = new PubSub<>();
    
    // IMU correlator for combining accel + gyro packets
    private final ImuCorrelator imuCorrelator = new ImuCorrelator();
    
    // Control point manager for sending commands
    public final Flysight2ControlPoint controlPoint = new Flysight2ControlPoint();
    
    // Track device mode (SLEEP=0, ACTIVE=1, etc.)
    private int deviceMode = FlysightModeEvent.MODE_SLEEP;
    private boolean sensorsSubscribed = false;
    
    // Time sync (TODO: implement proper TIME sync from FlySight)
    @Nullable
    private MTimeSync timeSync = null;

    // Flysight services
    private static final UUID flysightService0 = UUID.fromString("00000000-cc7a-482a-984a-7f2ed5b3e58f"); // File Transfer
    private static final UUID flysightService1 = UUID.fromString("00000001-cc7a-482a-984a-7f2ed5b3e58f"); // Sensor Data
    private static final UUID flysightService2 = UUID.fromString("00000002-cc7a-482a-984a-7f2ed5b3e58f"); // Starter Pistol
    private static final UUID flysightService3 = UUID.fromString("00000003-cc7a-482a-984a-7f2ed5b3e58f"); // Device State

    // Flysight characteristics - File Transfer
    private static final UUID flysightCharacteristicTX = UUID.fromString("00000001-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicRX = UUID.fromString("00000002-8e22-4541-9d4c-21edae82ed19");

    // Flysight characteristics - Sensor Data
    private static final UUID flysightCharacteristicGNSS = UUID.fromString("00000000-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicControlPoint = UUID.fromString("00000006-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicBaro = UUID.fromString("00000008-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicAccel = UUID.fromString("00000009-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicGyro = UUID.fromString("0000000A-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicMag = UUID.fromString("0000000B-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicHum = UUID.fromString("0000000C-8e22-4541-9d4c-21edae82ed19");

    // Flysight characteristics - Device State
    private static final UUID flysightCharacteristicMode = UUID.fromString("00000005-8e22-4541-9d4c-21edae82ed19");
    private static final UUID flysightCharacteristicDeviceControlPoint = UUID.fromString("00000007-8e22-4541-9d4c-21edae82ed19");

    // Flysight commands
    private static final byte[] flysightCommandHeartbeat = new byte[]{(byte) 0xfe};

    private static final long gpsEpochMilliseconds = 315964800000L - 18000L; // January 6, 1980 - 18s
    private static final long millisecondsPerWeek = 604800000L;

    // Bionic Avionics manufacturer ID for FlySight
    private static final int MANUFACTURER_ID_BIONIC = 0x09DB;

    public Flysight2Protocol(@NonNull PubSub<MLocation> locationUpdates) {
        this.locationUpdates = locationUpdates;
    }

    @Override
    public boolean canParse(@NonNull BluetoothPeripheral peripheral, @Nullable ScanRecord record) {
        String name = peripheral.getName();
        String address = peripheral.getAddress();
        
        // First check if this is a FlySight device by name
        if (!"FlySight".equals(name)) {
            return false;
        }
        
        // Check manufacturer data to ensure we're not connecting to a device in pairing mode
        // FlySight uses manufacturer data with status byte: 0x00 = normal, 0x01 = pairing mode
        if (record != null) {
            byte[] mfgData = record.getManufacturerSpecificData(MANUFACTURER_ID_BIONIC);
            if (mfgData != null && mfgData.length >= 1) {
                int statusByte = mfgData[0] & 0xFF;
                if (statusByte == 0x01) {
                    // Device is in pairing request mode - skip it to avoid pairing popup
                    Log.w(TAG, "Skipping FlySight " + address + " - device is in pairing mode (status=0x01)");
                    return false;
                }
                Log.i(TAG, "Found FlySight device! " + name + " " + address + " (status=0x" + String.format("%02X", statusByte) + ")");
            } else {
                // No manufacturer data or wrong manufacturer - still try to connect
                Log.i(TAG, "Found FlySight device! " + name + " " + address + " (no mfg data)");
            }
        } else {
            Log.i(TAG, "Found FlySight device! " + name + " " + address + " (no scan record)");
        }
        
        return true;
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
        
        // Enumerate all services and characteristics for debugging
        for (android.bluetooth.BluetoothGattService service : peripheral.getServices()) {
            String serviceUuid = service.getUuid().toString();
            Log.i(TAG, "  Service: " + serviceUuid.substring(0, 8) + "...");
            for (android.bluetooth.BluetoothGattCharacteristic c : service.getCharacteristics()) {
                String charUuid = c.getUuid().toString();
                int props = c.getProperties();
                String propsStr = "";
                if ((props & 0x01) != 0) propsStr += "BROADCAST ";
                if ((props & 0x02) != 0) propsStr += "READ ";
                if ((props & 0x04) != 0) propsStr += "WRITE_NO_RSP ";
                if ((props & 0x08) != 0) propsStr += "WRITE ";
                if ((props & 0x10) != 0) propsStr += "NOTIFY ";
                if ((props & 0x20) != 0) propsStr += "INDICATE ";
                Log.i(TAG, "    Char: " + charUuid.substring(0, 8) + "... props=" + propsStr);
            }
        }
        
        peripheral.requestMtu(256);
        // Note: Heartbeat is for File Transfer service only (to reset 30s timeout)
        // In ACTIVE mode, File Transfer is unavailable (SD card busy with logging)
        // Sensor data notifications keep the BLE connection alive, so no heartbeat needed
        // startHeartbeat(peripheral, flysightService0, flysightCharacteristicRX);
    }

    // Track which peripheral we're connected to for delayed commands
    private BluetoothPeripheral connectedPeripheral = null;
    
    @Override
    public void onMtuChanged(@NonNull BluetoothPeripheral peripheral, int mtu, @NonNull GattStatus status) {
        Log.i(TAG, "flysight mtu changed " + mtu + " status=" + status);
        connectedPeripheral = peripheral;
        controlPoint.setPeripheral(peripheral);
        sensorsSubscribed = false;
        
        // Subscribe to Control Point (SD_Control_Point) for command responses
        boolean cpOk = peripheral.setNotify(flysightService1, flysightCharacteristicControlPoint, true);
        Log.i(TAG, "setNotify SD control point=" + cpOk);
        
        // NOTE: DS_Control_Point (0x07) subscription removed from auto-connect.
        // Subscribing to it triggers the Quest OS pairing popup even on bonded devices.
        // Call subscribeToDeviceControlPoint() explicitly when needed (e.g., from Control Point panel).
        
        // Subscribe to Device Mode (DS_Mode) to detect ACTIVE mode transitions
        boolean modeOk = peripheral.setNotify(flysightService3, flysightCharacteristicMode, true);
        Log.i(TAG, "setNotify device mode=" + modeOk);
        
        // Also read current mode immediately
        peripheral.readCharacteristic(flysightService3, flysightCharacteristicMode);
    }
    
    /**
     * Subscribe to DS_Control_Point for firmware version/device ID commands.
     * Call this only when needed, as it may trigger Quest OS pairing popup on some devices.
     * @return true if subscription was queued successfully
     */
    public boolean subscribeToDeviceControlPoint() {
        if (connectedPeripheral == null) {
            Log.w(TAG, "subscribeToDeviceControlPoint: no peripheral connected");
            return false;
        }
        boolean ok = connectedPeripheral.setNotify(flysightService3, flysightCharacteristicDeviceControlPoint, true);
        Log.i(TAG, "setNotify DS control point=" + ok);
        return ok;
    }
    
    /**
     * Configure dividers for all sensors.
     * Only needed for dev devices without config file, or to override settings.
     */
    public void configureSensorDividers(int divider) {
        Log.i(TAG, "Configuring sensor dividers to " + divider);
        controlPoint.configureAllDividers(divider);
    }
    
    private void subscribeToSensors(@NonNull BluetoothPeripheral peripheral) {
        // Subscribe to all sensor characteristics
        boolean gnssOk = peripheral.setNotify(flysightService1, flysightCharacteristicGNSS, true);
        boolean accelOk = peripheral.setNotify(flysightService1, flysightCharacteristicAccel, true);
        boolean gyroOk = peripheral.setNotify(flysightService1, flysightCharacteristicGyro, true);
        boolean magOk = peripheral.setNotify(flysightService1, flysightCharacteristicMag, true);
        boolean baroOk = peripheral.setNotify(flysightService1, flysightCharacteristicBaro, true);
        boolean humOk = peripheral.setNotify(flysightService1, flysightCharacteristicHum, true);
        Log.i(TAG, "setNotify results: gnss=" + gnssOk + " accel=" + accelOk + " gyro=" + gyroOk + " mag=" + magOk + " baro=" + baroOk + " hum=" + humOk);
    }

    @Override
    public void onNotificationStateUpdate(@NonNull BluetoothPeripheral peripheral,
            @NonNull BluetoothGattCharacteristic characteristic, @NonNull GattStatus status) {
        String uuidShort = characteristic.getUuid().toString().substring(0, 8);
        Log.i(TAG, "onNotificationStateUpdate: uuid=" + uuidShort + " status=" + status);
        
        // Control point ready - we can now send commands
        if (characteristic.getUuid().equals(flysightCharacteristicControlPoint) && status == GattStatus.SUCCESS) {
            Log.i(TAG, "Control point ready for commands");
            // If device is already in ACTIVE mode, configure and subscribe now
            if (deviceMode == FlysightModeEvent.MODE_ACTIVE && !sensorsSubscribed) {
                onDeviceBecameActive(peripheral);
            }
        }
        
        // Device mode subscription ready
        if (characteristic.getUuid().equals(flysightCharacteristicMode) && status == GattStatus.SUCCESS) {
            Log.i(TAG, "Device mode notifications enabled");
        }
    }

    @Override
    public void onCharacteristicUpdate(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value, 
            @NonNull BluetoothGattCharacteristic characteristic, @NonNull GattStatus status) {
        if (status != GattStatus.SUCCESS || value.length == 0) {
            Log.w(TAG, "onCharacteristicUpdate: status=" + status + " len=" + value.length);
            return;
        }
        
        // Route by characteristic UUID
        UUID uuid = characteristic.getUuid();
        // Log characteristic UUID (abbreviated) for debugging
        String uuidShort = uuid.toString().substring(0, 8);
        Log.v(TAG, "onCharacteristicUpdate: uuid=" + uuidShort + " len=" + value.length);
        try {
            if (uuid.equals(flysightCharacteristicGNSS)) {
                processGnss(value);
            } else if (uuid.equals(flysightCharacteristicAccel)) {
                processAccel(value);
            } else if (uuid.equals(flysightCharacteristicGyro)) {
                processGyro(value);
            } else if (uuid.equals(flysightCharacteristicMag)) {
                processMag(value);
            } else if (uuid.equals(flysightCharacteristicBaro)) {
                processBaro(value);
            } else if (uuid.equals(flysightCharacteristicHum)) {
                processHum(value);
            } else if (uuid.equals(flysightCharacteristicControlPoint)) {
                controlPoint.processResponse(value);
            } else if (uuid.equals(flysightCharacteristicDeviceControlPoint)) {
                controlPoint.processResponse(value);
            } else if (uuid.equals(flysightCharacteristicMode)) {
                processDsMode(value);
            } else {
                // Unknown characteristic, use legacy processBytes
                processBytes(peripheral, value);
            }
        } catch (Exception e) {
            Exceptions.report(e);
        }
    }
    
    /**
     * Handle DS_Mode characteristic update (device mode changes)
     * Mode values: 0=SLEEP, 1=ACTIVE, 2=CONFIG, 3=USB, 4=PAIRING, 5=START
     */
    private void processDsMode(@NonNull byte[] value) {
        if (value.length < 1) {
            Log.w(TAG, "DS_Mode value too short");
            return;
        }
        int previousMode = deviceMode;
        deviceMode = value[0] & 0xFF;
        Log.i(TAG, "Device mode: " + FlysightModeEvent.modeName(previousMode) + " -> " + FlysightModeEvent.modeName(deviceMode));
        
        // Post event for other components
        EventBus.getDefault().post(new FlysightModeEvent(deviceMode, previousMode));
        
        // If transitioning to ACTIVE mode, configure and subscribe to sensors
        if ((deviceMode == FlysightModeEvent.MODE_ACTIVE || deviceMode == FlysightModeEvent.MODE_START) 
                && !sensorsSubscribed && connectedPeripheral != null) {
            onDeviceBecameActive(connectedPeripheral);
        }
    }
    
    /**
     * Called when device enters ACTIVE mode - subscribe to sensor data streams.
     * Note: Divider configuration is NOT done automatically. Production devices use config.txt.
     * For dev devices, call configureSensorDividers() manually or through UI.
     */
    private void onDeviceBecameActive(@NonNull BluetoothPeripheral peripheral) {
        Log.i(TAG, "Device became active, subscribing to sensors...");
        
        // Note: Divider configuration removed from automatic flow.
        // Production devices have dividers set from config.txt.
        // Dev devices can call controlPoint.configureAllDividers() manually.
        
        // Subscribe to sensor data
        subscribeToSensors(peripheral);
        sensorsSubscribed = true;
    }

    @Override
    public void processBytes(@NonNull BluetoothPeripheral peripheral, @NonNull byte[] value) {
        // Legacy fallback - try to parse as GNSS
        processGnss(value);
    }

    /**
     * Parse GNSS measurement from SD_GNSS_Measurement characteristic
     */
    private void processGnss(@NonNull byte[] value) {
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
                Log.d(TAG, "flysight -> app: gps " + loc);
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
     * Parse accelerometer measurement from SD_ACCEL_Measurement characteristic
     * Buffers data until matching gyro arrives (via ImuCorrelator)
     */
    private void processAccel(@NonNull byte[] value) {
        // Log hex dump of raw packet for debugging
        StringBuilder hex = new StringBuilder();
        for (byte b : value) hex.append(String.format("%02X ", b));
        Log.d(TAG, "flysight accel raw: " + hex.toString().trim());
        imuCorrelator.onAccelReceived(value);
    }

    /**
     * Parse gyroscope measurement from SD_GYRO_Measurement characteristic
     * Combines with buffered accel data to emit MImuData
     */
    private void processGyro(@NonNull byte[] value) {
        // Log hex dump of raw packet for debugging
        StringBuilder hex = new StringBuilder();
        for (byte b : value) hex.append(String.format("%02X ", b));
        Log.d(TAG, "flysight gyro raw: " + hex.toString().trim());
        MImuData imu = imuCorrelator.onGyroReceived(value, timeSync);
        if (imu != null) {
            Log.d(TAG, "flysight -> app: " + imu);
            imuUpdates.post(imu);
        } else {
            Log.w(TAG, "flysight gyro: no matching accel found");
        }
    }

    /**
     * Parse magnetometer measurement from SD_MAG_Measurement characteristic
     * Format: [mask] [time?] [x,y,z?] [temp?]
     */
    private void processMag(@NonNull byte[] value) {
        if (value.length < 1) return;
        
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        int mask = buf.get() & 0xFF;
        
        // Parse timestamp
        long sensorTimeMs = 0;
        if ((mask & 0x80) != 0) {
            sensorTimeMs = Integer.toUnsignedLong(buf.getInt());
        }
        
        // Parse magnetometer data (mGauss -> Gauss)
        float magX = 0, magY = 0, magZ = 0;
        if ((mask & 0x40) != 0) {
            magX = buf.getShort() / 1000f;  // mGauss -> Gauss
            magY = buf.getShort() / 1000f;
            magZ = buf.getShort() / 1000f;
        }
        
        // Parse temperature
        float temperature = Float.NaN;
        if ((mask & 0x20) != 0) {
            temperature = buf.getShort() / 100f;  // 0.01°C -> °C
        }
        
        double sensorTimeSec = sensorTimeMs / 1000.0;
        MMagData mag = MMagData.fromSensorTime(sensorTimeSec, timeSync, magX, magY, magZ, temperature);
        Log.d(TAG, "flysight -> app: " + mag);
        magUpdates.post(mag);
    }

    /**
     * Parse barometer measurement from SD_BARO_Measurement characteristic
     * Format: [mask] [time?] [pressure?] [temp?]
     */
    private void processBaro(@NonNull byte[] value) {
        if (value.length < 1) return;
        
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        int mask = buf.get() & 0xFF;
        
        // Parse timestamp
        long sensorTimeMs = 0;
        if ((mask & 0x80) != 0) {
            sensorTimeMs = Integer.toUnsignedLong(buf.getInt());
        }
        
        // Parse pressure (Pa)
        float pressure = 0;
        if ((mask & 0x40) != 0) {
            pressure = buf.getInt();  // Pa
        }
        
        // Parse temperature
        float temperature = Float.NaN;
        if ((mask & 0x20) != 0) {
            temperature = buf.getShort() / 100f;  // 0.01°C -> °C
        }
        
        double sensorTimeSec = sensorTimeMs / 1000.0;
        MBaroData baro = MBaroData.fromSensorTime(sensorTimeSec, timeSync, pressure, temperature);
        Log.d(TAG, "flysight -> app: " + baro);
        baroUpdates.post(baro);
    }

    /**
     * Parse humidity measurement from SD_HUM_Measurement characteristic
     * Format: [mask] [time?] [humidity?] [temp?]
     */
    private void processHum(@NonNull byte[] value) {
        if (value.length < 1) return;
        
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        int mask = buf.get() & 0xFF;
        
        // Parse timestamp
        long sensorTimeMs = 0;
        if ((mask & 0x80) != 0) {
            sensorTimeMs = Integer.toUnsignedLong(buf.getInt());
        }
        
        // Parse humidity (0.1% RH -> %)
        float humidity = 0;
        if ((mask & 0x40) != 0) {
            humidity = (buf.getShort() & 0xFFFF) / 10f;  // uint16, 0.1% -> %
        }
        
        // Parse temperature
        float temperature = Float.NaN;
        if ((mask & 0x20) != 0) {
            temperature = buf.getShort() / 100f;  // 0.01°C -> °C
        }
        
        double sensorTimeSec = sensorTimeMs / 1000.0;
        MHumData hum = MHumData.fromSensorTime(sensorTimeSec, timeSync, humidity, temperature);
        Log.d(TAG, "flysight -> app: " + hum);
        humUpdates.post(hum);
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
