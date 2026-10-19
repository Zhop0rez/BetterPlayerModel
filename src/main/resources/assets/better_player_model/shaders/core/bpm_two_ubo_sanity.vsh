#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec3 Position;
layout(std140) uniform BpmProjection {
    mat4 probeProjection;
};

layout(std140) uniform BpmTransforms {
    mat4 probeModelView;
    mat4 probeTexture;
    vec4 probeColor;
    vec3 probeOffset;
    float probePadding;
};

void main() {
    gl_Position = probeProjection * probeModelView * vec4(Position, 1.0);
}
