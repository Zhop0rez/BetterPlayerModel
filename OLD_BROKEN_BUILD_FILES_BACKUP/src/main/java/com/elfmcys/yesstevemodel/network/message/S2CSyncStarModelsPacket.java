package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.google.common.collect.Sets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import rip.ysm.api.network.PacketContext;

import java.util.HashSet;
import java.util.Set;

public class S2CSyncStarModelsPacket {
    private static final int MAX_MODEL_COUNT = 4096;
    private static final int MAX_MODEL_ID_LENGTH = 256;

    private final Set<String> starModels;

    public S2CSyncStarModelsPacket(Set<String> starModels) {
        this.starModels = starModels;
    }

    public static void encode(S2CSyncStarModelsPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.starModels.size());
        for (String starModel : message.starModels) {
            buf.writeUtf(starModel);
        }
    }

    public static S2CSyncStarModelsPacket decode(FriendlyByteBuf buf) {
        int varInt = buf.readVarInt();
        if (varInt < 0 || varInt > MAX_MODEL_COUNT) {
            throw new IllegalArgumentException("Invalid star model count: " + varInt);
        }
        HashSet<String> tmp = Sets.newHashSet();
        for (int i = 0; i < varInt; i++) {
            tmp.add(buf.readUtf(MAX_MODEL_ID_LENGTH));
        }
        return new S2CSyncStarModelsPacket(tmp);
    }

    public static void handle(S2CSyncStarModelsPacket message, PacketContext ctx) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> handleCapability(message));
        }
    }

    @Environment(EnvType.CLIENT)
    public static void handleCapability(S2CSyncStarModelsPacket message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            StarModelsCapability.get(minecraft.player).ifPresent(cap -> cap.setStarModels(message.starModels));
        }
    }
}
