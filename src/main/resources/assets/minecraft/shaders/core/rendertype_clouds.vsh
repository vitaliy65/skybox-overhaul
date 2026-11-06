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

    // Directional golden-hour tint. World +X is the sunrise side and -X is the sunset side,
    // matching the azimuth used by TwilightGlow. Only clouds in that sector catch warm light.
    float sunSide = CloudOffset.w < 0.0 ? -1.0 : 1.0;
    float twilight = clamp(CellSize.w, 0.0, 1.0);
    vec2 horizontalPosition = pos.xz;
    float horizontalDistance = length(horizontalPosition);
    vec2 sunDirection = vec2(sunSide, 0.0);
    vec2 toCloud = horizontalPosition / max(horizontalDistance, 0.001);
    float sunFacing = dot(toCloud, sunDirection);
    float directionalGradient = smoothstep(-0.34, 0.88, sunFacing);
    float distanceFade = mix(0.58, 1.0, smoothstep(12.0, 180.0, horizontalDistance));
    float warmAmount = twilight * directionalGradient * distanceFade;

    vec3 roseLight = vec3(0.96, 0.53, 0.54);
    vec3 amberLight = vec3(1.00, 0.66, 0.38);
    vec3 sunsetLight = mix(roseLight, amberLight, smoothstep(0.05, 0.92, sunFacing));
    float faceBoost = useTopColor ? 0.82 : 1.0;
    color.rgb *= mix(vec3(1.0), sunsetLight, clamp(warmAmount * faceBoost, 0.0, 0.72));

    // The non-sunward side stays cooler, rather than receiving the same orange wash.
    float coolSide = twilight * (1.0 - directionalGradient) * 0.12;
    color.rgb *= mix(vec3(1.0), vec3(0.78, 0.82, 0.96), coolSide);

    vertexColor = color;
}
