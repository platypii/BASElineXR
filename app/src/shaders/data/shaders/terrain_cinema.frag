#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <metaSpatialSdkFragmentBase.glsl>
#include <Uniforms.glsl>

const float OPACITY = 0.75;

// userParam0.x = fadeStart (m), default 400
// userParam0.y = fadeEnd   (m), default 600
// userParam0.z = rimStrength (0..1), default 0.25
// userParam0.w = exposure (0.5..2),   default 1.0
// userParam1.x = specStrength (0..2), default 1.0
// userParam1.y = diffuseWrap  (0..0.5), default 0.25
// userParam1.z = ambientBoost (0..2), default 1.0

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

    // material params (roughness, metallic, AO)
    float rmMix = getRoughnessMetallicMix();
    vec2 rmTex = texture(roughnessMetallicTexture, vertexOut.roughnessMetallicCoord).rg;
    float roughness = mix(g_MaterialUniform.matParams.x, rmTex.r, rmMix);
    float metallic  = mix(g_MaterialUniform.matParams.y, rmTex.g, rmMix);

    float aoMix = getOcclusionMix();
    float ao = mix(1.0, texture(occlusion, vertexOut.occlusionCoord).r, aoMix);

    // normal (supports normal map if available)
    vec3 N = normalize(vertexOut.worldNormal);
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
    #endif

    // lighting vectors
    vec3 L = normalize(-g_ViewUniform.sunDirection.xyz); // flip sign if sun appears inverted
    vec3 V = normalize(eyePos() - vertexOut.worldPosition);
    vec3 H = normalize(L + V);

    // tunables
    vec4 up0 = g_ViewUniform.userParam0;
    vec4 up1 = g_ViewUniform.userParam1;

    float fadeStart   = (up0.x > 0.0) ? up0.x : 400.0;
    float fadeEnd     = (up0.y > 0.0) ? up0.y : 600.0;
    float rimStrength = (up0.z > 0.0) ? up0.z : 0.1;
    float exposure    = (up0.w > 0.0) ? up0.w : 0.6;

    float specStrength = (up1.x > 0.0) ? up1.x : 1.0;
    float diffuseWrap  = (up1.y > 0.0) ? up1.y : 0.25;
    float ambientBoost = (up1.z > 0.0) ? up1.z : 0.25;

    // wrapped diffuse so sun reads on grazing slopes
    float ndl  = dot(N, L);
    float diff = saturate((ndl + diffuseWrap) / (1.0 + diffuseWrap));

    // specular (roughness -> gloss), Schlick Fresnel
    float ndh = saturate(dot(N, H));
    float specPower = mix(8.0, 128.0, 1.0 - saturate(roughness));
    float specBRDF  = pow(ndh, specPower);

    vec3  F0 = mix(vec3(0.04), albedo.rgb, metallic);
    float hv = saturate(dot(H, V));
    vec3  F  = F0 + (1.0 - F0) * pow(1.0 - hv, 5.0);

    float kD = (1.0 - metallic);

    // ambient * AO
    vec3 ambient = g_ViewUniform.ambientColor.rgb * ao * ambientBoost;

    // sun lighting
    vec3 sunCol = g_ViewUniform.sunColor.rgb;
    vec3 diffuseTerm  = kD * diff * sunCol;
    vec3 specularTerm = specStrength * specBRDF * F * sunCol;

    // rim light
    float rim = pow(1.0 - saturate(dot(N, V)), 3.0);
    vec3 rimTerm = rimStrength * rim * sunCol * (0.5 + 0.5 * (1.0 - roughness));

    // combine
    vec3 base = albedo.rgb;
    vec3 lit  = base * (ambient + diffuseTerm) + specularTerm + rimTerm;

    // tone map
    vec3 finalRGB = filmicTone(lit, exposure);
    finalRGB = saturate(finalRGB);

    // distance-based alpha fade (no fog)
    float d = length(eyePos() - vertexOut.worldPosition);
    float distFade = 1.0 - smoothstep(fadeStart, fadeEnd, d);
    distFade = clamp(distFade, 0.1, 0.9);

    // write
    outColor.rgb = finalRGB;
    outColor.a   = albedo.a * OPACITY * distFade;

    // If your pipeline expects premultiplied alpha, uncomment:
    // outColor.rgb *= outColor.a;
}
