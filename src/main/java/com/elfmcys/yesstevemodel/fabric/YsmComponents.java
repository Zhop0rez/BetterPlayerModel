package com.elfmcys.yesstevemodel.fabric;

import com.elfmcys.yesstevemodel.capability.fabric.*;
import net.minecraft.world.entity.Entity;

public final class YsmComponents {
    public static final ComponentKey<StarModelsComponent> STAR_MODELS = new ComponentKey<>(e -> ((YsmEntityExtension)e).ysm$getStarModels());
    public static final ComponentKey<AuthModelsComponent> AUTH_MODELS = new ComponentKey<>(e -> ((YsmEntityExtension)e).ysm$getAuthModels());
    public static final ComponentKey<ModelInfoComponent> MODEL_INFO = new ComponentKey<>(e -> ((YsmEntityExtension)e).ysm$getModelInfo());
    public static final ComponentKey<ProjectileModelComponent> PROJECTILE_MODEL = new ComponentKey<>(e -> ((YsmEntityExtension)e).ysm$getProjectileModel());
    public static final ComponentKey<VehicleModelComponent> VEHICLE_MODEL = new ComponentKey<>(e -> ((YsmEntityExtension)e).ysm$getVehicleModel());

    public static class ComponentKey<T> {
        private final java.util.function.Function<Entity, T> getter;
        public ComponentKey(java.util.function.Function<Entity, T> getter) { this.getter = getter; }
        public T getNullable(Entity provider) { return getter.apply(provider); }
    }
}
