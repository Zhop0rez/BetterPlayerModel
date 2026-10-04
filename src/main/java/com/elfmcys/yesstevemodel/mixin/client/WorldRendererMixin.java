package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.entity.EntityRenderCache;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class WorldRendererMixin {
    // 26.3: render(GraphicsResourceAllocator, boolean, CameraRenderState, GpuBufferSlice, Vector4f, boolean, boolean) — DeltaTracker и Matrix4fc удалены из сигнатуры
    @Inject(remap = false, method = {"render"}, at = @At("HEAD"))
    private void renderLevelPre(GraphicsResourceAllocator allocator, boolean renderBlockOutline, CameraRenderState cameraRenderState, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean flag1, boolean flag2, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            ModelPreviewRenderer.setWorldRenderMode(true);
            EntityRenderCache.tick(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }
    }

    @Inject(remap = false, method = {"render"}, at = @At("RETURN"))
    private void renderLevelPost(GraphicsResourceAllocator allocator, boolean renderBlockOutline, CameraRenderState cameraRenderState, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean flag1, boolean flag2, CallbackInfo ci) {
        if (YesSteveModel.isAvailable()) {
            EntityRenderCache.clear();
            ModelPreviewRenderer.setWorldRenderMode(false);
        }
    }
}
