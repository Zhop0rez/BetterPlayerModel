package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import net.minecraft.network.FriendlyByteBuf;
import rip.ysm.api.network.PacketContext;

import java.nio.ByteBuffer;

public class C2SModelSyncPayload {

    private static final int MAX_SYNC_PAYLOAD_BYTES = 1_048_576;

    private final ByteBuffer data;

    public C2SModelSyncPayload(ByteBuffer data) {
        this.data = data;
    }

    public static void encode(C2SModelSyncPayload message, FriendlyByteBuf buf) {
        buf.writeBytes(message.data);
    }

    public static C2SModelSyncPayload decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() > MAX_SYNC_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("C2S model sync payload too large: " + buf.readableBytes());
        }
        ByteBuffer data = ByteBuffer.allocateDirect(buf.readableBytes());
        buf.readBytes(data);
        return new C2SModelSyncPayload(data);
    }

    public static void handle(C2SModelSyncPayload message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null) {
            ServerModelManager.nativeSendModelData(ctx.getSender().getUUID(), message.data);
        }
    }
}
