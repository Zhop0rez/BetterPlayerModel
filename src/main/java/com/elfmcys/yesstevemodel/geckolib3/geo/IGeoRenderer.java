package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.client.renderer.SubmitRenderContext;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.entity.GeckoVehicleEntity;
import com.elfmcys.yesstevemodel.client.entity.GeckoProjectileEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public interface IGeoRenderer<T extends AnimatableEntity<?>> {
    SubmitNodeCollector getCurrentRTB();

    default void setCurrentRTB(SubmitNodeCollector bufferSource) {
    }

    default void renderWithBone(AnimatedGeoModel model, T animatable, float partialTick, PoseStack poseStack, @Nullable SubmitNodeCollector bufferSource, @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable SubmitNodeCollector bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5) {
        renderWithBoneAndRenderType(model, animatable, partialTick, renderType, poseStack, bufferSource, i, vertexConsumer, i2, i3, f2, f3, f4, f5, animatable.getTextureLocation());
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable SubmitNodeCollector bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5, Identifier textureLocation) {
        SubmitNodeCollector collector = SubmitRenderContext.get();
        if (collector != null && vertexConsumer == null) {
            animatable.resetAnimationState();
            float[] matrixData = Arrays.copyOf(model.getMatrixData(), model.getMatrixData().length);
            float[] absPivotData = Arrays.copyOf(model.getAbsPivotData(), model.getAbsPivotData().length);
            boolean previewContext =
                    ModelPreviewRenderer.isPreview() || ModelPreviewRenderer.isExtraPlayer();
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                    NativeModelRenderer.renderMesh(
                            buffer, pose, model.getGeoModel(), matrixData, absPivotData,
                            i, 0, i2, i3, f2, f3, f4, f5, textureLocation,
                            false, previewContext));
            setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
            return;
        }
        if (vertexConsumer == null) {
            return;
        }
        animatable.resetAnimationState();
        boolean allowDirectGpuRenderer = !(animatable instanceof GeckoVehicleEntity) && !(animatable instanceof GeckoProjectileEntity);
        NativeModelRenderer.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5, textureLocation, allowDirectGpuRenderer);
        setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
    }

    default void renderEarly(T animatable, PoseStack poseStack, float partialTick,
                             @Nullable SubmitNodeCollector bufferSource, @Nullable VertexConsumer buffer, int packedLight,
                             int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL) {
            float width = animatable.getWidthScale();
            float height = animatable.getHeightScale();
            poseStack.scale(width, height, width);
        }
    }

    default void renderLate(T animatable, PoseStack poseStack, float partialTick, SubmitNodeCollector bufferSource,
                            @Nullable VertexConsumer buffer, int packedLight, int packedOverlayIn, float red, float green, float blue,
                            float alpha) {
    }

    @Nullable
    default RenderType getRenderType(Identifier Identifier, boolean z, boolean z2, boolean z3) {
        if (z) {
            if (z3) {
                return RenderTypes.entityTranslucent(Identifier);
            }
            return RenderTypes.entityCutout(Identifier);
        }
        if (z2) {
            return RenderTypes.outline(Identifier);
        }
        return null;
    }

    default Color getRenderColor(T animatable, float partialTick, PoseStack poseStack, @Nullable SubmitNodeCollector bufferSource, @Nullable VertexConsumer buffer, int packedLight) {
        return Color.WHITE;
    }

    @NotNull
    default IRenderCycle getCurrentModelRenderCycle() {
        return EModelRenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(IRenderCycle cycle) {
    }
}
