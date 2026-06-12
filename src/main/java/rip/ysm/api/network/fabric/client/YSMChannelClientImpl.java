package rip.ysm.api.network.fabric.client;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import rip.ysm.api.network.fabric.YSMChannelImpl;

public final class YSMChannelClientImpl {

    private YSMChannelClientImpl() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(YSMChannelImpl.channelId, (client, handler, buf, responseSender) -> {
            FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.buffer(buf.readableBytes()));
            buf.readBytes(copy);
            client.execute(() -> {
                YSMChannelImpl.dispatch(copy, new ClientPacketContext(client, handler.getConnection()));
            });
        });
    }

    public static void sendToServer(FriendlyByteBuf buf) {
        ClientPlayNetworking.send(YSMChannelImpl.channelId, buf);
    }

    public static Packet<?> toServerboundPacket(FriendlyByteBuf buf) {
        return ClientPlayNetworking.createC2SPacket(YSMChannelImpl.channelId, buf);
    }
}
