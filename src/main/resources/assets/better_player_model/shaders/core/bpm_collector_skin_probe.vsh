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

layout(location = 0) out vec4 vertexColor;

float paletteFloat(int element) {
    vec4 bytes = texelFetch(Sampler1, ivec2(element, 0), 0) * 255.0 + 0.5;
    uvec4 bits = uvec4(bytes);
    return uintBitsToFloat(bits.r | (bits.g << 8u) | (bits.b << 16u) | (bits.a << 24u));
}

mat4 boneMatrix(int boneIndex) {
    int offset = boneIndex * 16;
    return mat4(
        vec4(paletteFloat(offset), paletteFloat(offset + 1), paletteFloat(offset + 2), paletteFloat(offset + 3)),
        vec4(paletteFloat(offset + 4), paletteFloat(offset + 5), paletteFloat(offset + 6), paletteFloat(offset + 7)),
        vec4(paletteFloat(offset + 8), paletteFloat(offset + 9), paletteFloat(offset + 10), paletteFloat(offset + 11)),
        vec4(paletteFloat(offset + 12), paletteFloat(offset + 13), paletteFloat(offset + 14), paletteFloat(offset + 15))
    );
}

void main() {
    gl_Position = ProjMat * ModelViewMat * boneMatrix(UV1.x) * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
}
