package com.elfmcys.yesstevemodel.network.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import rip.ysm.api.item.ToolActionBridge;
import rip.ysm.api.network.PacketContext;

public class C2SSwingArmPacket {

    private final InteractionHand hand;

    public C2SSwingArmPacket(InteractionHand hand) {
        this.hand = hand;
    }

    public static void encode(C2SSwingArmPacket message, FriendlyByteBuf buf) {
        buf.writeEnum(message.hand);
    }

    public static C2SSwingArmPacket decode(FriendlyByteBuf buf) {
        return new C2SSwingArmPacket(buf.readEnum(InteractionHand.class));
    }

    public static void handle(C2SSwingArmPacket message, PacketContext ctx) {
        ServerPlayer sender = ctx.getSender();
        if (ctx.isServerSide() && sender != null) {
            ctx.enqueueWork(() -> processSwingArm(message, sender));
        }
    }

    public static void processSwingArm(C2SSwingArmPacket message, ServerPlayer sender) {
        InteractionHand interactionHand = message.hand;
        ItemStack itemInHand = sender.getItemInHand(interactionHand);
        if (itemInHand.isEmpty() || !ToolActionBridge.onEntitySwing(itemInHand, sender)) {
            // 26.3: swing() сам проверяет старт/повтор свинга и шлёт ClientboundSwingAnimationPacket трекинг-игрокам
            sender.swing(interactionHand, SwingAnimation.DEFAULT, false);
        }
    }
}
