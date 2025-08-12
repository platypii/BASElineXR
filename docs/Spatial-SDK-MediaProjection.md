# MediaProjection API on Meta Quest

## Introduction

Meta Quest allows apps to access the video displayed in the headset and the device audio with the user’s consent using the [Android MediaProjection API](https://developer.android.com/media/grow/media-projection). This API is designed to enable remote casting, live streaming, and screen sharing experiences.

This API works mostly the same as in base Android, so existing code using the API on regular android devices should also work on Meta Quest.

## Policies and Guidelines

Your use of Media Projection API must at all times be consistent with the [Developer Data Use Policy](/horizon/policy/data-use/) and all applicable Oculus and Meta policies, terms and conditions.

On Meta Quest, Apps may only use the MediaProjection API to enable casting, live streaming, or screen sharing in accordance with our policies and guidelines and for no other purpose.

## User Consent and obtaining a MediaProjection token

Users must consent to the app accessing what the user sees and hears in the headset. The MediaProjection API is protected by a token. Apps must first acquire this token before they can perform any video or audio capture. To obtain this token, your app must launch the `MediaProjectionPermissionActivity` to request permission from the user to capture the screen. This will show a dialog to the user - if the user accepts the dialog, the system will provide a MediaProjection token.

Use the [`createScreenCaptureIntent`](https://developer.android.com/reference/kotlin/android/media/projection/MediaProjectionManager#createscreencaptureintent) method to create an intent for launching the permission activity. The token will be provided via an [activity result](https://developer.android.com/training/basics/intents/result). After receiving the activity result, use the [`getMediaProjection`](https://developer.android.com/reference/kotlin/android/media/projection/MediaProjectionManager#getmediaprojection) method to obtain the MediaProjection token. An example of obtaining the MediaProjection token is provided in the Android documentation:

```java
    final MediaProjectionManager mediaProjectionManager =
        getSystemService(MediaProjectionManager.class);
        final MediaProjection[] mediaProjection = new MediaProjection[1];

        ActivityResultLauncher startMediaProjection = registerForActivityResult(
        new StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                mediaProjection[0] = mediaProjectionManager
                    .getMediaProjection(result.getResultCode(), result.getData());
            }
        }
    );

    startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent());
```

## Integrating Video Capture

The MediaProjection video capture API works by accepting an [Android Surface](https://developer.android.com/reference/android/view/Surface). This Surface will be populated by Horizon OS with the contents of the screen. The API also returns a [`VirtualDisplay`](https://developer.android.com/reference/android/hardware/display/VirtualDisplay) object. In base Android, the screen contents are projected onto the `VirtualDisplay` and the `Surface` is then populated by that `VirtualDisplay`. However, in Horizon OS, the surface is populated directly by our own compositor. See the API Differences section below for details.

The `Surface` needs to be created by the client app, which is also responsible for hooking up the Consumer end of the `Surface`. Then, the app can pass the `Surface` to the MediaProjection API via the [`createVirtualDisplay`](https://developer.android.com/reference/kotlin/android/media/projection/MediaProjection#createvirtualdisplay) method. This will hook up the producer end to populate the surface with the screen contents.

Below is an example of calling this method from the Android docs:

 virtualDisplay = mediaProjection.createVirtualDisplay(
 "ScreenCapture",
 width,
 height,
 screenDensity,
 DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
 surface,
 null, null);

## Integrating Audio Capture

To capture audio, use the Android [AudioPlaybackCapture API](https://developer.android.com/media/platform/av-capture#capture_audio_playback). = Again, you will need to obtain the MediaProjection token. The same token can be used for both video and audio capture. First, create an [`AudioPlaybackCaptureConfiguration`](https://developer.android.com/reference/android/media/AudioPlaybackCaptureConfiguration) object using the MediaProjection token. Then, Create an [`AudioRecord`](https://developer.android.com/reference/android/media/AudioRecord) object and pass the `AudioPlaybackCaptureConfiguration` object in. Then, you can use the `AudioRecord` object to start recording and read captured audio data.

### Handling MediaProjection Callback

Android does not allow multiple concurrent MediaProjection captures. As a result, whenever another app requests and obtains a MediaProjection token, any existing MediaProjection session is ended. The original app is notified via the [MediaProjection callback](https://developer.android.com/reference/android/media/projection/MediaProjection.Callback).

Note

Your app must handle this case where the system ends a MediaProjection session. You must pass in a callback and [register it](https://developer.android.com/reference/android/media/projection/MediaProjection#registerCallback\(android.media.projection.MediaProjection.Callback,%20android.os.Handler\)) to correctly end your MediaProjection session and clean up any resources.

## API Differences

The main difference in how this API is implemented in HorizonOS is that the `VirtualDisplay` object that is returned does not actually populate the Surface. This means calling the `resize` method on the returned `VirtualDisplay` may not have the same behavior as on base Android.

The scaling of the image onto the surface is also handled by our custom compositor within XrRuntime, meaning that the exact scaling/letterboxing behavior described in [this section](https://developer.android.com/media/grow/media-projection#surface) may not be accurate.

## Known Issues

There is a known issue where muting the audio doesn’t actually mute the audio going to the system. Currently, there is a band-aid fix of setting the player’s AudioUsage attribute to `AUDIO_UNKNOWN`. Because of this, if you want your app’s audio capture to respect audio muting, you should not add the `AudioAttributes.USAGE_UNKNOWN` attribute to your `AudioPlaybackCaptureConfiguration`.

## Useful Links

### ScreenCapture sample app

<https://github.com/android/media-samples/tree/main/ScreenCapture>

### Android Docs

<https://developer.android.com/media/platform/av-capture>

<https://developer.android.com/media/grow/media-projection>

<https://source.android.com/docs/core/graphics/architecture>
