#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

const int FLAG_MASK_DIR = 7;
const int FLAG_INSIDE_FACE = 1 << 4;
const int FLAG_USE_TOP_COLOR = 1 << 5;
const int FLAG_EXTRA_Z = 1 << 6;
const int FLAG_EXTRA_X = 1 << 7;

// The last component of the two vec3 members occupies std140 padding in vanilla CloudInfo.
// CloudRenderer still writes the exact same 48-byte layout.
layout(std140) uniform CloudInfo {
    vec4 CloudColor;
    vec4 CloudOffset;
    vec4 CellSize;
};

uniform isamplerBuffer CloudFaces;

out float vertexDistance;
out vec4 vertexColor;

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
    int quadVertex = gl_VertexID % 4;
    int index = (gl_VertexID / 4) * 3;

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

    vec4 color = (useTopColor ? faceColors[1] : faceColors[direction]) * CloudColor;

    // Directional golden-hour lighting. Cloud mesh coordinates are world-axis aligned but
    // camera-relative, so +X is sunrise/east and -X is sunset/west from the viewer's position.
    // The tint is a continuous azimuth gradient: cool violet on the shadow side, rose in the
    // transition, and peach/amber toward the low sun. It is deliberately visible in both
    // Fancy (extruded faces) and Fast (flat faces) cloud modes.
    float packedSunDirection = CloudOffset.w;
    float sunSide = packedSunDirection < 0.0 ? -1.0 : 1.0;
    float warmPhase = clamp((abs(packedSunDirection) - 1.0) * 2.0, 0.0, 1.0);
    // CellSize.w carries three packed 8-bit lighting values: sunset/dawn, moonlight, lightning.
    // Every packed integer through 0xFFFFFF is exactly representable by a 32-bit float.
    float packedLighting = max(CellSize.w, 0.0);
    float twilight = mod(packedLighting, 256.0) / 255.0;
    float moonlight = mod(floor(packedLighting / 256.0), 256.0) / 255.0;
    float lightning = floor(packedLighting / 65536.0) / 255.0;

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
    // afterglow. The phase comes from the magnitude packed into CloudOffset.w.
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

    // The moon is on the opposite side of the solar path. Its contribution is deliberately
    // weak and blue-white, but it lifts cloud faces instead of leaving night clouds black.
    float moonFacing = smoothstep(-0.45, 0.92, -sunAlignment);
    vec3 moonTint = mix(vec3(0.25, 0.33, 0.53), vec3(0.58, 0.70, 0.94), moonFacing);
    float moonBlend = moonlight * (0.16 + 0.34 * moonFacing);
    color.rgb = mix(color.rgb, max(color.rgb, moonTint), moonBlend);

    // ClientLevel's sky-flash timer drives a brief cloud-wide lightning pulse.
    vec3 lightningTint = vec3(0.78, 0.87, 1.0);
    color.rgb = mix(color.rgb, max(color.rgb, lightningTint), lightning * 0.94);
    color.rgb = min(color.rgb + vec3(0.05, 0.07, 0.11) * lightning, vec3(1.0));

    vertexColor = color;
}
