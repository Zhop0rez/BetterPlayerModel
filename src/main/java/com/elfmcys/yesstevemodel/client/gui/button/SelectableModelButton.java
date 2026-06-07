package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.InputWithModifiers;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SelectableModelButton extends ModelButton {
    private final String modelId;
    private final Supplier<Boolean> selected;
    private final Consumer<String> toggle;

    public SelectableModelButton(int x, int y, boolean isAuthLocked, PlayerPreviewEntity playerPreviewEntity, ModelAssembly textureRegistry, String modelId, Supplier<Boolean> selected, Consumer<String> toggle) {
        super(x, y, isAuthLocked, playerPreviewEntity, textureRegistry);
        this.modelId = modelId;
        this.selected = selected;
        this.toggle = toggle;
    }

    @Override
    public void onPress(InputWithModifiers modifiers) {
        this.toggle.accept(this.modelId);
    }

    @Override
    protected void renderContents(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        super.renderContents(extractor, mouseX, mouseY, partialTick);
        if (this.selected.get()) {
            extractor.fillGradient(getX(), getY(), getX() + getWidth(), getY() + 3, 0xFF55FF88, 0xFF55FF88);
            extractor.fillGradient(getX(), getY(), getX() + 3, getY() + getHeight(), 0xFF55FF88, 0xFF55FF88);
            extractor.fillGradient(getX() + getWidth() - 3, getY(), getX() + getWidth(), getY() + getHeight(), 0xFF55FF88, 0xFF55FF88);
            extractor.fillGradient(getX(), getY() + getHeight() - 3, getX() + getWidth(), getY() + getHeight(), 0xFF55FF88, 0xFF55FF88);
        }
    }
}
