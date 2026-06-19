package com.elfmcys.yesstevemodel.capability.fabric.client;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PlayerCapabilityClientStore {

    private static final ConcurrentMap<Player, PlayerCapability> STORE = new ConcurrentHashMap<>();

    private PlayerCapabilityClientStore() {
    }

    public static Optional<PlayerCapability> get(Player player) {
        if (!(player instanceof AbstractClientPlayer)) {
            return Optional.empty();
        }
        PlayerCapability existing = STORE.get(player);
        if (existing != null) {
            return Optional.of(existing);
        }
        PlayerCapability fresh = new PlayerCapability(player);
        STORE.put(player, fresh);
        return Optional.of(fresh);
    }

    public static void clear() {
        STORE.clear();
    }

    public static void remove(Player player) {
        STORE.remove(player);
    }
}
