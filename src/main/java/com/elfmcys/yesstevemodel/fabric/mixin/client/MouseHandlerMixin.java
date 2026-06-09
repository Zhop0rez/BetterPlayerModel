package com.elfmcys.yesstevemodel.fabric.mixin.client;

import dev.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"))
    private void ysm$fireMouseClickedPre(long window, int button, int action, int mods, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        ClientRawInputEvent.MOUSE_CLICKED_PRE.invoker().mouseClicked(client, button, action, mods);
    }
}

