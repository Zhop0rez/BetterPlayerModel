package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.mixin.client.MinecraftAccessor;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(EnvType.CLIENT)
public final class ClientNetworkState {

    private ClientNetworkState() {
    }

    public static boolean isConnectedToYsmServer() {
        ClientPacketListener connection = ((MinecraftAccessor) Minecraft.getInstance()).ysm$getConnection();
        return connection != null && NetworkHandler.isConnectionValid(connection.getConnection());
    }
}
