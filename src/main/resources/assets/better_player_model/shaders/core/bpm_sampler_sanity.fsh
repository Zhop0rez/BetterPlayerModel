#version 330 core
#extension GL_ARB_separate_shader_objects : require
uniform sampler2D Sampler0;
layout(location = 0) in vec2 uv;
layout(location = 0) out vec4 fragColor;
void main() { fragColor = texture(Sampler0, uv) * vec4(1.0, 0.2, 0.6, 1.0); }
