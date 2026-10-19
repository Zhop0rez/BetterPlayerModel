#version 330 core
#extension GL_ARB_separate_shader_objects : require

// Diagnostic AO: exact production vertex format, no descriptors at all.
layout(location = 0) in vec3 a_position;
layout(location = 1) in vec2 a_uv;
layout(location = 2) in vec3 a_normal;
layout(location = 3) in uint a_boneId;
layout(location = 4) in uvec2 a_cullable;

void main() {
    gl_Position = vec4(a_position.xy, 0.0, 1.0);
}
