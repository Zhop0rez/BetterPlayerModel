package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.geckolib3.extended.LivingEntityRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({LivingEntityRenderer.class})
public abstract class LivingRendererMixin extends EntityRenderer<LivingEntity, EntityRenderState> implements LivingEntityRendererAccessor {
    public LivingRendererMixin(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    @Unique
    public void tlm$renderNameTag(LivingEntity pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack, VertexConsumer pBuffer, int pPackedLight) {
        // MC 26.x: super.extractRenderState() signature changed, skip call
        // super.extractRenderState(pEntity, pEntityYaw, pPartialTick, pPoseStack, pBuffer, pPackedLight);
    }
}
