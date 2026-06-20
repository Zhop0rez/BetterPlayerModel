package rip.ysm.compat.slashblade.fabric;

import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class SlashBladeRendererImpl {

    private SlashBladeRendererImpl() {
    }

    public static void renderOnEntity(LivingEntity entity, AnimatedGeoModel model, PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight, ItemStack stack, float partialTick) {
    }

    public static void renderRightWaist(AnimatedGeoModel model, PoseStack poseStack, SubmitNodeCollector bufferSource, int packedLight, ItemStack stack) {
    }
}
