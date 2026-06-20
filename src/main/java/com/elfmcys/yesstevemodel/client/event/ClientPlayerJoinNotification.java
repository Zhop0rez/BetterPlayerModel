package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientMessages;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.mixin.client.MinecraftAccessor;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import dev.ysm.architectury.event.events.client.ClientPlayerEvent;
import net.minecraft.client.Minecraft;
import java.util.concurrent.Executor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class ClientPlayerJoinNotification {

    private static boolean notified = false;

    private ClientPlayerJoinNotification() {
    }
    private static int pendingTicks = -1;

    public static void register() {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(ClientPlayerJoinNotification::onPlayerJoin);
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(ClientPlayerJoinNotification::onPlayerQuit);
    }

    private static void onPlayerJoin(LocalPlayer player) {
        if (notified) {
            return;
        }
        ClientModelManager.runPendingModelCallback();
        notified = true;
        if (!YesSteveModel.isAvailable()) {
            ClientMessages.sendUnavailableMessage();
            return;
        }
        if (((MinecraftAccessor) Minecraft.getInstance()).ysm$isLocalServer()) {
            return;
        }
        pendingTicks = 1200; // 60 seconds * 20 ticks
    }

    private static void onPlayerQuit(LocalPlayer player) {
        pendingTicks = -1;
        if (notified) {
            notified = false;
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            ClientModelManager.resetSync();
        }
        com.elfmcys.yesstevemodel.capability.PlayerCapability.clearAll();
    }

    public static void tick() {
        if (pendingTicks > 0) {
            pendingTicks--;
            if (pendingTicks == 0) {
                LocalPlayer localPlayer = Minecraft.getInstance().player;
                if (localPlayer != null && localPlayer.connection.isAcceptingMessages() && !NetworkHandler.isConnectionValid(localPlayer.connection.getConnection())) {
                    localPlayer.sendSystemMessage(Component.translatable("message.better_player_model.client.server_not_found"));
                }
            }
        }
    }
}


