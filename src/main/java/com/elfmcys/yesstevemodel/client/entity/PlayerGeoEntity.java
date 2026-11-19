package com.elfmcys.yesstevemodel.client.entity;

import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.client.animation.condition.ArmorConditions;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public class PlayerGeoEntity extends GeoEntity<LocalPlayer> {

    private final PlayerCapability playerCapability;

    public PlayerGeoEntity(LocalPlayer player, PlayerCapability capability) {
        super(player, false);
        this.playerCapability = capability;
        setModelId(capability.getModelId());
    }

    @Override
    public void registerAnimationControllers() {
        getModelAssembly().getAnimationBundle().getArmControllerInstaller().accept(this);
    }

    public PlayerCapability getPlayerCapability() {
        return this.playerCapability;
    }

    @Override
    public boolean shouldSkipAnimation(AnimationEvent<?> event) {
        return true;
    }

    @Override
    public void tickModel() {
        // A lazy model keeps the same assembly object while it swaps the bundled
        // default fallback for the real model.  The capability rebuilds its render
        // shape at that moment; follow it, otherwise the arms keep the fallback's
        // geometry/controllers but draw the new model's texture.
        if (this.playerCapability.getModelAssembly() != getModelAssembly()
                || this.playerCapability.getRenderShape() != getRenderShape()
                || !java.util.Objects.equals(this.playerCapability.getModelId(), getModelId())) {
            setModelId(this.playerCapability.getModelId());
        }
    }

    @Override
    @Nullable
    public GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        return this.playerCapability.getRenderShape();
    }

    @Override
    @Nullable
    public AnimationController getAnimationEntries(String str) {
        return getModelAssembly().getAnimationBundle().getAnimationEntries().get(str);
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return this.playerCapability.getTextureLocation();
    }

    @Override
    public float getHeightScale() {
        return getModelAssembly().getModelData().getModelProperties().getHeightScale();
    }

    @Override
    public float getWidthScale() {
        return getModelAssembly().getModelData().getModelProperties().getWidthScale();
    }

    @Override
    @Nullable
    public Animation getAnimation(String str) {
        return getModelAssembly().getAnimationBundle().getArmAnimations().get(str);
    }

    public ArmorConditions getArmModelProcessor() {
        return getModelAssembly().getAnimationBundle().getModelProcessor();
    }

    @Override
    public GeoModel getAnimationProcessor() {
        return getModelAssembly().getAnimationBundle().getArmModel();
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        getEvaluationContext().setRoamingProperties(this.playerCapability.getServerVarContainer());
    }
}
