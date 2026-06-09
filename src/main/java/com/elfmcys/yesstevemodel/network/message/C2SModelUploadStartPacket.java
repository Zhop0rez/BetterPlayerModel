package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import rip.ysm.api.network.PacketContext;

public record C2SModelUploadStartPacket(String modelId, String fileName, int totalBytes, String sha256) {

    private static final int MAX_MODEL_ID_LENGTH = 256;
    private static final int MAX_FILE_NAME_LENGTH = 256;
    private static final int SHA_256_LENGTH = 64;

    public static void encode(C2SModelUploadStartPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.modelId);
        buf.writeUtf(message.fileName == null ? "" : message.fileName);
        buf.writeVarInt(message.totalBytes);
        buf.writeUtf(message.sha256);
    }

    public static C2SModelUploadStartPacket decode(FriendlyByteBuf buf) {
        return new C2SModelUploadStartPacket(buf.readUtf(MAX_MODEL_ID_LENGTH), buf.readUtf(MAX_FILE_NAME_LENGTH), buf.readVarInt(), buf.readUtf(SHA_256_LENGTH));
    }

    public static void handle(C2SModelUploadStartPacket message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null) {
            ServerPlayer sender = ctx.getSender();
            ctx.enqueueWork(() -> {
                ServerModelManager.UploadStartResult result = ServerModelManager.beginModelUpload(sender, message.modelId, message.fileName, message.totalBytes, message.sha256);
                NetworkHandler.sendToClientPlayer(new S2CModelUploadStartPacket(result.uploadId(), result.status(), result.chunkSize(), result.maxTotalBytes(), result.chunksPerTick(), result.message()), sender);
            });
        }
    }
}
