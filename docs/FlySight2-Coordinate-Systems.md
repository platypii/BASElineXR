# FlySight 2 Coordinate Systems

This document describes the coordinate systems used by the FlySight 2 sensor fusion and how orientation data is interpreted in BASElineXR.

## NWU World Frame

The FlySight 2 sensor fusion outputs quaternions in the **NWU (North-West-Up)** coordinate frame:

- **+X** → East
- **+Y** → North  
- **+Z** → Up (opposite to gravity)

## Device Axes at Identity Quaternion

When the FlySight 2 reports identity quaternion `(1, 0, 0, 0)`, the device is:

- Lying **flat** on a surface (Z axis aligned with world Up)
- **Light/LED side (+Y local)** pointing **North**
- **Right edge (+X local)** pointing **East**
- **Top face (+Z local)** pointing **Up**

```
          N (+Y world)
          ↑
          |    LED/Light
    +-----|-----+
    |     |     |
    |     ●     |  → E (+X world)
    |   (USB)   |
    +───────────+
          
    (Device flat, viewed from above)
    USB port faces South at identity
```

## Euler Angles (Rotation About Axes)

The Raw Sensor Data panel displays Euler angles as **rotations about each axis**, starting from the identity position:

| Label | Axis | World Direction | Rotation Sense (Right-Hand Rule) |
|-------|------|-----------------|----------------------------------|
| **↻X** | X axis | East | Thumb points East, fingers curl positive |
| **↻Y** | Y axis | North | Thumb points North, fingers curl positive |
| **↻Z** | Z axis | Up | Thumb points Up, fingers curl positive |

### Physical Interpretation

Starting from identity (flat, +Y=North):

- **↻X = +90°**: Device tilts so the **North side (+Y) rotates upward**, South side down. Like doing a backflip to the North.

- **↻X = -90°**: Device tilts so the **South side rotates upward**, North side down.

- **↻Y = +90°**: Device tilts so the **East side (+X) rotates downward**, West side up. Like doing a cartwheel to the East.

- **↻Y = -90°**: Device tilts so the **West side rotates downward**, East side up.

- **↻Z = +90°**: Device rotates so the **+Y (light) now faces West**. Counterclockwise when viewed from above.

- **↻Z = -90°**: Device rotates so the **+Y (light) now faces East**. Clockwise when viewed from above.

## Quaternion to Euler Conversion

The Euler angles are computed using intrinsic ZYX rotation order:

```kotlin
// Roll (rotation about X axis / East)
val sinr = 2.0 * (qw * qx + qy * qz)
val cosr = 1.0 - 2.0 * (qx * qx + qy * qy)
val rotX = atan2(sinr, cosr)

// Pitch (rotation about Y axis / North)
val sinp = 2.0 * (qw * qy - qz * qx)
val rotY = asin(clamp(sinp, -1, 1))

// Yaw (rotation about Z axis / Up)
val siny = 2.0 * (qw * qz + qx * qy)
val cosy = 1.0 - 2.0 * (qy * qy + qz * qz)
val rotZ = atan2(siny, cosy)
```

## Mounted Configuration

When mounted on a pilot (typical wingsuit configuration):

- Device is rotated **-90° about the X axis** from identity
- In mounted position:
  - **+X** → East (unchanged)
  - **+Y** → Up (pilot's head direction)
  - **+Z** → South (pilot faces -Z = North)

The quaternion from sensor fusion represents the **absolute NWU orientation**, not pilot-relative. To get pilot-centric values (heading, pitch, roll of the pilot), you would need to:

1. Pre-multiply by the inverse mount rotation, OR
2. Transform the Euler angles after extraction

For the Raw Sensor Data panel, we display **raw NWU Euler angles** without mount compensation, which is useful for:
- Verifying sensor fusion is working correctly
- Debugging device orientation
- Understanding the raw sensor output

Flight displays (HUD, AHRS) should apply mount rotation for pilot-intuitive values.

## Quick Reference

| Device Position | Expected Values |
|-----------------|-----------------|
| Flat, LED=North | ↻X≈0°, ↻Y≈0°, ↻Z≈0° |
| Flat, LED=East | ↻X≈0°, ↻Y≈0°, ↻Z≈-90° |
| Flat, LED=West | ↻X≈0°, ↻Y≈0°, ↻Z≈+90° |
| Standing on USB (LED up, facing N) | ↻X≈+90°, ↻Y≈0°, ↻Z≈0° |
| Standing on LED (USB up, facing N) | ↻X≈-90°, ↻Y≈0°, ↻Z≈0° |

## Related Files

- [ImuCorrelator.java](../app/src/main/java/com/platypii/baselinexr/bluetooth/ImuCorrelator.java) - Quaternion parsing
- [SensorDataSystem.kt](../app/src/main/java/com/platypii/baselinexr/SensorDataSystem.kt) - Euler display
- [FusionAhrs.kt](../app/src/main/java/com/platypii/baselinexr/ahrs/FusionAhrs.kt) - Alternative AHRS implementation
- [ble.md](../ble.md) - FlySight 2 BLE protocol documentation
