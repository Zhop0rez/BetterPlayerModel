package rip.ysm.api.network.fabric;

import net.minecraft.network.FriendlyByteBuf;
import rip.ysm.api.network.PacketContext;
import rip.ysm.api.network.PacketDirection;

import java.util.function.BiConsumer;
import java.util.function.Function;

record Codec<T>(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
                BiConsumer<T, PacketContext> handler, PacketDirection direction) {
    void encode(Object packet, FriendlyByteBuf buf) {
        encoder.accept(type.cast(packet), buf);
    }
    void dispatch(FriendlyByteBuf buf, PacketContext ctx) {
        if ((direction == PacketDirection.PLAY_TO_CLIENT) != ctx.isClientSide()) {
            return;
        }
        handler.accept(decoder.apply(buf), ctx);
    }
}
