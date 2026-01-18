# FlySight 2 BLE Sensor Streaming Protocol

This document describes the Bluetooth Low Energy (BLE) protocol for receiving real-time sensor data from the FlySight 2 device.

## Overview

The FlySight 2 streams sensor data via BLE notifications. To receive data, a client must:
1. Connect to the FlySight 2 device
2. Discover the Sensor_Data service
3. Enable notifications on desired characteristics
4. Parse incoming data packets

## BLE Service Information

### UUID Format
FlySight uses a custom 128-bit UUID base with 16-bit short IDs embedded:

```
Base UUID: 0000XXXX-8e22-4541-9d4c-21edae82ed19
```

Where `XXXX` is the 16-bit short ID.

### Sensor_Data Service
- **Short ID:** `0x0003`
- **Full UUID:** `00000003-8e22-4541-9d4c-21edae82ed19`

## Characteristics

All characteristics use **notifications** (not indications). Enable the Client Characteristic Configuration Descriptor (CCCD) by writing `0x0001` to subscribe.

### 1. IMU Measurement (Accelerometer + Gyroscope)

| Property | Value |
|----------|-------|
| Short ID | `0x0010` |
| Full UUID | `00000010-8e22-4541-9d4c-21edae82ed19` |
| Size | 30 bytes |
| Properties | Notify |

#### Data Format (Little-Endian)

| Offset | Size | Type | Field | Units | Description |
|--------|------|------|-------|-------|-------------|
| 0 | 4 | uint32 | time | ms | System timestamp |
| 4 | 4 | int32 | wx | 0.001 °/s | Gyroscope X (multiply by 0.001 for °/s) |
| 8 | 4 | int32 | wy | 0.001 °/s | Gyroscope Y |
| 12 | 4 | int32 | wz | 0.001 °/s | Gyroscope Z |
| 16 | 4 | int32 | ax | 0.001 g | Accelerometer X (multiply by 0.001 for g) |
| 20 | 4 | int32 | ay | 0.001 g | Accelerometer Y |
| 24 | 4 | int32 | az | 0.001 g | Accelerometer Z |
| 28 | 2 | int16 | temperature | 0.01 °C | IMU temperature |

#### Example Parsing (Python)
```python
import struct

def parse_imu(data: bytes) -> dict:
    time, wx, wy, wz, ax, ay, az, temp = struct.unpack('<IiiiiiiiH', data)
    # Note: temp is actually int16, fix the unpack
    time, wx, wy, wz, ax, ay, az, temp = struct.unpack('<Iiiiiiih', data)
    return {
        'time_ms': time,
        'gyro_dps': (wx * 0.001, wy * 0.001, wz * 0.001),
        'accel_g': (ax * 0.001, ay * 0.001, az * 0.001),
        'temperature_c': temp * 0.01
    }
```

---

### 2. Magnetometer Measurement

| Property | Value |
|----------|-------|
| Short ID | `0x0011` |
| Full UUID | `00000011-8e22-4541-9d4c-21edae82ed19` |
| Size | 12 bytes |
| Properties | Notify |

#### Data Format (Little-Endian)

| Offset | Size | Type | Field | Units | Description |
|--------|------|------|-------|-------|-------------|
| 0 | 4 | uint32 | time | ms | System timestamp |
| 4 | 2 | int16 | mx | mGauss | Magnetic field X |
| 6 | 2 | int16 | my | mGauss | Magnetic field Y |
| 8 | 2 | int16 | mz | mGauss | Magnetic field Z |
| 10 | 2 | int16 | temperature | 0.01 °C | Magnetometer temperature |

#### Example Parsing (Python)
```python
def parse_mag(data: bytes) -> dict:
    time, mx, my, mz, temp = struct.unpack('<Ihhhh', data)
    return {
        'time_ms': time,
        'mag_mGauss': (mx, my, mz),
        'temperature_c': temp * 0.01
    }
```

---

### 3. Barometer Measurement

| Property | Value |
|----------|-------|
| Short ID | `0x0012` |
| Full UUID | `00000012-8e22-4541-9d4c-21edae82ed19` |
| Size | 10 bytes |
| Properties | Notify |

#### Data Format (Little-Endian)

| Offset | Size | Type | Field | Units | Description |
|--------|------|------|-------|-------|-------------|
| 0 | 4 | uint32 | time | ms | System timestamp |
| 4 | 4 | uint32 | pressure | Pa | Atmospheric pressure in Pascals |
| 8 | 2 | int16 | temperature | 0.01 °C | Barometer temperature |

#### Example Parsing (Python)
```python
def parse_baro(data: bytes) -> dict:
    time, pressure, temp = struct.unpack('<IIh', data)
    return {
        'time_ms': time,
        'pressure_pa': pressure,
        'pressure_hpa': pressure / 100.0,  # Convert to hPa/mbar
        'temperature_c': temp * 0.01
    }
```

#### Altitude Calculation
```python
import math

def pressure_to_altitude(pressure_pa: float, sea_level_pa: float = 101325.0) -> float:
    """Convert pressure to altitude using barometric formula (meters)"""
    return 44330.0 * (1.0 - math.pow(pressure_pa / sea_level_pa, 0.1903))
```

---

### 4. Humidity Measurement

| Property | Value |
|----------|-------|
| Short ID | `0x0013` |
| Full UUID | `00000013-8e22-4541-9d4c-21edae82ed19` |
| Size | 8 bytes |
| Properties | Notify |

#### Data Format (Little-Endian)

| Offset | Size | Type | Field | Units | Description |
|--------|------|------|-------|-------|-------------|
| 0 | 4 | uint32 | time | ms | System timestamp |
| 4 | 2 | int16 | humidity | 0.01 %RH | Relative humidity |
| 6 | 2 | int16 | temperature | 0.01 °C | Humidity sensor temperature |

#### Example Parsing (Python)
```python
def parse_hum(data: bytes) -> dict:
    time, humidity, temp = struct.unpack('<Ihh', data)
    return {
        'time_ms': time,
        'humidity_percent': humidity * 0.01,
        'temperature_c': temp * 0.01
    }
```

---

### 5. Time Synchronization

| Property | Value |
|----------|-------|
| Short ID | `0x0014` |
| Full UUID | `00000014-8e22-4541-9d4c-21edae82ed19` |
| Size | 10 bytes |
| Properties | Notify |

This characteristic provides GPS time correlation with the system timestamp, enabling precise time synchronization across all sensor streams.

#### Data Format (Little-Endian)

| Offset | Size | Type | Field | Units | Description |
|--------|------|------|-------|-------|-------------|
| 0 | 4 | uint32 | time | ms | System timestamp (same timebase as sensors) |
| 4 | 4 | uint32 | tow | ms | GPS Time of Week |
| 8 | 2 | uint16 | week | weeks | GPS Week Number |

#### Example Parsing (Python)
```python
from datetime import datetime, timedelta

# GPS epoch: January 6, 1980
GPS_EPOCH = datetime(1980, 1, 6)

def parse_time_sync(data: bytes) -> dict:
    time, tow, week = struct.unpack('<IIH', data)
    
    # Convert GPS time to UTC (note: does not account for leap seconds)
    gps_time = GPS_EPOCH + timedelta(weeks=week, milliseconds=tow)
    
    return {
        'system_time_ms': time,
        'gps_tow_ms': tow,
        'gps_week': week,
        'gps_datetime': gps_time  # Approximate, ignoring leap seconds
    }
```

---

## Configuration

Sensor streaming is configured via the device's `config.txt` file on the SD card.

```ini
; Sensor rate settings

Baro_ODR:      1 ; Barometer output data rate
                 ;   0 = 1 Hz
                 ;   1 = 10 Hz
                 ;   2 = 25 Hz
                 ;   3 = 50 Hz
                 ;   4 = 75 Hz
                 ;   5 = 100 Hz
                 ;   6 = 200 Hz (one-shot)
                 ;   7 = 200 Hz (one-shot)
Hum_ODR:       1 ; Humidity sensor output data rate
                 ;   0 = 1 Hz
                 ;   1 = 7 Hz
                 ;   2 = 12.5 Hz
                 ;   3 = 12.5 Hz
Mag_ODR:       1 ; Magnetometer output data rate
                 ;   0 = 10 Hz
                 ;   1 = 20 Hz
                 ;   2 = 50 Hz
                 ;   3 = 100 Hz
Accel_ODR:     1 ; Accelerometer output data rate
                 ;   0 = 12.5 Hz
                 ;   1 = 26 Hz
                 ;   2 = 52 Hz
                 ;   3 = 104 Hz
                 ;   4 = 208 Hz
                 ;   5 = 416 Hz
                 ;   6 = 833 Hz
                 ;   7 = 1666 Hz
                 ;   8 = 3332 Hz
                 ;   9 = 6664 Hz
                 ;   10 = 1.6 Hz
Accel_FS:      3 ; Accelerometer full scale
                 ;   0 = 2 G
                 ;   1 = 4 G
                 ;   2 = 8 G
                 ;   3 = 16 G
Gyro_ODR:      1 ; Gyroscope output data rate
                 ;   0 = 12.5 Hz
                 ;   1 = 26 Hz
                 ;   2 = 52 Hz
                 ;   3 = 104 Hz
                 ;   4 = 208 Hz
                 ;   5 = 416 Hz
                 ;   6 = 833 Hz
                 ;   7 = 1666 Hz
                 ;   8 = 3332 Hz
                 ;   9 = 6664 Hz
Gyro_FS:       3 ; Gyroscope full scale
                 ;   0 = 250 dps
                 ;   1 = 500 dps
                 ;   2 = 1000 dps
                 ;   3 = 2000 dps

; BLE sensor streaming

Enable_BLE_IMU:  1 ; Stream IMU data over BLE
Enable_BLE_Mag:  1 ; Stream magnetometer data over BLE
Enable_BLE_Baro: 1 ; Stream barometer data over BLE
Enable_BLE_Hum:  1 ; Stream humidity data over BLE
Enable_BLE_Time: 1 ; Stream time sync data over BLE
```

---

## Android Implementation (BLESSED Library)

This section shows how to integrate sensor streaming into `Flysight2Protocol.java` using the BLESSED BLE library.

### Add UUID Constants

```java
// Add to Flysight2Protocol.java alongside existing UUIDs

// Sensor Data service (0x0003)
private static final UUID sensorDataService = UUID.fromString("00000003-8e22-4541-9d4c-21edae82ed19");

// Sensor characteristics
private static final UUID charIMU = UUID.fromString("00000010-8e22-4541-9d4c-21edae82ed19");
private static final UUID charMag = UUID.fromString("00000011-8e22-4541-9d4c-21edae82ed19");
private static final UUID charBaro = UUID.fromString("00000012-8e22-4541-9d4c-21edae82ed19");
private static final UUID charHum = UUID.fromString("00000013-8e22-4541-9d4c-21edae82ed19");
private static final UUID charTimeSync = UUID.fromString("00000014-8e22-4541-9d4c-21edae82ed19");
```

### Subscribe to Sensor Notifications

```java
// Add to onMtuChanged() after existing setNotify call

@Override
public void onMtuChanged(@NonNull BluetoothPeripheral peripheral, int mtu, @NonNull GattStatus status) {
    Log.i(TAG, "flysight mtu changed " + mtu);
    // Subscribe to GNSS (existing)
    peripheral.setNotify(flysightService1, flysightCharacteristicGNSS, true);
    // Subscribe to sensor streams
    peripheral.setNotify(sensorDataService, charIMU, true);
    peripheral.setNotify(sensorDataService, charMag, true);
    peripheral.setNotify(sensorDataService, charBaro, true);
    peripheral.setNotify(sensorDataService, charHum, true);
    peripheral.setNotify(sensorDataService, charTimeSync, true);
}
```

### Handle Sensor Data in onCharacteristicUpdate

```java
// Override onCharacteristicUpdate to dispatch by characteristic UUID

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
```

### Sensor Parsing Methods

```java
// Add these parsing methods to Flysight2Protocol.java

private void processIMU(@NonNull byte[] value) {
    if (value.length < 30) return;
    final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
    
    final long time = buf.getInt() & 0xFFFFFFFFL;  // system timestamp (ms)
    final double wx = buf.getInt() * 0.001;  // gyro X (°/s)
    final double wy = buf.getInt() * 0.001;  // gyro Y
    final double wz = buf.getInt() * 0.001;  // gyro Z
    final double ax = buf.getInt() * 0.001;  // accel X (g)
    final double ay = buf.getInt() * 0.001;  // accel Y
    final double az = buf.getInt() * 0.001;  // accel Z
    final double temp = buf.getShort() * 0.01;  // temperature (°C)
    
    Log.d(TAG, String.format("IMU t=%d gyro=(%.2f,%.2f,%.2f) accel=(%.3f,%.3f,%.3f)", 
            time, wx, wy, wz, ax, ay, az));
    // TODO: Post to your sensor listeners
}

private void processMag(@NonNull byte[] value) {
    if (value.length < 12) return;
    final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
    
    final long time = buf.getInt() & 0xFFFFFFFFL;
    final short mx = buf.getShort();  // mGauss
    final short my = buf.getShort();
    final short mz = buf.getShort();
    final double temp = buf.getShort() * 0.01;
    
    Log.d(TAG, String.format("MAG t=%d field=(%d,%d,%d) mGauss", time, mx, my, mz));
}

private void processBaro(@NonNull byte[] value) {
    if (value.length < 10) return;
    final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
    
    final long time = buf.getInt() & 0xFFFFFFFFL;
    final long pressure = buf.getInt() & 0xFFFFFFFFL;  // Pa
    final double temp = buf.getShort() * 0.01;
    
    // Convert to altitude (meters)
    final double altitude = 44330.0 * (1.0 - Math.pow(pressure / 101325.0, 0.1903));
    
    Log.d(TAG, String.format("BARO t=%d pressure=%d Pa alt=%.1f m", time, pressure, altitude));
}

private void processHum(@NonNull byte[] value) {
    if (value.length < 8) return;
    final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
    
    final long time = buf.getInt() & 0xFFFFFFFFL;
    final double humidity = buf.getShort() * 0.01;  // %RH
    final double temp = buf.getShort() * 0.01;
    
    Log.d(TAG, String.format("HUM t=%d humidity=%.1f%% temp=%.1f°C", time, humidity, temp));
}

private void processTimeSync(@NonNull byte[] value) {
    if (value.length < 10) return;
    final ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
    
    final long systemTime = buf.getInt() & 0xFFFFFFFFL;  // device time (ms)
    final long gpsTow = buf.getInt() & 0xFFFFFFFFL;      // GPS time of week (ms)
    final int gpsWeek = buf.getShort() & 0xFFFF;
    
    // Convert to epoch millis (same pattern as your existing GNSS code)
    final long millis = gpsWeek * millisecondsPerWeek + gpsTow + gpsEpochMilliseconds;
    
    Log.d(TAG, String.format("TIME sys=%d gps_week=%d tow=%d -> %d", 
            systemTime, gpsWeek, gpsTow, millis));
}
```

---

## Python Example

```python
import asyncio
from bleak import BleakClient, BleakScanner
import struct

# FlySight 2 UUIDs
SENSOR_DATA_SERVICE = "00000003-8e22-4541-9d4c-21edae82ed19"
IMU_CHAR = "00000010-8e22-4541-9d4c-21edae82ed19"
MAG_CHAR = "00000011-8e22-4541-9d4c-21edae82ed19"
BARO_CHAR = "00000012-8e22-4541-9d4c-21edae82ed19"
HUM_CHAR = "00000013-8e22-4541-9d4c-21edae82ed19"
TIME_SYNC_CHAR = "00000014-8e22-4541-9d4c-21edae82ed19"

def handle_imu(sender, data: bytearray):
    time, wx, wy, wz, ax, ay, az, temp = struct.unpack('<Iiiiiiih', bytes(data))
    print(f"IMU t={time}ms gyro=({wx*0.001:.2f}, {wy*0.001:.2f}, {wz*0.001:.2f}) °/s "
          f"accel=({ax*0.001:.3f}, {ay*0.001:.3f}, {az*0.001:.3f}) g")

def handle_mag(sender, data: bytearray):
    time, mx, my, mz, temp = struct.unpack('<Ihhhh', bytes(data))
    print(f"MAG t={time}ms field=({mx}, {my}, {mz}) mGauss")

def handle_baro(sender, data: bytearray):
    time, pressure, temp = struct.unpack('<IIh', bytes(data))
    print(f"BARO t={time}ms pressure={pressure}Pa ({pressure/100:.2f}hPa) temp={temp*0.01:.1f}°C")

def handle_hum(sender, data: bytearray):
    time, humidity, temp = struct.unpack('<Ihh', bytes(data))
    print(f"HUM t={time}ms humidity={humidity*0.01:.1f}% temp={temp*0.01:.1f}°C")

def handle_time_sync(sender, data: bytearray):
    time, tow, week = struct.unpack('<IIH', bytes(data))
    print(f"TIME t={time}ms GPS week={week} tow={tow}ms")

async def main():
    # Scan for FlySight device
    print("Scanning for FlySight...")
    device = await BleakScanner.find_device_by_name("FlySight")
    
    if not device:
        print("FlySight not found!")
        return
    
    print(f"Found {device.name} at {device.address}")
    
    async with BleakClient(device) as client:
        print("Connected!")
        
        # Subscribe to all sensor characteristics
        await client.start_notify(IMU_CHAR, handle_imu)
        await client.start_notify(MAG_CHAR, handle_mag)
        await client.start_notify(BARO_CHAR, handle_baro)
        await client.start_notify(HUM_CHAR, handle_hum)
        await client.start_notify(TIME_SYNC_CHAR, handle_time_sync)
        
        print("Subscribed to all sensors. Receiving data...")
        
        # Run for 30 seconds
        await asyncio.sleep(30)
        
        print("Done!")

if __name__ == "__main__":
    asyncio.run(main())
```

---

## Swift Example (iOS/macOS)

```swift
import CoreBluetooth

class FlySightBLEManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    
    // UUIDs
    let sensorDataServiceUUID = CBUUID(string: "00000003-8e22-4541-9d4c-21edae82ed19")
    let imuCharUUID = CBUUID(string: "00000010-8e22-4541-9d4c-21edae82ed19")
    let magCharUUID = CBUUID(string: "00000011-8e22-4541-9d4c-21edae82ed19")
    let baroCharUUID = CBUUID(string: "00000012-8e22-4541-9d4c-21edae82ed19")
    let humCharUUID = CBUUID(string: "00000013-8e22-4541-9d4c-21edae82ed19")
    let timeSyncCharUUID = CBUUID(string: "00000014-8e22-4541-9d4c-21edae82ed19")
    
    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value else { return }
        
        switch characteristic.uuid {
        case imuCharUUID:
            parseIMU(data)
        case magCharUUID:
            parseMag(data)
        case baroCharUUID:
            parseBaro(data)
        case humCharUUID:
            parseHum(data)
        case timeSyncCharUUID:
            parseTimeSync(data)
        default:
            break
        }
    }
    
    func parseIMU(_ data: Data) {
        guard data.count >= 30 else { return }
        
        let time = data.withUnsafeBytes { $0.load(fromByteOffset: 0, as: UInt32.self) }
        let wx = data.withUnsafeBytes { $0.load(fromByteOffset: 4, as: Int32.self) }
        let wy = data.withUnsafeBytes { $0.load(fromByteOffset: 8, as: Int32.self) }
        let wz = data.withUnsafeBytes { $0.load(fromByteOffset: 12, as: Int32.self) }
        let ax = data.withUnsafeBytes { $0.load(fromByteOffset: 16, as: Int32.self) }
        let ay = data.withUnsafeBytes { $0.load(fromByteOffset: 20, as: Int32.self) }
        let az = data.withUnsafeBytes { $0.load(fromByteOffset: 24, as: Int32.self) }
        let temp = data.withUnsafeBytes { $0.load(fromByteOffset: 28, as: Int16.self) }
        
        print("IMU: gyro=(\(Double(wx)*0.001), \(Double(wy)*0.001), \(Double(wz)*0.001)) °/s")
        print("     accel=(\(Double(ax)*0.001), \(Double(ay)*0.001), \(Double(az)*0.001)) g")
    }
    
    // Similar parsing for other characteristics...
}
```

---

## Notes

1. **Endianness:** All multi-byte values are **little-endian** (LSB first)
2. **Timestamps:** The `time` field uses a common system timebase across all sensors
3. **Time Sync:** Use the Time Sync characteristic to correlate system time with GPS time
4. **BLE Bandwidth:** At high sample rates, not all samples may be transmitted. The device prioritizes newer data.
5. **Connection Interval:** Request a short connection interval (7.5-15ms) for best throughput

---

## Existing Characteristics (Reference)

The FlySight 2 also has other BLE services/characteristics not covered here:

| Service | Short ID | Description |
|---------|----------|-------------|
| GNSS_Info | 0x0001 | GNSS position, velocity, status |
| Start_Control | 0x0002 | Start/stop control, results |
| File_Transfer | 0x0004 | File upload/download |
| Device_State | 0x0005 | Mode, control point |
| Battery | 0x0006 | Battery level |

See the full FlySight 2 BLE specification for details on these services.
