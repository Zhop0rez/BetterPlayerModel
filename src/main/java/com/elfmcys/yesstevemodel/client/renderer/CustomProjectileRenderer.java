package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.ProjectileCapability;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.projectile.Projectile;

public class CustomProjectileRenderer {
    public static boolean renderProjectile(Projectile projectile, float entityYaw, float partialTick, PoseStack poseStack, SubmitNodeCollector multiBufferSource, SubmitNodeCollector collector, int packedLight) {
        return ProjectileCapability.get(projectile).map(cap -> {
            if (cap.isModelInitialized() && cap.isModelReady()) {
                SubmitRenderContext.set(collector);
                try {
                    RendererManager.getProjectileRenderer().render(cap, entityYaw, partialTick, poseStack, multiBufferSource, packedLight);
                } finally {
                    SubmitRenderContext.set(null);
                }
                return false;
            }
            return true;
        }).orElse(true);
    }
}
