package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class IconButton extends FlatColorButton {

    private static final ResourceLocation ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    private final int iconU;

    private final int iconV;

    public IconButton(int x, int y, int width, int height, int iconU, int iconV, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress);
        this.iconU = iconU;
        this.iconV = iconV;
    }

    @Override
    protected void renderWidget(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(extractor, mouseX, mouseY, partialTick);
        GuiGraphics guiGraphics = extractor;
        int iconOffsetX = (this.width - 16) / 2;
        int iconOffsetY = (this.height - 16) / 2;
        int x = getX() + iconOffsetX;
        int y = getY() + iconOffsetY;
        guiGraphics.blit(ICON_TEXTURE, x, y, (float)this.iconU, (float)this.iconV, 16, 16, 256, 256);
    }
}



