package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.client.gui.button.FlatColorButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class CategoryDeleteConfirmScreen extends Screen {
    private final Screen parent;
    private final String category;
    private final Consumer<Boolean> onConfirm;
    private int guiLeft;
    private int guiTop;

    public CategoryDeleteConfirmScreen(Screen parent, String category, Consumer<Boolean> onConfirm) {
        super(Component.translatable("gui.better_player_model.model_select.delete_category_confirm", category));
        this.parent = parent;
        this.category = category;
        this.onConfirm = onConfirm;
    }

    @Override
    public void init() {
        clearWidgets();
        this.guiLeft = (this.width - 300) / 2;
        this.guiTop = (this.height - 100) / 2;
        addRenderableWidget(new FlatColorButton(this.guiLeft + 20, this.guiTop + 64, 78, 18, Component.translatable("gui.better_player_model.model_select.delete_category_keep_models"), button -> confirm(false)));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 111, this.guiTop + 64, 78, 18, Component.translatable("gui.better_player_model.model_select.delete_category_with_models"), button -> confirm(true)));
        addRenderableWidget(new FlatColorButton(this.guiLeft + 202, this.guiTop + 64, 78, 18, Component.translatable("gui.better_player_model.config.cancel"), button -> Minecraft.getInstance().setScreen(this.parent)));
    }

    private void confirm(boolean deleteModels) {
        this.onConfirm.accept(deleteModels);
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(extractor);
        extractor.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + 300, this.guiTop + 100, -14540254, -14540254);
        extractor.drawString(this.font, Component.translatable("gui.better_player_model.model_select.delete_category_confirm", this.category), this.guiLeft + 20, this.guiTop + 16, 0xFFF3F3E0);
        extractor.drawString(this.font, Component.translatable("gui.better_player_model.model_select.delete_category_hint"), this.guiLeft + 20, this.guiTop + 34, 0xFFAAAAAA);
        super.render(extractor, mouseX, mouseY, partialTick);
    }
}

