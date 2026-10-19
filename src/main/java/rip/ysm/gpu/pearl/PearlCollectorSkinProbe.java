package rip.ysm.gpu.pearl;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.mixin.client.RenderTypeInvoker;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * A one-shot proof of the descriptor contract used by the real skinning path.
 *
 * <p>The important part is not the triangle: it is a normal entity-format draw
 * prepared by Minecraft's frame graph, with a bone matrix stored in {@code Sampler1}.
 * That avoids the second mod-owned UBO which Sodium rejects in a separate pass.</p>
 */
public final class PearlCollectorSkinProbe {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(
            "better_player_model", "core/bpm_collector_skin_probe");
    private static final Identifier PALETTE_TEXTURE = Identifier.fromNamespaceAndPath(
            "better_player_model", "bpm_collector_skin_probe_palette");

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/collector_skin_probe"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    private static final RenderType TYPE = RenderTypeInvoker.bpm$create(
            "better_player_model:collector_skin_probe",
            RenderSetup.builder(PIPELINE).withTexture("Sampler1", PALETTE_TEXTURE).createRenderSetup());

    private static DynamicTexture palette;
    private static boolean submitted;
    private static boolean emitted;

    private PearlCollectorSkinProbe() {
    }

    public static void submitOnce(SubmitNodeCollector collector, PoseStack poseStack) {
        if (!Boolean.getBoolean("bpm.pearlDebug") || submitted) {
            return;
        }

        submitted = true;
        try {
            ensureIdentityPalette();
            var compiled = com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(PIPELINE);
            YesSteveModel.LOGGER.info("[BPM] Collector skin probe pipeline compiled: {}", compiled.getClass().getName());
            collector.submitCustomGeometry(poseStack, TYPE, (pose, vertices) -> emitTriangle(vertices, pose));
            YesSteveModel.LOGGER.info("[BPM] Collector skin probe submitted: ENTITY vertices + Sampler1 bone palette");
        } catch (Throwable error) {
            YesSteveModel.LOGGER.warn("[BPM] Collector skin probe submission failed", error);
        }
    }

    private static void ensureIdentityPalette() {
        if (palette != null) {
            return;
        }

        palette = new DynamicTexture(() -> "BPM collector skin probe palette", 16, 1, false);
        NativeImage pixels = palette.getPixels();
        // mat4 identity in GLSL/JOML column-major order.  Each float is copied as its
        // IEEE-754 bits into RGBA8, then uintBitsToFloat reconstructs it in the shader.
        for (int i = 0; i < 16; i++) {
            float value = i % 5 == 0 ? 1.0f : 0.0f;
            pixels.setPixelABGR(i, 0, Float.floatToRawIntBits(value));
        }
        palette.upload();
        Minecraft.getInstance().getTextureManager().register(PALETTE_TEXTURE, palette);
        YesSteveModel.LOGGER.info("[BPM] Collector skin probe uploaded identity bone palette as Sampler1");
    }

    private static void emitTriangle(VertexConsumer vertices, PoseStack.Pose pose) {
        addVertex(vertices, pose, -0.35f, 2.15f, 0.0f);
        addVertex(vertices, pose,  0.35f, 2.15f, 0.0f);
        addVertex(vertices, pose,  0.00f, 2.85f, 0.0f);
        if (!emitted) {
            emitted = true;
            YesSteveModel.LOGGER.info("[BPM] Collector skin probe emitted 3 ENTITY vertices with bone index 0");
        }
    }

    private static void addVertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(64, 255, 128, 255)
                .setUv(0.0f, 0.0f)
                .setUv1(0, 0)
                .setUv2(0, 0)
                .setNormal(0.0f, 0.0f, 1.0f);
    }
}
