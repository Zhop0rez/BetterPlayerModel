package dev.ysm.architectury.event.events.client;

import dev.ysm.architectury.event.Event;
import dev.ysm.architectury.event.EventResult;
import net.minecraft.client.Minecraft;

/**
 * Stub for dev.ysm.architectury.event.events.client.ClientRawInputEvent.
 */
public class ClientRawInputEvent {
    public static final Event<KeyPressed> KEY_PRESSED = new Event<>();
    public static final Event<MouseClickedPre> MOUSE_CLICKED_PRE = new Event<>();

    @FunctionalInterface
    public interface KeyPressed {
        EventResult keyPressed(Minecraft client, int action, net.minecraft.client.input.KeyEvent event);
    }

    @FunctionalInterface
    public interface MouseClickedPre {
        EventResult mouseClicked(Minecraft client, net.minecraft.client.input.MouseButtonInfo info, int action);
    }
}
