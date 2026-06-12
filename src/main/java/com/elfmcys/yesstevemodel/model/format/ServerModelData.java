package com.elfmcys.yesstevemodel.model.format;

import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public class ServerModelData {
    // е¦ЇвЂізЂ·йђЁе‹­жґ°й–·е‹«ж‚•з»‹?
    private final String modelId;
    private final ServerAnimationInfo serverAnimationInfo;
    private final Set<Identifier> entityTypes = new HashSet<>();
    private final Set<Identifier> excludedEntityTypes = new HashSet<>();
    private final ServerModelInfo info;
    private final boolean isCustomSkinModel; // йЌ™о€ќе…ж·‡?
    private final boolean isAuth; // йЌ¦в•ќuthз’©е›ЁжћЎжѕ¶еЏҐз¬–is_freeйђђзЇєalse

    // йЋ·е¬Єзљ йђ—?жёље¬Єо›§з» ?ж¶“е¤Љеј¶йЋґ?ж¶”е¬®о”ЈйђЁ?йЏ‰ж„Їе”±йЌ¦в•°extures minecraft:arrow ....
    private Object[] projectiles;
    // йЌ§ж„°в—Ј жёље¬Єо›§ й‘ё?з»Ђп№ЃзІ– жЈЈ?minecraft:horse ....
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
