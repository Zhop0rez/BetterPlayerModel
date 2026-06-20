package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import rip.ysm.gui.ModernAnimationRouletteScreen;

import java.util.List;

public class PauseScreenButtonBuilder {
    public static boolean isServerConnected() {
        return YesSteveModel.isOnAndroid();
    }

    @Nullable
    public static List<Button> createButtons(PauseScreen pauseScreen) {
        if (isServerConnected()) {
            Minecraft minecraft = Minecraft.getInstance();
            Button buttonBuild = Button.builder(Component.translatable("gui.better_player_model.skin"), button -> {
                com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(minecraft, new PlayerModelScreen());
            }).bounds((pauseScreen.width / 2) - 94, pauseScreen.height - 35, 138, 30).build();
            buttonBuild.setTooltip(Tooltip.create(Component.translatable("key.better_player_model.player_model.desc")));
            Button buttonBuild2 = Button.builder(Component.translatable("gui.better_player_model.pause.render_config"), button2 -> {
                com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(minecraft, new ExtraPlayerRenderScreen());
            }).bounds((pauseScreen.width / 2) - 145, pauseScreen.height - 35, 50, 30).build();
            buttonBuild2.setTooltip(Tooltip.create(Component.translatable("key.better_player_model.open_extra_player_render.desc")));
            Button buttonBuild3 = Button.builder(Component.translatable("gui.better_player_model.pause.roulette"), button3 -> {
                if (minecraft.player != null) {
                    PlayerCapability.get(minecraft.player).ifPresent(cap -> {
                        String str = cap.getModelId();
                        ModelAssembly modelAssembly = cap.getModelAssembly();
                        if (modelAssembly != null && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                            com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(minecraft, new ModernAnimationRouletteScreen(str, modelAssembly, cap));
                        }
                    });
                }
            }).bounds((pauseScreen.width / 2) + 45, pauseScreen.height - 35, 50, 30).build();
            buttonBuild3.setTooltip(Tooltip.create(Component.translatable("key.better_player_model.animation_roulette.desc")));
            Button buttonBuild4 = Button.builder(Component.literal("YSM"), button4 -> {
                com.elfmcys.yesstevemodel.client.ScreenFixer.setScreen(minecraft, new ExtraPlayerConfigScreen(pauseScreen));
            }).bounds((pauseScreen.width / 2) + 96, pauseScreen.height - 35, 50, 30).build();
            buttonBuild4.setTooltip(Tooltip.create(Component.translatable("gui.better_player_model.config")));
            return List.of(buttonBuild, buttonBuild2, buttonBuild3, buttonBuild4);
        }
        return null;
    }
}

