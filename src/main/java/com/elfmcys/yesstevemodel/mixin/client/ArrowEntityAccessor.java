package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;

@Mixin(AbstractArrow.class)
public abstract class ArrowEntityAccessor {

    @Shadow
    protected abstract ItemStack getPickupItem();

    @Unique
    public Set<MobEffectInstance> getEffects() {
        ItemStack pickup = getPickupItem();
        return new HashSet<>(PotionUtils.getMobEffects(pickup));
    }
}
