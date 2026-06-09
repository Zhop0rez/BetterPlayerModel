# Network Troubleshooting Guide: Model Sync Timeouts and Disconnections

If players with slower or unstable internet connections (such as Wi-Fi, mobile data, or satellite links like Starlink) are consistently disconnected while joining your server or during model synchronization, this guide explains how to fix the issue.

---

## Why Is This Happening?

1. **Bandwidth Saturation (Bufferbloat):**
   When a player joins, the server transfers missing custom player models. If the server tries to push this data faster than the player's connection can download it, the network buffers on the player's router or operating system overflow. 
2. **Delayed Ping/Keep-Alive Packets:**
   Because the connection is saturated, critical game packets (like Minecraft's `KeepAlive` packets which verify the player is still connected) are queued behind the model files. If the client cannot respond to these packets within 15–30 seconds, the server times them out and kicks them.
3. **Large Model Files:**
   If your server hosts many models, or if some models are particularly large (e.g., several megabytes each), the initial download size might be substantial (tens of megabytes), lengthening the critical sync window.

---

## Solution 1: Configure Bandwidth Limits on the Server (Recommended)

Version 1.0.6 introduces a per-player bandwidth limiter to prevent connection saturation. You can restrict the speed at which model chunks are sent.

1. Open your server's config directory: `config/better_player_model-server.toml`.
2. Locate the `[server_scheduler]` category and configure the limits:
   ```toml
   [server_scheduler]
       # Global bandwidth limit for model distribution across all active players (in Mbps)
       # Default: 100
       BandwidthLimit = 25

       # Bandwidth limit per individual player (in Mbps)
       # This prevents slow connections from getting overwhelmed.
       # Default: 5 (Recommended: 3 to 10)
       PlayerBandwidthLimit = 5
   ```
   *Setting `PlayerBandwidthLimit` to 5 Mbps (~625 KB/s) allows models to download steadily without saturating the link, leaving plenty of room for gameplay and Keep-Alive traffic.*

---

## Solution 2: Increase Network Connection Timeouts

By default, Minecraft kicks a player if it doesn't receive network responses within 30 seconds. You can increase this read timeout to 120 seconds.

### For Dedicated Servers:
Add the following Java system property to your server's startup script or command line:
```bash
-Dnet.minecraft.network.readTimeout=120
```

### For Clients (in Prism Launcher or other launch options):
1. Open your launcher's settings for the Minecraft instance.
2. Under **Java** -> **Java Arguments**, add:
   ```bash
   -Dnet.minecraft.network.readTimeout=120
   ```

---

## Solution 3: Use Timeout-Extension Mods

Alternatively, you can install one of the following server-side and client-side mods to handle timeouts automatically:
* **Connectivity**
* **Timeouts**
