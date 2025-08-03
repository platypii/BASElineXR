# CPU and GPU levels

Updated: May 9, 2025

As an app developer for Meta Quest headsets, you have the ability to change the clock speed of the headset's CPU and GPU while running your app. Increasing the clock speed raises power consumption. These controls allow you to decide whether to emphasize long battery life (through low clock speeds) or enhanced features (through high clock speeds) in your application.

To support simultaneous development on headsets with different chipsets, and to ensure applications can receive boosts without having to update their builds (such as [the 7% GPU boost for GPU level 4 in v49 OS](/horizon/blog/boost-app-performance-525-mhz-gpu-frequency-meta-quest-2/)), we expose a system of CPU and GPU levels to select from, rather than giving you direct control over clock frequencies.

Select the lowest CPU and GPU levels that allow your application to run consistently at framerate.

**Performance per clock cycle differs between headset models.** For example, although Meta Quest 1 has a higher maximum clock speed than Meta Quest 2, when running VR applications, Meta Quest 2 runs 80% more instructions per cycle due to differences in their CPU architecture.

## Profiling CPU and GPU level and frequencies

You can profile current CPU and GPU level, frequency, and utilization using the [OVRMetrics Tool](/horizon/documentation/spatial-sdk/ts-ovrmetricstool/), the **Performance Analyzer** section of [Meta Quest Developer Hub](/horizon/documentation/spatial-sdk/ts-mqdh/), or the [VrApi log stats](/horizon/documentation/spatial-sdk/ts-logcat-stats/).

_In this image, MQDH is used to profile an application running on Quest 2 at CPU level 4 / 1.478GHz, and GPU level 3 / 490MHz._

## Setting CPU and GPU levels

Set CPU and GPU levels for your apps by using the `ProcessorPerformanceLevel` enum. This is exposed in the following functions in each engine's integration package:

| Engine | Function |
|---|---|
| Unreal | `UOculusXRFunctionLibrary::SetSuggestedCpuAndGpuPerformanceLevels` (exposed in Blueprint) |
| Unity | `OVRPlugin::suggestedCpuPerfLevel`, `OVRPlugin::suggestedGpuPerfLevel` |
| Native | `ovrp_SetSuggestedCpuPerformanceLevel`, `ovrp_GetSuggestedGpuPerformanceLevel` |
| Spatial SDK | `spatial.setPerformanceLevel` |
These functions provide a layer of indirection. Rather than directly setting a CPU or GPU level, you pick a `ProcessorPerformanceLevel`. The operating system will then keep your CPU and GPU levels at the lowest value within a certain range that preserves your application's framerate. This allows your application to automatically adjust for longer battery life during less-intensive scenes.

| ProcessorPerformanceLevel | CPU level range | GPU level range |
|---|---|---|
| `PowerSavings` | 0 to 4 | 0 to 4 |
| `SustainedLow` | 2 to 4 | 1 to 4 |
| `SustainedHigh` | 4 to 6* | 3 to 5 |
| `Boost`** | 4 to 6* | 3 to 5 |

Spatial SDK defaults to [`SustainedHigh`](/horizon/documentation/spatial-sdk/os-cpu-gpu-levels/). Prior to 0.7.0, the default was `SustainedLow`. This improves app performance at the cost of battery life.

*The CPU level range when CPU `ProcessorPerformanceLevel` is set to `SustainedHigh` depends on your headset generation and settings, as follows:

| Headset | CPU level range |
|---|---|
| Quest 3, Quest 3S | 4 to 4 |
| Quest 2, Quest Pro, Quest 3 or 3S with [CPU level trading](/horizon/documentation/spatial-sdk/po-quest-boost/) | 5 to 5 |
| Quest 2 or Quest Pro with [dual-core mode](/horizon/documentation/spatial-sdk/po-quest-boost/) | 6 to 6 |

**The `Boost` ProcessorPerformanceLevel exists for historical reasons, and behaves exactly the same as `SustainedHigh`.

**Note** : Depending on your headset, some power levels may be inaccessible based on features and settings specified by your application, requiring modifications to your application to access. See your headset's _CPU/GPU level availability_ section for more details.

## Meta Quest 1

### Clock speed of application cores

| CPU level | Clock speed |
|---|---|
| 0 | 0.98 GHz |
| 1 | 1.34 GHz |
| 2 | 1.65 GHz |
| 3 | 1.96 GHz |
| 4 | 2.30 GHz |

If possible, CPU level will increase when CPU utilization is 83% or greater, and decrease when CPU utilization is 77% or lesser.

_Note:_ CPU and GPU levels above 4 were introduced after the Meta Quest 1 end-of-life.

_Note:_ Although the Meta Quest 1 has a higher clock speed on its application cores than the Meta Quest 2, it uses a simpler CPU architecture. Internal tests on VR applications found that Meta Quest 2's CPU architecture allowed it to run 80% more instructions per cycle.

### Clock speed of GPU cores

| GPU level | Clock speed |
|---|---|
| 0 | 257 MHz |
| 1 | 342 MHz |
| 2 | 414 MHz |
| 3 | 515 MHz |
| 4 | 670 MHz |

If possible, GPU level will increase when GPU utilization is 91% or greater, and decrease when GPU utilization is 85% or lesser.

_Note:_ CPU and GPU levels above 4 were introduced after the Meta Quest 1 end-of-life.

### CPU/GPU level availability

| CPU level | Availability |
|---|---|
| 0-4 | Always available |

| GPU level | Availability |
|---|---|
| 0-4 | Always available |

_Note_: Access to CPU/GPU levels is further restricted based on your set `ProcessorPerformanceLevel`.

## Meta Quest 2 & Meta Quest Pro

### Clock speed of application cores

| CPU level | Clock speed |
|---|---|
| 0 | 0.71 GHz |
| 1 | 0.94 GHz |
| 2 | 1.17 GHz |
| 3 | 1.38 GHz |
| 4 | 1.48 GHz |
| 5 | 1.86 GHz |
| 6 | 2.15 GHz |

If possible, CPU level will increase when CPU utilization is 83% or greater, and decrease when CPU utilization is 77% or lesser.

### Clock speed of GPU cores

| GPU level | Clock speed |
|---|---|
| 0 | 305 MHz |
| 1 | 400 MHz |
| 2 | 442 MHz |
| 3 | 490 MHz |
| 4 | 525 MHz |
| 5 | 587 MHz |

If possible, GPU level will increase when GPU utilization is 87% or greater, and decrease when GPU utilization is 81% or lesser.

### Meta Quest 2 CPU/GPU level availability

| CPU level | Availability |
|---|---|
| 0-4 | Always available |
| 5 | Available if [dual-core mode](/horizon/documentation/native/android/po-quest-boost/#dual-core-mode) is not enabled |
| 6 | Available if [dual-core mode](/horizon/documentation/native/android/po-quest-boost/#dual-core-mode) is enabled |

| GPU level | Availability |
|---|---|
| 0-4 | Always available |
| 5 | Available if dynamic resolution is enabled |

_Note_: Access to CPU/GPU levels is further restricted based on your set `ProcessorPerformanceLevel`, and may be overridden if certain OS features are running in the background (i.e. casting). See [Logcat stats definitions](/horizon/documentation/native/android/ts-logcat-stats/#performancemanager_zsf-logs) to learn how to track these behaviors.

### Meta Quest Pro CPU/GPU level availability

| CPU level | Availability |
|---|---|
| 0-3 | Always available |
| 4 | Available if the application does not enable passthrough, eye tracking, face tracking, body tracking, or gaze-based foveated rendering features |
| 5 | Available if CPU level 4 is available, and [dual-core mode](/horizon/documentation/native/android/po-quest-boost/#dual-core-mode) is not enabled |
| 6 | Available if CPU level 4 is available, and [dual-core mode](/horizon/documentation/native/android/po-quest-boost/#dual-core-mode) is enabled |

| GPU level | Availability |
|---|---|
| 0-3 | Always available |
| 4 | Available if the application does not enable passthrough, eye tracking, face tracking, body tracking, or gaze-based foveated rendering features |
| 5 | Available if dynamic resolution is enabled |

_Note_: Access to CPU/GPU levels is further restricted based on your set `ProcessorPerformanceLevel`, and may be overridden if certain OS features are running in the background (i.e. casting). See [Logcat stats definitions](/horizon/documentation/native/android/ts-logcat-stats/#performancemanager_zsf-logs) to learn how to track these behaviors.

## Meta Quest 3 & Meta Quest 3S

### Clock speed of application cores

| CPU level | Clock speed |
|---|---|
| 0 | 0.69 GHz |
| 1 | 1.09 GHz |
| 2 | 1.38 GHz |
| 3 | 1.65 GHz |
| 4 | 1.92 GHz |
| 5 | 2.05 GHz |
| 6 | 2.36 GHz |

If possible, CPU level will increase when CPU utilization is 83% or greater, and decrease when CPU utilization is 77% or lesser.

### Clock speed of GPU cores

| GPU level | Clock speed |
|---|---|
| 0 | 285 MHz |
| 1 | 350 MHz |
| 2 | 456 MHz |
| 3 | 492 MHz |
| 4 | 545 MHz |
| 5 | 599 MHz |

If possible, GPU level will increase when GPU utilization is 87% or greater, and decrease when GPU utilization is 81% or lesser.

### CPU/GPU level availability

| CPU level | Availability |
|---|---|
| 0-3 | Always available |
| 4 | Available if the application does not enable passthrough features |
| 5 | Available if CPU level 4 is available, and [CPU and GPU level trading](/horizon/documentation/native/android/po-quest-boost/#trading-between-cpu-and-gpu-levels) is set to `-1` |
| 6 | Available if the CPU Boost is enabled. See [CPU Boost Hint](/horizon/documentation/native/android/po-quest-boost/#cpu-boost-hint) |

| GPU level | Availability |
|---|---|
| 0-2 | Always available |
| 3-4 | Available if the application does not enable passthrough features |
| 5 | Available if GPU level 4 is available, and [CPU and GPU level trading](/horizon/documentation/native/android/po-quest-boost/#trading-between-cpu-and-gpu-levels) is set to `+1`, or dynamic resolution is enabled |

_Note_: Access to CPU/GPU levels is further restricted based on your set `ProcessorPerformanceLevel`, and may be overridden if certain OS features are running in the background (i.e. casting). See [Logcat stats definitions](/horizon/documentation/native/android/ts-logcat-stats/#performancemanager_zsf-logs) to learn how to track these behaviors.