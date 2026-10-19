#version 330 core
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec3 Position;
layout(location = 0) out vec2 uv;
void main() { gl_Position = vec4(Position, 1.0); uv = vec2(0.5); }
