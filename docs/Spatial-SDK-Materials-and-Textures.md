# Materials and textures

Updated: May 15, 2025

Materials define the appearance of a 3D object’s surface. They consist of properties, like colors and textures, that work together to create the desired appearance.

## Material properties

The standard material component includes just one property: **Double Sided**. If it’s not selected, the faces using the material will only be visible on one side. From the other side, the faces will not be visible.

Other options, under **PBR Material Component**, determine textures and how light interacts with the surface of your object. Here are the properties that make up a PBR material:

| Property | Type | Definition |
|----------|------|------------|
| *Base Color Factor* | Color | The main color of the material. If using a texture, leave this white (255,255,255) unless you want to tint the texture. |
| *Base Color Texture* | Texture | Applies varying color values across the geometry using texture coordinates. This will form the base of your material. Use it to add visible color and details. |
| *Alpha Mode* | Text | This determines how the alpha value of the base color or texture is interpreted: In **Opaque** mode, the alpha value is ignored. In **Mask** mode, pixels are written off if the alpha value is > the alpha cutoff (see below). In **Blend** mode, the alpha value is used to blend with the color behind the object. Alpha value is the product of the base color texture alpha and the base color factor alpha. |
| *Alpha Cutoff* | Percent | A parameter (between 0 and 100) that determines the cutoff point when **Alpha Mode** is set to **Mask**. |
| *Metallic Factor* | Percent | This determines the extent to which the metallic channel of the roughness texture has an effect on the material. |
| *Roughness Factor* | Percent | This determines the extent to which the roughness channel of the roughness texture has an effect on the material. |
| *Roughness Texture* | Texture | Maps different metalness and roughness values across the geometry using texture coordinates. The blue channel defines metalness, and the green channel defines roughness. Representing these details as a texture instead of geometry will increase the performance of your composition. The extent to which these textures are applied is adjusted using the metallic and roughness factor settings. Spatial Editor packs ORM values into the RGB channels in that order. |
| *Normal Texture* | Texture | Used to create the appearance of real-world surface detail like bumps, grooves and rivets without adding extra geometry to your object. |
| *Occlusion Texture* | Texture | Approximates soft shadows baked into the creased areas of a surface. The occlusion information is read from the red channel of the texture only. |
| *Occlusion Strength* | Percent | This determines the extent to which the occlusion texture has an effect on the material. |
| *Emissive Texture* | Texture | This texture provides emissive information indicating where the material should emit light. For example, power light indicators, LED displays or glowing eyes. |
| *Specular Factor* | Percent | The strength of the specular reflection. |
| *Specular Texture* | Image | Specular describes the strength and color tint of the specular reflectivity on dielectric materials. This texture defines the strength of the specular reflection, stored in the alpha channel. This will be multiplied by the Factor. |
| *Specular Color Texture* | Texture | A texture that defines the F0 (fresnel zero - a surface directly oriented toward the viewer) color of the specular reflection, stored in the RGB channels and encoded in sRGB. |
| *Specular Color* | Color | The F0 (fresnel zero - a surface directly oriented toward the viewer) color of the specular reflection. If using a texture, leave this white (255,255,255). |

## Design guidelines

- [Materials and textures](/horizon/design/art-assets#uvs-textures-and-materials)
