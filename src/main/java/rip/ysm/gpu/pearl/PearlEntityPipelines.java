package rip.ysm.gpu.pearl;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/**
 * Device-agnostic ("renderpearl") entity pipelines for the bone-skinned player model.
 * Works on both the OpenGL and Vulkan backends of Minecraft 26.x, as long as the
 * pipeline definition and the shader assets (bpm_entity.vsh/.fsh) agree.
 */
public final class PearlEntityPipelines {
    private PearlEntityPipelines() {
    }

    /**
     * Path must include the "core/" prefix: FileToIdConverter("shaders", ".vsh") maps this id
     * to assets/better_player_model/shaders/core/bpm_entity.vsh (same as vanilla "core/position_color").
     */
    private static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_entity");

    /** Stride 36: pos(12) + uv(8) + normal(12) + boneId(2) + cullable as RG8 (2, second component unused). */
    public static final VertexFormat BPM_ENTITY = VertexFormat.builder(0)
            .addAttribute("a_position", GpuFormat.RGB32_FLOAT)
            .addAttribute("a_uv", GpuFormat.RG32_FLOAT)
            .addAttribute("a_normal", GpuFormat.RGB32_FLOAT)
            .addAttribute("a_boneId", GpuFormat.R16_UINT)
            .addAttribute("a_cullable", GpuFormat.RG8_UINT)
            .build();

    /*
     * A single private scene block avoids the frontend's fragile interaction between
     * several mod-owned UBO descriptors in an off-screen Vulkan render pass.  It also
     * deliberately avoids vanilla's reserved Projection/DynamicTransforms names.
     */
    private static final BindGroupLayout BPM_SCENE = BindGroupLayout.builder()
            .withUniform("BpmScene", UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline ENTITY_OPAQUE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/entity_opaque"))
            .withVertexShader(SHADER_ID)
            .withFragmentShader(SHADER_ID)
            .withVertexBinding(0, BPM_ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderPipeline ENTITY_TRANSLUCENT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/entity_translucent"))
            .withVertexShader(SHADER_ID)
            .withFragmentShader(SHADER_ID)
            .withVertexBinding(0, BPM_ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    /**
     * Diagnostic-only pipeline with neither vertex buffers nor descriptors. The shaders
     * construct a triangle from gl_VertexID and write a constant yellow fragment, making
     * it the smallest possible probe of an off-screen render pass on every backend.
     */
    public static final RenderPipeline DIRECT_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/direct_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_direct_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_direct_sanity"))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    /** Direct control plus one mapped vertex buffer; intentionally has no descriptors or index buffer. */
    public static final VertexFormat DIRECT_POSITION = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .build();

    public static final RenderPipeline VERTEX_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/vertex_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_vertex_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_vertex_sanity"))
            .withVertexBinding(0, DIRECT_POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    private static final BindGroupLayout PROBE_MATRIX = BindGroupLayout.builder()
            .withUniform("ProbeMatrix", UniformType.UNIFORM_BUFFER)
            .build();

    /** Two custom names that match the production BPM matrix block names. */
    private static final BindGroupLayout PROBE_BPM_PROJECTION = BindGroupLayout.builder()
            .withUniform("BpmProjection", UniformType.UNIFORM_BUFFER)
            .build();

    private static final BindGroupLayout PROBE_BPM_TRANSFORMS = BindGroupLayout.builder()
            .withUniform("BpmTransforms", UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline INDEX_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/index_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_index_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_index_sanity"))
            .withVertexBinding(0, DIRECT_POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderPipeline UBO_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/ubo_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_ubo_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_ubo_sanity"))
            .withBindGroupLayout(PROBE_MATRIX)
            .withVertexBinding(0, DIRECT_POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    /**
     * Two private UBOs with the exact production names, stripped to matrix multiplication.
     */
    public static final RenderPipeline TWO_UBO_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/two_ubo_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_two_ubo_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_two_ubo_sanity"))
            .withBindGroupLayout(PROBE_BPM_PROJECTION)
            .withBindGroupLayout(PROBE_BPM_TRANSFORMS)
            .withVertexBinding(0, DIRECT_POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderPipeline SAMPLER_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/sampler_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_sampler_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_sampler_sanity"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withVertexBinding(0, DIRECT_POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    /** Tests the exact vanilla Projection and DynamicTransforms bind-group layouts. */
    public static final RenderPipeline MATRICES_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/matrices_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_matrices_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_matrices_sanity"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexBinding(0, DIRECT_POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    /** Same bind groups as MATRICES_SANITY, fed identity matrices as a layout control. */
    public static final RenderPipeline MATRICES_IDENTITY_SANITY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/matrices_identity_sanity"))
            .withVertexShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_matrices_identity_sanity"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("better_player_model", "core/bpm_matrices_identity_sanity"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexBinding(0, DIRECT_POSITION)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    /**
     * Resolves both pipelines. The first call may block while shaders compile;
     * later calls are cache hits. Returns false if compilation failed.
     */
    public static boolean ensureCompiled() {
        try {
            CompiledRenderPipeline opaque = RenderSystem.getCompiledPipelineNullable(ENTITY_OPAQUE);
            CompiledRenderPipeline translucent = RenderSystem.getCompiledPipelineNullable(ENTITY_TRANSLUCENT);
            CompiledRenderPipeline direct = RenderSystem.getCompiledPipelineNullable(DIRECT_SANITY);
            CompiledRenderPipeline vertex = RenderSystem.getCompiledPipelineNullable(VERTEX_SANITY);
            CompiledRenderPipeline index = RenderSystem.getCompiledPipelineNullable(INDEX_SANITY);
            CompiledRenderPipeline ubo = RenderSystem.getCompiledPipelineNullable(UBO_SANITY);
            CompiledRenderPipeline twoUbo = RenderSystem.getCompiledPipelineNullable(TWO_UBO_SANITY);
            CompiledRenderPipeline sampler = RenderSystem.getCompiledPipelineNullable(SAMPLER_SANITY);
            CompiledRenderPipeline matrices = RenderSystem.getCompiledPipelineNullable(MATRICES_SANITY);
            CompiledRenderPipeline matricesIdentity = RenderSystem.getCompiledPipelineNullable(MATRICES_IDENTITY_SANITY);
            return opaque != null && translucent != null && direct != null && vertex != null
                    && index != null && ubo != null && twoUbo != null && sampler != null && matrices != null && matricesIdentity != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
