#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float OPACITY = 1.0;

// Fog parameters
const float FOG_START = 800.0;  // Fog starts at 400m
const float FOG_END = 1600.0;    // Full fog at 1200m
const vec3 FOG_COLOR = vec3(0.7, 0.75, 0.8);  // Light blue-gray fog
const float FOG_STRENGTH = 0.15;

void main() {
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;

    // Add alpha cutoff test (critical for proper depth handling)
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    if(albedo.a < alphaCutoff) {
        discard;
    }

    // Use vertex lighting for better performance
    vec3 lighting = vertexOut.lighting * g_ViewUniform.ambientColor.rgb;

    // Calculate distance-based fog
    vec3 eyePos = g_ViewUniform.eyeCenter0.xyz;
    float distance = length(eyePos - vertexOut.worldPosition);

    // Calculate fog factor (0 = no fog, 1 = full fog)
    float fogFactor = clamp((distance - FOG_START) / (FOG_END - FOG_START), 0.0, 1.0);

    // Smooth the fog transition
    fogFactor = smoothstep(0.0, 1.0, fogFactor);

    // Mix terrain color with fog color
    vec3 terrainColor = albedo.rgb * lighting;
    vec3 finalColor = mix(terrainColor, FOG_COLOR, fogFactor * FOG_STRENGTH);

    outColor.rgb = finalColor;
    outColor.a = albedo.a * OPACITY;
}
