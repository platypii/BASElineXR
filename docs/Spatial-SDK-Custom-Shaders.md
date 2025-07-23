# Custom Shaders

Updated: May 13, 2025

## Overview

**Note** : This is an advanced, experimental feature.

This page explains how to integrate your custom shaders with the Spatial SDK.

Shaders are programs on your graphics card that calculate vertex positions and pixel colors for geometry. By default in Spatial SDK, we use physically based shaders to create realistic graphics.

For most use cases, you can use the default, physically based shader, or choose from the many built-in shaders. For some advanced use cases, like performance optimization or custom effects, you may want to create your own shaders for your application.

## Shader pipeline

To begin developing your own shaders, it’s helpful to understand the Spatial SDK shader pipeline. For each material, there is a pair of source shaders: the vertex (`.vert`) and fragment (`.frag`) shaders. The vertex shader computes the vertex locations of your geometry, and the fragment shader calculates the color of each pixel rendered.

These vertex and fragment source files are written in [OpenGL Shading Language (GLSL)](https://www.khronos.org/opengl/wiki/Core_Language_\(GLSL\)). The source files are compiled into a format that the graphics driver can interpret before you deploy your app using a program called [glslc](https://github.com/google/shaderc). Spatial SDK compiles these source shaders with many different configurations and loads them from your `assets` folder at runtime.

## Specifying shader overrides

By default, Spatial SDK uses a physically based shader found at `SceneMaterial.PHYSICALLY_BASED_SHADER`. This string value is defined as `"data/shaders/pbr/pbLit"`, which represents the path in the Android `assets` folder. This folder is also where compiled vertex and fragment shaders live (compiled from `pbLit.vert` and `pbLit.frag`).

Another built-in shader can be found at `SceneMaterial.UNLIT_SHADER`. This shader uses the model’s base color without adding lighting or shadows. This shader is useful for enhancing performance, or for models with pre-baked lighting.

### Location strings

To use these shaders, you can specify location strings as arguments in a number of Spatial SDK APIs.

 * In the `Mesh` component, specify a `defaultShaderOverride` to set the deafult shader for all materials loaded from the associated glTF. 
 * If you want to change the shader of a specific material in your glTF, you can utilize the [extras](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#reference-extras) properties on your glTF material with the `"meta_spatial_sdk_shader"` key. For example, you can set a material shader to be unlit with the following key/value pair: `"meta_spatial_sdk_shader": "data/shaders/unlit/unlit"`.
 * You can specify the shader on the `Material` component with the `shader` attribute. This lets you set the shaders for mesh creators like `mesh://box` and `mesh://sphere`.
 * You can set the shader path directly on `SceneMaterial`, either in the constructor, or through a custom `SceneMaterial` (described later in this guide).
 * For advanced cases, use this to manage a direct reference to a material.

## Plugin setup

To use a custom shader, compile it with your app’s build process using our Meta Spatial Gradle Plugin. This plugin is used for many Spatial SDK features, like integration with [Meta Spatial Editor](/horizon/documentation/spatial-sdk/spatial-editor-overview/), [hot reloading](/horizon/documentation/spatial-sdk/spatial-sdk-hot-reload), and shader compilation. Apply it to your project as a plugin to use it.

After you apply the plugin, configure your `spatial` block:

```kotlin
// later in your build.gradle.kts
spatial {
    ...
    shaders {
        sources.add(
            // replace with your shader directory
            project.layout.projectDirectory.dir("src/shaders")
        )
    }
}
```

In our samples (for example, [MediaPlayerSample](https://github.com/meta-quest/Meta-Spatial-SDK-Samples/tree/main/MediaPlayerSample)), there is a custom directory in `app/src/shaders`. You can put your folders wherever you would like, as long as they are not in `app/src/main/shaders`, as this will conflict with some built-in Android Studio shader compilation.

If you try to build your app at this stage, you might get an error that the Native Development Kit (NDK) is not installed. This is because the Meta Spatial Plugin uses the glslc executable bundled with the Android NDK. Instructions for installing it can be found [here](https://developer.android.com/studio/projects/install-ndk).

Specify the NDK version you’re using in your `android` block to ensure compatibility:

```kotlin
android {
    ...
    // use the version that was found on the "SDK Tools" window
    ndkVersion = "..."
}
```

To confirm everything works as expected, create two shader files.

 1. Create the vertex shader at `src/shaders/myShader.vert`:

```glsl
#version 430
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkDefaultVertex.glsl>
```

 1. Create the fragment shader at `src/shaders/myShader.frag`:

```glsl
#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>

void main() {
    // simply write out a red color
    outColor = vec4(1.0f, 0.0f, 0.0f, 1.0f);
}
```

If you now build your app, your shaders should be compiled and packaged in your `assets` folder. You can then reference them in your app as `myShader`, which should make the rendered objects red.

## Shader interface

By default, shaders are assumed to be working off of the physically based material setup. With this setup, we have set up a pipeline to take base color, emissive, roughness/metallic, and other textures as well as other scalar parameters to render in a physically based way. If you want to build a shader off of these parameters, there are a number of files to help with authoring your shaders. Any file prefixed with `metaSpatialSdk` will be relative to the default material setup while others will be useful in any custom shader.

All material uniforms and attributes need to be specified to match the layout in the default `metaSpatialSdkDefaultVertex.glsl` shader example.

### app2vertex.glsl

This file gives access to the base vertex geometry, including positions, normals, and UV coordinates. To get access, you call `App2VertexUnpacked app = getApp2VertexUnpacked();`.

```glsl
struct App2VertexUnpacked {
    // in object space
    vec3 position;
    // normalized, in object space
    vec3 normal;
    // material albedo color without texture in linear
    vec3 linearColor;
    // texture coordinate 0
    vec2 uv;

#if VERTEX_FORMAT_TWOUV == 1 || VERTEX_FORMAT_TWOUV_TANGENT == 1
    // texture coordinate 1
    vec2 u1;
#endif

#if VERTEX_FORMAT_TWOUV_TANGENT == 1
    // XYZ unit vector defining a tangential direction on the surface.
    // W component defines the handedness of the tangent basis (-1 or 1).
    vec4 tangent;
#endif

#if VERTEX_FORMAT_SKINNED == 1
    // For 4-bone skinning; each uint in the uvec4 specifies an index into a skinningMatrices array
    uvec4 jointIndices;
    // For 4-bone skinning; each float in the vec4 specifies a weight used to weight the effect of the skinning matrix selected by jointIndices[i]
    vec4 jointWeights;
#endif
}

App2VertexUnpacked getApp2VertexUnpacked();
```

### Uniforms.glsl

This file gives you access to a struct of data that does not change throughout the rendering of all objects in a given frame including any object transformation matrices. You can access this data by including this file and using `g_ViewUniform` and `g_PrimitiveUniform`.

```glsl
struct ViewUniform {
    mat4 clipFromWorld0;        // mono or left eye stereo
    mat4 clipFromWorld1;        // right eye stereo
    vec4 eyeCenter0;            // left eye location in world space, .w = 0
    vec4 eyeCenter1;            // right eye location in world space, .w = 0
    vec4 random;                // .x: random 0..1, .yzw: unused
    vec4 moduloTime;            // modulus of time in sec. .x: mod 1sec, .y: mod 1min, .z: mod 1hour, w: unused
    uvec4 time;                 // .x: total time spent on rendering any scene in msec, .y: frame no, .zw: unused
    vec4 renderTargetSize;      // .xy: size of render target width/height, .z: 1/width, w: 1/height
    vec4 viewParam1;            // test variable that can be used in multiple places
    vec4 viewParam2;            // test variable that can be used in multiple places
    ivec4 editorShaderData;     // .x: editor shader visualization index.
    vec4 ambientColor;          // .rgb: for simple lighting, .a:unused
    vec4 sunColor;              // .rgb: for simple lighting, .a:unused
    vec4 sunDirection;          // .rgb: for simple lighting, .a:unused
    vec4 environmentIntensity;  // .x: intensity multiplier for IBL, .yzw: unused
    vec4 sh0;                   // .xyz: spherical harmonics coefficients used for diffuse IBL, .w:unused
    vec4 sh1;                   // .xyz: see sh0, .w:unused
    vec4 sh2;                   // .xyz: see sh0, .w:unused
    vec4 sh3;                   // .xyz: see sh0, .w:unused
    vec4 sh4;                   // .xyz: see sh0, .w:unused
    vec4 sh5;                   // .xyz: see sh0, .w:unused
    vec4 sh6;                   // .xyz: see sh0, .w:unused
    vec4 sh7;                   // .xyz: see sh0, .w:unused
    vec4 sh8;                   // .xyz: see sh0, .w:unused
    vec4 userParam0;
    vec4 userParam1;
    vec4 userParam2;
    vec4 userParam3;
} g_ViewUniform;

struct PrimitiveUniform {
    mat4 worldFromObject;   // useful to transform positions from object to world or normals and tagents to object space
    mat4 objectFromWorld;   // inverse of worldFromObject
} g_PrimitiveUniform;
```

### metaSpatialSdkMaterialBase.glsl

This file contains most of the scalar uniforms for your material setup. It will give you access to the `g_MaterialUniform` variable to access these values.

```glsl
uniform MaterialUniform {
    vec4 matParams;        // x = roughness, y = metallic, z = unlit
    vec4 alphaParams;      // x = minAlpha, y = cutoff
    vec4 stereoParams;     // controls UV per eye, xy = eye 2 xy offset, zw = xy scale
    vec4 emissiveFactor;   // multiplier for emissive texture
    vec4 albedoFactor;     // multiplier for base color texture

    // used in the below transform functions
    vec4 albedoUVTransformM00;
    vec4 albedoUVTransformM10;
    vec4 roughnessMetallicUVTransformM00;
    vec4 roughnessMetallicUVTransformM10;
    vec4 emissiveUVTransformM00;
    vec4 emissiveUVTransformM10;
    vec4 occlusionUVTransformM00;
    vec4 occlusionUVTransformM10;
    vec4 normalUVTransformM00;
    vec4 normalUVTransformM10;
} g_MaterialUniform;

// can be used to get transformed texture UVs
vec2 getAlbedoCoord(vec2 uv);
float getAlbedoMix();

vec2 getRoughnessMetallicCoord(vec2 uv);
float getRoughnessMetallicMix();

vec2 getEmissiveCoord(vec2 uv);
float getEmissiveMix();

vec2 getOcclusionCoord(vec2 uv);
float getOcclusionMix();

vec2 getNormalCoord(vec2 uv);
float getNormalMix();
```

### metaSpatialSdkVertexBase.glsl

This file sets out the common output format from the vertex shader used by the fragment shaders (via `vertexOut`) as well as exposes bindings to a material uniform buffer used for skinning (in `g_StorageBuffer`).

```glsl
// includes from "common.glsl", "app2vertex.glsl", and "metaSpatialSdkMaterialBase.glsl"
out struct {
    vec4 color;
    vec2 albedoCoord;
    vec2 roughnessMetallicCoord;
    vec2 emissiveCoord;
    vec2 occlusionCoord;
    vec2 normalCoord;
    vec3 lighting;
    vec3 worldNormal;
    vec3 worldPosition;
#if VERTEX_FORMAT_TWOUV_TANGENT == 1
    vec4 tangent;
#endif
} vertexOut;

#if VERTEX_FORMAT_SKINNED == 1
readonly buffer StorageBuffer {
    mat4 skinningMatrices[];
} g_StorageBuffer;
#endif
```

### metaSpatialSdkFragmentBase.glsl

This file sets up all the material textures and inputs interpolated from the vertex shaders.

```glsl
// includes from "metaSpatialSdkMaterialBase.glsl"
// used for IBL
uniform sampler2D brdfLookup;
uniform samplerCube specularCubemap;
uniform sampler2DArray depth;

uniform sampler2D albedoSampler;
uniform sampler2D roughnessMetallicTexture;
uniform sampler2D emissive;
uniform sampler2D occlusion;
#if VERTEX_FORMAT_TWOUV_TANGENT == 1
uniform sampler2D normalMap;
#endif

in struct {
    vec4 color;
    vec2 albedoCoord;
    vec2 roughnessMetallicCoord;
    vec2 emissiveCoord;
    vec2 occlusionCoord;
    vec2 normalCoord;
    vec3 lighting;
    vec3 worldNormal;
    vec3 worldPosition;
#if VERTEX_FORMAT_TWOUV_TANGENT == 1
    vec4 tangent;
#endif
} vertexOut;

out vec4 outColor;
```

### metaSpatialSdkDefaultVertex.glsl

This sets up a vertex shader if you don’t want to rewrite your own. It is recommended to avoid re-implementing operations like joint skinning.

Putting all of these together, you can write a simple fragment shader that changes the colors of a model as you move it around like so:

```glsl
#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

void main() {
    vec4 pixel = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    if(pixel.a < alphaCutoff){
        discard;
    }
    // tint the object based on it's position in the world
    outColor.xyz = pixel.xyz * sin(vertexOut.worldPosition.xyz * 10.0f);
    outColor.a = pixel.a;
}
```

## Custom materials

Sometimes, you may want to specify custom inputs and you don’t need the whole physically based material setup. For these cases you can use custom `SceneMaterial`s. Below is an example roughly lifted from our MediaPlayer sample. Please check out the sample for full implementation details.

```kotlin
val myMaterial = SceneMaterial.custom(
    "data/shaders/custom/360",
    arrayOf(
        // define the some standard material attributes.
        SceneMaterialAttribute("albedoSampler", SceneMaterialDataType.Texture2D),
        SceneMaterialAttribute("stereoParams", SceneMaterialDataType.Vector4),
        // define the custom material attributes.
        SceneMaterialAttribute("customParams", SceneMaterialDataType.Vector4)
    ))

// update a value
myMaterial.apply {
    // set to texture red
    setTexture("albedoSampler", SceneTexture(Color.valueOf(1f, 0f, 0f, 1f)))
    setAttribute("customParams", Vector4(1.0f, 0f, 0f, 0f))
}
```

Currently, we support vector4 and texture material definitions in your custom materials. For how these translate to the shaders, check out the implementation of the vertex and fragment shaders.

```glsl
// in data/shaders/custom/360.vert
...
// vec4s are stored in set 3, binding 0 (in order they were defined)
layout (std140, set = 3, binding = 0) uniform MaterialUniform {
    vec4 stereoParams;
    vec4 customParams;
} g_MaterialUniform;

vec2 stereo(vec2 uv) {
    return getStereoPassId() * g_MaterialUniform.stereoParams.xy + uv * g_MaterialUniform.stereoParams.zw;
}

void main() {
    App2VertexUnpacked app = getApp2VertexUnpacked();

    vec4 wPos4 = g_PrimitiveUniform.worldFromObject * vec4(app.position, 1.0f);
    vertexOut.albedoCoord = stereo(app.uv);
    vertexOut.lighting = app.incomingLighting;
    vertexOut.worldPosition = wPos4.xyz;
    vertexOut.worldNormal = normalize((transpose(g_PrimitiveUniform.objectFromWorld) * vec4(app.normal, 0.0f)).xyz);

    gl_Position = getClipFromWorld() * wPos4;

    postprocessPosition(gl_Position);
}
```

```glsl
// in data/shaders/custom/360.frag
...

// vec4s are stored in set 3, binding 0 (in order they were defined)
layout (std140, set = 3, binding = 0) uniform MaterialUniform {
    vec4 stereoParams;
    vec4 customParams;
} g_MaterialUniform;

// textures are in set 3, in bindings 1+ (in order they were defined)
layout (set = 3, binding = 1) uniform sampler2D albedoSampler;

...

layout (location = 0) out vec4 outColor;

void main() {
    vec4 pixel = texture(albedoSampler, vertexOut.albedoCoord);

    // direction the transition will start
    vec3 direction = vec3(0.0, 0.0, 1.0);

    // angular distance the vertex is from the direction, from -1 to 1
    float d = dot(vertexOut.worldNormal, direction);
    d = (d+1.0)*0.5; // normalise the dot product to 0 to 1

    float amount = clamp(1.0-g_MaterialUniform.customParams.x, 0.0, 1.0);
    float feather = 0.05;
    float alpha = smoothstep(d-feather, d+feather, amount);

    outColor.rgba = vec4(pixel.rgb, alpha);
}
```

## Design guidelines

 * [Balancing art and performance](/horizon/design/art-and-performance/)
 * [Art direction](/horizon/design/art-direction/)
 