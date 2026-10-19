#version 330 core
#extension GL_ARB_separate_shader_objects : require

void main() {
    const vec2 positions[3] = vec2[3](
        vec2(-0.80, -0.80),
        vec2( 0.80, -0.80),
        vec2( 0.00,  0.80)
    );
    // RenderPearl's Vulkan GLSL environment exposes the Vulkan built-in spelling.
    gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
}
