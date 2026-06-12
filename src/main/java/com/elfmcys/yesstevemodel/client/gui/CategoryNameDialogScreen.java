package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class CategoryNameDialogScreen extends Screen {
    private final Screen parent;
    private final Component prompt;
    private final Consumer<String> onConfirm;
    private final String initialValue;
    private EditBox nameBox;
    private int guiLeft;
    private int guiTop;

    public CategoryNameDialogScreen(Screen parent, Component prompt, String initialValue, Consumer<String> onConfirm) {
        super(prompt);
        this.parent = parent;
        this.prompt = prompt;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.onConfirm = onConfirm;
    }

    @Override
    public void init() {
        clearWidgets();
        this.guiLeft = (this.width - 260) / 2;
        this.guiTop = (this.height - 92) / 2;
        this.nameBox = new EditBox(this.font, this.guiLeft + 20, this.guiTop + 32, 220, 18, this.prompt);
        this.nameBox.setValue(this.initialValue);
        this.nameBox.setFocused(true);
        this.nameBox.setCursorPosition(this.nameBox.getValue().length());
        addWidget(this.nameBox);
        addRenderableWidget(new FlatColorButton(this.guiLeft + 52, this.guiTop + 62, 64, 18, Component.translatable("gui.better_player_model.model_select.confirm"), button -> confirm()));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 144, this.guiTop + 62, 64, 18, Component.translatable("gui.better_player_model.config.cancel"), button -> Minecraft.getInstance().setScreen(this.parent)));
    }

    private void confirm() {
        this.onConfirm.accept(this.nameBox.getValue());
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        renderBackground(extractor);
        extractor.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + 260, this.guiTop + 92, -14540254, -14540254);
        extractor.drawString(this.font, this.prompt, this.guiLeft + 20, this.guiTop + 14, 0xFFF3F3E0);
        this.nameBox.render(extractor, mouseX, mouseY, partialTick);
        super.render(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.nameBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return this.nameBox.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.nameBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.nameBox);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
