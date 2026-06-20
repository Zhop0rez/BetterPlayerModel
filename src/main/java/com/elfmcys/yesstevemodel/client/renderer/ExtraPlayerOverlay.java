package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.client.gui.ExtraPlayerRenderScreen;
import com.elfmcys.yesstevemodel.config.ExtraPlayerRenderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import rip.ysm.api.client.HudOverlay;

public class ExtraPlayerOverlay implements HudOverlay {
    @Override
    public void render(GuiGraphicsExtractor GuiGraphicsExtractor, Font font, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft;
        LocalPlayer localPlayer;
        if (ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER.get() || (localPlayer = (minecraft = Minecraft.getInstance()).player) == null || (com.elfmcys.yesstevemodel.client.ScreenFixer.getScreen(minecraft) instanceof ExtraPlayerRenderScreen)) {
            return;
        }
        ModelPreviewRenderer.renderPlayerOverlay(GuiGraphicsExtractor, localPlayer, ExtraPlayerRenderConfig.PLAYER_POS_X.get(), ExtraPlayerRenderConfig.PLAYER_POS_Y.get(), ExtraPlayerRenderConfig.PLAYER_SCALE.get().floatValue(), ExtraPlayerRenderConfig.PLAYER_YAW_OFFSET.get().floatValue(), -500, partialTick, false);
    }
}

