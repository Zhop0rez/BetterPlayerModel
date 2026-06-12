package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.renderer.CustomFishingHookRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomVehicleRenderer;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomProjectileRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void ysm$renderCustom(E entity, double x, double y, double z, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource collector, int packedLight, CallbackInfo ci) {
        if (ModelPreviewRenderer.renderQueuedGuiPreview(entity, poseStack, collector)) {
            ci.cancel();
            return;
        }

        if (!YesSteveModel.isAvailable()) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        if (entity instanceof Projectile projectile) {
            if (!GeneralConfig.DISABLE_PROJECTILE_MODEL.get()) {
                if (projectile instanceof FishingHook fishingHook) {
                    poseStack.pushPose();
                    poseStack.translate(x, y, z);
                    boolean shouldRenderVanilla = CustomFishingHookRenderer.tryRenderCustomHook(fishingHook, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
                    poseStack.popPose();
                    if (!shouldRenderVanilla) {
                        bufferSource.endBatch();
                        ci.cancel();
                    }
                    return;
                }
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                boolean shouldRenderVanilla = CustomProjectileRenderer.renderProjectile(projectile, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
                poseStack.popPose();
                if (!shouldRenderVanilla) {
                    bufferSource.endBatch();
                    ci.cancel();
                }
                return;
            }
        }
        if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get().booleanValue()) {
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            ModelPreviewRenderer.renderVehicleModel(entity, poseStack, partialTick);
            boolean shouldRenderVanilla = CustomVehicleRenderer.renderVehicle(entity, entityYaw, partialTick, poseStack, bufferSource, collector, packedLight);
            poseStack.popPose();
            if (!shouldRenderVanilla) {
                bufferSource.endBatch();
                ci.cancel();
            }
        }
    }
}
