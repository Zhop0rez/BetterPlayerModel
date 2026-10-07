package com.elfmcys.yesstevemodel.model.format;

import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public class ServerModelData {
    // The model's directory name
    private final String modelId;
    private final ServerAnimationInfo serverAnimationInfo;
    private final Set<Identifier> entityTypes = new HashSet<>();
    private final Set<Identifier> excludedEntityTypes = new HashSet<>();
    private final ServerModelInfo info;
    private final boolean isCustomSkinModel; // Possibly deprecated
    private final boolean isAuth; // In the auth folder and is_free is false

    // Projectile, e.g. arrow/trident/etc.; textures are under textures minecraft:arrow ....
    private Object[] projectiles;
    // Mount, e.g. boat/minecart/horse -> minecraft:horse ....
    private Object[] vehicles;

    public ServerModelData(String modelId, ServerAnimationInfo serverAnimationInfo, Object[] projectiles, Object[] vehicles, ServerModelInfo info, boolean encrypted, boolean isAuth) {
        this.modelId = modelId;
        this.serverAnimationInfo = serverAnimationInfo;
        this.projectiles = projectiles;
        this.vehicles = vehicles;
        this.info = info;
        this.isCustomSkinModel = encrypted;
        this.isAuth = isAuth;
    }

    public String getModelId() {
        return this.modelId;
    }

    public Object[] getProjectiles() {
        return this.projectiles;
    }

    public Object[] getVehicles() {
        return this.vehicles;
    }

    public ServerAnimationInfo getModelInfo() {
        return this.serverAnimationInfo;
    }

    public Set<Identifier> getEntityTypes() {
        for (Object obj : this.projectiles) {
            this.entityTypes.addAll(FileTypeUtil.resolveEntityTypes((String[]) obj));
            this.projectiles = null;
        }
        return this.entityTypes;
    }

    public Set<Identifier> getExcludedEntityTypes() {
        for (Object obj : this.vehicles) {
            this.excludedEntityTypes.addAll(FileTypeUtil.resolveEntityTypes((String[]) obj));
            this.vehicles = null;
        }
        return this.excludedEntityTypes;
    }

    public ServerModelInfo getLoadedModelData() {
        return this.info;
    }

    public boolean isCustomSkinModel() {
        return this.isCustomSkinModel;
    }

    public boolean isAuth() {
        return this.isAuth;
    }
}
