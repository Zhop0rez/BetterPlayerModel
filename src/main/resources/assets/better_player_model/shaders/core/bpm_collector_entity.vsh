#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler1;

layout(std140) uniform Lighting {
    vec3 Light0_Direction;
    vec3 Light1_Direction;
};

uniform sampler2D Sampler2;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 texCoord0;
layout(location = 2) out vec4 lightMapColor;

const int PALETTE_WIDTH = 1024;
const int PALETTE_STRIDE = 33;

float paletteFloat(int element) {
    ivec2 coordinate = ivec2(element & (PALETTE_WIDTH - 1), element / PALETTE_WIDTH);
    vec4 bytes = texelFetch(Sampler1, coordinate, 0) * 255.0 + 0.5;
    uvec4 bits = uvec4(bytes);
    return uintBitsToFloat(bits.r | (bits.g << 8u) | (bits.b << 16u) | (bits.a << 24u));
}

mat4 boneMatrix(int boneIndex) {
    int offset = boneIndex * PALETTE_STRIDE;
    return mat4(
        vec4(paletteFloat(offset), paletteFloat(offset + 1), paletteFloat(offset + 2), 0.0),
        vec4(paletteFloat(offset + 4), paletteFloat(offset + 5), paletteFloat(offset + 6), paletteFloat(offset + 7)),
        vec4(paletteFloat(offset + 8), paletteFloat(offset + 9), paletteFloat(offset + 10), paletteFloat(offset + 11)),
        vec4(paletteFloat(offset + 12), paletteFloat(offset + 13), paletteFloat(offset + 14), paletteFloat(offset + 15))
    );
}

mat4 normalMatrix(int boneIndex) {
    int offset = boneIndex * PALETTE_STRIDE + 16;
    // Only the upper-left 3x3 is consumed by mat3() below. Avoid seven
    // redundant palette texelFetch calls per vertex while preserving the
    // exact normal matrix values used for lighting.
    return mat4(
        vec4(paletteFloat(offset), paletteFloat(offset + 1), paletteFloat(offset + 2), paletteFloat(offset + 3)),
        vec4(paletteFloat(offset + 4), paletteFloat(offset + 5), paletteFloat(offset + 6), 0.0),
        vec4(paletteFloat(offset + 8), paletteFloat(offset + 9), paletteFloat(offset + 10), 0.0),
        vec4(0.0, 0.0, 0.0, 1.0)
    );
}

vec4 sampleLightmap(ivec2 uv) {
    return texture(Sampler2, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}

void main() {
    int boneIndex = UV1.x;
    if (paletteFloat(boneIndex * PALETTE_STRIDE + 32) < 0.5) {
        // Hidden bones must not be rendered with their identity fallback matrix.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    } else {
        gl_Position = ProjMat * ModelViewMat * boneMatrix(boneIndex) * vec4(Position, 1.0);
    }
    vec3 transformedNormal = normalize(mat3(normalMatrix(boneIndex)) * Normal);
    vec2 light = max(vec2(0.0), vec2(dot(Light0_Direction, transformedNormal), dot(Light1_Direction, transformedNormal)));
    float directionalLight = min(1.0, (light.x + light.y) * 0.6 + 0.4);
    vertexColor = vec4(Color.rgb * directionalLight, Color.a) * ColorModulator;
    texCoord0 = UV0;
    lightMapColor = sampleLightmap(UV2);
}
