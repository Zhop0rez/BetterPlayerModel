package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.event.ReplacePlayerRenderEvent;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.mixin.client.MinecraftAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void ysm$onRender(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource collector, int packedLight, CallbackInfo ci) {
        boolean preview = ModelPreviewRenderer.isPreview();
        float yaw = preview ? player.yBodyRot : entityYaw;

        float oldBodyRot = player.yBodyRot;
        float oldBodyRotO = player.yBodyRotO;
        float oldYRot = player.getYRot();
        float oldYRotO = player.yRotO;
        float oldXRot = player.getXRot();
        float oldXRotO = player.xRotO;
        float oldHeadRot = player.yHeadRot;
        float oldHeadRotO = player.yHeadRotO;
        
        PlayerCapability capability = PlayerCapability.get(player).orElse(null);
        if (capability != null) {
            capability.beginRenderState(player, partialTick);
        }
        try {
            net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = ((MinecraftAccessor) Minecraft.getInstance()).ysm$renderBuffers().bufferSource();
            if (ReplacePlayerRenderEvent.onRenderPlayerPre(player, yaw, partialTick, poseStack, bufferSource, collector, packedLight)) {
                bufferSource.endBatch();
                ci.cancel();
            }
        } finally {
            if (capability != null) {
                capability.endRenderState();
            }
            if (preview) {
                player.yBodyRot = oldBodyRot;
                player.yBodyRotO = oldBodyRotO;
                player.setYRot(oldYRot);
                player.yRotO = oldYRotO;
                player.setXRot(oldXRot);
                player.xRotO = oldXRotO;
                player.yHeadRot = oldHeadRot;
                player.yHeadRotO = oldHeadRotO;
            }
        }
    }
}
