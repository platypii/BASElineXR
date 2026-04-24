# Layers and UI quality

Updated: May 13, 2025

## Overview

By default, Spatial SDK optimizes panels to work well for most use cases. However, if you want to achieve a specific effect, you may have to do some deeper configuring.

## Layers in 2D panels

Spatial SDK has a layers feature that lets you get the best visual fidelity out of your panels. Layers provide a superior look but come with many tradeoffs. Below is a comparison of a panel with and without layers. The effect is much more noticeable when viewed from within your headset.

### Example of a panel not using layers

### Example of a panel using layers

More details can be found [here](/horizon/documentation/native/android/os-compositor-layers).

## Layers example

This example shows how to add layers to your panels and should work in most general use cases.

```kotlin
...
override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        PanelRegistration(R.layout.ui_example) {
            ...
            config {
                ...
                layerConfig = LayerConfig()
                enableTransparent = true
            }
            ...
        })
}
```

You can also specify the layer configuration directly:

```kotlin
import com.meta.spatial.runtime.AlphaMode
import com.meta.spatial.runtime.LayerConfig
import com.meta.spatial.runtime.SceneMaterial

override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        PanelRegistration(R.layout.ui_example) {
            ...
            config {
                ...
                // tells the panel to use layers
                layerConfig = LayerConfig()
                // sets up the hole punching
                alphaMode = AlphaMode.HOLE_PUNCH
                panelShader = SceneMaterial.HOLE_PUNCH_PANEL_SHADER
                forceSceneTexture = true
                ...
            }
            ...
        })
}
```

If you don’t want to use layers, simply remove `layerConfig` from your configuration, along with the `panelShader` and `alphaMode` changes.

## Technical explanation

Use [OpenXR Layers](https://registry.khronos.org/OpenXR/specs/1.0/html/xrspec.html#composition-layer-types) to get the best quality out of your panels. These layers allow you to submit your panel directly to the OpenXR compositor, which minimizes the amount of sampling required and produces the highest quality images possible.

The technical details can be found [here](https://developers.meta.com/horizon/documentation/native/android/os-compositor-layers),

Using OpenXR layers comes at a cost. For example, these layers won’t be composited into your panel in the scene. Instead, OpenXR will either render the layers below or above your scene. If you try to render a layer without extra configuration, it will render above your screen. This is fine in some cases, but if you try to bring something like a controller in front of the panel, the panel will render over the controller instead.

You can use the hole punching technique to resolve this issue. Hole punching means rendering the layers first and then rendering the scene on top of them. This technique covers up your layers, but you can punch holes in the scene to clear out the pixels and reveal the layers underneath.

Here is an illustration of how hole punching works:

In Spatial SDK, layers render above the scene unless you enable hole punching by calling `scene.enableHolePunching(true)`. This call causes Spatial SDK to render the layers before the scene. Spatial SDK enables hole punching by default. To disable it, you have to call `scene.enableHolePunching(false)`.

If you only enable hole punching and use the layers config, you will no longer be able to see your panels, because you haven’t punched holes in the scene yet. To punch the required holes, you can use `panelShader = SceneMaterial.HOLE_PUNCH_PANEL_SHADER` and `alphaMode = AlphaMode.HOLE_PUNCH`. These two calls do the following:

- Tells the panel mesh in the scene to only write to the depth of the mesh.
- Clears all color and alpha values, which causes the scene to show through to the layers beneath.

You can get creative with layers and blending modes (set in the layer config) to achieve interesting effects like masking and additive blending.

## Restrictions

- **Transparency**: Transparencies are challenging because you need to have your panel punch holes in the places where your panel has transparency. This hole punching is hard to do arbitrarily. You can sometimes use `panelShader = SceneMaterial.HOLE_PUNCH_PANEL_SHADER` to get around the difficulty of ad hoc hole-punching.
- **Overlapping or intersecting layers**: Spatial SDK writes layers one-by-one and does not write depth. This can sometimes make overlapping panels an issue.
- **Shader capabilities**: You can attach an effect process to our panel, but the compositor handles the final image rather than our shaders, so we cannot edit the panel with a custom shader after the fact.

## Design guidelines

- [Window affordances](/horizon/design/windows/)
- [Tooltips](/horizon/design/tooltips/)
- [Fonts and Icons](/horizon/design/fonts-icons/)
- [Cards](/horizon/design/cards/)
- [Dialogs](/horizon/design/dialogs/)
