
#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float BRIGHTNESS = 3.0;
const float CONTRAST = 0.5; // 1.0 = S-curve contrast, 0.0 = no contrast enhancement
const float SATURATION = 1.3; // 1.0 = normal, >1.0 = more saturated

void main() {
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;

    // Calculate distance from camera to fragment
    float distance = length(vertexOut.worldPosition - getEyeCenter());

    // Distance fade parameters
    float minDistance = 100.0; // Full opacity until this distance
    float maxDistance = 200.0; // Fully transparent at this distance
    float minAlpha = 1; // Alpha value at minDistance and closer
    float maxAlpha = 0.0; // Alpha value at maxDistance and farther

    // Distance-based alpha: minAlpha until minDistance, then fade to maxAlpha at maxDistance
    float distanceAlpha;
    if (distance <= minDistance) {
        distanceAlpha = minAlpha;
    } else if (distance >= maxDistance) {
        distanceAlpha = maxAlpha;
    } else {
        // Linear fade from minAlpha to maxAlpha between minDistance and maxDistance
        float t = (distance - minDistance) / (maxDistance - minDistance);
        distanceAlpha = mix(minAlpha, maxAlpha, t);
    }

    // Apply alpha cutoff first to discard fully transparent pixels
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    float finalAlpha = albedo.a * distanceAlpha;

    // Screen‑door dither at alpha cutoff
    // 2‑tap hash → uniform random in [0,1]
    float dither = fract(dot(gl_FragCoord.xy , vec2(13.4, 17.3)));
    if(finalAlpha < alphaCutoff && dither > finalAlpha)
        discard;

    // Increase contrast using S-curve
    vec3 enhanced = albedo.rgb;
    enhanced = mix(albedo.rgb, enhanced * enhanced * (3.0 - 2.0 * enhanced), CONTRAST);

    // Boost saturation
    float luminance = dot(enhanced, vec3(0.299, 0.587, 0.114));
    enhanced = mix(vec3(luminance), enhanced, SATURATION);

    // Use vertex lighting for better performance
    vec3 lighting = vertexOut.lighting * g_ViewUniform.ambientColor.rgb;

    outColor.rgb = enhanced * lighting * BRIGHTNESS;
    outColor.a = finalAlpha;
}
