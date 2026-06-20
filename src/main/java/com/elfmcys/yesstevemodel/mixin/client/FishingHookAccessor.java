package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({FishingHook.class})
public interface FishingHookAccessor {
    @Accessor(remap = false, value = "biting")
    boolean isBiting();

    @Accessor(remap = false, value = "hookedIn")
    Entity getHookedIn();
}
