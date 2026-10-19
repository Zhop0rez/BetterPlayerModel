package rip.ysm.gpu.pearl;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlRenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryStack;
import rip.ysm.gpu.GpuCompat;
import rip.ysm.gpu.GpuRenderPath;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * M1 of the renderpearl render path: a scene-independent debug harness.
 * Renders the model into private color/depth textures and dumps the result as a PNG.
 * Activated with -Dbpm.pearlDebug=true; only the first eligible draw is rendered.
 * Runs on every backend (GL and Vulkan), because the renderpearl API is device-agnostic.
 * GL-only diagnostics (getState/probes/scanGlError) are skipped under the Vulkan
 * backend: there is no GL context there, and a GL call aborts the JVM natively.
 */
public final class PearlGpuPath {
    private PearlGpuPath() {
    }

    private static final int DEBUG_TEXTURE_SIZE = 320;
    /** Bisect build (1.1.4m): model draws disabled, so the readback has exactly two shapes. */
    private static final boolean BIS_ONLY = true;
    private static final int UNIFORM_ALIGNMENT = 256;
    /**
     * Diagnostic only: one bone makes the combined block 416 bytes.  The sanity
     * triangle uses bone zero exclusively, so this isolates the UBO-size limit from
     * descriptor/shader binding failures before the production bone transport changes.
     */
    private static final int BPM_SCENE_SIZE = 272 + PearlGpuMesh.BONE_STRIDE;

    private static boolean scheduled;
    private static boolean compileFailureLogged;
    private static boolean unsupportedLogged;

    public static boolean debugEnabled() {
        return Boolean.getBoolean("bpm.pearlDebug");
    }

    /**
     * Schedules the debug render for the start of the next frame (mc.execute), so no
     * vanilla render pass is active while we create our own. Only the first call wins.
     */
    public static void scheduleDebugRender(
            GeoModel model,
            PoseStack.Pose pose,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            int packedOverlay,
            float r, float g, float b, float a,
            Identifier textureLocation,
            boolean translucentTexture
    ) {
        if (scheduled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        scheduled = true;
        YesSteveModel.LOGGER.info("[BPM] Pearl debug: scheduled (bones={})",
                model.bakedBones != null ? model.bakedBones.size() : -1);

        // Deterministic debug camera: model centered in front of the origin.
        Matrix4f root = new Matrix4f();
        root.translation(0.0f, -0.9f, -2.6f);
        root.rotateY((float) Math.toRadians(20.0));
        Matrix3f rootNormal = new Matrix3f();

        float[] boneParamsCopy = boneParams != null ? boneParams.clone() : new float[0];
        float[] stateBufferCopy = stateBuffer != null ? stateBuffer.clone() : null;

        mc.execute(() -> renderDebug(model, root, rootNormal, boneParamsCopy, stateBufferCopy,
                packedLight, packedOverlay, r, g, b, a, textureLocation, translucentTexture));
    }

    private static void renderDebug(
            GeoModel model,
            Matrix4f rootUnused,
            Matrix3f rootNormalUnused,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            int packedOverlay,
            float r, float g, float b, float a,
            Identifier textureLocation,
            boolean translucentTexture
    ) {
        RenderSystem.assertOnRenderThread();
        try {
            if (!PearlEntityPipelines.ensureCompiled()) {
                if (!compileFailureLogged) {
                    compileFailureLogged = true;
                    YesSteveModel.LOGGER.warn("[BPM] Pearl debug: pipeline compile unavailable (shader assets missing?)");
                }
                return;
            }

            GpuDevice device = RenderSystem.getDevice();
            CommandEncoder encoder = device.createCommandEncoder();
            dumpCompiledPipelineInfo("pre-render", PearlEntityPipelines.ENTITY_OPAQUE, "bpm_entity");
            dumpCompiledPipelineInfo("pre-render", RenderPipelines.GUI_TEXTURED, "vanilla position_tex_color");

            PearlGpuMesh mesh = PearlGpuMesh.build(model);
            if (mesh == null) {
                if (!unsupportedLogged) {
                    unsupportedLogged = true;
                    YesSteveModel.LOGGER.warn("[BPM] Pearl debug: mesh unsupported (no quads or >BPM_MAX_BONES bones)");
                }
                return;
            }
            YesSteveModel.LOGGER.info("[BPM] Pearl debug: rendering (bones={}, indices={})", mesh.boneCount, mesh.indexCount);
            if (mesh.debugBounds != null) {
                float[] bb = mesh.debugBounds;
                YesSteveModel.LOGGER.info("[BPM] Pearl debug: raw vert bbox min=({}, {}, {}) max=({}, {}, {})",
                        f(bb[0]), f(bb[1]), f(bb[2]), f(bb[3]), f(bb[4]), f(bb[5]));
            }

            // Dry bone pass with an identity root to (a) count hidden bones and
            // (b) measure the bone-space (eye-space) bounds for a camera auto-fit.
            Matrix4f root;
            Matrix3f rootNormal;
            {
                ByteBuffer boneScratch = ByteBuffer.allocateDirect(mesh.boneCount * PearlGpuMesh.BONE_STRIDE)
                        .order(ByteOrder.nativeOrder());
                int hiddenBones = -1;
                float[] eyeBounds = null;
                try {
                    YesSteveModel.LOGGER.info("[BPM] Pearl debug: dry pass step 1 (computeBoneMatrices)");
                    if (GpuRenderPath.computeBoneMatrices(model, new Matrix4f(), new Matrix3f(),
                            boneParams, stateBuffer, packedLight, boneScratch)) {
                        YesSteveModel.LOGGER.info("[BPM] Pearl debug: dry pass step 2 (countHiddenBones)");
                        hiddenBones = countHiddenBones(boneScratch, mesh.boneCount);
                        YesSteveModel.LOGGER.info("[BPM] Pearl debug: dry pass step 3 (eyeBoneSpaceBounds)");
                        eyeBounds = eyeBoneSpaceBounds(model, boneScratch);
                        YesSteveModel.LOGGER.info("[BPM] Pearl debug: dry pass step 4 (done)");
                    } else {
                        YesSteveModel.LOGGER.warn("[BPM] Pearl debug: dry pass: computeBoneMatrices returned false");
                    }
                } catch (Throwable t) {
                    YesSteveModel.LOGGER.warn("[BPM] Pearl debug: dry bone pass failed", t);
                }
                YesSteveModel.LOGGER.info("[BPM] Pearl debug: hidden bones {}/{}", hiddenBones, mesh.boneCount);
                if (eyeBounds != null) {
                    YesSteveModel.LOGGER.info("[BPM] Pearl debug: eye bbox min=({}, {}, {}) max=({}, {}, {})",
                            f(eyeBounds[0]), f(eyeBounds[1]), f(eyeBounds[2]), f(eyeBounds[3]), f(eyeBounds[4]), f(eyeBounds[5]));
                }
                boolean fit = Boolean.parseBoolean(System.getProperty("bpm.pearlFit", "true"));
                float fsc = (fit && eyeBounds != null) ? fitScale(eyeBounds) : 0.0f;
                if (fit && eyeBounds != null && Float.isFinite(fsc) && fsc > 0.0f) {
                    float cx = (eyeBounds[0] + eyeBounds[3]) * 0.5f;
                    float cy = (eyeBounds[1] + eyeBounds[4]) * 0.5f;
                    float cz = (eyeBounds[2] + eyeBounds[5]) * 0.5f;
                    root = new Matrix4f().translation(0.0f, -0.9f, -2.6f);
                    root.rotateY((float) Math.toRadians(20.0));
                    root.scale(fsc);
                    root.translate(-cx, -cy, -cz);
                    rootNormal = new Matrix3f().rotateY((float) Math.toRadians(20.0)).scale(fsc);
                    YesSteveModel.LOGGER.info("[BPM] Pearl debug: camera auto-fit scale={}", f(fsc));
                } else {
                    root = new Matrix4f().translation(0.0f, -0.9f, -2.6f);
                    root.rotateY((float) Math.toRadians(20.0));
                    rootNormal = new Matrix3f();
                    YesSteveModel.LOGGER.info("[BPM] Pearl debug: camera auto-fit skipped, using fixed camera");
                }
            }

            if (!computeAndUploadBones(mesh, model, root, rootNormal, boneParams, stateBuffer, packedLight)) {
                mesh.close();
                return;
            }

            int size = DEBUG_TEXTURE_SIZE;
            GpuTexture color = device.createTexture(() -> "BPM pearl debug color",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, size, size, 1, 1);
            GpuTextureView colorView = device.createTextureView(color);
            GpuTexture depth = device.createTexture(() -> "BPM pearl debug depth",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.D32_FLOAT, size, size, 1, 1);
            GpuTextureView depthView = device.createTextureView(depth);

            GpuTextureView modelTextureView = resolveModelTextureView(textureLocation);
            GpuSampler modelSampler = resolveModelSampler(textureLocation);
            GpuTextureView overlayView = mcOverlayView();
            GpuTextureView lightmapView = mcLightmapView();
            if (modelTextureView == null || modelSampler == null || overlayView == null || lightmapView == null) {
                YesSteveModel.LOGGER.warn("[BPM] Pearl debug: textures not available (model={}, overlay={}, lightmap={})",
                        textureLocation, overlayView != null, lightmapView != null);
                closeAll(mesh, color, depth, null, null, null);
                return;
            }
            GpuSampler clampSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

            GpuBuffer projectionBuffer = device.createBuffer(() -> "BPM pearl projection",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_ALIGNMENT);
            GpuBuffer dynamicTransformsBuffer = device.createBuffer(() -> "BPM pearl dynamic transforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_ALIGNMENT);
            GpuBuffer drawUniformsBuffer = device.createBuffer(() -> "BPM pearl draw uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_ALIGNMENT * 2);
            fillProjection(projectionBuffer);
            fillDynamicTransforms(dynamicTransformsBuffer, r, g, b, a);
            fillDrawUniforms(drawUniformsBuffer, packedOverlay);

            Sanity sanity = createSanity(device, encoder);
            GpuBuffer vertexOnly = createVertexOnlySanity(device);
            GpuBuffer indexOnly = createProbeTriangle(device, "IBO-only", 0.20f, -0.75f, 0.75f, -0.75f, 0.48f, -0.20f);
            GpuBuffer uboOnly = createProbeTriangle(device, "UBO-only", -0.75f, 0.20f, -0.20f, 0.20f, -0.48f, 0.75f);
            GpuBuffer twoUboOnly = createProbeTriangle(device, "two-UBO-only", -0.1f, -0.75f, 0.45f, -0.75f, 0.18f, -0.2f);
            GpuBuffer samplerOnly = createProbeTriangle(device, "sampler-only", 0.20f, 0.20f, 0.75f, 0.20f, 0.48f, 0.75f);
            GpuBuffer matricesOnly = createProbeTriangle(device, "matrices-only", -0.9f, 0.35f, -0.35f, 0.35f, -0.62f, 0.85f, -2.0f);
            GpuBuffer matricesIdentityOnly = createProbeTriangle(device, "matrices-identity-only", -0.9f, -0.2f, -0.35f, -0.2f, -0.62f, 0.25f);
            GpuBuffer probeMatrix = createIdentityProbeMatrix(device);

            // Depth reversed-Z: DepthStencilState.DEFAULT is GREATER_THAN_OR_EQUAL and the
            // far plane is 0.0, so the clear value MUST be 0.0 (clearing 1.0 makes every
            // fragment fail the depth test and the output stays as the clear color).
            // Clear color is opaque magenta, so "nothing drawn" is unambiguous.
            scanGlError("after buffer staging");
            DebugGlSnapshot snapshot = null;
            try (RenderPass pass = encoder.createRenderPass(() -> "BPM pearl debug", colorView,
                    Optional.of(new Vector4f(1.0f, 0.0f, 1.0f, 1.0f)), depthView, OptionalDouble.of(0.0))) {
                YesSteveModel.LOGGER.info("[BPM] Pearl debug: render pass class = {}", pass.getClass().getName());
                // 0) Zero-dependency control: no vertex/index buffer, texture, sampler or
                // UBO. The vertex shader derives positions solely from gl_VertexID and the
                // fragment shader emits solid yellow. If this is absent, the failure is
                // below the BPM pipeline and below all resource binding paths.
                pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.DIRECT_SANITY));
                pass.draw(3, 1, 0, 0);
                // 0b) VBO-only control: same no-descriptor setup but positions are read
                // from a host-mapped vertex buffer. It distinguishes vertex-buffer wiring
                // from the later index-buffer and uniform/sampler paths.
                pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.VERTEX_SANITY));
                pass.setVertexBuffer(0, vertexOnly.slice());
                pass.draw(3, 1, 0, 0);
                if (sanity != null) {
                    // 0c) IBO-only control (blue): VBO + mapped indices, no descriptors.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.INDEX_SANITY));
                    pass.setVertexBuffer(0, indexOnly.slice());
                    pass.setIndexBuffer(sanity.idx(), IndexType.INT);
                    pass.drawIndexed(3, 1, 0, 0, 0);

                    // 0d) UBO-only control (orange): VBO plus a mapped identity matrix.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.UBO_SANITY));
                    pass.setUniform("ProbeMatrix", probeMatrix.slice());
                    pass.setVertexBuffer(0, uboOnly.slice());
                    pass.draw(3, 1, 0, 0);

                    // 0d2) Private BPM matrix blocks (violet), matching production names.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.TWO_UBO_SANITY));
                    pass.setUniform("BpmProjection", probeMatrix.slice());
                    pass.setUniform("BpmTransforms", sanity.dyn().slice());
                    pass.setVertexBuffer(0, twoUboOnly.slice());
                    pass.draw(3, 1, 0, 0);

                    // 0e) sampler-only control (pink): VBO plus the same private white
                    // texture used by the old resource-backed reference leg.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.SAMPLER_SANITY));
                    pass.setUniform("Sampler0", sanity.whiteView(), clampSampler);
                    pass.setVertexBuffer(0, samplerOnly.slice());
                    pass.draw(3, 1, 0, 0);

                    // Exact vanilla Projection/DynamicTransforms, no sampler, IBO or BPM data.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.MATRICES_SANITY));
                    pass.setUniform("Projection", sanity.proj().slice());
                    pass.setUniform("DynamicTransforms", sanity.dyn().slice());
                    pass.setVertexBuffer(0, matricesOnly.slice());
                    pass.draw(3, 1, 0, 0);

                    // Same layouts, but both matrices are identity and positions are already
                    // in clip space. This tells a bad descriptor/layout path from bad
                    // perspective contents without involving any BPM resource.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.MATRICES_IDENTITY_SANITY));
                    pass.setUniform("Projection", probeMatrix.slice());
                    pass.setUniform("DynamicTransforms", sanity.dyn().slice());
                    pass.setVertexBuffer(0, matricesIdentityOnly.slice());
                    pass.draw(3, 1, 0, 0);
                }
                if (sanity != null) {
                    // 1) Reference draw: the SAME white texture and the SAME identity
                    //    Projection/DynamicTransforms buffers as the sanity draw, but via
                    //    the VANILLA pipeline RenderPipelines.GUI_TEXTURED
                    //    ("core/position_tex_color", vertex format POSITION_TEX_COLOR,
                    //    blend TRANSLUCENT, NO depth test), in the upper-right quadrant.
                    //    Vanilla draws through this pipeline every GUI frame on every
                    //    backend, so if it appears in our off-screen pass at all, the pass
                    //    machinery is proven good and the fault lives in OUR pipeline def.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.GUI_TEXTURED));
                    pass.setUniform("Projection", sanity.proj().slice());
                    pass.setUniform("DynamicTransforms", sanity.dyn().slice());
                    pass.setUniform("Sampler0", sanity.whiteView(), clampSampler);
                    pass.setVertexBuffer(0, sanity.vanVerts().slice());
                    pass.setIndexBuffer(sanity.idx(), IndexType.INT);
                    // Debug-only: neutralize every state that can silently suppress fragments
                    // (scissor, cull, depth func) and force full color/depth write; the exact
                    // original values are restored after the draws, so the rest of the game
                    // frame keeps its GL state intact. GL-only: skipped on Vulkan.
                    snapshot = GpuCompat.isGl() ? dumpDebugGlState("before reference + sanity draws") : null;
                    if (snapshot != null) {
                        applyDebugVisibilityOverrides();
                    }
                    if (GpuCompat.isGl()) {
                        // GL-only: the driver itself counts exact sample coverage of this
                        // single draw, independent of every readback path below.
                        glCountDrawSamples("vanilla reference quad", () -> pass.drawIndexed(4, 1, 0, 0, 0));
                    } else {
                        pass.drawIndexed(4, 1, 0, 0, 0);
                    }
                    scanGlError("after vanilla reference draw");
                    if (GpuCompat.isGl()) {
                        glValidateProgramProbe("after vanilla reference draw",
                                RenderSystem.getCompiledPipeline(RenderPipelines.GUI_TEXTURED));
                    }

                    // 2) Our own sanity triangle (bone 0 = identity, cullable = 0) through
                    //    OUR bpm_entity pipeline, in the lower-left quadrant. All three
                    //    samplers point to the private opaque white texture and fog is
                    //    disabled, so the draw depends on nothing but the pass machinery.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(PearlEntityPipelines.ENTITY_OPAQUE));
                    pass.setVertexBuffer(0, sanity.verts().slice());
                    pass.setIndexBuffer(sanity.idx(), IndexType.INT);
                    if (GpuCompat.isGl()) {
                        glCountDrawSamples("sanity triangle", () -> pass.drawIndexed(3, 1, 0, 0, 0));
                    } else {
                        pass.drawIndexed(3, 1, 0, 0, 0);
                    }
                    scanGlError("after sanity draw");
                    probeDebugFrame("after reference + sanity draws");
                }

                // Bisect build: the real model draws are disabled, so the readback holds
                // exactly two shapes (upper-right = vanilla reference, lower-left = ours).
                if (!BIS_ONLY) {
                    RenderPipeline opaquePipeline = PearlEntityPipelines.ENTITY_OPAQUE;
                    RenderPipeline translucentPipeline = PearlEntityPipelines.ENTITY_TRANSLUCENT;

                    // 3) Real model, opaque pass.
                    pass.setUniform("BpmProjection", projectionBuffer.slice());
                    pass.setUniform("BpmTransforms", dynamicTransformsBuffer.slice());
                    pass.setUniform("BpmBones", mesh.bonesSlice());
                    pass.setUniform("Sampler0", modelTextureView, modelSampler);
                    pass.setUniform("Sampler1", overlayView, clampSampler);
                    pass.setUniform("Sampler2", lightmapView, clampSampler);

                    pass.setPipeline(RenderSystem.getCompiledPipeline(opaquePipeline));
                    pass.setUniform("BpmDraw", drawUniformsBuffer.slice(0L, UNIFORM_ALIGNMENT));
                    pass.setVertexBuffer(0, mesh.vertexSlice());
                    pass.setIndexBuffer(mesh.indexBuffer, IndexType.INT);
                    pass.drawIndexed(mesh.indexCount, 1, 0, 0, 0);

                    // 4) Real model, translucent pass.
                    pass.setPipeline(RenderSystem.getCompiledPipeline(translucentPipeline));
                    pass.setUniform("BpmDraw", drawUniformsBuffer.slice(UNIFORM_ALIGNMENT, UNIFORM_ALIGNMENT));
                    pass.drawIndexed(mesh.indexCount, 1, 0, 0, 0);
                    scanGlError("after model draws");
                    probeDebugFrame("after model draws");
                    if (GpuCompat.isGl()) {
                        glValidateProgramProbe("after model draws", RenderSystem.getCompiledPipeline(opaquePipeline));
                    }
                }
                if (snapshot != null) {
                    restoreDebugVisibility(snapshot);
                }
            }
            scanGlError("after pass");

            // Readback + PNG. The callback runs when the copy has been executed; the
            // mesh stays alive until then, since the draw precedes the copy on the GPU.
            GpuBuffer readback = device.createBuffer(() -> "BPM pearl debug readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) size * size * 4L);
            encoder.copyTextureToBuffer(color, readback, 0L, () -> {
                try {
                    try (GpuBufferSlice.MappedView view = readback.map(true, false)) {
                        writeDebugPng(view.data(), size);
                    }
                } catch (Throwable t) {
                    YesSteveModel.LOGGER.warn("[BPM] Pearl debug readback failed", t);
                } finally {
                    closeSanity(sanity);
                    closeQuietly(vertexOnly);
                    closeQuietly(indexOnly);
                    closeQuietly(uboOnly);
                    closeQuietly(twoUboOnly);
                    closeQuietly(samplerOnly);
                    closeQuietly(matricesOnly);
                    closeQuietly(matricesIdentityOnly);
                    closeQuietly(probeMatrix);
                    closeAll(mesh, color, null, projectionBuffer, dynamicTransformsBuffer, drawUniformsBuffer);
                    readback.close();
                }
            }, 0);

            // Depth verdict: the depth buffer is cleared to 0.0 (reversed-Z), so any
            // fragment that passed the depth test leaves a value > 0. This is
            // backend-agnostic (same copy path as the color readback) and answers
            // "did anything ever rasterize AND pass depth" even without GL probes.
            GpuBuffer depthReadback = device.createBuffer(() -> "BPM pearl debug depth readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) size * size * 4L);
            encoder.copyTextureToBuffer(depth, depthReadback, 0L, () -> {
                try {
                    try (GpuBufferSlice.MappedView view = depthReadback.map(true, false)) {
                        ByteBuffer depthPixels = view.data();
                        int total = size * size;
                        int nonZero = 0;
                        float maxDepth = Float.NEGATIVE_INFINITY;
                        int firstNonZero = -1;
                        for (int i = 0; i < total; i++) {
                            float d = depthPixels.getFloat(i * 4);
                            if (d != 0.0f) {
                                nonZero++;
                                if (d > maxDepth) {
                                    maxDepth = d;
                                }
                                if (firstNonZero < 0) {
                                    firstNonZero = i;
                                }
                            }
                        }
                        if (firstNonZero < 0) {
                            YesSteveModel.LOGGER.info("[BPM] Pearl debug depth verdict: depth buffer untouched (0 of {} pixels), zero fragments passed depth", total);
                        } else {
                            YesSteveModel.LOGGER.info("[BPM] Pearl debug depth verdict: {} / {} pixels touched (max {}, first index {})",
                                    nonZero, total, maxDepth, firstNonZero);
                        }
                    }
                } catch (Throwable t) {
                    YesSteveModel.LOGGER.warn("[BPM] Pearl debug depth readback failed", t);
                } finally {
                    depthReadback.close();
                    depthView.close();
                    depth.close();
                }
            }, 0);
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl debug render failed", t);
        }
    }

    /** Three clip-space vertices, deliberately no index buffer or shader descriptor. */
    private static GpuBuffer createVertexOnlySanity(GpuDevice device) {
        return createProbeTriangle(device, "VBO-only", -0.75f, -0.75f, -0.20f, -0.75f, -0.48f, -0.20f);
    }

    private static GpuBuffer createProbeTriangle(GpuDevice device, String label,
                                                  float ax, float ay, float bx, float by, float cx, float cy) {
        return createProbeTriangle(device, label, ax, ay, bx, by, cx, cy, 0.0f);
    }

    private static GpuBuffer createProbeTriangle(GpuDevice device, String label,
                                                  float ax, float ay, float bx, float by, float cx, float cy, float z) {
        GpuBuffer buffer = device.createBuffer(() -> "BPM pearl " + label + " sanity",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_MAP_READ, 3L * 12);
        try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
            ByteBuffer data = view.data().order(ByteOrder.nativeOrder());
            data.position(0);
            data.putFloat(ax).putFloat(ay).putFloat(z);
            data.putFloat(bx).putFloat(by).putFloat(z);
            data.putFloat(cx).putFloat(cy).putFloat(z);
        }
        verifyMappedBytes(label + ".verts", buffer, 3 * 12);
        return buffer;
    }

    private static GpuBuffer createIdentityProbeMatrix(GpuDevice device) {
        GpuBuffer buffer = device.createBuffer(() -> "BPM pearl UBO-only matrix",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_ALIGNMENT);
        try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
            new Matrix4f().get(view.data().position(0));
        }
        return buffer;
    }

    private static void closeQuietly(GpuBuffer buffer) {
        try {
            buffer.close();
        } catch (Throwable ignored) {
        }
    }

    private static boolean computeAndUploadBones(PearlGpuMesh mesh, GeoModel model, Matrix4f root, Matrix3f rootNormal,
                                                 float[] boneParams, float[] stateBuffer, int packedLight) {
        ByteBuffer boneData = ByteBuffer.allocateDirect(mesh.boneCount * PearlGpuMesh.BONE_STRIDE)
                .order(ByteOrder.nativeOrder());
        if (!GpuRenderPath.computeBoneMatrices(model, root, rootNormal, boneParams, stateBuffer, packedLight, boneData)) {
            return false;
        }
        mesh.uploadBones(boneData);
        return true;
    }

    private static String f(float v) {
        if (Float.isNaN(v)) {
            return "NaN";
        }
        if (Float.isInfinite(v)) {
            return v > 0 ? "Inf" : "-Inf";
        }
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static int countHiddenBones(ByteBuffer bones, int boneCount) {
        int hidden = 0;
        for (int i = 0; i < boneCount; i++) {
            if (bones.getInt(i * PearlGpuMesh.BONE_STRIDE + 132) != 0) { // meta.y = isHidden
                hidden++;
            }
        }
        return hidden;
    }

    /**
     * Bone-space bounds of the visible vertices, using the identity-root dry bone pass:
     * hidden bones are skipped (the shader pushes their vertices off-clip).
     * Returns null if nothing visible or the data is degenerate (would produce NaN/Inf).
     * Pure Java matrix math (no JOML buffer IO) — the buffer is column-major,
     * element {@code [j * 4 + i]} is {@code m[i][j]}.
     */
    private static float[] eyeBoneSpaceBounds(GeoModel model, ByteBuffer bones) {
        float[] e = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                     -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] mm = new float[16];
        for (int i = 0; i < model.bakedBones.size(); i++) {
            int base = i * PearlGpuMesh.BONE_STRIDE;
            if (bones.getInt(base + 132) != 0) {
                continue;
            }
            for (int j = 0; j < 16; j++) {
                mm[j] = bones.getFloat(base + j * 4);
            }
            GeoModel.BakedBone bone = model.bakedBones.get(i);
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    for (Vector3f p : quad.positions) {
                        float x = p.x(), y = p.y(), z = p.z();
                        float tx = mm[0] * x + mm[4] * y + mm[8] * z + mm[12];
                        float ty = mm[1] * x + mm[5] * y + mm[9] * z + mm[13];
                        float tz = mm[2] * x + mm[6] * y + mm[10] * z + mm[14];
                        if (!Float.isFinite(tx) || !Float.isFinite(ty) || !Float.isFinite(tz)) {
                            YesSteveModel.LOGGER.warn("[BPM] Pearl debug: non-finite eye position at bone {} of {}",
                                    i, model.bakedBones.size());
                            return null;
                        }
                        if (tx < e[0]) e[0] = tx;
                        if (ty < e[1]) e[1] = ty;
                        if (tz < e[2]) e[2] = tz;
                        if (tx > e[3]) e[3] = tx;
                        if (ty > e[4]) e[4] = ty;
                        if (tz > e[5]) e[5] = tz;
                    }
                }
            }
        }
        return e[0] > e[3] ? null : e;
    }

    private static float fitScale(float[] e) {
        float hx = Math.abs(e[3] - e[0]) * 0.5f;
        float hy = Math.abs(e[4] - e[1]) * 0.5f;
        float hz = Math.abs(e[5] - e[2]) * 0.5f;
        float max = Math.max(hx, Math.max(hy, hz));
        return max > 0.0f ? 1.25f / max : 0.0f;
    }

    /**
     * Synthetic sanity draw through the REAL projection (fillProjection): the vertices are
     * eye-space positions, NOT clip positions. They must sit at negative eye z (in front of
     * the camera: clip w = -z_eye > 0) and inside the 60-degree frustum (visible half-extent
     * at depth |z| is |z|/tan(30deg) = |z| * 1.732; 0.9-wide shapes need |z| > 1.56).
     * All three samplers point to a private fully opaque white texture, so the draw never
     * depends on Minecraft texture content, and fog is disabled, so the fragment color is
     * the white modulator itself.
     */
    private record Sanity(GpuBuffer proj, GpuBuffer dyn, GpuBuffer bones, GpuBuffer draw, GpuBuffer scene,
                          GpuBuffer verts, GpuBuffer idx, GpuTexture white, GpuTextureView whiteView,
                          GpuBuffer vanVerts) {
    }

    /** Same contract as the {@link Sanity} record doc: eye-space geometry in front of the camera. */
    private static Sanity createSanity(GpuDevice device, CommandEncoder encoder) {
        try {
            GpuBuffer proj = device.createBuffer(() -> "BPM pearl sanity projection",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_ALIGNMENT);
            GpuBuffer dyn = device.createBuffer(() -> "BPM pearl sanity dynamic transforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_ALIGNMENT);
            GpuBuffer bones = device.createBuffer(() -> "BPM pearl sanity bones",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, (long) PearlGpuMesh.BONE_BLOCK_SIZE);
            GpuBuffer draw = device.createBuffer(() -> "BPM pearl sanity draw uniforms",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UNIFORM_ALIGNMENT);
            GpuBuffer scene = device.createBuffer(() -> "BPM pearl sanity scene",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, BPM_SCENE_SIZE);
            // Sanity vertices are eye-space coordinates at z = -2.  They therefore need
            // the same perspective projection as the real debug model; an identity
            // ProjMat would clip them at z < -w before either pipeline can rasterize.
            fillProjection(proj);
            Matrix4f identity = new Matrix4f();
            try (GpuBufferSlice.MappedView view = dyn.map(false, true)) {
                ByteBuffer data = view.data();
                data.position(0);
                identity.get(data); // ModelViewMat
                identity.get(data); // TextureMat
                data.putFloat(1.0f).putFloat(1.0f).putFloat(1.0f).putFloat(1.0f); // ColorModulator
                data.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f);               // ModelOffset
            }
            try (GpuBufferSlice.MappedView view = bones.map(false, true)) {
                ByteBuffer data = view.data();
                data.position(0);
                identity.get(data);   // bone 0 transform
                identity.get(data);   // bone 0 normal
                data.putInt(0xF000F0); // meta.x = fullbright lightmap corner (15,15)
                data.putInt(0);        // meta.y = not hidden
                data.putInt(0);
                data.putInt(0);
                // Zero the tail so the whole uniform block contains defined data.
                for (int i = PearlGpuMesh.BONE_STRIDE; i < PearlGpuMesh.BONE_BLOCK_SIZE; i++) {
                    data.put(i, (byte) 0);
                }
            }
            try (GpuBufferSlice.MappedView view = draw.map(false, true)) {
                writeDrawBlock(view.data(), 0, 0, 0, 1);
            }
            fillSanityScene(scene, identity);

            // Private 16x16 opaque white texture for all three samplers (Sampler2 is
            // texel-fetched at (15,15), hence the 16x16 size).
            GpuTexture white = device.createTexture(() -> "BPM pearl sanity white texture",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, 16, 16, 1, 1);
            try (NativeImage image = new NativeImage(16, 16, false)) {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        image.setPixelABGR(x, y, 0xFFFFFFFF);
                    }
                }
                encoder.writeToTexture(white, image);
            }
            GpuTextureView whiteView = device.createTextureView(white);

            // Vertex/index data is filled via map-write, the same documented, verified path
            // as all the UBOs above. The createBuffer(label, usage, data) shortcut stages
            // its upload in the backend's init command buffer (VulkanDevice.createBuffer
            // -> objectInitCommandBuffer), a separate command buffer that only executes
            // on a LATER submit — on Vulkan the draws could then read undefined memory.
            // Mapped host-coherent writes are visible to the GPU immediately.
            GpuBuffer verts = device.createBuffer(() -> "BPM pearl sanity vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_MAP_READ, 3L * 36);
            try (GpuBufferSlice.MappedView view = verts.map(false, true)) {
                view.data().position(0).put(sanityVertices());
            }
            verifyMappedBytes("sanity.verts", verts, 3 * 36);
            GpuBuffer idx = device.createBuffer(() -> "BPM pearl sanity indices",
                    GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_MAP_READ, 4L * 4);
            try (GpuBufferSlice.MappedView view = idx.map(false, true)) {
                view.data().position(0).put(sanityIndices());
            }
            verifyMappedBytes("sanity.idx", idx, 4 * 4);
            GpuBuffer vanVerts = device.createBuffer(() -> "BPM pearl vanilla reference quad",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_MAP_READ, 4L * 24);
            try (GpuBufferSlice.MappedView view = vanVerts.map(false, true)) {
                view.data().position(0).put(vanillaQuadContents());
            }
            verifyMappedBytes("vanilla.verts", vanVerts, 4 * 24);
            return new Sanity(proj, dyn, bones, draw, scene, verts, idx, white, whiteView, vanVerts);
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl sanity setup failed (sanity draw skipped)", t);
            return null;
        }
    }

    private static void closeSanity(Sanity sanity) {
        if (sanity == null) {
            return;
        }
        for (GpuBuffer buffer : new GpuBuffer[]{sanity.proj(), sanity.dyn(), sanity.bones(),
                sanity.draw(), sanity.scene(), sanity.verts(), sanity.idx(), sanity.vanVerts()}) {
            try {
                buffer.close();
            } catch (Throwable ignored) {
            }
        }
        try {
            sanity.whiteView().close();
        } catch (Throwable ignored) {
        }
        try {
            sanity.white().close();
        } catch (Throwable ignored) {
        }
    }

    /** Same 36-byte vertex layout as {@link PearlGpuMesh}: pos12 uv8 normal12 boneId2 cullable RG8. */
    private static ByteBuffer sanityVertices() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(3 * 36).order(ByteOrder.nativeOrder());
        // Left half of the frustum ONLY (all NDC x < 0), so the vanilla reference quad on
        // the right can never overlap it in the readback verdict. z = -2.0 eye space:
        // in front of the camera (clip w = 2) and inside the 60-degree frustum (visible
        // half-extent at depth 2 is 2/tan(30) ~ 1.155 > 0.9, so NDC xy stays within ~0.78).
        putSanityVertex(buffer, -0.9f,  -0.9f, -2.0f);
        putSanityVertex(buffer, -0.35f, -0.9f, -2.0f);
        putSanityVertex(buffer, -0.65f, -0.35f, -2.0f);
        buffer.flip();
        return buffer;
    }

    private static void putSanityVertex(ByteBuffer buffer, float x, float y, float z) {
        buffer.putFloat(x).putFloat(y).putFloat(z);
        buffer.putFloat(0.5f).putFloat(0.5f);
        // Normal (0,1,0) so BOTH cardinal lights hit it: l0 == l1 == dot(light, n) == 1/sqrt(1.53)
        // -> lightAccum = min(1, 1.616*0.6 + 0.4) == 1.0 -> the fragment is EXACT opaque white.
        // (With (0,0,1) it would be ~0.74 grey and harder to classify in the readback.)
        buffer.putFloat(0.0f).putFloat(1.0f).putFloat(0.0f);
        buffer.putShort((short) 0);      // boneId = 0 (identity bone)
        buffer.put((byte) 0).put((byte) 0); // cullable.x = 0 (no backface discard)
    }

    private static ByteBuffer sanityIndices() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder());
        // Vulkan uses CLOCKWISE as the front face. GUI_TEXTURED culls back faces, so
        // its first TRIANGLE_LIST triangle must use clockwise winding. BPM disables
        // culling and is unaffected by this reversal.
        buffer.putInt(0).putInt(2).putInt(1).putInt(3);
        buffer.flip();
        return buffer;
    }

    /**
     * Vanilla position_tex_color quad through RenderPipelines.GUI_TEXTURED, with a fully
     * opaque green color on the right half of the frustum. The vertex cycle (BL, BR, TR, TL
     * in y-up NDC) matches the winding of vanilla blit quads, so culling treats it exactly
     * like real GUI geometry on both backends. Four vertices because the pipeline topology
     * is QUADS. Indices [0,1,2,3] come from the shared sanity index buffer (on Vulkan the
     * QUADS topology maps to a triangle list, so this draws the first half-triangle).
     * IMPORTANT: DefaultVertexFormat.POSITION_TEX_COLOR is Position RGB32F (12) +
     * UV0 RG32F (8) + Color RGBA8_UNORM (4) = stride 24, NOT 36-byte floats (the Color
     * vertex element is unorm bytes, verified in DefaultVertexFormat: COLOR_FORMAT =
     * GpuFormat.RGBA8_UNORM). Memory byte order of RGBA8_UNORM is R,G,B,A; a fully opaque
     * green is bytes {0, 255, 0, 255}.
     */
    private static ByteBuffer vanillaQuadContents() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 24).order(ByteOrder.nativeOrder());
        putVanillaQuadVertex(buffer, 0.35f, 0.35f);
        putVanillaQuadVertex(buffer, 0.9f,  0.35f);
        putVanillaQuadVertex(buffer, 0.9f,  0.85f);
        putVanillaQuadVertex(buffer, 0.35f, 0.85f);
        buffer.flip();
        return buffer;
    }

    private static void putVanillaQuadVertex(ByteBuffer buffer, float x, float y) {
        // z = -2.0 eye space: same frustum-fit constraint as sanityVertices().
        buffer.putFloat(x).putFloat(y).putFloat(-2.0f);  // Position = 12 bytes
        buffer.putFloat(0.5f).putFloat(0.5f);            // UV0 = 8 bytes
        buffer.put((byte) 0).put((byte) 255)             // Color RGBA8_UNORM = 4 bytes
              .put((byte) 0).put((byte) 255);            // {R=0, G=255, B=0, A=255} = opaque green
    }

    /**
     * Reversed-Z perspective projection in the active device's [0,1] depth convention.
     * This is the same formulation as Minecraft's Projection.getMatrix(): its near/far
     * arguments are intentionally swapped and zZeroToOne is true on the Vulkan backend.
     */
    private static void fillProjection(GpuBuffer buffer) {
        float near = 0.05f;
        float far = 100.0f;
        Matrix4f proj = new Matrix4f().setPerspective(
                (float) Math.toRadians(60.0), 1.0f,
                far, near, true);
        try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
            proj.get(view.data().position(0));
        }
    }

    /** Fills the combined BpmScene std140 block used by the real entity shader sanity draw. */
    private static void fillSanityScene(GpuBuffer buffer, Matrix4f identity) {
        Matrix4f projection = new Matrix4f().setPerspective(
                (float) Math.toRadians(60.0), 1.0f, 100.0f, 0.05f, true);
        try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
            ByteBuffer data = view.data().order(ByteOrder.nativeOrder());
            for (int i = 0; i < BPM_SCENE_SIZE; i++) {
                data.put(i, (byte) 0);
            }
            data.position(0);
            projection.get(data);
            identity.get(data); // ModelViewMat
            identity.get(data); // TextureMat
            data.putFloat(1.0f).putFloat(1.0f).putFloat(1.0f).putFloat(1.0f); // color
            data.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f); // ModelOffset stride
            data.putFloat(0.0f).putFloat(0.0f).putFloat(0.0f).putFloat(0.0f); // fog color
            data.putFloat(1.0e30f).putFloat(1.0e30f).putFloat(0.0f).putFloat(1.0f); // fog/opaque
            data.putInt(0).putInt(0).putInt(0).putInt(0); // overlay
            // First bone begins at the std140 header boundary, byte 272.
            identity.get(data);
            identity.get(data);
            data.putInt(0xF000F0).putInt(0).putInt(0).putInt(0);
        }
    }

    /** std140 DynamicTransforms: ModelViewMat, TextureMat, ColorModulator, ModelOffset. */
    private static void fillDynamicTransforms(GpuBuffer buffer, float r, float g, float b, float a) {
        Matrix4f identity = new Matrix4f();
        try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
            ByteBuffer data = view.data();
            data.position(0);
            identity.get(data);
            identity.get(data);
            data.putFloat(r);
            data.putFloat(g);
            data.putFloat(b);
            data.putFloat(a);
            data.putFloat(0.0f);
            data.putFloat(0.0f);
            data.putFloat(0.0f);
        }
    }

    /** Two 256-aligned blocks at 0 (opaque, alphaMode=1) and 256 (translucent, alphaMode=2). */
    private static void fillDrawUniforms(GpuBuffer buffer, int packedOverlay) {
        int oU = packedOverlay & 0xFFFF;
        int oV = (packedOverlay >> 16) & 0xFFFF;
        try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
            ByteBuffer data = view.data();
            writeDrawBlock(data, 0, oU, oV, 1);
            writeDrawBlock(data, UNIFORM_ALIGNMENT, oU, oV, 2);
        }
    }

    private static void writeDrawBlock(ByteBuffer data, int base, int oU, int oV, int alphaMode) {
        data.position(base);
        data.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f); // fogColor (fog disabled, as on the GL path)
        // fogStart/fogEnd are astronomically large so v_vertexDistance <= fogStart always
        // short-circuits the fog mix; fogShape 0, alphaMode 1 = opaque / 2 = translucent.
        data.putFloat(1.0e30f).putFloat(1.0e30f).putFloat(0f).putFloat(alphaMode);
        data.putInt(oU).putInt(oV).putInt(0).putInt(0); // overlay oU, oV
    }

    /** The magenta clear color as the ABGR int produced by the little-endian RGBA readback. */
    private static final int CLEAR_ABGR = 0xFFFF00FF;

    private static void writeDebugPng(ByteBuffer pixels, int size) throws Exception {
        int nonClear = 0;
        int firstX = -1;
        int firstY = -1;
        int firstArgb = 0;
        // Quadrant non-clear counts in readback space (y = row 0 of the copy):
        // 0 = upper-left, 1 = upper-right (vanilla ref), 2 = lower-left (ours), 3 = lower-right.
        // NOTE: copyTextureToBuffer rows are backend-dependent (GL: row0 = bottom,
        // Vulkan: row0 = top), so the quadrant labels above can flip between backends.
        // The PRIMARY verdict below therefore counts FIXED COLORS, which are axis-safe.
        int half = size / 2;
        int[] quads = new int[4];
        int yellowCount = 0;  // direct control leg: solid yellow, no resource bindings
        int cyanCount = 0;    // VBO-only leg: tests mapped vertex buffer binding
        int blueCount = 0;    // IBO-only leg: tests mapped index buffer binding
        int orangeCount = 0;  // UBO-only leg: tests mapped uniform buffer binding
        int violetCount = 0;  // private BpmProjection + BpmTransforms descriptors
        int pinkCount = 0;    // sampler-only leg: tests private texture/sampler binding
        int azureCount = 0;   // exact vanilla Projection/DynamicTransforms bind-group layouts
        int limeCount = 0;    // same layouts with identity matrices and clip-space vertices
        int greenCount = 0;   // vanilla reference leg: exact opaque green (RGBA8 bytes 0,255,0,255)
        int whiteCount = 0;   // our bpm_entity leg: near-white pixels
        try (NativeImage image = new NativeImage(size, size, false)) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    // Force the alpha so "transparent background" and "white content" can
                    // never be confused: nothing-drawn shows as flat magenta, content as
                    // anything different.
                    int argb = pixels.getInt((x + y * size) * 4) | 0xFF000000;
                    image.setPixelABGR(x, size - y - 1, argb);
                    if (argb != CLEAR_ABGR) {
                        nonClear++;
                        int quad = (y >= half ? 0 : 2) + (x >= half ? 1 : 0);
                        quads[quad]++;
                        if (firstX < 0) {
                            firstX = x;
                            firstY = y;
                            firstArgb = argb;
                        }
                        int sr = argb & 0xFF;             // red in the little-endian ABGR int
                        int sg = (argb >>> 8) & 0xFF;     // green
                        int sb = (argb >>> 16) & 0xFF;    // blue
                        if (sr >= 125 && sr <= 155 && sg <= 15 && sb >= 240) {
                            violetCount++;
                        } else if (sr <= 15 && sg >= 240 && sb >= 45 && sb <= 80) {
                            limeCount++;
                        } else if (sr <= 15 && sg >= 110 && sg <= 145 && sb >= 240) {
                            azureCount++;
                        } else if (sr <= 15 && sg <= 15 && sb >= 240) {
                            blueCount++;
                        } else if (sr >= 240 && sg >= 110 && sg <= 145 && sb <= 15) {
                            orangeCount++;
                        } else if (sr >= 240 && sg >= 35 && sg <= 65 && sb >= 135 && sb <= 170) {
                            pinkCount++;
                        } else if (sr <= 15 && sg >= 240 && sb >= 240) {
                            cyanCount++;                  // VBO-only control = cyan
                        } else if (sr >= 240 && sg >= 240 && sb <= 15) {
                            yellowCount++;                // direct control = yellow
                        } else if (argb == 0xFF00FF00) {
                            greenCount++;                 // EXACT opaque green = vanilla reference
                        } else if (sg >= 140 && Math.abs(sr - sg) < 45 && Math.abs(sb - sg) < 45) {
                            // Our bpm_entity leg: the sanity triangle writes EXACT opaque white.
                            // AA edge pixels blend white with magenta (r,b >> g) or with the
                            // green leg (r,g >> b); both blends break the |r-g|/|b-g| < 45
                            // condition, so only true white-ish fragments count here.
                            whiteCount++;
                        }
                    }
                }
            }
            Minecraft mc = Minecraft.getInstance();
            File dir = new File(mc.gameDirectory, "pearl_debug");
            boolean created = dir.isDirectory() || dir.mkdirs();
            if (!created) {
                YesSteveModel.LOGGER.warn("[BPM] Pearl debug: cannot create {}", dir.getAbsolutePath());
                return;
            }
            File png = new File(dir, "bpm_pearl_" + System.currentTimeMillis() + ".png");
            image.writeToFile(png);
            YesSteveModel.LOGGER.info("[BPM] Pearl debug render saved: {}", png.getAbsolutePath());
        }
        int total = size * size;
        YesSteveModel.LOGGER.info("[BPM] Pearl debug verdict: non-clear pixels {}/{} ({}%)",
                nonClear, total, String.format(Locale.ROOT, "%.2f", 100.0 * nonClear / total));
        String bisect;
        if (yellowCount == 0) {
            bisect = "DIRECT control did not draw -> Vulkan off-screen pass/rasterization path is broken";
        } else if (cyanCount == 0) {
            bisect = "DIRECT drew but VBO-only control did not -> mapped vertex-buffer path is broken";
        } else if (blueCount == 0) {
            bisect = "VBO drew but IBO-only control did not -> mapped index-buffer path is broken";
        } else if (orangeCount == 0) {
            bisect = "VBO/IBO drew but UBO-only control did not -> uniform-buffer descriptor path is broken";
        } else if (violetCount == 0) {
            bisect = "one UBO works but two UBO descriptors do not -> multi-UBO descriptor path is broken";
        } else if (pinkCount == 0) {
            bisect = "VBO/IBO/UBO drew but sampler-only control did not -> texture/sampler descriptor path is broken";
        } else if (azureCount == 0) {
            bisect = limeCount > 0
                    ? "vanilla layouts work with identity, but perspective contents clip -> projection convention/content is wrong"
                    : "generic controls drew but both Projection/DynamicTransforms controls failed -> vanilla matrix/layout binding is broken";
        } else if (greenCount > 0 && whiteCount > 0) {
            bisect = "BOTH drew -> pass machinery OK -> fault is inside OUR pipeline definition";
        } else if (greenCount > 0) {
            bisect = "ONLY vanilla reference drew -> pass OK -> fault is in OUR pipeline/shader/vertex format";
        } else if (whiteCount > 0) {
            bisect = "ONLY our sanity drew -> vanilla reference failed unexpectedly: check its pipeline dump";
        } else if (yellowCount > 0 && cyanCount > 0 && blueCount > 0 && orangeCount > 0 && pinkCount > 0 && azureCount > 0 && limeCount > 0) {
            bisect = "all controls including vanilla matrices drew -> fault is in vanilla/BPM shader logic or primitive topology";
        } else if (yellowCount > 0) {
            bisect = "ONLY direct control drew -> pass/rasterizer OK -> resource-backed legs still failing";
        } else if (nonClear > 0) {
            bisect = "NONCLEAR noise present but NEITHER leg produced its expected color -> treat as NEITHER (see PNG)";
        } else {
            bisect = "NEITHER drew -> the pass/draw mechanism itself is broken";
        }
        YesSteveModel.LOGGER.info(
                "[BPM] Pearl debug bisect: direct(yellow)={} px, vboOnly(cyan)={} px, iboOnly(blue)={} px, uboOnly(orange)={} px, twoUbo(violet)={} px, samplerOnly(pink)={} px, matrices(azure)={} px, matricesIdentity(lime)={} px, vanillaRef(green)={} px, ours(white)={} px, other={} px, quadrants UR/LL/UL/LR={}/{}/{}/{} -> {}",
                yellowCount, cyanCount, blueCount, orangeCount, violetCount, pinkCount, azureCount, limeCount, greenCount, whiteCount,
                nonClear - yellowCount - cyanCount - blueCount - orangeCount - violetCount - pinkCount - azureCount - limeCount - greenCount - whiteCount,
                quads[1], quads[2], quads[0], quads[3], bisect);
        if (firstX >= 0) {
            YesSteveModel.LOGGER.info("[BPM] Pearl debug verdict: first non-clear pixel at ({}, {}) ABGR=0x{}",
                    firstX, firstY, Integer.toHexString(firstArgb));
        } else {
            YesSteveModel.LOGGER.info("[BPM] Pearl debug verdict: NOTHING was rasterized (frame is the pure clear color)");
        }
    }

    /**
     * Debug-only: reads the first 16 bytes back through the mapped view (same GPU-visible
     * memory the draw will read) so wrong content can be told apart from wrong wiring.
     * Expected first integers of sanity.idx: 00000000 00000002 00000001 00000003.
     */
    private static void verifyMappedBytes(String tag, GpuBuffer buffer, int bytes) {
        try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
            ByteBuffer data = view.data();
            StringBuilder hex = new StringBuilder();
            int shown = Math.min(bytes, 16);
            for (int i = 0; i < shown; i++) {
                hex.append(String.format(Locale.ROOT, "%02X ", data.get(i)));
            }
            YesSteveModel.LOGGER.info("[BPM] Pearl debug mapped {} ({} bytes): {}", tag, bytes, hex.toString().trim());
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl debug mapped read failed for {}", tag, t);
        }
    }

    /** Reads and reports pending GL errors (safe on the render thread; one flag read per call). */
    private static void scanGlError(String at) {
        if (GpuCompat.isVulkan()) {
            // No GL context exists here; any GL call aborts the JVM natively (uncatchable).
            YesSteveModel.LOGGER.info("[BPM] Pearl debug: GL error scan skipped (Vulkan backend) at {}", at);
            return;
        }
        try {
            int err = GL11C.glGetError();
            if (err != 0) {
                YesSteveModel.LOGGER.warn("[BPM] Pearl debug: GL error 0x{} at {}",
                        Integer.toHexString(err), at);
            } else {
                YesSteveModel.LOGGER.info("[BPM] Pearl debug: GL clean {}", at);
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl debug: GL error scan failed at {}", at, t);
        }
    }

    /**
     * Backend-agnostic dump of the compiled pipeline wiring: which uniform names the
     * shaders actually expose (from SPIRV reflection) and which vertex elements are bound
     * to which offsets. A missing uniform name here means pass.setUniform silently drops
     * the value (release builds skip validation), which would collapse the draw.
     */
    private static void dumpCompiledPipelineInfo(String when, RenderPipeline pipeline, String label) {
        try {
            CompiledRenderPipeline compiled = RenderSystem.getCompiledPipeline(pipeline);
            YesSteveModel.LOGGER.info("[BPM] Pearl debug pipeline {} @{}: class={}", label, when, compiled.getClass().getName());
            if (compiled instanceof FrontendRenderPipeline frp) {
                YesSteveModel.LOGGER.info("[BPM] Pearl debug pipeline uniforms (name->sparse index): {}", frp.uniformIndices());
                YesSteveModel.LOGGER.info("[BPM] Pearl debug pipeline uniform defs: {}", frp.uniforms());
                YesSteveModel.LOGGER.info("[BPM] Pearl debug pipeline vertex formats: {}", frp.vertexFormats());
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl debug pipeline dump failed", t);
        }
    }

    /**
     * GL-only: wraps a single draw in an occlusion query so the driver reports the exact
     * number of rasterized samples, regardless of the framebuffer content afterwards.
     */
    private static int glCountDrawSamples(String tag, Runnable draw) {
        try {
            int query = GL15C.glGenQueries();
            GL15C.glBeginQuery(GL15C.GL_SAMPLES_PASSED, query);
            draw.run();
            GL15C.glEndQuery(GL15C.GL_SAMPLES_PASSED);
            int samples;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer result = stack.mallocInt(1);
                GL15C.glGetQueryObjectuiv(query, GL15C.GL_QUERY_RESULT, result);
                samples = result.get(0);
            }
            GL15C.glDeleteQueries(query);
            YesSteveModel.LOGGER.info("[BPM] Pearl debug: {} samples passed = {}", tag, samples);
            return samples;
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl debug: {} occlusion query failed", tag, t);
            return -1;
        }
    }

    /**
     * GL-only: asks the driver to validate the compiled program against the live pass
     * state (framebuffer, vertex buffers, uniform bindings) and prints the reason on fail.
     */
    private static void glValidateProgramProbe(String when, CompiledRenderPipeline pipeline) {
        try {
            if (pipeline instanceof FrontendRenderPipeline frp
                    && frp.backendRenderPipeline() instanceof GlRenderPipeline glPipeline) {
                int programId = glPipeline.program().getProgramId();
                GL20C.glValidateProgram(programId);
                int status = GL20C.glGetProgrami(programId, GL20C.GL_VALIDATE_STATUS);
                String infoLog = GL20C.glGetProgramInfoLog(programId);
                YesSteveModel.LOGGER.info("[BPM] Pearl debug validateProgram @{}: status={} ({}), infoLog: {}",
                        when, status, status != 0 ? "VALID" : "INVALID",
                        infoLog.isBlank() ? "<empty>" : infoLog.replace("\n", " | "));
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl debug validateProgram failed", t);
        }
    }

    /** Debug-only snapshot of the GL rasterization state, used to restore after overrides. */
    private record DebugGlSnapshot(int[] viewport, int[] scissor, boolean scissorTest, boolean depthTest,
                                   int depthFunc, boolean depthWrite, boolean[] colorMask, boolean cull,
                                   int cullMode, int frontFace, boolean blend, int program, int polygonMode) {
    }

    /** Dumps every GL state that can silently kill fragments and returns it for restoration. */
    private static DebugGlSnapshot dumpDebugGlState(String tag) {
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
        GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, scissor);
        ByteBuffer colorMaskBuf = ByteBuffer.allocateDirect(4);
        GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, colorMaskBuf);
        ByteBuffer depthMaskBuf = ByteBuffer.allocateDirect(1);
        GL11C.glGetBooleanv(GL11C.GL_DEPTH_WRITEMASK, depthMaskBuf);
        boolean[] colorMask = {colorMaskBuf.get(0) != 0, colorMaskBuf.get(1) != 0,
                colorMaskBuf.get(2) != 0, colorMaskBuf.get(3) != 0};
        DebugGlSnapshot snapshot = new DebugGlSnapshot(
                viewport, scissor,
                GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST), GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST),
                GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC), depthMaskBuf.get(0) != 0, colorMask,
                GL11C.glIsEnabled(GL11C.GL_CULL_FACE), GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE),
                GL11C.glGetInteger(GL11C.GL_FRONT_FACE), GL11C.glIsEnabled(GL11C.GL_BLEND),
                GL11C.glGetInteger(org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM),
                GL11C.glGetInteger(GL11C.GL_POLYGON_MODE));
        YesSteveModel.LOGGER.info(
                "[BPM] Pearl debug GL state @{}: viewport=[{} {} {} {}] scissor=[{} {} {} {}] scissorTest={} "
                        + "depthTest={} depthFunc=0x{} depthWrite={} colorMask=[{} {} {} {}] cull={} cullMode=0x{} "
                        + "frontFace=0x{} blend={} program={} polygonMode=0x{}",
                tag, viewport[0], viewport[1], viewport[2], viewport[3],
                scissor[0], scissor[1], scissor[2], scissor[3], snapshot.scissorTest(), snapshot.depthTest(),
                Integer.toHexString(snapshot.depthFunc()), snapshot.depthWrite(),
                colorMask[0], colorMask[1], colorMask[2], colorMask[3],
                snapshot.cull(), Integer.toHexString(snapshot.cullMode()), Integer.toHexString(snapshot.frontFace()),
                snapshot.blend(), snapshot.program(), Integer.toHexString(snapshot.polygonMode()));
        return snapshot;
    }

    /** Neutralizes every state that can silently suppress rasterization (debug build only). */
    private static void applyDebugVisibilityOverrides() {
        GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        GL11C.glDisable(GL11C.GL_CULL_FACE);
        GL11C.glDepthFunc(GL11C.GL_ALWAYS);
        GL11C.glColorMask(true, true, true, true);
        GL11C.glDepthMask(true);
    }

    private static void restoreDebugVisibility(DebugGlSnapshot s) {
        GL11C.glDepthFunc(s.depthFunc());
        if (s.scissorTest()) {
            GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
        } else {
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        }
        if (s.cull()) {
            GL11C.glEnable(GL11C.GL_CULL_FACE);
        } else {
            GL11C.glDisable(GL11C.GL_CULL_FACE);
        }
        boolean[] cm = s.colorMask();
        GL11C.glColorMask(cm[0], cm[1], cm[2], cm[3]);
        GL11C.glDepthMask(s.depthWrite());
    }

    /**
     * Reads 1x1 pixels straight from the currently bound draw framebuffer, inside the
     * render pass, bypassing the readback/copy/PNG chain entirely. GL window coordinates
     * (origin bottom-left). Point 1 is strictly inside the vanilla reference quad
     * (right half, pixels x 209..284 / y 208..278), point 2 inside the sanity triangle
     * (x 35..111 / y 35..111); points 3-4 lie outside both and must always stay the pure
     * clear (magenta) color. Also reads the depth buffer at an inside point of the sanity
     * triangle (the only depth-writing shape) and an outside point (reversed-Z: untouched
     * background is 0.0).
     */
    private static void probeDebugFrame(String tag) {
        if (GpuCompat.isVulkan()) {
            // glReadPixels inside the active render pass is GL-specific; the PNG readback
            // below remains the backend-independent verdict source on Vulkan.
            YesSteveModel.LOGGER.info("[BPM] Pearl debug probe @{}: skipped (Vulkan backend)", tag);
            return;
        }
        try {
            // (1) inside the vanilla reference quad, (2) inside the sanity triangle,
            // (3)-(4) outside both shapes and must stay the clear color.
            int[][] points = {{260, 242}, {60, 44}, {300, 30}, {160, 290}};
            StringBuilder text = new StringBuilder();
            for (int[] point : points) {
                ByteBuffer rgba = ByteBuffer.allocateDirect(4);
                GL11C.glReadPixels(point[0], point[1], 1, 1, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, rgba);
                int r = rgba.get(0) & 0xFF;
                int g = rgba.get(1) & 0xFF;
                int b = rgba.get(2) & 0xFF;
                int a = rgba.get(3) & 0xFF;
                boolean isClear = r >= 250 && g <= 5 && b >= 250;
                text.append(String.format(Locale.ROOT, "(%d,%d)=[%d %d %d %d]%s ",
                        point[0], point[1], r, g, b, a, isClear ? "=clear" : " NONCLEAR"));
            }
            float innerDepth = readDepthAt(70, 60);
            float outerDepth = readDepthAt(160, 290);
            int err = GL11C.glGetError();
            YesSteveModel.LOGGER.info("[BPM] Pearl debug probe @{}: {} innerDepth={} outerDepth={} GLerr=0x{}",
                    tag, text, String.format(Locale.ROOT, "%.5f", innerDepth),
                    String.format(Locale.ROOT, "%.5f", outerDepth), Integer.toHexString(err));
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[BPM] Pearl debug: frame probe failed at {}", tag, t);
        }
    }

    private static float readDepthAt(int x, int y) {
        ByteBuffer buf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        GL11C.glReadPixels(x, y, 1, 1, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, buf);
        return buf.getFloat(0);
    }

    private static void closeAll(PearlGpuMesh mesh, GpuTexture color, GpuTexture depth,
                                 GpuBuffer projection, GpuBuffer dynamicTransforms, GpuBuffer drawUniforms) {
        try {
            mesh.close();
        } catch (Throwable ignored) {
        }
        if (color != null) color.close();
        if (depth != null) depth.close();
        if (projection != null) projection.close();
        if (dynamicTransforms != null) dynamicTransforms.close();
        if (drawUniforms != null) drawUniforms.close();
    }

    private static GpuTextureView resolveModelTextureView(Identifier textureLocation) {
        AbstractTexture tex = mcTexture(textureLocation);
        return tex != null ? tex.getTextureView() : null;
    }

    private static GpuSampler resolveModelSampler(Identifier textureLocation) {
        AbstractTexture tex = mcTexture(textureLocation);
        if (tex != null) {
            GpuSampler own = tex.getSampler();
            if (own != null) {
                return own;
            }
        }
        return RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
    }

    private static AbstractTexture mcTexture(Identifier textureLocation) {
        try {
            return Minecraft.getInstance().getTextureManager().getTexture(textureLocation);
        } catch (Throwable t) {
            return null;
        }
    }

    private static GpuTextureView mcOverlayView() {
        try {
            return Minecraft.getInstance().gameRenderer.overlayTexture().getTextureView();
        } catch (Throwable t) {
            return null;
        }
    }

    private static GpuTextureView mcLightmapView() {
        try {
            return Minecraft.getInstance().gameRenderer.lightmap();
        } catch (Throwable t) {
            return null;
        }
    }
}
