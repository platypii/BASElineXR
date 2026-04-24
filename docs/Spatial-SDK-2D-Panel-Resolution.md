# Optimize panel configuration and resolution

Updated: Jun 9, 2025

## Overview

In `PanelRegistration`, you can define the panel’s config options, which determine the panel’s size and layout. You may want to adjust the panel’s layout to make the panel look sharper.

Consider a real-world screen, such as a TV or phone. These screens have specific resolutions, such as 4k for TVs or 1080 x 2280 for phones. The clarity of the image on these screens depends on their distance from you. Close-up, the screens appear clearer, while those further away might seem blurry. Additionally, the screen’s field of view (FOV) influences whether you can see the display clearly.

In a 3D scene, the clarity of the panel depends on its resolution and its FOV relative to the eye buffer of the Quest device. You can adjust this clarity by modifying the panel’s size and position in relation to the VR/MR viewpoint.

For reference, Quest 3 device’s eye buffer screen resolution is 2064 x 2208, and the FOV is 110° x 96° per eye.

## 2D panel resolution

In the panel configuration, you can define:

 * `layoutWidthInDp`/`layoutHeightInDp`: the panel layout’s width and height in [density-independent pixels](https://developer.android.com/training/multiscreen/screendensities) to maintain consistent layout content across various screen densities. This approach is similar to common Android layouts, ensuring that adjustments in the panel’s pixels do not affect the layout’s appearance.
 * `layoutDpi`: the dots per inch (DPI) of a panel, similar to an Android screen’s DPI, determines its width and height in DP. Increasing the DPI enhances the panel’s density, thereby improving its resolution. By default, panels have a DPI of 256, a value empirically set based on the eye buffer of Quest 2 and adjusted to accommodate Quest 3’s size.
 * `layoutWidthInPx`/`layoutHeightInPx`: the panel layout’s width and height in pixels. Panels will use them to create Android `VirtualDisplay` with `DPI`. If not defined specifically, they will be derived from `layoutWidthInDp/layoutHeightInDp` and `DPI`.
 * `width`/`height`: the panel’s scene object size, with width and height in meters. In mixed reality/VR, the width/height will be the physical size of the panel.
* `fractionOfScreen`: determines the panel's width as a percentage of the rendering screen, specifically for Quest devices' eye buffer. If `layoutWidth/HeightInDp` or `layoutWidth/HeightInPx` are undefined, the panel defaults to using `fractionOfScreen`. For instance, setting `fractionOfScreen = 0.5` implies the panel occupies half the screen's width. The height is then derived from the width based on the height-to-width ratio. In a Quest 3 device, with `fractionOfScreen = 0.5`, the calculations would be `layoutWidthInPx = 2064 * 0.5` and `layoutHeightInPx = layoutWidthInPx * height/width ratio`

## Building and rendering panels in 3D

To understand these configurations, it’s important to know how the panels are built and rendered in a 3D scene.

Here’s the high level process:

 1. Build the panel content with an Android `VirtualDisplay`, and render the display as scene texture.
 * Create the Android screen/display with `layoutHeightInPx`/`Width` and DPI.
 * DPI defines the clarity of the panel screen. Together with the height/width in pixels, they will determine the size of the panel of the Android display.
 * With the Android display/screen, you’ll get the scene texture of the panel.
 2. Build the Spatial SDK 3D mesh (surface or cylinder) and attach the panel’s texture to the surface of 3D object.
 * For the panel, you will build the 3D object/mesh. For example, for a quad panel, you will create a box with height/width and nearly zero thickness.
 * The height and width are from the attributes of panel config, and are measured in meters in the 3D scene.
 * Adjusting the height/width will only scale the panel scene texture (not resize it), which can make it blurry if scaling height/width too large.
 3. Rendering the panel scene object to the eye buffer, the screen of the Quest.
 * With the panel object/mesh in the 3D scene, you can render it to the eye buffer, like any other 3D objects.
 * As you move closer or farther from the panel, the FOV of the panel will update.
 * You can also create a system to scale or pivot the panel, like the [custom components sample app](https://github.com/meta-quest/Meta-Spatial-SDK-Samples/tree/main/CustomComponentsSample), keeping the panel facing you or its FOV the same.

## Resolution calculation

### Calculating an Android virtual device’s size and resolution

You might want to use [Android virtual devices](https://developer.android.com/studio/run/managing-avds) to build and test your 2D panels layout.

Here’s an example of how to calculate the 2D panel’s size and resolution for Android virtual devices.

* Assume you want to set the panel's FOV to `Fraction of Screen = 0.5`.
* Calculate the width in pixels:

```
WidthPx = Eyebuffer Width * Fraction of Screen
        = 2064 * 0.5
        = 1032
```

You can also adjust this manually.

* Calculate the height in pixels:

```
HeightPx = Eyebuffer Height * Fraction of Screen * (height / width)
         = 2208 * 0.5 * (3 / 4)
         = 828
```

You can also adjust this manually.

* Determine the default DPI:

```
DPI = 256 * Eyebuffer Width / 1832
    = 256 * 2064 / 1832
    = 288.419
```

You can also adjust this manually.

* Compute the width in DP:

```
Width DP = (WidthPx / DPI) * 160
         = (1032 / 288.419) * 160
         = 572.51
```

* Compute the height in DP:

```
Height DP = (HeightPx / DPI) * 160
          = (828 / 288.419) * 160
          = 459.34
```

* Calculate the diagonal in pixels:

```
Diagonal in Px = Sqrt(WidthPx^2 + HeightPx^2)
               = Sqrt(1032^2 + 828^2)
               = 1323
```

* Finally, calculate the diagonal in inches:

```
Diagonal in Inches = Diagonal in Px / DPI
                   = 1323 / 288.419
                   = 4.6
```

With all these calculations, you can set Android virtual device’s screen size as 4.6 inches, and the resolution as 1032x828px.

### Calculating size and position for 2D panels in 3D scene

* Assume you want to set your panel's FOV to `Fraction of Screen = 0.5`.
* For rendering on Quest 3, the panel should appear in the eye buffer as `1032x1104px`, calculated from `(2064/2)x(2208/2)px`.
* Define `layoutWidthInDp`, `layoutHeightInDp`, and `layoutDpi` to maintain a clear and stable layout. Use these to compute the panel texture's resolution:
  * `layoutWidthInPx = layoutWidthInDp * layoutDpi / 160`
  * `layoutHeightInPx = layoutHeightInDp * layoutDpi / 160`
* Avoid setting `layoutWidthInPx`/`layoutHeightInPx` larger than 2064x2208px due to performance limitations.
* To ensure the `layoutWidthInPx` and `layoutHeightInPx` are rendered correctly in the eye buffer as 1032x1104px, adjust the panel's size and distance proportionally.
* For example, for a width = 1.0 meter, set the viewpoint distance as `1032px / layoutWidthInPx * width`.

## 2D Panel sizing and positioning

As mentioned before, you can set the panel’s initial size in `PanelRegistration`:

```kotlin
...
override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        PanelRegistration(R.id.panel_id) {
            ...
            config {
                width = 2.0f // In meters of VR/MR world
                height = 1.5f // In meters of VR/MR world
            }
            ...
        })
}
...
```

You can make the panel sharper and better-looking by sizing and positioning it accordingly. You can also change the size with `Scale` components after creating the panel:

```kotlin
panelEntity.setComponent(Scale(Vector3(scaledSize)))
```

For positioning, you can set and update the `Transform` component:

```kotlin
panelEntity.setComponent(Transform(Pose(Vector3(x, y, z), Quaternion(x, y, z))))
```

## Known issue: large panel cropping

Large panels may be cropped and have black pixels. This can break apps that display large media, such as 4k resolution media.

To workaround this issue, you can manage your own `PanelSceneObject` and render video directly to the panel surface. This involves setting up a `PanelSceneObject` and attaching it to [ECS](/horizon/documentation/spatial-sdk/spatial-sdk-ecs).

### Prerequisites

* The panel is using [compositor layers](/horizon/documentation/spatial-sdk/spatial-sdk-2dpanel-layers).
* This approach may not work without using layers.
* The panel is using [ExoPlayer](https://developer.android.com/media/media3/exoplayer).
* The panel is only displaying video.
* The video must be in a simple `PlayerView` or something comparable. All UI will be removed in this workaround.
* This workaround also removes Android based input handling. If you need click handling, you will need to move to `SceneObject` input listeners.

### Base code

The following code is an example of a panel that may be cropped. The next section will walk through the necessary changes to workaround the issue.

```kotlin
class MyActivity : AppSystemActivity() {
    lateinit var exoPlayer: ExoPlayer
    lateinit var videoEntity: Entity

    override fun onVRReady() {
        super.onVRReady()
        videoEntity = Entity.create(
            Transform(),
            Grabbable(),
            Panel(R.layout.video_layout)
        )
    }

    override fun registerPanels(): List<PanelRegistration> {
        return listOf(
            PanelRegistration(R.layout.spatialized_video) {
                config {
                    layoutWidthInPx = 1000
                    layoutHeightInPx = 2000
                    stereoMode = StereoMode.LeftRight
                    includeGlass = false
                    layerConfig = LayerConfig()
                }
                panel {
                    val playerView = rootView?.findViewById<PlayerView>(R.id.video_view)!!
                    playerView.player = player
                    // panel code here
                }
            }
        )
    }
}
```

### Code updates

1. Remove the `Panel()` component from your panel `Entity`.
   * Move the corresponding `PanelRegistration` to a separate function that you can call when you want to spawn your panel.
   * Put the code inside the `config {...}` block inside a `PanelConfigOptions().apply {...}` block.
   * Inside your config, set `mips = 1` and `enableTransparent = false`.
   * Put the code inside the `panel {...}` block inside a `PanelSceneObject(scene, myEntityHere, panelConfigOptions).apply{...}` block.
2. Make sure `exoPlayer` is accessible somewhere.
3. Call `exoPlayer.setVideoSurface(panelSceneObject.getSurface())` to get the video to render to surface directly.
   * For more details on direct-to-surface rendering see [Configuring media playback](/horizon/documentation/spatial-sdk/spatial-sdk-media-playbook#direct-to-surface-rendering).
4. Attach the panel to ECS with the following block:

```kotlin
systemManager
    .findSystem<SceneObjectSystem>()
    .addSceneObject(
        myEntityHere,
        CompletableFuture<PanelSceneObject>().apply { complete(panelSceneObject) })
```

5. _(Optional)_ If you have any Android based input handling, you will need to move to SceneObject InputListeners.

Your new code should look something like this:

```kotlin
class MyActivity : AppSystemActivity() {
    lateinit var exoPlayer: ExoPlayer
    lateinit var videoEntity: Entity

    override fun onVRReady() {
        super.onVRReady()
        videoEntity = Entity.create(
            Transform(),
            Grabbable(),
        )
        createVideoPanel()
    }

    fun createVideoPanel() {
        // create panel manually
        val panelConfigOptions = PanelConfigOptions().apply {
            layoutWidthInPx = 1000
            layoutHeightInPx = 2000
            stereoMode = StereoMode.LeftRight
            includeGlass = false
            layerConfig = LayerConfig()
            // these options force the best performance and quality (may not work otherwise)
            mips = 1
            enableTransparent = false
        }
        // this constructor will just create an empty Surface to render to
        val panelSceneObject = PanelSceneObject(scene, videoEntity, panelConfigOptions).apply {
            // do stuff with your panel here
        }
        // attach exoPlayer to the panel's Surface
        exoPlayer.setVideoSurface(panelSceneObject.getSurface())
        // hook it up to the ECS
        systemManager
            .findSystem<SceneObjectSystem>()
            .addSceneObject(
                videoEntity, CompletableFuture<PanelSceneObject>().apply { complete(panelSceneObject) })
        // optionally mark the mesh as explicitly able to catch input
        videoEntity.setComponent(Hittable())
    }

    override fun registerPanels(): List<PanelRegistration> {
        return listOf(
            PanelRegistration(R.layout.spatialized_video) {
                config {
                    layoutWidthInPx = 1000
                    layoutHeightInPx = 2000
                    stereoMode = StereoMode.LeftRight
                    includeGlass = false
                    layerConfig = LayerConfig()
                }
                // no need for panel callback as we render directly to the surface
            }
        )
    }
}
```

### Example apps

You can view the history of these public samples to see the changes made to implement this workaround.

* [SpatialVideoSample](https://github.com/meta-quest/Meta-Spatial-SDK-Samples/blob/main/SpatialVideoSample/app/src/main/java/com/meta/spatial/samples/spatialvideosample/SpatialVideoSampleActivity.kt)
  * This is the most straightforward example
  * It also shows an example of moving from Android to `SceneObject` input handling
* [MediaPlayerSample](https://github.com/meta-quest/Meta-Spatial-SDK-Samples/blob/main/MediaPlayerSample/app/src/main/java/com/meta/spatial/samples/mediaplayersample/MediaPlayerSampleActivity.kt)
  * This example is slightly more complicated, as it changes from `VideoView` to `ExoPlayer`.

## Design guidelines

* [2D UI Key Considerations](/horizon/design/mr-design-guideline#2d-ui)