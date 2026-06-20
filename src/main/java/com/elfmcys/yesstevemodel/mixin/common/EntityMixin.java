package com.elfmcys.yesstevemodel.mixin.common;

import com.elfmcys.yesstevemodel.capability.fabric.*;
import com.elfmcys.yesstevemodel.fabric.YsmEntityExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin implements YsmEntityExtension {
    @Unique private final StarModelsComponent ysm$starModels = new StarModelsComponent();
    @Unique private final AuthModelsComponent ysm$authModels = new AuthModelsComponent();
    @Unique private final ModelInfoComponent ysm$modelInfo = new ModelInfoComponent();
    @Unique private final ProjectileModelComponent ysm$projectileModel = new ProjectileModelComponent();
    @Unique private final VehicleModelComponent ysm$vehicleModel = new VehicleModelComponent();

    @Override public StarModelsComponent ysm$getStarModels() { return ysm$starModels; }
    @Override public AuthModelsComponent ysm$getAuthModels() { return ysm$authModels; }
    @Override public ModelInfoComponent ysm$getModelInfo() { return ysm$modelInfo; }
    @Override public ProjectileModelComponent ysm$getProjectileModel() { return ysm$projectileModel; }
    @Override public VehicleModelComponent ysm$getVehicleModel() { return ysm$vehicleModel; }

    @Inject(remap = false, method = "saveWithoutId", at = @At("RETURN"))
    private void ysm$save(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
        CompoundTag tag = new CompoundTag();
        ysm$starModels.writeToNbt(tag, null);
        ysm$authModels.writeToNbt(tag, null);
        ysm$modelInfo.writeToNbt(tag, null);
        output.store("ysm_data", CompoundTag.CODEC, tag);
    }

    @Inject(remap = false, method = "load", at = @At("RETURN"))
    private void ysm$load(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
        input.read("ysm_data", CompoundTag.CODEC).ifPresent(tag -> {
            ysm$starModels.readFromNbt(tag, null);
            ysm$authModels.readFromNbt(tag, null);
            ysm$modelInfo.readFromNbt(tag, null);
        });
    }
}
