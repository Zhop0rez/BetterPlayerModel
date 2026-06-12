package com.elfmcys.yesstevemodel.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.joml.Vector3f;

public class InventoryLightingFix {
    public static void setup(PoseStack poseStack, Runnable renderCall) {
        // Bright diffuse lighting from top-left and top-right
        RenderSystem.setShaderLights(new Vector3f(1.0F, 1.0F, 1.0F), new Vector3f(1.0F, 1.0F, 1.0F));
        
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        RenderSystem.runAsFancy(() -> {
            renderCall.run();
        });

        dispatcher.setRenderShadow(true);
        Lighting.setupFor3DItems();
    }
}
