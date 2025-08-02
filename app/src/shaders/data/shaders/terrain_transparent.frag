
#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float OPACITY = 1.0;
const float BRIGHTNESS = 3.0;
const float CONTRAST = 0.5; // 1.0 = S-curve contrast, 0.0 = no contrast enhancement
const float SATURATION = 1.3; // 1.0 = normal, >1.0 = more saturated

void main() {
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;

    // Increase contrast using S-curve
    vec3 enhanced = albedo.rgb;
    enhanced = mix(albedo.rgb, enhanced * enhanced * (3.0 - 2.0 * enhanced), CONTRAST);

    // Boost saturation
    float luminance = dot(enhanced, vec3(0.299, 0.587, 0.114));
    enhanced = mix(vec3(luminance), enhanced, SATURATION);

    // Apply gamma correction for better VR display
    // enhanced = pow(enhanced, vec2(0.85).xxx); // Slight gamma boost

    // Use vertex lighting for better performance
    vec3 lighting = vertexOut.lighting * g_ViewUniform.ambientColor.rgb;

    outColor.rgb = enhanced * lighting * BRIGHTNESS;
    outColor.a = OPACITY;
}
