package net.fabricmc.fabric.api.client.rendering.v1;

import net.minecraft.client.gui.GuiGraphics;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface HudRenderCallback {
    Event<HudRenderCallback> EVENT = EventFactory.createArrayBacked(HudRenderCallback.class, listeners -> (graphics, tickDelta) -> {
        for (HudRenderCallback listener : listeners) {
            listener.onHudRender(graphics, tickDelta);
        }
    });
    void onHudRender(GuiGraphics guiGraphics, TickDeltaCounter tickDelta);
}
