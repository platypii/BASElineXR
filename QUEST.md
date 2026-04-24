# Quest Debugging Notes

## Advanced administration

Set BASElineXR as the default home activity:

Register for BOOT_COMPLETED.
- `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>`
- Receive `android.intent.action.BOOT_COMPLETED`

Off to Meta Home -> 18s
Off to BaselineXR -> 28s

List boot services:

```
adb shell pm query-receivers --user 0 --components -a android.intent.action.BOOT_COMPLETED
```

Disable unnecessary services for slightly faster boot time:

```
adb shell pm disable-user --user 0 com.google.android.apps.youtube.vr.oculus
adb shell pm disable-user --user 0 com.oculus.assistant
adb shell pm disable-user --user 0 com.oculus.avatareditor
adb shell pm disable-user --user 0 com.oculus.firsttimenux
adb shell pm disable-user --user 0 com.oculus.igvr
adb shell pm disable-user --user 0 com.oculus.panelapp.library
adb shell pm disable-user --user 0 com.oculus.store
adb shell pm disable-user --user 0 com.oculus.tv
adb shell pm disable-user -0 com.oculus.nux.ota
Package com.oculus.socialplatform new state: disabled-user
```

Get GPU memory usage:

```
adb shell gpumeminfo -l
```

Persist android logcat to a file:

```
adb shell "nohup logcat -v time -f /sdcard/persisted.log &"
```

Retrieve log file:

```
adb pull /sdcard/persisted.log
```

### Things that don't work:

```
./gradlew assembleRelease
adb uninstall com.platypii.baselinexr
adb install ./app/build/intermediates/apk/debug/app-debug.apk

adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.platypii.baselinexr

adb shell pm disable-user com.oculus.vrshell
adb shell cmd package set-home-activity com.platypii.baselinexr/.BaselineActivity

adb shell setprop persist.debug.oculus.autolaunch com.platypii.baselinexr/.BaselineActivity
```

Restore Oculus Home as the default home activity:

```
adb shell pm enable com.oculus.vrshell
adb shell cmd package set-home-activity com.oculus.vrshell/com.oculus.vrshell.MainActivity
```

Current home:

```
$ adb shell cmd role get-role-holders --user 0 android.app.role.HOME
com.oculus.systemux
```

Removing this causes "social features disabled" popup:

```
adb shell pm disable-user --user 0 com.oculus.socialplatform
```

If you remove permission `oculus.software.handtracking` you will get a "Switch to Controllers" popup.
Ignore the deprecation warning in logcat `HANDTRACKING: Deprecated Manifest Permission request for com.platypii.baselinexr please update to 'horizonos.permission.HAND_TRACKING'`.
