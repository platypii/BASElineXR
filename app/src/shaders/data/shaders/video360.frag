#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>

void main() {
    // Use the albedo texture from the material
    vec4 texColor = texture(albedoSampler, vertexOut.albedoCoord);

    // Multiply by vertex color and material factor
    vec4 color = texColor * vertexOut.color * g_MaterialUniform.albedoFactor;

    // Output the color
    outColor = color;
}
