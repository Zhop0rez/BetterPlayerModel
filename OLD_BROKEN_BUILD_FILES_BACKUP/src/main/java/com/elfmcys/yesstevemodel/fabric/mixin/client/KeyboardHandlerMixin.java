package com.elfmcys.yesstevemodel.fabric.mixin.client;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void ysm$fireKeyPress(long window, int action, int keyCode, int scanCode, int modifiers, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        EventResult result = ClientRawInputEvent.KEY_PRESSED.invoker().keyPressed(client, action, event);
        if (result.isFalse()) {
            ci.cancel();
        }
    }
}

