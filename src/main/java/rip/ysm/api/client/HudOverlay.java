package rip.ysm.api.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface HudOverlay {
    void render(GuiGraphicsExtractor GuiGraphicsExtractor, Font font, float partialTick, int screenWidth, int screenHeight);
}

