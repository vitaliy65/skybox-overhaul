#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

const int FLAG_MASK_DIR = 7;
const int FLAG_INSIDE_FACE = 1 << 4;
const int FLAG_USE_TOP_COLOR = 1 << 5;
const int FLAG_EXTRA_Z = 1 << 6;
const int FLAG_EXTRA_X = 1 << 7;

// 26.3 CloudInfo is exactly 44 bytes. PackedSunDirection occupies the spare word at offset 28.
// The alpha word at offset 12 contains alpha/twilight/moonlight/lightning in its mantissa.
layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec3 CloudOffset;
    float PackedSunDirection;
    vec3 CellSize;
};

uniform isamplerBuffer CloudFaces;

layout(location = 0) out float vertexDistance;
layout(location = 1) out vec4 vertexColor;

const vec3[] vertices = vec3[](
    vec3(1, 0, 0), vec3(1, 0, 1), vec3(0, 0, 1), vec3(0, 0, 0),
    vec3(0, 1, 0), vec3(0, 1, 1), vec3(1, 1, 1), vec3(1, 1, 0),
    vec3(0, 0, 0), vec3(0, 1, 0), vec3(1, 1, 0), vec3(1, 0, 0),
    vec3(1, 0, 1), vec3(1, 1, 1), vec3(0, 1, 1), vec3(0, 0, 1),
    vec3(0, 0, 1), vec3(0, 1, 1), vec3(0, 1, 0), vec3(0, 0, 0),
    vec3(1, 0, 0), vec3(1, 1, 0), vec3(1, 1, 1), vec3(1, 0, 1)
);

const vec4[] faceColors = vec4[](
    vec4(0.7, 0.7, 0.7, 1.0),
    vec4(1.0, 1.0, 1.0, 1.0),
    vec4(0.8, 0.8, 0.8, 1.0),
    vec4(0.8, 0.8, 0.8, 1.0),
    vec4(0.9, 0.9, 0.9, 1.0),
    vec4(0.9, 0.9, 0.9, 1.0)
);

void main() {
    int quadVertex = gl_VertexIndex % 4;
    int index = (gl_VertexIndex / 4) * 3;

    int cellX = texelFetch(CloudFaces, index).r;
    int cellZ = texelFetch(CloudFaces, index + 1).r;
    int dirAndFlags = texelFetch(CloudFaces, index + 2).r;
    int direction = dirAndFlags & FLAG_MASK_DIR;
    bool isInsideFace = (dirAndFlags & FLAG_INSIDE_FACE) == FLAG_INSIDE_FACE;
    bool useTopColor = (dirAndFlags & FLAG_USE_TOP_COLOR) == FLAG_USE_TOP_COLOR;
    cellX = (cellX << 1) | ((dirAndFlags & FLAG_EXTRA_X) >> 7);
    cellZ = (cellZ << 1) | ((dirAndFlags & FLAG_EXTRA_Z) >> 6);

    vec3 faceVertex = vertices[(direction * 4) + (isInsideFace ? 3 - quadVertex : quadVertex)];
    vec3 pos = (faceVertex * CellSize.xyz) + (vec3(cellX, 0, cellZ) * CellSize.xyz) + CloudOffset.xyz;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexDistance = fog_spherical_distance(pos);

    uint packedLighting = floatBitsToUint(CloudColor.a) & 0x007FFFFFu;
    float cloudAlpha = float((packedLighting >> 16u) & 0x7Fu) / 127.0;
    float twilight = float((packedLighting >> 10u) & 0x3Fu) / 63.0;
    float moonlight = float((packedLighting >> 5u) & 0x1Fu) / 31.0;
    float lightning = float(packedLighting & 0x1Fu) / 31.0;
    vec4 color = (useTopColor ? faceColors[1] : faceColors[direction]) * vec4(CloudColor.rgb, cloudAlpha);

    // Directional golden-hour lighting. Cloud mesh coordinates are world-axis aligned but
    // camera-relative, so +X is sunrise/east and -X is sunset/west from the viewer's position.
    // The tint is a continuous azimuth gradient: cool violet on the shadow side, rose in the
    // transition, and peach/amber toward the low sun. It is deliberately visible in both
    // Fancy (extruded faces) and Fast (flat faces) cloud modes.
    float packedSunDirection = PackedSunDirection;
    float sunSide = packedSunDirection < 0.0 ? -1.0 : 1.0;
    float warmPhase = clamp((abs(packedSunDirection) - 1.0) * 2.0, 0.0, 1.0);

    float horizontalDistance = length(pos.xz);
    vec2 cloudAzimuth = pos.xz / max(horizontalDistance, 0.001);
    float sunAlignment = clamp(dot(cloudAzimuth, vec2(sunSide, 0.0)), -1.0, 1.0);

    // A broad transition gives the clouds a real sky-wide gradient rather than a hard half-sky mask.
    vec3 shadowViolet = vec3(0.42, 0.45, 0.66);
    vec3 rosePink = vec3(0.78, 0.50, 0.68);
    vec3 dustyRose = vec3(0.91, 0.61, 0.71);
    vec3 peach = vec3(1.00, 0.65, 0.47);
    vec3 amber = vec3(1.00, 0.76, 0.53);

    float roseBlend = smoothstep(-0.78, 0.20, sunAlignment);
    float amberBlend = smoothstep(0.24, 0.92, sunAlignment);
    vec3 warmRamp = mix(shadowViolet, rosePink, roseBlend);
    warmRamp = mix(warmRamp, peach, amberBlend * 0.72);
    warmRamp = mix(warmRamp, amber, amberBlend * 0.34);

    // After the sun drops below the horizon, the orange ramp cools into a pink/violet
    // afterglow. The phase comes from the magnitude packed into PackedSunDirection.
    vec3 afterglowRamp = mix(shadowViolet * 0.92, rosePink * 0.94,
                             smoothstep(-0.72, 0.28, sunAlignment));
    afterglowRamp = mix(afterglowRamp, dustyRose,
                        smoothstep(0.12, 0.94, sunAlignment) * 0.58);
    vec3 twilightColor = mix(afterglowRamp, warmRamp, warmPhase);

    // The low horizon catches the strongest colour. Overhead clouds still receive a weaker
    // atmospheric bounce, so the gradient does not disappear when the player looks upward.
    float viewElevation = degrees(atan(abs(pos.y), max(horizontalDistance, 0.001)));
    float horizonInfluence = 1.0 - smoothstep(12.0, 58.0, viewElevation);
    float elevationFactor = mix(0.58, 1.0, horizonInfluence);
    float tintStrength = clamp(twilight * elevationFactor * 0.88, 0.0, 0.82);

    // Blend rather than only multiply RGB: the previous multiplicative tint mostly darkened
    // white clouds and produced too little hue shift to be visible in-game.
    vec3 twilightLit = color.rgb * 0.46 + twilightColor * 0.64;
    color.rgb = mix(color.rgb, twilightLit, tintStrength);

    // In Fancy mode, the vertical cloud wall facing the sun gets an extra warm rim light.
    // Direction values follow Minecraft Direction: WEST=4, EAST=5.
    bool sunFacingWall = (sunSide > 0.0 && direction == 5)
                      || (sunSide < 0.0 && direction == 4);
    if (sunFacingWall && !useTopColor) {
        float rimStrength = twilight * 0.24;
        vec3 rimColor = mix(vec3(0.80, 0.57, 0.76), vec3(1.0, 0.64, 0.43), warmPhase);
        color.rgb = mix(color.rgb, rimColor, rimStrength);
    }

    // Cold moon fill comes from the opposite X direction to the sun, with a subtle directional gradient.
    float moonSide = -sunSide;
    float moonAlignment = clamp(dot(cloudAzimuth, vec2(moonSide, 0.0)), -1.0, 1.0);
    float moonFacing = smoothstep(-0.40, 0.85, moonAlignment);
    float moonInfluence = moonlight * (0.42 + 0.58 * moonFacing);
    vec3 moonTint = vec3(0.40, 0.56, 0.88);
    color.rgb = mix(color.rgb, color.rgb * 0.90 + moonTint * 0.32, moonInfluence);

    // Lightning briefly lights every visible face with a cool white-blue flash.
    color.rgb = min(color.rgb + vec3(0.58, 0.68, 0.88) * lightning, vec3(1.0));

    vertexColor = color;
}
