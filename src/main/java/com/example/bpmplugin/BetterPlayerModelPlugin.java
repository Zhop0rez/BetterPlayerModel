package com.example.bpmplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class BetterPlayerModelPlugin extends JavaPlugin implements PluginMessageListener, Listener {
    
    private static final String CHANNEL = "better_player_model:2_6_0";
    private YsmSessionManager sessionManager;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        sessionManager = new YsmSessionManager(this);
        
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        
        getLogger().info("BetterPlayerModel Plugin for Paper enabled!");
        getLogger().info("Crypto-Router is online. Ready to securely relay YSM packets.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        getLogger().info("BetterPlayerModel Plugin disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Initiate the cryptographic handshake
        sessionManager.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up the session
        sessionManager.onPlayerQuit(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Delay to ensure the player is fully spawned in the new world
        getServer().getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                sessionManager.onPlayerRespawn(event.getPlayer());
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerTrackEntity(PlayerTrackEntityEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Player) {
            Player target = (Player) event.getEntity();
            sessionManager.onPlayerTrack(event.getPlayer(), target);
        }
    }

    @EventHandler
    public void onPlayerUntrackEntity(PlayerUntrackEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player target = (Player) event.getEntity();
            sessionManager.onPlayerUntrack(event.getPlayer(), target);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        sessionManager.onPlayerChangedWorld(event.getPlayer());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL)) {
            return;
        }

        // Pass the raw encrypted packet to our cryptographic router
        sessionManager.handleIncomingPacket(player, message);
    }
    
    public void sendYsmPacket(Player player, byte[] data) {
        player.sendPluginMessage(this, CHANNEL, data);
    }
}
