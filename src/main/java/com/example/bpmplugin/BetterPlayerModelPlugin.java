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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.ArrayList;
import java.util.List;

public final class BetterPlayerModelPlugin extends JavaPlugin implements PluginMessageListener, Listener, CommandExecutor, TabCompleter {
    
    private static final String CHANNEL = "better_player_model:2_6_0";
    private YsmSessionManager sessionManager;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        sessionManager = new YsmSessionManager(this);
        
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("bpm") != null) {
            getCommand("bpm").setExecutor(this);
            getCommand("bpm").setTabCompleter(this);
        } else {
            getLogger().warning("Command 'bpm' is not declared in plugin.yml; /bpm reload will be unavailable.");
        }

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
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("Usage: /" + label + " reload");
            return true;
        }

        if (!sender.hasPermission("bpm.command")) {
            sender.sendMessage("You do not have permission to run this command.");
            return true;
        }

        // The heavy model scan runs async inside reload(); only the catalog swap and
        // player resync run on the server thread when it finishes. The command thus
        // returns immediately and never blocks the tick loop (watchdog-safe).
        sender.sendMessage("[BPM] Reload started... (see the console for progress)");
        getLogger().info("[BPM] /" + label + " reload requested by " + sender.getName() + " ...");
        sessionManager.reload(summary -> {
            sender.sendMessage("[BPM] " + summary);
            // Plain STDOUT line so the result is clearly visible on the console.
            getLogger().info("[BPM] /" + label + " reload -> " + summary);
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            completions.add("reload");
        }
        return completions;
    }

    public void sendYsmPacket(Player player, byte[] data) {
        player.sendPluginMessage(this, CHANNEL, data);
    }
}
