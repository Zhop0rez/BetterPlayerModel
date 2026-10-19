package rip.ysm.gpu.pearl;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.mixin.client.RenderTypeInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * A one-shot main-frame proof for the Sodium-safe route.  Unlike PearlGpuPath this
 * never creates a separate CommandEncoder or off-screen attachment: it is prepared
 * and drawn by Minecraft's regular LevelRenderer frame graph.
 */
public final class PearlCollectorProbe {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(
            "better_player_model", "core/bpm_collector_probe");

    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .build();

    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/collector_probe"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withVertexBinding(0, FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    private static final RenderType TYPE = RenderTypeInvoker.bpm$create(
            "better_player_model:collector_probe",
            RenderSetup.builder(PIPELINE).createRenderSetup());

    private static boolean submitted;
    private static boolean emitted;

    private PearlCollectorProbe() {
    }

    public static void submitOnce(SubmitNodeCollector collector, PoseStack poseStack) {
        if (!Boolean.getBoolean("bpm.pearlDebug") || submitted) {
            return;
        }
        submitted = true;
        try {
            // Compile now, while still on the render thread, so a missing shader or an
            // incompatible descriptor contract is reported at the submission site.
            var compiled = com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(PIPELINE);
            YesSteveModel.LOGGER.info("[BPM] Collector probe pipeline compiled: {}", compiled.getClass().getName());
            collector.submitCustomGeometry(poseStack, TYPE, (pose, vertices) -> {
                // A small white triangle above the model origin.  Transforming it through
                // the supplied pose matches the normal custom-geometry contract.
                vertices.addVertex(pose.pose(), -0.25f, 2.25f, 0.0f).setColor(255, 255, 255, 255);
                vertices.addVertex(pose.pose(),  0.25f, 2.25f, 0.0f).setColor(255, 255, 255, 255);
                vertices.addVertex(pose.pose(),  0.00f, 2.75f, 0.0f).setColor(255, 255, 255, 255);
                if (!emitted) {
                    emitted = true;
                    YesSteveModel.LOGGER.info("[BPM] Collector probe emitted 3 vertices into Minecraft's staged buffer");
                }
            });
            YesSteveModel.LOGGER.info("[BPM] Collector probe submitted through the main LevelRenderer frame graph");
        } catch (Throwable error) {
            YesSteveModel.LOGGER.warn("[BPM] Collector probe submission failed", error);
        }
    }
}
