package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.capability.ModelInfoCapability;
import com.elfmcys.yesstevemodel.capability.VehicleModelCapability;
import rip.ysm.compat.touhoulittlemaid.TouhouMaidCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import rip.ysm.api.network.PacketContext;

public record C2SCompleteFeedbackPacket(FeedbackData feedbackData) {
    private static final double MAX_REMOTE_ENTITY_DISTANCE_SQR = 64.0D * 64.0D;

    public static void encode(C2SCompleteFeedbackPacket message, FriendlyByteBuf buf) {
        FeedbackData.writeToBuf(message.feedbackData, buf);
    }

    public static C2SCompleteFeedbackPacket decode(FriendlyByteBuf buf) {
        return new C2SCompleteFeedbackPacket(FeedbackData.readFromBuf(buf, false));
    }

    public static void handle(C2SCompleteFeedbackPacket message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null) {
            ServerPlayer sender = ctx.getSender();
            ctx.enqueueWork(() -> handleOnServer(message, sender, (ServerLevel) sender.level()));
        }
    }

    public static void handleOnServer(C2SCompleteFeedbackPacket message, ServerPlayer sender, ServerLevel serverLevel) {
        Entity entity = serverLevel.getEntity(message.feedbackData.flags());
        if (TouhouMaidCompat.isMaidEntity(entity)) {
            if (sender.distanceToSqr(entity) > MAX_REMOTE_ENTITY_DISTANCE_SQR) {
                return;
            }
            TouhouMaidCompat.applyFeedback(entity, message.feedbackData);
        } else if (entity instanceof ServerPlayer serverPlayer) {
            if (serverPlayer != sender) {
                return;
            }
            ModelInfoCapability.get(serverPlayer).ifPresent(cap -> {
                cap.applyFeedback(serverPlayer, message.feedbackData);
                if (serverPlayer.getVehicle() != null && serverPlayer.getVehicle().getFirstPassenger() == serverPlayer) {
                    VehicleModelCapability.get(serverPlayer.getVehicle()).ifPresent(vehicleCap -> {
                        cap.getMolangVars().ifPresent(map -> vehicleCap.setModel(cap.getModelId(), map));
                    });
                }
            });
        }
    }
}
