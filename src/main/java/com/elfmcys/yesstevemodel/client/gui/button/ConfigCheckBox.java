package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.ISpecialWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;


import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ConfigCheckBox extends AbstractWidget implements ISpecialWidget {

    private static final Identifier location = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/roulette.png");

    private final Consumer<Boolean> consumer2;

    private final Component component2;

    private boolean isStateTriggered;

    public ConfigCheckBox(int x, int y, int width, Component component, Consumer<Boolean> consumer) {
        super(x, y, width, 12, component);
        this.component2 = component;
        this.consumer2 = consumer;
    }

    public ConfigCheckBox(int x, int y, Component component, Consumer<Boolean> consumer) {
        this(x, y, 115, component, consumer);
    }

    public void extractWidgetRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor GuiGraphicsExtractor = extractor;
        int uOffset = isStateTriggered ? 128 : 0;
        int vOffset = isHovered() ? 12 : 0;
        int boxSize = 12;
        GuiGraphicsExtractor.blit(location, getX(), getY(), getX() + boxSize, getY() + boxSize, uOffset / 256.0f, (uOffset + boxSize) / 256.0f, vOffset / 24.0f, (vOffset + boxSize) / 24.0f);
        GuiGraphicsExtractor.text(Minecraft.getInstance().font, this.component2, getX() + boxSize + 2, getY() + 2, -1, false);
    }

    public void onClick(MouseButtonEvent event, boolean flag) {
        this.isStateTriggered = !this.isStateTriggered;
        this.consumer2.accept(Boolean.valueOf(this.isStateTriggered));
    }

    public void setStateTriggered(boolean stateTriggered) {
        this.isStateTriggered = stateTriggered;
    }

    public boolean isStateTriggered() {
        return this.isStateTriggered;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}

