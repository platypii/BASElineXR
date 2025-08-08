
#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

void main() {
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;

    // Calculate distance from camera to fragment
    float distance = length(vertexOut.worldPosition - getEyeCenter());

    // Distance fade parameters
    float minDistance = 400.0; // Full opacity until this distance
    float maxDistance = 500.0; // Fully transparent at this distance
    float minAlpha = 0.8; // Alpha value at minDistance and closer
    float maxAlpha = 0.1; // Alpha value at maxDistance and farther

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

    // Use vertex lighting for better performance
    vec3 lighting = vertexOut.lighting * g_ViewUniform.ambientColor.rgb;

    outColor.rgb = albedo.rgb * lighting;
    outColor.a = finalAlpha;
}
