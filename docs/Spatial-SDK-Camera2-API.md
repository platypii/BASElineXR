# Android Camera2 API

Updated: Apr 29, 2025

Camera Access to Quest 3 and Quest 3s is via the [Android Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary), which is included in the SDK.

After completing this section, the developer should understand how to use the Android Camera2 API to:

 1. Query for camera configuration
 2. Start and Stop camera image capture
 3. Access image data from application code and compute the average brightness

## Use Cases

The method described in this section should be used if the preferred integration method for your application is to use the Android Camera2 API directly from Java/Kotlin code, instead of using a texture or other abstractions.

## Android Sample Prerequisites

Note: The sample provided has minSdk is 34 (Android 14), and Camera2 API is available on HzOS v76+.

## Building The Sample

 1. Clone the [CameraDemo](https://github.com/meta-quest/Meta-Passthrough-Camera-API-Samples) repository from Github.
 2. Open the project in Android Studio.
 3. Connect your Quest device and Select Run from the toolbar to build and install to your device.

## Managing Permissions

The passthrough camera is especially privacy sensitive - the user's surroundings can be seen and potentially recorded - so it is protected by a new runtime permission introduced for HorizonOS: `horizonos.permission.HEADSET_CAMERA`

Please add the following to your apps' manifest:

```xml
<uses-permission android:name="horizonos.permission.HEADSET_CAMERA"/>
<uses-feature android:name="android.hardware.camera2.any" android:required="true"/>
```

This is a runtime permission (same as original android.permission.CAMERA) and must therefore be requested before using it. For more information about how to do this, see <https://developer.android.com/training/permissions/requesting>.

Keep in mind that you need both `android.permission.CAMERA` and `horizonos.permission.HEADSET_CAMERA` permissions to access the camera.

## Sample Application Usage

On startup the application will query the _Camera2 API_ for all camera configurations available from the _Quest 3_ or _Quest 3s_ and the _Average Brightness_ values on the right side of the screen will be set to zero. The camera configuration information includes the lens position and rotation relative to the center of the HMD. This is used to compute the exact position of the camera at the time of the image. Currently, the application only supports calculation of the average brightness of the image, examples on calculation position and rotation will follow soon.

## Getting camera configurations

In general, the camera access is the same as the standard [Android Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary). The difference lies in a few vendor-specific properties that need to be accessed via custom `CameraCharacteristics.Key`:

```kotlin
import android.hardware.camera2.CameraCharacteristics.Key

const val KEY_CAMERA_POSITION = "com.meta.extra_metadata.position"
const val KEY_CAMERA_SOURCE = "com.meta.extra_metadata.camera_source"

val KEY_POSITION = Key(KEY_CAMERA_POSITION, Int::class.java)
val KEY_SOURCE = Key(KEY_CAMERA_SOURCE, Int::class.java)
```

These keys can be used to obtain camera characteristics via camera manager:

```kotlin
val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
cameraManager.cameraIdList.forEach { cameraId ->
    val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
    val cameraSource = cameraCharacteristics.get(KEY_SOURCE)
    val cameraPosition = cameraCharacteristics.get(KEY_POSITION)
}
```

Camera Source value is 0 for passthrough cameras. Camera position is 0 for the leftmost camera and 1 for the rightmost. Non-passthrough cameras do not have Position field set (the value is null).

### Starting The Camera

Camera access follows the same principles and uses same API as described in [Camera2 documentation](https://developer.android.com/media/camera/camera2/capture-sessions-requests). To start the camera, you need to call [`cameraManager.openCamera`](https://developer.android.com/reference/android/hardware/camera2/CameraManager?hl=en#openCamera\(java.lang.String,%20android.hardware.camera2.CameraDevice.StateCallback,%20android.os.Handler\)) and provide required camera ID and an instance of [`StateCallback`](https://developer.android.com/reference/android/hardware/camera2/CameraDevice.StateCallback?hl=en):

```kotlin
val CAMERA_SOURCE_PASSTHROUGH = 0
val POSITION_LEFT = 0
val POSITION_RIGHT = 1
// ...

// To find a config for passthrough camera, use Source value
val targetConfig = cameraConfigs.find { it.source == CAMERA_SOURCE_PASSTHROUGH }
// Alternatively, to find leftmost or rightmost camera, use Position
val leftCameraConfig = cameraConfigs.find { it.position == POSITION_LEFT }
val rightCameraConfig = cameraConfigs.find { it.position == POSITION_RIGHT }
// ...

cameraManager.openCamera(targetConfig.id, stateCallback, handler)
```

Once camera is opened, the callback’s `onOpened` method will be called. Now we can start a [camera session](https://developer.android.com/reference/android/hardware/camera2/CameraDevice?hl=en#createCaptureSession\(android.hardware.camera2.params.SessionConfiguration\)):

```kotlin
override fun onOpened(camera: CameraDevice) {
    camera.createCaptureSession(
        SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            mutableListOf(
                OutputConfiguration(imageReader.surface),
                OutputConfiguration(previewSurface)
            ),
            cameraSessionExecutor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    onSessionStarted(session, previewSurface)
                    postEvent("Camera session started!")
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    loge("Failed to start camera session for camera ${targetConfig.id}")
                }
            }
        )
    )
}
```

Finally, when session is configured, you can create a capture request:

```kotlin
private fun onSessionStarted(session: CameraCaptureSession, previewSurface: Surface) {
    val captureRequest =
        activeCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(imageReader.surface)
            addTarget(previewSurface)
        }

    session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
}
```