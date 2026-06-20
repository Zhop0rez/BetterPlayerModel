package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import com.google.common.collect.Sets;
import dev.ysm.architectury.injectables.annotations.PlatformOnly;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import rip.ysm.api.network.PacketContext;

import java.util.HashSet;
import java.util.Set;

public class S2CSyncAuthModelsPacket {
    private static final int MAX_MODEL_COUNT = 4096;
    private static final int MAX_MODEL_ID_LENGTH = 256;

    private final Set<String> authModels;

    public S2CSyncAuthModelsPacket(Set<String> authModels) {
        this.authModels = authModels;
    }

    public static void encode(S2CSyncAuthModelsPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.authModels.size());
        for (String modelId : message.authModels) {
            buf.writeUtf(modelId);
        }
    }

    public static S2CSyncAuthModelsPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_MODEL_COUNT) {
            throw new IllegalArgumentException("Invalid auth model count: " + size);
        }
        HashSet<String> tmp = Sets.newHashSet();
        for (int i = 0; i < size; i++) {
            tmp.add(buf.readUtf(MAX_MODEL_ID_LENGTH));
        }
        return new S2CSyncAuthModelsPacket(tmp);
    }

    public static void handle(S2CSyncAuthModelsPacket message, PacketContext ctx) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> handleCapability(message));
        }
    }


    @Environment(EnvType.CLIENT)
    public static void handleCapability(S2CSyncAuthModelsPacket message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            AuthModelsCapability.get(minecraft.player).ifPresent(cap -> {
                cap.setAuthModels(message.authModels);
            });
        }
    }
}
