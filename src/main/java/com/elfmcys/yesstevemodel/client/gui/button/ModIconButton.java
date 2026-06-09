package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SSetStarModelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ModIconButton extends FlatColorButton {

    private static final ResourceLocation ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    public ModIconButton(int x, int y) {
        super(x, y, 20, 20, Component.empty(), button -> {
        });
    }

    @Override
    protected void renderWidget(GuiGraphics extractor, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(extractor, mouseX, mouseY, partialTick);
        GuiGraphics guiGraphics = extractor;
        int iconOffsetX = (this.width - 16) / 2;
        int iconOffsetY = (this.height - 16) / 2;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                    if (cap2.containsModel(cap.getModelId())) {
                        int x = getX() + iconOffsetX;
                        int y = getY() + iconOffsetY;
                        guiGraphics.blit(ICON_TEXTURE, x, y, 16.0f, 0.0f, 16, 16, 256, 256);
                    } else {
                        int x = getX() + iconOffsetX;
                        int y = getY() + iconOffsetY;
                        guiGraphics.blit(ICON_TEXTURE, x, y, 0.0f, 0.0f, 16, 16, 256, 256);
                    }
                });
            });
        }
    }

    @Override
    public void onPress() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                    String str = cap.getModelId();
                    if (cap2.containsModel(str)) {
                        cap2.removeModel(str);
                        NetworkHandler.sendToServer(C2SSetStarModelPacket.remove(str));
                    } else {
                        cap2.addModel(str);
                        NetworkHandler.sendToServer(C2SSetStarModelPacket.add(str));
                    }
                });
            });
        }
    }
}



