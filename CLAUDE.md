# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BASElineXR is a VR wingsuit skydiving application for Meta Quest devices (Quest 2, Quest Pro, Quest 3) that visualizes GPS flight paths in virtual reality. It uses the Meta Spatial SDK v0.7.0 for VR/XR development on top of Android.

## Build Commands

```bash
# Build
./gradlew build

# Install on connected Quest device
./gradlew installDebug

# Clean build
./gradlew clean
```

## Architecture

### Key Components

1. **app/src/main/java/com/platypii/baselinexr/BaselineActivity.kt** - Main VR activity entry point
2. **app/src/main/java/com/platypii/baselinexr/TerrainSystem.kt** - 3D model system for rendering terrain from DEM + satellite imagery
3. **app/src/main/java/com/platypii/baselinexr/GpsToWorldTransform.java** - Critical GPS to 3D coordinate transformation
4. **app/src/main/java/com/platypii/baselinexr/Services.java** - Central service management (GPS, Bluetooth, Cloud)
5. **app/src/main/java/com/platypii/baselinexr/VROptions.java** - Configuration management for VR scenes and display options
6. **app/src/main/assets/terrain/eiger_tile.json** - Terrain tile configuration for Eiger mountain model

### Coordinate System

The app transforms GPS coordinates (latitude/longitude/altitude) to VR world space:
- Uses a dynamic origin that updates based on current GPS location
- Implements spherical earth calculations for positioning relative to current location
- See `GpsToWorldTransform.java` for implementation details

### 3D Models

#### TerrainSystem (`TerrainSystem.kt`)
- Renders terrain tiles from JSON config, anchored to **GPS world coordinates**
- Supports dual LOD with high/low resolution models and custom shaders

#### HUD System (`HudSystem.kt`)
- GPS/speed display panel, anchored to **head position**, grabbable panel loaded via GLXF

#### DirectionArrow (`DirectionArrowSystem.kt`)
- Movement direction arrow, anchored to **head position** (5m below), when moving at least 2 mph

#### Target Panel (`TargetPanel.kt`)
- Shows a reticle on the target landing zone. Anchored to **head position** but oriented towards landing zone

### Service Architecture

- **Location Services**: Multiple providers (Android GPS, Bluetooth GPS, NMEA, Mock GPS)
- **Bluetooth**: Supports FlySight2 GPS devices via Blessed Android library

## Development Notes

- **Event System**: Uses EventBus for inter-component communication
- **Always Build**: Always build the project after making changes to ensure everything compiles correctly.
- **Live Testing**: The only real way to test things is for me to run them on my VR device as a user. When you think the code is working, ask the user to test it on the headset.

Javadocs for Spatial SDK: `meta-spatial-sdk-0.7.0-javadoc/`
Sample Spatial SDK projects: `Meta-Spatial-SDK-Samples/`
Quest Debugging Notes: `QUEST.md`

## Testing

Run `./gradlew test` to execute unit tests.

## Key Dependencies

- Meta Spatial SDK modules (base, vr, physics, toolkit, mruk, etc.)
- Blessed Android 2.5.0 (Bluetooth LE)
- Retrofit 2.9.0 + Gson (REST API)
- EventBus 3.3.1 (Event communication)
