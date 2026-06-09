# Better Player Model v1.0.6 (Fabric)

This release focuses on improving connection stability under high network load, fixing client-side crashes during model synchronization, and reducing console log spam.

## Changelog

### 🔧 Fixes & Stability
* **Decryption Protocol Fix (Step Validation):**
  * Added sync-step validation to the client-side packet decryption loop. This ensures Packet 05 (chunks) are decrypted using `key1` and prevents the client from attempting to decrypt them with `lastKey` (Packet 03 catalog key). This fixes decryption integrity failures, "ZSTD decompression errors", and client-side connection drops.
* **StackOverflow Protection (Fallback Recursion Safety):**
  * Resolved a crash loop in `LazyModelAssembly` resolution. When a model fails to resolve and falls back to `"default"`, the system now ensures it doesn't fall back to itself or another unresolved lazy assembly, preventing an infinite recursion loop (`StackOverflowError`).

### ⚡ Networking & Optimizations
* **Per-Player Bandwidth Limiting (`PlayerBandwidthLimit`):**
  * Introduced a new server-side configuration `PlayerBandwidthLimit` (default `5` Mbps) under the `[server_scheduler]` section in `better_player_model-server.toml`.
  * Every player now receives a separate rate-limiting budget. This prevents satellite (Starlink) or unstable Wi-Fi connections from experiencing severe bufferbloat, keeping the connection queue clear so that game keep-alive packets pass instantly and players do not time out.
* **Log Spam Reduction:**
  * Reduced chunk synchronization log level from `info` to `debug` on both server and client to keep the console output clean during bulk model transfers.

### 📝 Documentation
* **Network Troubleshooting Guide:**
  * Added a comprehensive `network_troubleshooting.md` file to the repository detailing server configurations and JVM arguments to resolve connection timeouts on custom servers.
