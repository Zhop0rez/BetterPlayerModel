# PaperBPM (BetterPlayerModel Server-side Plugin)

PaperBPM is a native Paper/Folia Minecraft server plugin that implements the server-side protocol of the Better Player Model (openYSM / YesSteveModel fork) client mod. It acts as a lightweight replacement for running the mod on hybrid servers, eliminating memory leaks, network congestion, and crashes associated with hybrid mod engines.

> Version 1.0.1 is currently a beta release.

## Features

- **Protocol Emulation:** Complete cryptographic handshake matching the openYSM 2.6.0 specification (using XChaCha20, CityHash, and MT19937).
- **Zstandard Compression:** Fully-contained Java Zstandard implementation, eliminating native C++ library requirements on the hosting machine.
- **Model Distribution:** Distributes and caches models to clients in 32 KB chunks with configurable global and per-player bandwidth limits.
- **Persistence:** Automatically saves and restores player model selections across restarts using the native Bukkit PersistentDataContainer.
- **Model Upload Support:** Validates and processes client uploads (.ysm and .zip archives) asynchronously with SHA-256 integrity checks.
- **Permission Checks:** Optional permission checks (`bpm.model.<model-id>`) before a player can apply a custom model.

## Installation

1. Copy the compiled `PaperBPM-1.0.1.jar` into your server's `plugins/` directory.
2. Restart the server to generate the configuration files.
3. Place custom `.ysm` or `.zip` models inside the `plugins/PaperBPM/models/` folder.
4. Players running the Better Player Model mod on their client will automatically synchronize and render custom models.

## Configuration

The default configuration file is generated at `plugins/PaperBPM/config.yml`:

```yaml
network:
  # Bandwidth limit for model distribution to all players, in Mbps.
  global-bandwidth-limit: 100

  # Bandwidth limit per player during model distribution, in Mbps.
  player-bandwidth-limit: 5

upload:
  # Whether clients are allowed to upload model files to the server.
  allow-model-upload: true

  # Maximum size of a single uploaded model file, in MiB.
  model-upload-max-mib: 128

  # How many upload chunks a client may send per tick.
  model-upload-chunks-per-tick: 4

models:
  # If true, requires players to have the permission "bpm.model.<model_id>" to use a model.
  require-model-permission: false
```

## Compilation

Build the plugin using the Gradle wrapper:

```bash
./gradlew shadowJar
```

The output shaded jar will be located at `build/libs/PaperBPM-1.0.1.jar`.
