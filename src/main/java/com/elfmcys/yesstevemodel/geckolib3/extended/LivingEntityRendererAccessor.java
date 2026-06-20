package com.elfmcys.yesstevemodel.geckolib3.extended;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.LivingEntity;

public interface LivingEntityRendererAccessor {
    void tlm$renderNameTag(LivingEntity pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack, VertexConsumer pBuffer, int pPackedLight);
}
