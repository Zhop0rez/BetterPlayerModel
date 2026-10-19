package rip.ysm.gpu.pearl;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jspecify.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Mesh buffers for the renderpearl render path. Mirrors {@link rip.ysm.gpu.GpuMeshBuilder},
 * but serializes normals as RGB32_FLOAT instead of packed 2.10.10.10 (the renderpearl
 * GpuFormat set has no signed 10-bit packed format) and stores bones in a UBO
 * (std140) instead of an SSBO.
 */
public final class PearlGpuMesh {
    public static final int BONE_STRIDE = 144; // mat4 transform + mat4 normal + ivec4 meta
    public static final int MAX_BONES = 113; // 113 * 144 = 16272 <= 16KB uniform block guarantee
    /**
     * The shader block {@code BpmBones} is declared with {@code BpmBone[113]}, so the GL
     * uniform block size is always 113 * 144 = 16272 bytes. The bound buffer range must be
     * at least the block size, otherwise the draw generates GL_INVALID_OPERATION and is
     * skipped entirely (and reads inside/outside the range are undefined on other drivers).
     */
    public static final int BONE_BLOCK_SIZE = MAX_BONES * BONE_STRIDE;
    private static final int VERTEX_STRIDE = 36; // pos12 + uv8 + normal12 + boneId2 + cullable as RG8 (2, second component unused)

    public final GpuBuffer vertexBuffer;
    public final GpuBuffer indexBuffer;
    public final GpuBuffer boneBuffer;
    public final int vertexCount;
    public final int indexCount;
    public final int boneCount;
    public final int partMask0Start;
    public final int partMask0Count;
    public final int partMask1Start;
    public final int partMask1Count;
    public final int partMask2Start;
    public final int partMask2Count;
    public final int partMask3Start;
    public final int partMask3Count;
    /** Debug-only:{minX,minY,minZ,maxX,maxY,maxZ} of raw (unbinded) vertex positions; null if mesh has no quads. */
    public final float[] debugBounds;

    private PearlGpuMesh(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, GpuBuffer boneBuffer,
                         int vertexCount, int indexCount, int boneCount,
                         int partMask0Start, int partMask0Count,
                         int partMask1Start, int partMask1Count,
                         int partMask2Start, int partMask2Count,
                         int partMask3Start, int partMask3Count, float[] debugBounds) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.boneBuffer = boneBuffer;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.boneCount = boneCount;
        this.partMask0Start = partMask0Start;
        this.partMask0Count = partMask0Count;
        this.partMask1Start = partMask1Start;
        this.partMask1Count = partMask1Count;
        this.partMask2Start = partMask2Start;
        this.partMask2Count = partMask2Count;
        this.partMask3Start = partMask3Start;
        this.partMask3Count = partMask3Count;
        this.debugBounds = debugBounds;
    }

    public GpuBufferSlice bonesSlice() {
        return this.boneBuffer.slice();
    }

    public GpuBufferSlice vertexSlice() {
        return this.vertexBuffer.slice();
    }

    /** First index (in index units) of the part mask draw range. */
    public int indexRangeStart(int partMask) {
        return switch (normalizePartMask(partMask)) {
            case 1 -> partMask1Start;
            case 2 -> partMask2Start;
            case 3 -> partMask3Start;
            default -> partMask0Start;
        };
    }

    /** Index count of the part mask draw range. */
    public int indexRangeCount(int partMask) {
        return switch (normalizePartMask(partMask)) {
            case 1 -> partMask1Count;
            case 2 -> partMask2Count;
            case 3 -> partMask3Count;
            default -> partMask0Count;
        };
    }

    private static int normalizePartMask(int partMask) {
        return partMask >= 0 && partMask <= 3 ? partMask : 0;
    }

    /**
     * Uploads interleaved bone data with the exact layout of
     * {@code GpuRenderPath.computeBoneMatrices} (144 bytes per bone).
     */
    public void uploadBones(ByteBuffer interleaved) {
        RenderSystem.assertOnRenderThread();
        interleaved.position(0);
        interleaved.limit(this.boneCount * BONE_STRIDE);
        try (GpuBufferSlice.MappedView view = this.boneBuffer.map(false, true)) {
            ByteBuffer block = view.data();
            block.position(0);
            if (block.capacity() < BONE_BLOCK_SIZE) {
                throw new IllegalStateException("bone buffer is smaller than the uniform block: "
                        + block.capacity() + " < " + BONE_BLOCK_SIZE);
            }
            // Zero the tail beyond the used bones so the rest of the block is defined data.
            for (int i = this.boneCount * BONE_STRIDE; i < BONE_BLOCK_SIZE; i++) {
                block.put(i, (byte) 0);
            }
            block.put(interleaved);
        }
    }

    public void close() {
        this.vertexBuffer.close();
        this.indexBuffer.close();
        this.boneBuffer.close();
    }

    @Nullable
    public static PearlGpuMesh build(GeoModel model) {
        if (model.bakedBones == null || model.bakedBones.isEmpty()) {
            return null;
        }
        RenderSystem.assertOnRenderThread();
        int boneCount = model.bakedBones.size();
        if (boneCount > MAX_BONES) {
            return null; // would exceed the uniform block guarantee; CPU path covers these models
        }

        Serialized collected = serialize(model);
        if (collected == null) {
            return null;
        }

        GpuDevice device = RenderSystem.getDevice();
        // Same map-write fill as the bone buffer: the createBuffer(..., data) shortcut
        // stages its upload in the backend's init command buffer, which on Vulkan only
        // executes on a later submit — the draws could read undefined memory.
        GpuBuffer vbo = device.createBuffer(() -> "BPM pearl vertex buffer",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, (long) collected.vertices.remaining());
        try (GpuBufferSlice.MappedView view = vbo.map(false, true)) {
            view.data().position(0).put(collected.vertices);
        }
        GpuBuffer ibo = device.createBuffer(() -> "BPM pearl index buffer",
                GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_MAP_WRITE, (long) collected.indices.remaining());
        try (GpuBufferSlice.MappedView view = ibo.map(false, true)) {
            view.data().position(0).put(collected.indices);
        }
        // Full uniform block size even if only a few bones are used; the tail stays zeroed.
        GpuBuffer bones = device.createBuffer(() -> "BPM pearl bone buffer",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, (long) BONE_BLOCK_SIZE);

        return new PearlGpuMesh(
                vbo, ibo, bones,
                collected.vertexCount, collected.indexCount, boneCount,
                0, collected.partIndexCounts[0],
                collected.partIndexCounts[0], collected.partIndexCounts[1],
                collected.partIndexCounts[0] + collected.partIndexCounts[1], collected.partIndexCounts[2],
                collected.partIndexCounts[0] + collected.partIndexCounts[1] + collected.partIndexCounts[2], collected.partIndexCounts[3],
                collected.debugBounds
        );
    }

    private static Serialized serialize(GeoModel model) {
        int totalQuads = 0;
        for (GeoModel.BakedBone bone : model.bakedBones) {
            for (GeoModel.BakedCube cube : bone.cubes) {
                totalQuads += cube.quads.size();
            }
        }
        if (totalQuads <= 0) {
            return null;
        }

        int vertexCount = totalQuads * 4;
        int indexCount = totalQuads * 6;
        ByteBuffer vertices = ByteBuffer.allocateDirect(vertexCount * VERTEX_STRIDE).order(ByteOrder.nativeOrder());
        int[] quadMasks = new int[totalQuads];
        int[] partIndexCounts = new int[4];
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                          -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

        int quadIndex = 0;
        for (int boneIdx = 0; boneIdx < model.bakedBones.size(); boneIdx++) {
            GeoModel.BakedBone bone = model.bakedBones.get(boneIdx);
            int partMask = bone.partMask >= 1 && bone.partMask <= 3 ? bone.partMask : 0;
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    quadMasks[quadIndex] = partMask;
                    partIndexCounts[partMask] += 6;
                    for (int v = 0; v < 4; v++) {
                        putVertex(vertices, quad.positions[v], quad.uvs[v], quad.normal, boneIdx, cube.cullable);
                        Vector3f p = quad.positions[v];
                        if (p.x() < bounds[0]) bounds[0] = p.x();
                        if (p.y() < bounds[1]) bounds[1] = p.y();
                        if (p.z() < bounds[2]) bounds[2] = p.z();
                        if (p.x() > bounds[3]) bounds[3] = p.x();
                        if (p.y() > bounds[4]) bounds[4] = p.y();
                        if (p.z() > bounds[5]) bounds[5] = p.z();
                    }
                    quadIndex++;
                }
            }
        }
        vertices.flip();

        ByteBuffer indices = ByteBuffer.allocateDirect(indexCount * Integer.BYTES).order(ByteOrder.nativeOrder());
        for (int partMask = 0; partMask <= 3; partMask++) {
            for (int i = 0; i < quadMasks.length; i++) {
                if (quadMasks[i] == partMask) {
                    putQuadIndices(indices, i * 4);
                }
            }
        }
        indices.flip();

        if (bounds[0] > bounds[3]) { // degenerate: no vertex was iterated
            bounds = null;
        }
        return new Serialized(vertices, indices, vertexCount, indexCount, partIndexCounts, bounds);
    }

    private static void putVertex(ByteBuffer vertices, Vector3f position, Vector2f uv, Vector3f normal, int boneIdx, boolean cullable) {
        vertices.putFloat(position.x());
        vertices.putFloat(position.y());
        vertices.putFloat(position.z());
        vertices.putFloat(uv.x());
        vertices.putFloat(uv.y());
        vertices.putFloat(normal.x());
        vertices.putFloat(normal.y());
        vertices.putFloat(normal.z());
        vertices.putShort((short) boneIdx);
        vertices.put((byte) (cullable ? 1 : 0));
        vertices.put((byte) 0); // RG8_UINT component 1, unused
    }

    private static void putQuadIndices(ByteBuffer indices, int base) {
        indices.putInt(base);
        indices.putInt(base + 1);
        indices.putInt(base + 2);
        indices.putInt(base + 2);
        indices.putInt(base + 3);
        indices.putInt(base);
    }

    private static final class Serialized {
        final ByteBuffer vertices;
        final ByteBuffer indices;
        final int vertexCount;
        final int indexCount;
        final int[] partIndexCounts;
        final float[] debugBounds;

        Serialized(ByteBuffer vertices, ByteBuffer indices, int vertexCount, int indexCount, int[] partIndexCounts, float[] debugBounds) {
            this.vertices = vertices;
            this.indices = indices;
            this.vertexCount = vertexCount;
            this.indexCount = indexCount;
            this.partIndexCounts = partIndexCounts;
            this.debugBounds = debugBounds;
        }
    }
}
