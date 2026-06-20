import os
import re

base_dir = r"E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java\com\elfmcys\yesstevemodel\capability\fabric"

components = [
    "AuthModelsComponent",
    "ModelInfoComponent",
    "ProjectileModelComponent",
    "StarModelsComponent",
    "VehicleModelComponent"
]

for comp in components:
    filepath = os.path.join(base_dir, f"{comp}.java")
    if not os.path.exists(filepath): continue
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Remove import org.ladysnake...
    content = re.sub(r'import org\.ladysnake[^\n]+\n', '', content)
    # Remove implements Component
    content = content.replace(" implements Component", "")
    # Remove @Override writeData / readData completely
    content = re.sub(r'@Override\s+public void writeData\(.*?\}\n', '', content, flags=re.DOTALL)
    content = re.sub(r'@Override\s+public void readData\(.*?\}\n', '', content, flags=re.DOTALL)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# Now modify YsmComponents.java
ysm_comp_path = r"E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java\com\elfmcys\yesstevemodel\fabric\YsmComponents.java"
ysm_comp_code = """package com.elfmcys.yesstevemodel.fabric;

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
"""
with open(ysm_comp_path, 'w', encoding='utf-8') as f:
    f.write(ysm_comp_code)

# Create YsmEntityExtension
ext_code = """package com.elfmcys.yesstevemodel.fabric;
import com.elfmcys.yesstevemodel.capability.fabric.*;
public interface YsmEntityExtension {
    StarModelsComponent ysm$getStarModels();
    AuthModelsComponent ysm$getAuthModels();
    ModelInfoComponent ysm$getModelInfo();
    ProjectileModelComponent ysm$getProjectileModel();
    VehicleModelComponent ysm$getVehicleModel();
}
"""
with open(r"E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java\com\elfmcys\yesstevemodel\fabric\YsmEntityExtension.java", 'w', encoding='utf-8') as f:
    f.write(ext_code)

# Create EntityMixin
mixin_code = """package com.elfmcys.yesstevemodel.mixin.common;

import com.elfmcys.yesstevemodel.capability.fabric.*;
import com.elfmcys.yesstevemodel.fabric.YsmEntityExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void ysm$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        ysm$starModels.writeToNbt(tag, null);
        ysm$authModels.writeToNbt(tag, null);
        ysm$modelInfo.writeToNbt(tag, null);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void ysm$load(CompoundTag tag, CallbackInfo ci) {
        ysm$starModels.readFromNbt(tag, null);
        ysm$authModels.readFromNbt(tag, null);
        ysm$modelInfo.readFromNbt(tag, null);
    }
}
"""
os.makedirs(r"E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java\com\elfmcys\yesstevemodel\mixin\common", exist_ok=True)
with open(r"E:\BPM_Workspace_Fixes\BPM_26.2\src\main\java\com\elfmcys\yesstevemodel\mixin\common\EntityMixin.java", 'w', encoding='utf-8') as f:
    f.write(mixin_code)

print("Finished rewriting CCA!")
