#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float OPACITY = 1.0;
const float FADE_START = 400.0;
const float FADE_END = 700.0;
const float CUTOFF_DIST = 1200.0;
const float CLOSE_ALPHA = 0.9;
const float FAR_ALPHA = 0.01;

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3  saturate(vec3 v)  { return clamp(v, 0.0, 1.0); }

vec3 filmicTone(vec3 c, float exposure) {
    // simple photographic curve
    return 1.0 - exp(-c * max(exposure, 1e-3));
}

void main() {
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color * g_MaterialUniform.albedoFactor;

    // alpha cutoff
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    if (albedo.a < alphaCutoff) discard;

    // constants
    float exposure    = 0.6;
    float diffuseWrap = 0.25;
    float ambientBoost = 0.25;

    // distance-based alpha fade (no sqrt)
    vec3 eyePos = g_ViewUniform.eyeCenter0.xyz;
    vec3 toEye = eyePos - vertexOut.worldPosition;
    float d2 = dot(toEye, toEye);
    float s2 = FADE_START * FADE_START;
    float e2 = FADE_END * FADE_END;
    float c2 = CUTOFF_DIST * CUTOFF_DIST;

    // Discard beyond fade_end
    if (d2 > c2) discard;

    float distFade = 1.0 - smoothstep(s2, e2, d2);
    distFade = clamp(distFade, FAR_ALPHA, CLOSE_ALPHA);

    // wrapped diffuse so sun reads on grazing slopes
    vec3 N = normalize(vertexOut.worldNormal);
    vec3 L = normalize(-g_ViewUniform.sunDirection.xyz);
    float ndl  = dot(N, L);
    float diff = saturate((ndl + diffuseWrap) / (1.0 + diffuseWrap));

    // ambient + sun diffuse only (no specular/Fresnel)
    vec3 ambient = g_ViewUniform.ambientColor.rgb * ambientBoost;
    vec3 sunCol  = g_ViewUniform.sunColor.rgb;
    vec3 lit     = albedo.rgb * (ambient + diff * sunCol);

    // tone map
    vec3 finalRGB = saturate(filmicTone(lit, exposure));

    // write
    outColor.rgb = finalRGB;
    outColor.a   = albedo.a * OPACITY * distFade;

    // Apply slight depth bias to render high LOD in front of low LOD
    // Push fragments slightly closer to camera to avoid z-fighting
    gl_FragDepth = gl_FragCoord.z - 0.00001;
}