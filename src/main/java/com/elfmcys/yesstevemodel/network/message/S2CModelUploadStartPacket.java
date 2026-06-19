package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.client.upload.ModelUploadSession;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.FriendlyByteBuf;
import rip.ysm.api.network.PacketContext;

public record S2CModelUploadStartPacket(long uploadId, byte status, int chunkSize, int maxTotalBytes, int chunksPerTick, String message) {
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int MAX_CHUNK_SIZE = 16_000;
    private static final int MAX_TOTAL_BYTES = 512 * 1024 * 1024;
    private static final int MAX_CHUNKS_PER_TICK = 32;

    public static void encode(S2CModelUploadStartPacket packet, FriendlyByteBuf buf) {
        buf.writeVarLong(packet.uploadId);
        buf.writeByte(packet.status);
        buf.writeVarInt(packet.chunkSize);
        buf.writeVarInt(packet.maxTotalBytes);
        buf.writeVarInt(packet.chunksPerTick);
        buf.writeUtf(packet.message);
    }

    public static S2CModelUploadStartPacket decode(FriendlyByteBuf buf) {
        long uploadId = buf.readVarLong();
        byte status = buf.readByte();
        int chunkSize = buf.readVarInt();
        int maxTotalBytes = buf.readVarInt();
        int chunksPerTick = buf.readVarInt();
        if (status == 0) {
            if (chunkSize < 1 || chunkSize > MAX_CHUNK_SIZE) {
                throw new IllegalArgumentException("Invalid upload chunk size: " + chunkSize);
            }
            if (maxTotalBytes < 1 || maxTotalBytes > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Invalid upload max size: " + maxTotalBytes);
            }
            if (chunksPerTick < 1 || chunksPerTick > MAX_CHUNKS_PER_TICK) {
                throw new IllegalArgumentException("Invalid upload rate: " + chunksPerTick);
            }
        }
        String message = buf.readUtf(MAX_MESSAGE_LENGTH);
        return new S2CModelUploadStartPacket(uploadId, status, chunkSize, maxTotalBytes, chunksPerTick, message);
    }

    public static void handle(S2CModelUploadStartPacket packet, PacketContext ctx) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> handleOnClient(packet));
        }
    }

    @Environment(EnvType.CLIENT)
    private static void handleOnClient(S2CModelUploadStartPacket packet) {
        ModelUploadSession.onStartAck(packet.uploadId, packet.status, packet.chunkSize, packet.maxTotalBytes, packet.chunksPerTick, packet.message);
    }
}

