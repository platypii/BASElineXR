#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float OPACITY = 1.0;
const float DEFAULT_FADE_START = 400.0;
const float DEFAULT_FADE_END = 700.0;
const float CLOSE_ALPHA = 0.9;
const float FAR_ALPHA = 0.1;

float saturate(float x) { return clamp(x, 0.0, 1.0); }
vec3  saturate(vec3 v)  { return clamp(v, 0.0, 1.0); }

vec3 filmicTone(vec3 c, float exposure) {
    // simple photographic curve
    return 1.0 - exp(-c * max(exposure, 1e-3));
}

vec3 eyePos() {
    // left-eye is fine for both passes here
    return g_ViewUniform.eyeCenter0.xyz;
}

void main() {
    // base color
    vec4 albedo = texture(albedoSampler, vertexOut.albedoCoord) * vertexOut.color;

    // alpha cutoff
    float alphaCutoff = g_MaterialUniform.alphaParams.y;
    if (albedo.a < alphaCutoff) discard;

    // normal
    vec3 N;
    #if VERTEX_FORMAT_TWOUV_TANGENT == 1
    {
        float nMix = getNormalMix();
        if (nMix > 0.0) {
            vec3 T = normalize((g_PrimitiveUniform.worldFromObject * vec4(vertexOut.tangent.xyz, 0.0)).xyz);
            vec3 B = normalize(cross(N, T) * vertexOut.tangent.w);
            mat3 TBN = mat3(T, B, N);
            vec3 nTex = texture(normalMap, vertexOut.normalCoord).xyz * 2.0 - 1.0;
            vec3 nW   = normalize(TBN * nTex);
            N = normalize(mix(N, nW, saturate(nMix)));
        }
    }
    #else
        N = normalize(vertexOut.worldNormal);
    #endif

    // light dir (keep normalized)
    vec3 L = normalize(-g_ViewUniform.sunDirection.xyz); // flip sign if needed

    // tunables
    vec4 up0 = g_ViewUniform.userParam0;
    vec4 up1 = g_ViewUniform.userParam1;

    float fadeStart   = (up0.x > 0.0) ? up0.x : DEFAULT_FADE_START;
    float fadeEnd     = (up0.y > 0.0) ? up0.y : DEFAULT_FADE_END;
    float exposure    = (up0.w > 0.0) ? up0.w : 0.6;

    float diffuseWrap  = (up1.y > 0.0) ? up1.y : 0.25;
    float ambientBoost = (up1.z > 0.0) ? up1.z : 0.25;

    // wrapped diffuse so sun reads on grazing slopes
    float ndl  = dot(N, L);
    float diff = saturate((ndl + diffuseWrap) / (1.0 + diffuseWrap));

    // ambient + sun diffuse only (no specular/Fresnel)
    vec3 ambient = g_ViewUniform.ambientColor.rgb * ambientBoost;
    vec3 sunCol  = g_ViewUniform.sunColor.rgb;
    vec3 lit     = albedo.rgb * (ambient + diff * sunCol);

    // tone map
    vec3 finalRGB = saturate(filmicTone(lit, exposure));

    // distance-based alpha fade (no sqrt)
    vec3 toEye = eyePos() - vertexOut.worldPosition;
    float d2 = dot(toEye, toEye);
    float s2 = fadeStart * fadeStart;
    float e2 = fadeEnd   * fadeEnd;
    float distFade = 1.0 - smoothstep(s2, e2, d2);
    distFade = clamp(distFade, FAR_ALPHA, CLOSE_ALPHA);

    // write
    outColor.rgb = finalRGB;
    outColor.a   = albedo.a * OPACITY * distFade;

    // If your pipeline expects premultiplied alpha, uncomment:
    // outColor.rgb *= outColor.a;
}
