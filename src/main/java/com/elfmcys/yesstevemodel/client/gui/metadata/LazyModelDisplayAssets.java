package com.elfmcys.yesstevemodel.client.gui.metadata;

import com.elfmcys.yesstevemodel.client.model.LazyModelAssembly;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.Map;

public class LazyModelDisplayAssets extends ModelDisplayAssets {
    private final LazyModelAssembly parent;
    private final boolean initialAuth;
    private boolean isAuthOverridden;
    private boolean overriddenAuthValue;

    public LazyModelDisplayAssets(LazyModelAssembly parent, boolean initialAuth) {
        super(null, initialAuth, null, null);
        this.parent = parent;
        this.initialAuth = initialAuth;
    }

    @Override
    public boolean isAuthModel() {
        if (isAuthOverridden) {
            return overriddenAuthValue;
        }
        return parent.isResolved() ? parent.resolve().getTextureRegistry().isAuthModel() : initialAuth;
    }

    @Override
    public void setAuthModel(boolean isModelReady) {
        this.isAuthOverridden = true;
        this.overriddenAuthValue = isModelReady;
        if (parent.isResolved()) {
            parent.resolve().getTextureRegistry().setAuthModel(isModelReady);
        }
    }

    @Override
    public String getSelectedTexture() {
        return parent.resolve().getTextureRegistry().getSelectedTexture();
    }

    @Override
    public Map<String, OuterFileTexture> getAuthorAvatars() {
        return parent.resolve().getTextureRegistry().getAuthorAvatars();
    }

    @Override
    public AbstractTexture getGuiForeground() {
        return parent.resolve().getTextureRegistry().getGuiForeground();
    }

    @Override
    public AbstractTexture getGuiBackground() {
        return parent.resolve().getTextureRegistry().getGuiBackground();
    }
}
