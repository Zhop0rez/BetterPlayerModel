package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.client.input.AnimationRouletteKey;
import com.elfmcys.yesstevemodel.client.input.DebugAnimationKey;
import com.elfmcys.yesstevemodel.client.input.ExtraAnimationKey;
import com.elfmcys.yesstevemodel.client.input.ExtraPlayerRenderKey;
import com.elfmcys.yesstevemodel.client.input.InputStateKey;
import com.elfmcys.yesstevemodel.client.input.PlayerModelToggleKey;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.event.EntityJoinCallbackEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.world.entity.player.Player;
import com.elfmcys.yesstevemodel.capability.fabric.client.PlayerCapabilityClientStore;

@Environment(EnvType.CLIENT)
public final class ClientEventBootstrap {

    private ClientEventBootstrap() {
    }

    public static void register() {
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Player player) {
                PlayerCapabilityClientStore.remove(player);
            }
        });

        EntityJoinCallbackEvent.register();

        ClientSetupEvent.register();
        ClientTickEvent.register();
        ClientPlayerJoinNotification.register();
        ClientPlayerCloneEvent.register();
        AnimationLockEvent.register();
        PlayerSkinTextureManager.register();
        RendererManager.register();
        PlayerModelToggleKey.register();
        AnimationRouletteKey.register();
        DebugAnimationKey.register();
        ExtraPlayerRenderKey.register();
        ExtraAnimationKey.register();
        InputStateKey.register();
    }
}
