# FlySight 2 BLE Integration Summary

## Device Info
- **Firmware**: v2026.05.11.develop
- **MAC**: 45:81:C5:3E:80:FB

## Service UUIDs
- **Sensor Data Service**: `00000001-cc7a-482a-984a-7f2ed5b3e58f`

| Characteristic | UUID (short) | Props |
|----------------|--------------|-------|
| GNSS | 00000000 | READ NOTIFY |
| Control Point | 00000006 | WRITE INDICATE |
| Baro | 00000008 | READ NOTIFY |
| Accel | 00000009 | READ NOTIFY |
| Gyro | 0000000A | READ NOTIFY |
| Mag | 0000000B | READ NOTIFY |
| Humidity | 0000000C | READ NOTIFY |

## Connection Sequence (Critical Order)

1. Connect and discover services
2. Request MTU 250
3. Subscribe to Control Point (00000006) for INDICATE
4. **Send SET_BLE_DIVIDER commands BEFORE subscribing to sensors** (prevents queue flooding)
5. Subscribe to sensor characteristics

## SET_BLE_DIVIDER Protocol

Write to Control Point characteristic:
```
Command: 0x10 (SET_BLE_DIVIDER)
Payload: [sensor_id, divider_low, divider_high]
Response: 0xF0, 0x10, status (via INDICATE)
```

| Sensor ID | Sensor |
|-----------|--------|
| 0 | Baro |
| 1 | Humidity |
| 2 | Accel |
| 3 | Gyro |
| 4 | Mag |

- `divider=20` → ~10-20 Hz output from native 200-500 Hz sensors
- Must send dividers for ALL sensors (0-4) to prevent flooding

## Packet Formats

**Accel (19 bytes)**: `[marker, time_ms (4), ax (2), ay (2), az (2), temp (2), ...]`
**Gyro (27 bytes)**: `[marker, time_ms (4), gx (2), gy (2), gz (2), temp (2), quat_w (2), quat_x (2), quat_y (2), quat_z (2)]`
**Mag (13 bytes)**: `[marker, time_ms (4), mx (2), my (2), mz (2)]`

Marker bytes: Accel=0xE0, Gyro=0xF0

## Working Status

| Feature | Status |
|---------|--------|
| GPS | ✅ 5 Hz |
| Accel | ✅ ~15-20 Hz |
| Gyro | ✅ ~15-20 Hz |
| Mag | ✅ ~0.5 Hz |
| Baro | ✅ ~0.5 Hz |
| IMU Correlation | ✅ Accel+Gyro paired by timestamp |

## Remaining Issues

1. **Reconnection popup** - "Connect to FlySight" appears even when connected; possibly caused by heartbeat thread failing during connection transition
2. **IMU Correlator** - Uses fuzzy timestamp matching (100ms tolerance); works but could be fragile
3. **Quaternion** - Always returns (1,0,0,0); may require different firmware mode

## Key Files

- `Flysight2Protocol.java` - BLE protocol, divider commands, packet parsing
- `ImuCorrelator.java` - Pairs accel/gyro packets by timestamp
- `SensorParser.java` - Parses raw BLE packets to sensor values
