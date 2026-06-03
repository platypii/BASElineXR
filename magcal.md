# MagCal Design — ST Micro MotionFX Integration

## Overview

The FlySight 2 firmware runs ST Micro's **MotionFX** sensor fusion library, which includes its own magnetometer calibration algorithm (a sphere fitter). It accumulates raw mag samples, fits a hard-iron offset, and stores the result in `MAGCAL.BIN` on the SD card. The headset's job is to **fetch** that calibration and **apply** it to the raw mag vectors it draws — not to compute its own calibration.

The previous BASElineXR system ran an independent sphere-fitting algorithm on the headset (MinMax + ellipsoid fit), visualized the samples, and pushed the result back to the FlySight via `SD_CMD_SET_FUSION_MAG_HARD (0x20)`. This is now redundant and should be removed once the MotionFX path is working reliably.

---

## Phase 1 — GET_MAG_CAL button + quality display

### New BLE Command: `SD_CMD_GET_MAG_CAL (0x34)`

**Documented in:** `ble.md` → Sensor_Data Service → SD_Control_Point

| Field | Value |
|---|---|
| Opcode | `0x34` |
| Payload | none (1 byte total) |
| Valid modes | Any connected mode (Idle, Active, Start) |

**Response:** `[0xF0][0x34][0x01][hx i16 LE][hy i16 LE][hz i16 LE][quality u8]` — 10 bytes total  
Data bytes (7 bytes after status):
- `hx`, `hy`, `hz` — hard iron offsets in **milligauss** (int16_t, little-endian)
- `quality` — `0`=UNKNOWN, `1`=POOR, `2`=OK, `3`=GOOD

Hard iron values are zero when quality is UNKNOWN (no calibration yet).  
Calibration formula: `calibrated_enu = raw_mag - hard_iron`

### Changes needed

#### `Flysight2ControlPoint.java`
- Add constant: `SD_CMD_GET_MAG_CAL = 0x34`
- Add method `getMagCal()` — same pattern as `getSensorOdrs()` / `getRates()`

#### New data holder
A simple Kotlin data class (e.g., `DeviceMagCal.kt`) to hold the last fetched calibration. Could live in `bluetooth/` or `calibration/`:

```kotlin
data class DeviceMagCal(
    val hardIronX: Float,   // gauss (converted from milligauss)
    val hardIronY: Float,
    val hardIronZ: Float,
    val quality: Int        // 0=UNKNOWN, 1=POOR, 2=OK, 3=GOOD
) {
    val qualityLabel: String get() = when (quality) {
        0 -> "UNKNOWN"; 1 -> "POOR"; 2 -> "OK"; 3 -> "GOOD"; else -> "?"
    }
    val qualityColor: String get() = when (quality) {
        0 -> "#888888"; 1 -> "#FF8844"; 2 -> "#FFCC44"; 3 -> "#44FF88"; else -> "#888888"
    }
    fun apply(rawX: Float, rawY: Float, rawZ: Float) =
        Triple(rawX - hardIronX, rawY - hardIronY, rawZ - hardIronZ)
}
```

Store the latest fetched value somewhere globally accessible — either as a field on `Flysight2Protocol` or a singleton companion object.

#### `ControlPointSystem.kt`
- Add `SD_CMD_GET_MAG_CAL = 0x34` constant
- Add `getMagCalButton: Button?` field, param, assignment, listener
- Add `onGetMagCalClick()` method
- Add `when` case for `0x34`:
  - Parse 7 data bytes: `hx = i16le(0)/1000f`, `hy = i16le(2)/1000f`, `hz = i16le(4)/1000f`, `quality = data[6]`
  - Store into the global `DeviceMagCal` holder
  - `appendLog("MagCal: hx=${hx}mG hy=${hy}mG hz=${hz}mG qual=${qualityLabel}")`

#### `control_point_panel.xml`
- Add a 5th button "Get MagCal" to the Config Query row (same row as Get ODRs / Get Rates / Get BLE BW / Reset MagCal)
- Or: move Reset MagCal and Get MagCal into a new "Mag Cal" sub-row with two buttons side by side. Probably cleaner given we already have 4 in the row.

**Suggested layout:** New row labelled "Mag Cal" with:
- `get_mag_cal_button` "Get MagCal" (#FFCC44)
- `reset_mag_cal_button` "Reset MagCal" (#FF8844) ← move from existing row

#### `BaselineActivity.kt`
- Find and pass `get_mag_cal_button`

### Quality display in Raw Sensor Data view

The raw sensor data panel (`RawSensorVisualizationSystem`, displayed as part of `showRawSensor`) should show:
- A `TextView` with the calibration quality: `"MagCal: GOOD"` in the matching color  
- Possibly also show the hard iron XYZ values: `"hi=(12, -45, 8)mG"`
- This should update whenever a new `DeviceMagCal` is fetched and stored

The layout for the raw sensor data panel is in `app/src/main/res/layout/` — needs a `TextView` for quality label. The system already receives sensor updates; it just needs to check the global `DeviceMagCal` on each `execute()` tick (or via an EventBus event when a new fetch arrives).

**For now:** A manual "Get MagCal" button in the ControlPoint panel is sufficient. Periodic auto-polling (~5–10 s) can be added in a later phase.

---

## Phase 2 — Apply hard iron to displayed vectors

The bug: mag arrows in both visualization systems currently draw **raw uncalibrated** mag field. This makes the vector unreliable as a north reference.

### `RawSensorVisualizationSystem.kt`

In `updateMagArrow()` and the headset-anchored mag arrow section:

```kotlin
// Before:
val magVec = Vector3(mag.magX, mag.magY, mag.magZ)

// After:
val cal = DeviceMagCal.latest  // or however it's accessed
val magVec = if (cal != null) {
    val (cx, cy, cz) = cal.apply(mag.magX, mag.magY, mag.magZ)
    Vector3(cx, cy, cz)
} else {
    Vector3(mag.magX, mag.magY, mag.magZ)
}
```

Apply the same correction in **both** the FlySight-fusion-driven arrow (`magArrowEntity`) and the headset-anchored arrow (`headMagArrowEntity`). Both draw the same raw mag data, both need the same offset.

**Note on coordinate frame:** The firmware hard iron is in the **sensor body frame (ENU)**, same frame as the raw `mag.magX/Y/Z` coming off the BLE characteristic. The subtraction should be applied before any axis remapping or rotation. Check `REMAP_X/Y/Z` in `RawSensorVisualizationSystem` — the remap happens after this step so order is: apply hard iron → remap axes → rotate by quaternion.

### `AhrsVisualizationSystem.kt`

The AHRS viz system draws a `magArrow` — this uses the mag vector after it passes through `FusionAhrsAdapter`. The adapter currently applies its own `MagCalibration` (our old app-side calibration). Once we remove that, the raw vector will be used instead. Apply the same `DeviceMagCal` offset here before the AHRS transform.

The mag data flows through `FusionAhrsAdapter` → `AhrsSystem` → `AhrsVisualizationSystem`. The cleanest place to subtract hard iron is in `FusionAhrsAdapter` when it processes incoming mag samples — before passing them to the MotionFX algorithm inputs and before storing them for the vis system.

---

## Phase 3 — Remove old app-side calibration system

These are the files and code to remove once Phase 1 and 2 are working and the MotionFX path is confirmed reliable on-device.

### Files to delete entirely

| File | Why |
|---|---|
| `calibration/MagCalibrationSystem.kt` | Entire VR panel for sphere-fitting mag calibration |
| `calibration/MagCalibrationResult.kt` | Data class for our computed calibration result |
| `calibration/MagCalibrator.kt` | Min/max and ellipsoid sphere-fitting algorithm |
| `calibration/MagRingBuffer.kt` | Ring buffer for live mag sample collection |
| `calibration/CalibrationStorage.kt` | Saving/loading our computed calibration to Android storage |

### Code to remove in remaining files

#### `Flysight2ControlPoint.java`
- Remove `SD_CMD_SET_FUSION_MAG_HARD = 0x20` constant
- Remove `SD_CMD_SET_FUSION_MAG_SOFT = 0x21` constant
- Remove `setMagHardIron()` method
- Update file-level Javadoc comment

#### `ControlPointSystem.kt` (constants mirror)
- Remove `SD_CMD_SET_FUSION_MAG_HARD` and `SD_CMD_SET_FUSION_MAG_SOFT` if they appear
- Remove any `when` response cases for `0x20` and `0x21`

#### `FusionAhrsAdapter.kt`
- Remove `MagCalibration` inner data class
- Remove `setMagCalibration()` method
- Remove `setSoftIronMatrix()` method
- Remove the `magCalibration` and `softIronMatrix` fields
- Remove the calibration application step in the mag processing path

The mag processing in `FusionAhrsAdapter` will be simplified to: apply `DeviceMagCal` hard iron offset (Phase 2) → feed to MotionFX, no more complex matrix math.

#### `BaselineActivity.kt`
- Remove `magCalibrationSystem` field, initialization, and any `setViews()` call for it

#### Scene file (`Main.scene` / `Composition/Main.scene`)
- Remove `MagCalibrationPanel` node (the VR panel for the sphere visualization)

#### Layout
- Remove `app/src/main/res/layout/mag_calibration_panel.xml` (the Android layout for that panel)
- Remove any navigation entries referencing it

#### Unrelated UI cleanup
- The `MagCalibrationSystem` has "Apply Hard Iron" and "Apply Full" buttons that pushed our computed result to the FlySight via `setMagHardIron()`. Both the buttons and the BLE send path are removed.

---

## Phase 4 — Future / nice to have

- **Auto-poll**: After connecting, fetch `SD_CMD_GET_MAG_CAL` once immediately, then every ~5–10 s while connected. Store last quality and broadcast via EventBus. The raw sensor view can subscribe and auto-update the quality label without needing a manual button press.

- **In-flight quality indicator**: If a flight is in progress (Active mode), show a small mag-quality badge (GOOD/OK/POOR/UNKNOWN) somewhere on the HUD. Athletes calibrating in a new environment would see the quality improve in real time as they tumble the device.

- **Soft iron from device**: The firmware currently only exposes hard iron via `0x34`. If soft iron is ever added to the BLE protocol, the heading accuracy could improve further (soft iron corrects for device-body magnetic distortion). For now, hard iron alone is a significant improvement over nothing.

- **Accel vector accuracy**: The accel arrows also need review. The current `REMAP_X/Y/Z` values in `RawSensorVisualizationSystem` (`2, 3, -1`) are manually tuned empirically. These should be validated against the FlySight hardware axis documentation once the mag vector is known-good (since with both vectors correct, the orientation should look right from all angles).

---

## Open questions — resolved

1. **Coordinate frame of device hard iron**: The physical sensor is mounted on the bottom of the board, so raw mag arrives in "W N Down" frame (East and Up are flipped). The quaternion is corrected internally to ENU. The hard iron calibration is **saved in the ENU device frame** (after that flip). Therefore raw mag data must be transformed from sensor body → ENU device frame before subtracting the hard iron offset, using the same axis transformation already applied to the quaternion.

2. **`DeviceMagCal` storage location**: Field on `Services` — simple, accessible from all visualization systems each frame.

3. **Raw sensor view panel layout**: One line for the quality badge; also show the raw hard iron XYZ values on the same or an adjacent line. If MotionFX is not running on device, the response will have quality=UNKNOWN (0) and zero offsets — that state is already handled.

4. **When to remove Phase 3 code**: Only after Phase 1 (Get MagCal button + quality display) AND Phase 2 (corrected vectors) have been tested on headset and confirmed working. Phase 3 is the last step.
