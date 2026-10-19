#version 330 core
#extension GL_ARB_separate_shader_objects : require
layout(location = 0) in vec3 Position;
layout(std140) uniform ProbeMatrix { mat4 probe; };
void main() { gl_Position = probe * vec4(Position, 1.0); }
