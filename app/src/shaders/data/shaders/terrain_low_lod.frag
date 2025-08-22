#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float OPACITY = 1.0;
const float FADE_START = 1000.0;
const float FADE_END = 1200.0;
const float CUTOFF_DIST = 1000.0;
const float CLOSE_ALPHA = 0.1;
const float FAR_ALPHA = 0.1;

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3  saturate(vec3 v)  { return clamp(v, 0.0, 1.0); }

vec3 filmicTone(vec3 c, float exposure) {
    // simple photographic curve
    return 1.0 - exp(-c * max(exposure, 1e-3));
}

void main() {
    // distance-based cutoff check first (before texture sampling to save compute)
    vec3 eyePos = g_ViewUniform.eyeCenter0.xyz;
    vec3 toEye = eyePos - vertexOut.worldPosition;
    float d2 = dot(toEye, toEye);
    float c2 = CUTOFF_DIST * CUTOFF_DIST;

    // Discard fragments closer than cutoff distance before texture sampling
    if (d2 < c2) discard;

    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color * g_MaterialUniform.albedoFactor;

    // alpha cutoff
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    if (albedo.a < alphaCutoff) discard;

    // constants
    float exposure    = 0.6;
    float diffuseWrap = 0.25;
    float ambientBoost = 0.25;

    // distance fade calculations
    float s2 = FADE_START * FADE_START;
    float e2 = FADE_END * FADE_END;

    // linear ramp instead of smoothstep
    float invRange = 1.0 / max(e2 - s2, 1e-6);
    float ramp     = saturate((e2 - d2) * invRange);
    float distFade = mix(FAR_ALPHA, CLOSE_ALPHA, ramp);

    // wrapped diffuse so sun reads on grazing slopes
    vec3 N = vertexOut.worldNormal;
    vec3 L = -g_ViewUniform.sunDirection.xyz;
    float ndl  = dot(N, L);
    float diff = saturate((ndl + diffuseWrap) / (1.0 + diffuseWrap));

    // ambient + sun diffuse only (no specular/Fresnel)
    vec3 ambient = g_ViewUniform.ambientColor.rgb * ambientBoost;
    vec3 sunCol  = g_ViewUniform.sunColor.rgb;
    vec3 lit     = albedo.rgb * (ambient + diff * sunCol);

    // tone map
    vec3 finalRGB = saturate(filmicTone(lit, exposure));

    outColor.rgb = finalRGB;
    outColor.a = albedo.a * OPACITY * distFade;

    // Apply slight depth bias to render high LOD in front of low LOD
    // Push fragments slightly back from camera to avoid z-fighting
    // gl_FragDepth = gl_FragCoord.z + 0.00001;
}
