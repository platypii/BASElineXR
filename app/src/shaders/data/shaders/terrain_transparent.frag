
#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

void main() {
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;

    // Apply alpha cutoff first to discard fully transparent pixels
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    float finalAlpha = albedo.a * 0.95; // opacity

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
