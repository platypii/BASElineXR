# BASElineXR Threading Architecture

This document explains the threading model used in BASElineXR, particularly for GPS and sensor data playback during mock/replay mode.

## Overview

The app uses a **generation-based cooperative shutdown pattern** to manage thread lifecycles when configuration changes (like switching tracks or modes via the HUD menu).

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Main Thread (UI)                             │
│  • HUD button press → VROptions.current = new mode                   │
│  • Services.restart() / LocationService.restart()                    │
└───────────────────────────────────────┬──────────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
        ┌───────────────────┐ ┌─────────────────┐ ┌─────────────────┐
        │ MockLocation-     │ │ MockSensor-     │ │ PubSub-Async    │
        │ Playback Thread   │ │ Playback Thread │ │ (ExecutorService│
        └───────────────────┘ └─────────────────┘ └─────────────────┘
                    │                   │
                    └─────────┬─────────┘
                              ▼
                  ┌─────────────────────┐
                  │  PlaybackTimeline   │
                  │    (Singleton)      │
                  │  • generation++     │
                  │  • time sync        │
                  └─────────────────────┘
```

## The Generation Pattern

The core mechanism for clean thread shutdown is the **generation counter**. Each provider has a local generation counter that increments on every `start()`:

```java
// MockLocationProvider.java
private int generation = 0;

private void startInternal(...) {
    generation++;                        // ← Increment on start
    final int myGeneration = generation; // ← Capture for this thread
    
    playbackThread = new Thread(() -> {
        for (MLocation loc : all) {
            // Check if superseded by newer generation
            if (generation != myGeneration) break;  // ← Exit if stale
            ...
        }
    });
}
```

### PlaybackTimeline Generation

The `PlaybackTimeline` singleton also has its own generation counter that signals all playback threads to stop:

```java
// PlaybackTimeline.java
private volatile int generation = 0;

public void reset() {
    synchronized (lock) {
        generation++;  // ← Signal all threads to stop
        ready = false;
        ...
    }
}

public void seekTo(long positionMs) {
    synchronized (lock) {
        generation++;  // ← Also increments on seek
        ...
    }
}
```

Threads capture both their local generation AND the timeline generation at startup, then check both in their loop:

```java
final int myGeneration = generation;
final int timelineGeneration = timeline.getGeneration();

playbackThread = new Thread(() -> {
    for (...) {
        // Exit if either generation changed
        if (!started || generation != myGeneration || 
            timeline.getGeneration() != timelineGeneration) break;
        ...
    }
});
```

## Thread Lifecycle on Track/Mode Switch

When the user changes mode (e.g., via HUD button):

### 1. UI Thread - Mode Change
```java
VROptions.current = VROptionsList.getNextMode(...)
```

### 2. UI Thread - Service Restart
```java
LocationService.restart()  // or Services.restart()
```

### 3. LocationService.stop()
- Unsubscribes from provider updates
- Calls `MockLocationProvider.stop()`
- `MockLocationProvider.stop()` sets `started = false`

### 4. PlaybackTimeline.reset()
- `generation++` (signals all threads)
- `ready = false` (blocks new threads until init)

### 5. Old Threads Detect Shutdown
- Check: `generation != myGeneration` → break out of loop
- Check: `timeline.getGeneration() != timelineGeneration` → break
- Log: `"Mock location thread superseded by newer generation"`

### 6. LocationService.start()
- Creates new MockLocationProvider
- `generation++` (new local generation)
- Loads new track from `MockTrackOptions.current`
- Calls `timeline.init()` with first GPS timestamp
- Spawns new `"MockLocation-Playback"` thread

### 7. New Thread Begins
- Starts emitting data with new generation number
- Old thread has already exited or will exit shortly

## Key Components

| Component | Thread Name | Role |
|-----------|-------------|------|
| `PlaybackTimeline` | Shared singleton | Central clock + generation tracking |
| `MockLocationProvider` | `MockLocation-Playback` | Emits GPS fixes at real-time pace |
| `MockSensorProvider` | `MockSensor-Playback` | Emits IMU/Mag/Baro at ~30Hz |
| `LocationService` | Main thread | Orchestrates GPS provider start/stop |
| `SensorService` | Main thread | Orchestrates sensor provider start/stop |
| `PubSub` | `PubSub-Async` | Delivers async events via ExecutorService |
| `BluetoothService` | `BluetoothThread` | Handles BLE communication |
| `PlotSurface` | Drawing thread | Chart rendering |

## GPS/Sensor Synchronization

Both `MockLocationProvider` and `MockSensorProvider` share the `PlaybackTimeline` singleton for synchronized playback:

### Initialization Race
1. **First provider to load** calls `timeline.init(firstTimestamp, "source")`
2. Second provider sees `timeline.isReady() == true` and uses existing offset
3. `lock.wait()` / `lock.notifyAll()` used to coordinate startup

### Synchronized Playback
Both providers:
- Calculate elapsed time from `timeline.getElapsedSinceStart()`
- Sleep until `dataElapsed > elapsed` to pace data emission
- Check `timeline.getGeneration()` to detect resets/seeks

```java
// Both threads use the same timing logic:
final long elapsed = timeline.getElapsedSinceStart();
if (dataElapsed > elapsed) {
    Thread.sleep(dataElapsed - elapsed);
}
```

### Time Conversion
GPS epoch timestamps are converted to phone time for downstream consumers:

```java
// PlaybackTimeline converts GPS epoch → phone time
loc.millis = timeline.toPhoneTime(loc.millis);
```

The offset is calculated once at init:
```java
timeDelta = systemStartTime - trackStartTimeGps;
// phoneTime = gpsEpoch + timeDelta
```

## Thread Safety Mechanisms

| Mechanism | Purpose |
|-----------|---------|
| `volatile int generation` | Ensures visibility across threads |
| `synchronized (lock)` | Protects PlaybackTimeline state changes |
| `Thread.interrupt()` | Wakes sleeping threads on stop |
| `generation != myGeneration` | Cooperative shutdown check |
| `lock.notifyAll()` | Wakes threads waiting for `init()` |
| `lock.wait(timeoutMs)` | Blocks until timeline is ready |

## Playback Control

`PlaybackTimeline` also supports:

| Method | Effect |
|--------|--------|
| `pause()` | Freezes `elapsed` calculation |
| `resume()` | Adjusts `systemStartTime` to account for pause duration |
| `seekTo(positionMs)` | Increments `generation`, resets timeline offset |
| `setPlaybackSpeed(float)` | Multiplier for elapsed time calculation |
| `restart()` | Seeks to 0 and resumes |

## File Locations

Key source files:

- `location/PlaybackTimeline.java` - Central timeline singleton
- `location/MockLocationProvider.java` - GPS playback thread
- `location/MockSensorProvider.java` - Sensor playback thread
- `location/LocationService.java` - GPS provider orchestration
- `location/SensorService.java` - Sensor provider orchestration
- `util/PubSub.java` - Async event delivery
- `VROptions.java` / `VROptionsList.java` - Mode configuration

## Design Rationale

### Why Generation Counters?

1. **No race conditions** - Old threads exit gracefully without needing `Thread.stop()` (which is deprecated)
2. **No orphan threads** - Every thread checks its generation before emitting data
3. **Clean shutdown** - No need to wait for old thread to finish before starting new one
4. **Seek support** - Seeking just increments generation, threads restart from new position

### Why Shared PlaybackTimeline?

1. **GPS/Sensor sync** - Both data sources need identical timing
2. **Single source of truth** - One place to manage playback state
3. **Centralized control** - Pause/resume/seek affects all providers

### Why Not Thread Pools?

The playback threads are long-running loops (entire track duration), so a simple `new Thread()` is more appropriate than a thread pool. The generation pattern handles lifecycle management.
