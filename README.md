# BASEline XR

BASEline Augmented Reality.

To setup:

1. Pair FlySight with Quest: Double-tap FlySight, Pair in Quest Settings.
2. Use Android Studio to install on Quest.

Notes:

Left controller hamburger is the ONLY way to force return to home.

## Advanced administation

Set BASElineXR as the default home activity:

```
adb shell pm disable-user com.oculus.vrshell
adb shell cmd package set-home-activity com.platypii.baselinexr/com.platypii.baselinexr.MainActivity
```

Restore Oculus Home as the default home activity:

```
adb shell pm enable com.oculus.vrshell
adb shell cmd package set-home-activity com.oculus.vrshell/com.oculus.vrshell.MainActivity
```
