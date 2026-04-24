#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float OPACITY = 0.75;

void main() {
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;

    // Add alpha cutoff test (critical for proper depth handling)
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    if(albedo.a < alphaCutoff) {
        discard;
    }

    // Use vertex lighting for better performance
    vec3 lighting = vertexOut.lighting * g_ViewUniform.ambientColor.rgb;

    outColor.rgb = albedo.rgb * lighting;
    outColor.a = albedo.a * OPACITY;
}
