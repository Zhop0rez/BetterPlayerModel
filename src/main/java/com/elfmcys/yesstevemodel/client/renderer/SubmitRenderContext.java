package com.elfmcys.yesstevemodel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SubmitRenderContext {

    private static final ThreadLocal<SubmitNodeCollector> CURRENT = new ThreadLocal<>();

    private SubmitRenderContext() {
    }

    public static void set(SubmitNodeCollector collector) {
        if (collector == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(collector);
        }
    }

    public static SubmitNodeCollector get() {
        return CURRENT.get();
    }

    /**
     * 26.3: ItemInHandRenderer удалён — предметы рендерятся через экстракцию в ItemStackRenderState и submit.
     */
    public static void renderItemStack(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        if (collector == null || stack.isEmpty()) {
            return;
        }
        ItemStackRenderState renderState = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(renderState, stack, context, entity);
        if (!renderState.isEmpty()) {
            renderState.submit(poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
