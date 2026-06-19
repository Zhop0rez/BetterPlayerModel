package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.client.entity.GeckoVehicleEntity;
import com.elfmcys.yesstevemodel.client.entity.GeckoProjectileEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IGeoRenderer<T extends AnimatableEntity<?>> {
    MultiBufferSource getCurrentRTB();

    default void setCurrentRTB(MultiBufferSource bufferSource) {
    }

    default void renderWithBone(AnimatedGeoModel model, T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5) {
        renderWithBoneAndRenderType(model, animatable, partialTick, renderType, poseStack, bufferSource, i, vertexConsumer, i2, i3, f2, f3, f4, f5, animatable.getTextureLocation());
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5, ResourceLocation textureLocation) {
        if (vertexConsumer == null) {
            vertexConsumer = bufferSource.getBuffer(renderType);
        }
        animatable.resetAnimationState();
        boolean allowDirectGpuRenderer = !(animatable instanceof GeckoVehicleEntity) && !(animatable instanceof GeckoProjectileEntity);
        NativeModelRenderer.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5, textureLocation, allowDirectGpuRenderer);
        setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
    }

    default void renderEarly(T animatable, PoseStack poseStack, float partialTick,
                             @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight,
                             int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL) {
            float width = animatable.getWidthScale();
            float height = animatable.getHeightScale();
            poseStack.scale(width, height, width);
        }
    }

    default void renderLate(T animatable, PoseStack poseStack, float partialTick, MultiBufferSource bufferSource,
                            @Nullable VertexConsumer buffer, int packedLight, int packedOverlayIn, float red, float green, float blue,
                            float alpha) {
    }

    @Nullable
    default RenderType getRenderType(ResourceLocation ResourceLocation, boolean z, boolean z2, boolean z3) {
        if (z) {
            if (z3) {
                return RenderType.entityTranslucent(ResourceLocation);
            }
            return RenderType.entityCutout(ResourceLocation);
        }
        if (z2) {
            return RenderType.outline(ResourceLocation);
        }
        return null;
    }

    default Color getRenderColor(T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight) {
        return Color.WHITE;
    }

    @NotNull
    default IRenderCycle getCurrentModelRenderCycle() {
        return EModelRenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(IRenderCycle cycle) {
    }
}
