package rip.ysm.gpu.pearl;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.geckolib3.geo.FaceCulling;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.mixin.client.RenderTypeInvoker;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RenderPearl implementation of BPM's bone skinning for the Vulkan renderer.
 *
 * <p>Unlike the old OpenGL path, this deliberately has no manual command encoder,
 * VBO, SSBO, or extra uniform buffer.  Minecraft stages the geometry through its
 * normal {@link SubmitNodeCollector}; the only per-model GPU data is a palette in
 * a dynamic texture bound as {@code Sampler1}.  That is the descriptor shape Sodium
 * accepts in the main frame graph.</p>
 */
public final class PearlCollectorGpuPath {
    private static final int PALETTE_WIDTH = 1024;
    // 32 rows provide 32,768 texels / 33 texels per bone = 992 bones.
    // This covers large real-world YSM models such as 无相 (950 bones) while
    // retaining a guarded CPU fallback for models beyond the palette capacity.
    private static final int SMALL_PALETTE_HEIGHT = 8;
    private static final int LARGE_PALETTE_HEIGHT = 32;
    // Model matrix, normal matrix, then visibility. Both matrices are calculated
    // with the entity pose on the CPU, exactly as the fallback renderer does.
    private static final int PALETTE_STRIDE = 33;
    private static final int MAX_BONES = PALETTE_WIDTH * LARGE_PALETTE_HEIGHT / PALETTE_STRIDE;

    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(
            "better_player_model", "core/bpm_collector_entity");
    private static final RenderPipeline OPAQUE_PIPELINE = pipeline("collector_entity_opaque", ColorTargetState.DEFAULT);
    private static final RenderPipeline TRANSLUCENT_PIPELINE = pipeline("collector_entity_translucent", new ColorTargetState(BlendFunction.TRANSLUCENT));

    /* Animatables, rather than GeoModels, are the keys: two players can legitimately
       share one baked model while their animation matrices differ in the same frame. */
    private static final Map<Object, PaletteBinding> PALETTES = new IdentityHashMap<>();
    private static final ThreadLocal<CullingScratch> CULLING_SCRATCH = ThreadLocal.withInitial(CullingScratch::new);
    private static int nextPaletteId;
    private static boolean loggedEnabled;

    private PearlCollectorGpuPath() {
    }

    public static boolean trySubmit(
            Object animatable,
            SubmitNodeCollector collector,
            PoseStack.Pose pose,
            GeoModel model,
            float[] boneParams,
            float[] stateBuffer,
            int renderPartMask,
            int packedLight,
            float red, float green, float blue, float alpha,
            Identifier textureLocation,
            boolean translucent
    ) {
        if (!rip.ysm.gpu.GpuCompat.isVulkan()
                || !GeneralConfig.USE_GPU_RENDERER.get()
                || textureLocation == null
                || model.bakedBones == null
                || model.bakedBones.isEmpty()
                || model.bakedBones.size() > MAX_BONES) {
            return false;
        }

        try {
            // A failed pipeline must never be allowed into the frame graph: the graph
            // would only discover it while executing and take the whole render frame
            // down.  Compile synchronously here so the catch below can retain CPU.
            com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(
                    translucent ? TRANSLUCENT_PIPELINE : OPAQUE_PIPELINE);
            int requiredPaletteHeight = model.bakedBones.size() <= (PALETTE_WIDTH * SMALL_PALETTE_HEIGHT / PALETTE_STRIDE)
                    ? SMALL_PALETTE_HEIGHT : LARGE_PALETTE_HEIGHT;
            PaletteBinding palette = PALETTES.get(animatable);
            if (palette == null || palette.height < requiredPaletteHeight) {
                palette = new PaletteBinding(nextPaletteId++, requiredPaletteHeight);
                PALETTES.put(animatable, palette);
            }
            if (!palette.upload(model, pose, boneParams, stateBuffer, packedLight)) {
                return false;
            }

            RenderType type = palette.renderType(textureLocation, translucent);
            CullingScratch culling = CULLING_SCRATCH.get();
            culling.configureProjection();
            ByteBuffer uploadedBoneData = palette.boneData;
            collector.submitCustomGeometry(new PoseStack(), type, (ignoredPose, vertices) ->
                    emitModel(vertices, model, renderPartMask, packedLight, red, green, blue, alpha,
                            uploadedBoneData, culling));

            if (!loggedEnabled) {
                loggedEnabled = true;
                YesSteveModel.LOGGER.info(
                        "[BPM] Vulkan GPU skinning enabled: main-frame collector, Sampler0=model texture, Sampler1=bone palette");
            }
            return true;
        } catch (Throwable error) {
            YesSteveModel.LOGGER.warn("[BPM] Vulkan GPU skinning submission failed; using CPU fallback", error);
            return false;
        }
    }

    private static RenderPipeline pipeline(String name, ColorTargetState colorTarget) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("better_player_model", "bpm_pearl/" + name))
                .withVertexShader(SHADER)
                .withFragmentShader(SHADER)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.LIGHTING)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1_SAMPLER2)
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withColorTargetState(colorTarget)
                .withCull(false)
                .build();
    }

    private static void emitModel(VertexConsumer vertices, GeoModel model, int renderPartMask,
                                  int packedLight, float red, float green, float blue, float alpha,
                                  ByteBuffer boneData, CullingScratch culling) {
        int r = Math.clamp((int) (red * 255.0f), 0, 255);
        int g = Math.clamp((int) (green * 255.0f), 0, 255);
        int b = Math.clamp((int) (blue * 255.0f), 0, 255);
        int a = Math.clamp((int) (alpha * 255.0f), 0, 255);

        for (int boneIndex = 0; boneIndex < model.bakedBones.size(); boneIndex++) {
            GeoModel.BakedBone bone = model.bakedBones.get(boneIndex);
            if (renderPartMask != 0 && bone.partMask != renderPartMask && bone.partMask != 3) {
                continue;
            }
            if (!culling.isBoneVisible(boneData, boneIndex)) {
                continue;
            }
            // Match the fallback renderer: build the projected bone matrix once
            // per bone, not once per quad.  Large models contain thousands of
            // quads per frame, so reconstructing this same matrix for each face
            // made the supposed GPU path CPU-bound.
            culling.selectBone(boneData, boneIndex);
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    // This exactly mirrors NativeModelRenderer's per-frame
                    // CPU culling.  Back-facing geometry of a closed/cullable
                    // cube must not reach the collector: otherwise it can
                    // compete with a nearby authored layer in the depth buffer.
                    if (cube.cullable && !culling.isFrontFacing(quad)) {
                        continue;
                    }
                    // RenderPearl's collector has triangle topology, while baked BPM
                    // geometry arrives as quads.  Keep the same winding as GpuMeshBuilder.
                    emitVertex(vertices, quad, 0, boneIndex, packedLight, r, g, b, a);
                    emitVertex(vertices, quad, 1, boneIndex, packedLight, r, g, b, a);
                    emitVertex(vertices, quad, 2, boneIndex, packedLight, r, g, b, a);
                    emitVertex(vertices, quad, 2, boneIndex, packedLight, r, g, b, a);
                    emitVertex(vertices, quad, 3, boneIndex, packedLight, r, g, b, a);
                    emitVertex(vertices, quad, 0, boneIndex, packedLight, r, g, b, a);
                }
            }
        }
    }

    private static void emitVertex(VertexConsumer out, GeoModel.BakedQuad quad, int vertex, int bone,
                                   int packedLight, int r, int g, int b, int a) {
        out.addVertex(quad.positions[vertex])
                .setColor(r, g, b, a)
                .setUv(quad.uvs[vertex].x(), quad.uvs[vertex].y())
                .setUv1(bone, 0)
                .setUv2(packedLight & 0xFFFF, packedLight >>> 16)
                .setNormal(quad.normal.x(), quad.normal.y(), quad.normal.z());
    }

    private static final class PaletteBinding {
        private final Identifier textureId;
        private final DynamicTexture texture;
        private final int height;
        private final Map<Identifier, RenderType> opaqueTypes = new LinkedHashMap<>();
        private final Map<Identifier, RenderType> translucentTypes = new LinkedHashMap<>();
        private ByteBuffer boneData = ByteBuffer.allocate(0);
        // Last compact palette sent to the GPU. Idle models submit every frame,
        // but their bone matrices often remain unchanged; avoid a redundant
        // full DynamicTexture upload in that case.
        private ByteBuffer uploadedBoneData = ByteBuffer.allocate(0);
        private int uploadedBoneBytes;

        PaletteBinding(int id, int height) {
            this.height = height;
            textureId = Identifier.fromNamespaceAndPath("better_player_model", "bpm_gpu/bone_palette_" + id);
            texture = new DynamicTexture(() -> "BPM Vulkan bone palette " + id, PALETTE_WIDTH, height, false);
            Minecraft.getInstance().getTextureManager().register(textureId, texture);
        }

        boolean upload(GeoModel model, PoseStack.Pose pose, float[] boneParams, float[] stateBuffer, int packedLight) {
            int byteCount = model.bakedBones.size() * 144;
            if (boneData.capacity() < byteCount) {
                boneData = ByteBuffer.allocate(byteCount).order(ByteOrder.nativeOrder());
            }
            boneData.clear();
            if (!rip.ysm.gpu.GpuRenderPath.computeBoneMatrices(
                    model, pose.pose(), new Matrix3f(pose.normal()), boneParams, stateBuffer, packedLight, boneData)) {
                return false;
            }

            if (uploadedBoneBytes == byteCount && sameBytes(boneData, uploadedBoneData, byteCount)) {
                return true;
            }

            NativeImage pixels = texture.getPixels();
            for (int bone = 0; bone < model.bakedBones.size(); bone++) {
                int byteOffset = bone * 144;
                for (int component = 0; component < 16; component++) {
                    putBits(pixels, bone * PALETTE_STRIDE + component, boneData.getInt(byteOffset + component * Integer.BYTES));
                }
                for (int component = 0; component < 16; component++) {
                    putBits(pixels, bone * PALETTE_STRIDE + 16 + component,
                            boneData.getInt(byteOffset + (16 + component) * Integer.BYTES));
                }
                // The second metadata integer is written as 1 for a hidden bone.
                putBits(pixels, bone * PALETTE_STRIDE + 32,
                        boneData.getInt(byteOffset + 33 * Integer.BYTES) == 0 ? Float.floatToRawIntBits(1.0f) : 0);
            }
            texture.upload();
            if (uploadedBoneData.capacity() < byteCount) {
                uploadedBoneData = ByteBuffer.allocate(byteCount).order(ByteOrder.nativeOrder());
            }
            for (int i = 0; i < byteCount; i++) {
                uploadedBoneData.put(i, boneData.get(i));
            }
            uploadedBoneBytes = byteCount;
            return true;
        }

        private static boolean sameBytes(ByteBuffer left, ByteBuffer right, int bytes) {
            for (int i = 0; i < bytes; i++) {
                if (left.get(i) != right.get(i)) {
                    return false;
                }
            }
            return true;
        }

        RenderType renderType(Identifier modelTexture, boolean translucent) {
            Map<Identifier, RenderType> types = translucent ? translucentTypes : opaqueTypes;
            return types.computeIfAbsent(modelTexture, texture -> RenderTypeInvoker.bpm$create(
                    "better_player_model:collector_gpu_" + textureId.getPath() + "_" + texture.getPath(),
                    RenderSetup.builder(translucent ? TRANSLUCENT_PIPELINE : OPAQUE_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .withTexture("Sampler1", textureId)
                            .useLightmap()
                            .createRenderSetup()));
        }

        private static void putBits(NativeImage image, int linearIndex, int bits) {
            image.setPixelABGR(linearIndex % PALETTE_WIDTH, linearIndex / PALETTE_WIDTH, bits);
        }
    }

    /**
     * CPU-side copy of the fallback renderer's face test.  Palette matrices
     * already include the entity pose, so it also tracks animated bones.
     */
    private static final class CullingScratch {
        private static final int BONE_BYTES = 144;
        private static final int HIDDEN_FLAG_OFFSET = 33 * Integer.BYTES;

        private final Matrix4f projection = new Matrix4f();
        private final Matrix4f boneMatrix = new Matrix4f();
        private final Matrix4f clipMatrix = new Matrix4f();
        private final Vector4f[] clipPositions = {
                new Vector4f(), new Vector4f(), new Vector4f()
        };

        void configureProjection() {
            projection.identity();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameRenderer != null && minecraft.gameRenderer.mainCamera().isInitialized()) {
                minecraft.gameRenderer.mainCamera().getViewRotationProjectionMatrix(projection);
                return;
            }

            int width = Math.max(1, minecraft.getWindow().getWidth());
            int height = Math.max(1, minecraft.getWindow().getHeight());
            float fovRadians = (float) Math.toRadians(minecraft.options.fov().get().doubleValue());
            float farPlane = Math.max(16.0f, minecraft.options.getEffectiveRenderDistance() * 4.0f);
            projection.setPerspective(fovRadians, (float) width / height, 0.05f, farPlane);
        }

        boolean isBoneVisible(ByteBuffer boneData, int boneIndex) {
            return boneData.getInt(boneIndex * BONE_BYTES + HIDDEN_FLAG_OFFSET) == 0;
        }

        void selectBone(ByteBuffer boneData, int boneIndex) {
            // Do not feed a heap ByteBuffer view to JOML here.  On the Java 25
            // runtime bundled with this instance that path reaches Unsafe with
            // a null native address and terminates the JVM.  Absolute reads are
            // bounds-checked Java operations and have the same column layout as
            // Matrix4f#get(float[]) used when creating the palette.
            int base = boneIndex * BONE_BYTES;
            boneMatrix
                    .m00(boneData.getFloat(base))
                    .m01(boneData.getFloat(base + 4))
                    .m02(boneData.getFloat(base + 8))
                    .m03(boneData.getFloat(base + 12))
                    .m10(boneData.getFloat(base + 16))
                    .m11(boneData.getFloat(base + 20))
                    .m12(boneData.getFloat(base + 24))
                    .m13(boneData.getFloat(base + 28))
                    .m20(boneData.getFloat(base + 32))
                    .m21(boneData.getFloat(base + 36))
                    .m22(boneData.getFloat(base + 40))
                    .m23(boneData.getFloat(base + 44))
                    .m30(boneData.getFloat(base + 48))
                    .m31(boneData.getFloat(base + 52))
                    .m32(boneData.getFloat(base + 56))
                    .m33(boneData.getFloat(base + 60));
            clipMatrix.set(projection).mul(boneMatrix);
        }

        boolean isFrontFacing(GeoModel.BakedQuad quad) {
            for (int vertex = 0; vertex < 3; vertex++) {
                Vector4f clipPosition = clipPositions[vertex];
                clipPosition.set(quad.positions[vertex].x(), quad.positions[vertex].y(),
                        quad.positions[vertex].z(), 1.0f).mul(clipMatrix);
            }
            return FaceCulling.isFrontFacing(clipPositions);
        }
    }
}
