package com.elfmcys.yesstevemodel.client.input;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import rip.ysm.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.util.InputUtil;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import rip.ysm.api.PlatformAPI;
import rip.ysm.api.client.KeyMappingFactory;
import rip.ysm.gui.ModernAnimationRouletteScreen;

public final class AnimationRouletteKey {

    public static final KeyMapping KEY_ROULETTE = KeyMappingFactory.createInGameNone("key.better_player_model.animation_roulette.desc", InputConstants.Type.KEYSYM, 90, "key.category.better_player_model");

    public static final KeyMapping KEY_LOCK = KeyMappingFactory.createInGameAlt("key.better_player_model.lock_roulette.desc", InputConstants.Type.KEYSYM, 76, "key.category.better_player_model");

    private AnimationRouletteKey() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        ClientRawInputEvent.KEY_PRESSED.register((client, action, event) -> {
            int keyCode = event.key();
            int scanCode = event.scancode();
            if (YesSteveModel.isAvailable() && InputUtil.isPlayerReady() && action == 1 && InputUtil.isKeyPressed(keyCode, scanCode, KEY_ROULETTE)) {
                if (TouhouLittleMaidCompat.isMaidChatAvailable()) {
                    TouhouLittleMaidCompat.openMaidChat();
                    return EventResult.interruptFalse();
                } else if (Minecraft.getInstance().player != null) {
                    PlayerCapability.get(Minecraft.getInstance().player).ifPresent(cap -> {
                        String modelId = cap.getModelId();
                        ModelAssembly modelAssembly = cap.getModelAssembly();
                        if (modelAssembly != null && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                            if (Minecraft.getInstance().screen == null) {
                                Minecraft.getInstance().setScreen(new ModernAnimationRouletteScreen(modelId, modelAssembly, cap));
                            } else if (Minecraft.getInstance().screen instanceof ModernAnimationRouletteScreen) {
                                Minecraft.getInstance().setScreen(null);
                            }
                        }
                    });
                }
                return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
    }
}
