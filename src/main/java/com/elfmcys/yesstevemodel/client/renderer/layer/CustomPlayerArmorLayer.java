package com.elfmcys.yesstevemodel.client.renderer.layer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import rip.ysm.compat.simplehats.SimpleHatsHelper;
import com.elfmcys.yesstevemodel.client.renderer.SubmitRenderContext;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CustomPlayerArmorLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private final ItemInHandRenderer itemRenderer;

    public CustomPlayerArmorLayer(EntityRendererProvider.Context context) {
        this.itemRenderer = context.getEntityRenderDispatcher().getItemInHandRenderer();
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Player player = entityLivingBaseIn.getEntity();
        AnimatedGeoModel model = entityLivingBaseIn.getCurrentModel();
        if (model != null && !model.headBones().isEmpty()) {
            ItemStack itemBySlot = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!itemBySlot.isEmpty() && !isArmorItem(itemBySlot)) {
                renderArmorPiece(poseStack, bufferSource, packedLightIn, model, player, itemBySlot);
            }
            ItemStack stack = SimpleHatsHelper.getHatItem(player);
            if (stack != null && !stack.isEmpty()) {
                renderArmorPiece(poseStack, bufferSource, packedLightIn, model, player, stack);
            }
        }
    }

    private boolean isArmorItem(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem armorItem && armorItem.getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    private void renderArmorPiece(PoseStack poseStack, MultiBufferSource bufferSource, int i, AnimatedGeoModel model, Player player, ItemStack stack) {
        MultiBufferSource collector = SubmitRenderContext.get();
        if (collector == null) {
            return;
        }
        poseStack.pushPose();
        RenderUtils.prepMatrixForLocator(poseStack, model.headBones());
        poseStack.scale(0.625f, 0.625f, 0.625f);
        poseStack.translate(0.0f, 0.25f, 0.0f);
        // this.itemRenderer.renderStatic(player, stack, ItemDisplayContext.HEAD, false, poseStack, collector, player.level(), i, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, player.getId());
        poseStack.popPose();
    }
}
