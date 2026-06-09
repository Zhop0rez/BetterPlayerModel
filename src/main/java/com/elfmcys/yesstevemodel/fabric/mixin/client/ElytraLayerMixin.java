package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerElytraLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ysm$suppressVanillaElytra(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, LivingEntity livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (livingEntity instanceof Player player) {
            PlayerCapability capability = PlayerCapability.get(player).orElse(null);
            if (capability != null
                    && capability.isModelActive()
                    && capability.isModelReady()
                    && CustomPlayerElytraLayer.shouldSuppressVanillaWings(capability)) {
                ci.cancel();
            }
        }
    }
}
