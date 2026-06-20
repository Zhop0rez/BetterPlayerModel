package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;


public class OpenModelFolderScreen extends Screen {

    private final PlayerModelScreen parentScreen;

    public OpenModelFolderScreen(PlayerModelScreen modelScreen) {
        super(Component.literal("Open Model Folder"));
        this.parentScreen = modelScreen;
    }

    public void init() {
        int x = (this.width - 310) / 2;
        int y = (this.height / 2) + 60;
        clearWidgets();
        addRenderableWidget(Button.builder(Component.translatable("gui.better_player_model.open_model_folder.open"), button -> {
            Util.getPlatform().openFile(ServerModelManager.CUSTOM.toFile());
        }).bounds(x, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.better_player_model.model.return"), button2 -> {
            com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(Minecraft.getInstance(), this.parentScreen);
        }).bounds(x + 160, y, 150, 20).build());
    }

    public void extractRenderState(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor.textWithWordWrap(this.font, Component.translatable("gui.better_player_model.open_model_folder.tips"), (this.width - 400) / 2, (this.height / 2) - 80, 400, 16777215);
        super.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
    }
}

