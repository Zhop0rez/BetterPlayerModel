package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.world.entity.projectile.ThrowableProjectile.ThrowableProjectile;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ThrowableProjectile.class})
interface ThrowableProjectileAccessor {
    @Invoker("getDefaultItem")
    Item invokeGetDefaultItem();
}


