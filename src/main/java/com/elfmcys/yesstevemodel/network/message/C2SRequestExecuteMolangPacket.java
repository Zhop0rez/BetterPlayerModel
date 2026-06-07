package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import rip.ysm.compat.touhoulittlemaid.TouhouMaidCompat;
import rip.ysm.api.network.PacketContext;

public class C2SRequestExecuteMolangPacket {
    private static final int MAX_EXPRESSION_LENGTH = 1024;
    private static final double MAX_REMOTE_ENTITY_DISTANCE_SQR = 64.0D * 64.0D;

    private final String animationName;

    private final int entityId;

    public C2SRequestExecuteMolangPacket(String str, int i) {
        this.animationName = str;
        this.entityId = i;
    }

    public static void encode(C2SRequestExecuteMolangPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.animationName);
        buf.writeVarInt(message.entityId);
    }

    public static C2SRequestExecuteMolangPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestExecuteMolangPacket(buf.readUtf(MAX_EXPRESSION_LENGTH), buf.readVarInt());
    }

    public static void handle(C2SRequestExecuteMolangPacket message, PacketContext ctx) {
        if (ctx.isServerSide()) {
            ctx.enqueueWork(() -> handleOnServer(message, ctx.getSender()));
        }
    }

    public static void handleOnServer(C2SRequestExecuteMolangPacket message, ServerPlayer sender) {
        Entity entity;
        if (sender == null || !sender.isAlive() || (entity = sender.level().getEntity(message.entityId)) == null) {
            return;
        }
        if (entity instanceof Player && entity != sender) {
            return;
        }
        if (entity != sender && (!TouhouMaidCompat.isMaidEntity(entity) || sender.distanceToSqr(entity) > MAX_REMOTE_ENTITY_DISTANCE_SQR)) {
            return;
        }
        NetworkHandler.sendToTrackingEntity(new S2CExecuteMolangPacket(message.entityId, message.animationName), entity);
    }
}
