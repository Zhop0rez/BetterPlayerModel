package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InventoryScreen.class})
public class InventoryScreenMixin {
    @Inject(at = {@At("HEAD")}, method = {"renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"})
    private static void renderEntityInInventoryPre(GuiGraphics extractor, int x, int y, int scale, int xDiff, int yDiff, float f1, float f2, float f3, LivingEntity entity, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
    }

    @ModifyArg(
            method = {"renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;submitEntityRenderState(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;IIII)V"),
            index = 0)
    private static EntityRenderState ysm$markDeferredInventoryPreview(EntityRenderState renderState) {
        ModelPreviewRenderer.markDeferredGuiPreview(renderState);
        return renderState;
    }

    @Inject(at = {@At("RETURN")}, method = {"renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"})
    private static void renderEntityInInventoryPost(GuiGraphics extractor, int x, int y, int scale, int xDiff, int yDiff, float f1, float f2, float f3, LivingEntity entity, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }
}
