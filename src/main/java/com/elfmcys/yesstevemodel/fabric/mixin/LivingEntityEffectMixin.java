package com.elfmcys.yesstevemodel.fabric.mixin;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {

    @Inject(method = "onEffectRemoved", at = @At("HEAD"))
    private void ysm$onEffectsRemoved(MobEffectInstance instance, CallbackInfo ci) {
        // ClientModelManager.onEffectRemoved((LivingEntity) (Object) this, instance);
    }
}

