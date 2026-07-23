package com.example.bpmplugin;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.entity.Player;
import rip.ysm.security.YSMByteBuf;
import rip.ysm.security.YsmCrypt;

import java.util.Arrays;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

public class YsmSessionManager {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int UPLOAD_CHUNK_SIZE = 16_000;
    private static final int MAX_CLIENT_UPLOAD_BYTES = 512 * 1024 * 1024;
    private static final int MAX_ACTIVE_UPLOADS_PER_PLAYER = 1;
    private static final int MAX_ACTIVE_UPLOAD_SESSIONS = 8;
    private static final long UPLOAD_SESSION_TIMEOUT_MS = 120_000L;
    private static final long UPLOAD_RATE_WINDOW_MS = 1_000L;
    private static final int MAX_MODEL_ID_LENGTH = 256;
    private static final int MAX_FILE_NAME_LENGTH = 256;
    private static final int MAX_EXPRESSION_VALUES = 64;
    private static final short MODEL_SWITCH_FLAG = 2048;
    private static final Pattern MODEL_ID_PATTERN = Pattern.compile("[a-z0-9_./-]+");
    private static final byte[] CLIENT_KEY = createClientKey();
    public static byte[] SERVER_KEY = new byte[56];

    public static class PlayerSyncState {
        public int step = 0;
        public long lastActiveMs = 0;
        public byte[] key1; // Server-generated key
        public byte[] clientKey = Arrays.copyOf(CLIENT_KEY, CLIENT_KEY.length);
        public byte[] clientNextKey;
        public String modelId = "default";
        public String textureId = "default";
        public String animationId = "";
        public java.util.Queue<PendingTransfer> transferQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
        public PendingTransfer currentTransfer = null;
        public long tokens = 0;
        // The Paper bridge has no client-side "models are ready" acknowledgement.
        // Use a generation to keep bounded compatibility resyncs from overlapping.
        public long modelStateResyncGeneration = 0;
        public boolean resyncAfterTransfers = false;
    }

    class PendingTransfer {
        public ServerModel sm;
        public byte[] encryptedPayload;
        public int offset;
        public PendingTransfer(ServerModel sm, byte[] encryptedPayload) {
            this.sm = sm;
            this.encryptedPayload = encryptedPayload;
            this.offset = 0;
        }
    }

    public static class UploadSession {
        public long uploadId;
        public java.util.UUID uploaderId;
        public String modelId;
        public String fileName;
        public int totalBytes;
        public String sha256;
        public byte[] fileData;
        public int bytesReceived;
        public boolean failed;
        public long createdAtMs;
        public long lastActivityMs;
        public long chunkWindowStartedMs;
        public int chunksInWindow;
    }

    public static class ServerModel {
        public String modelId;
        public long hash1;
        public long hash2;
        public byte[] data;
        public String defaultTexture = "default";
        public List<String> textureIds = List.of();
        public List<String> extraAnimationIds = List.of();
        public Map<String, List<String>> classifiedAnimationIds = Map.of();
    }

    private enum UploadImportKind {
        YSM,
        ZIP,
        SEVEN_ZIP,
        UNKNOWN
    }

    private final Map<UUID, PlayerSyncState> sessions = new ConcurrentHashMap<>();
    private final Map<Long, UploadSession> uploadSessions = new ConcurrentHashMap<>();
    private final Map<String, ServerModel> serverModels = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> trackedViewersByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> trackedTargetsByViewer = new ConcurrentHashMap<>();
    private final Set<String> processingModelIds = ConcurrentHashMap.newKeySet();
    
    private final BetterPlayerModelPlugin plugin;
    private final java.io.File modelsDir;

    private final org.bukkit.NamespacedKey MODEL_KEY;
    private final org.bukkit.NamespacedKey TEXTURE_KEY;

    private long globalTokens = 0;
    private final long globalBytesPerTick;
    private final long playerBytesPerTick;
    private final Semaphore modelProcessingPermits;

    public YsmSessionManager(BetterPlayerModelPlugin plugin) {
        this.plugin = plugin;
        this.MODEL_KEY = new org.bukkit.NamespacedKey(plugin, "ysm_model_id");
        this.TEXTURE_KEY = new org.bukkit.NamespacedKey(plugin, "ysm_texture_id");
        this.modelsDir = new java.io.File(plugin.getDataFolder(), "models");
        if (!this.modelsDir.exists()) {
            this.modelsDir.mkdirs();
        }

        java.io.File keyFile = new java.io.File(plugin.getDataFolder(), "server_key.dat");
        if (keyFile.exists()) {
            try {
                byte[] readKey = java.nio.file.Files.readAllBytes(keyFile.toPath());
                if (readKey.length == 56) {
                    System.arraycopy(readKey, 0, SERVER_KEY, 0, 56);
                } else {
                    RANDOM.nextBytes(SERVER_KEY);
                    java.nio.file.Files.write(keyFile.toPath(), SERVER_KEY);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to read server_key.dat: " + e.getMessage());
                RANDOM.nextBytes(SERVER_KEY);
            }
        } else {
            try {
                RANDOM.nextBytes(SERVER_KEY);
                java.nio.file.Files.write(keyFile.toPath(), SERVER_KEY);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to write server_key.dat: " + e.getMessage());
            }
        }

        long globalMbps = plugin.getConfig().getLong("network.global-bandwidth-limit", 100);
        long playerMbps = plugin.getConfig().getLong("network.player-bandwidth-limit", 5);
        this.globalBytesPerTick = (globalMbps * 1000000L) / 8L / 20L;
        this.playerBytesPerTick = (playerMbps * 1000000L) / 8L / 20L;
        this.modelProcessingPermits = new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));

        loadModelsFromDisk();

        // Bukkit player and networking APIs must stay on the server thread.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::processNetworkQueues, 1L, 1L);
    }

    private static byte[] createClientKey() {
        byte[] key = new byte[56];
        new Random(114514).nextBytes(key);
        return key;
    }

    private void loadModelsFromDisk() {
        if (!modelsDir.exists()) return;
        try {
            java.nio.file.Path modelsPath = modelsDir.toPath();
            // FIX: wrap Files.walk in try-with-resources to avoid file handle leak on Windows
            try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(modelsPath)) {
                for (java.nio.file.Path path : walk.filter(p -> {
                    UploadImportKind importKind = importKindFromFileName(p.getFileName().toString());
                    return importKind == UploadImportKind.YSM || importKind == UploadImportKind.ZIP;
                }).toArray(java.nio.file.Path[]::new)) {
                    byte[] data = java.nio.file.Files.readAllBytes(path);

                    String relativePath = modelsPath.relativize(path).toString().replace('\\', '/');
                    String modelId = normalizeUploadedModelId(relativePath);
                    if (modelId == null) {
                        plugin.getLogger().warning("Skipping model with invalid id: " + relativePath);
                        continue;
                    }

                    try {
                        byte[] compiled = compileModelToYsm(data, modelId);
                        if (compiled != null) {
                            byte[] finalData = new byte[compiled.length + 4];
                            finalData[0] = 'Y'; finalData[1] = 'S'; finalData[2] = 'M'; finalData[3] = 'M';
                            System.arraycopy(compiled, 0, finalData, 4, compiled.length);
                            data = finalData;

                            // Save as .ysm if it was a zip or legacy model
                            java.nio.file.Path sourcePath = path;
                            path = resolveModelOutput(modelId);
                            // FIX: write to disk FIRST, then compute hash — so disk and memory are always consistent
                            writeAtomically(path, data);
                            if (importKindFromFileName(sourcePath.getFileName().toString()) == UploadImportKind.ZIP) {
                                java.nio.file.Files.deleteIfExists(sourcePath);
                            }
                            plugin.getLogger().info("Compiled model to YSMM format 32: " + modelId);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to compile model " + modelId + " from disk: " + e.getMessage());
                        continue;
                    }

                    ServerModel sm = createServerModel(modelId, data);
                    serverModels.put(sm.modelId, sm);
                    plugin.getLogger().info("Loaded model from disk: " + sm.modelId);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load models: " + e.getMessage());
        }
    }

    private byte[] compileModelToYsm(byte[] rawData, String modelId) throws Exception {
        if (rawData.length >= 4 && rawData[0] == 'Y' && rawData[1] == 'S' && rawData[2] == 'M' && rawData[3] == 'M') {
            return null; // Already compiled to YSMM format 32
        }

        if (rawData.length >= 4 && rawData[0] == 0x50 && rawData[1] == 0x4B && rawData[2] == 0x03 && rawData[3] == 0x04) {
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("ysm_upload_", ".zip");
            try {
                java.nio.file.Files.write(tempFile, rawData);
                try (com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(tempFile)) {
                    com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel rawModel = deserializer.deserialize();
                    return compileRawModel(rawModel);
                }
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile);
            }
        }

        int ysmCryptoVersion = rip.ysm.legacy.YesModelUtils.getYsmCryptoVersion(rawData);
        if (ysmCryptoVersion == 1 || ysmCryptoVersion == 2) {
            java.util.Map<String, byte[]> input = rip.ysm.legacy.YesModelUtils.input(rawData);
            try (com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(input)) {
                com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel rawModel = deserializer.deserialize();
                return compileRawModel(rawModel);
            }
        }

        // Check for raw format-32 binary (client uploads without YSMM prefix)
        // First 4 bytes = format dword = 32 (0x20 0x00 0x00 0x00 in little-endian)
        if (rawData.length >= 4 && rawData[0] == 0x20 && rawData[1] == 0x00 && rawData[2] == 0x00 && rawData[3] == 0x00) {
            // Already raw format-32 bytes — return as-is (caller adds YSMM prefix)
            return java.util.Arrays.copyOf(rawData, rawData.length);
        }

        try {
            byte[] decrypted = rip.ysm.security.YsmCrypt.decryptYsmFile(rawData);
            // Use single-arg constructor: it reads and CONSUMES the 4-byte format dword
            // before parsing the body (the explicit-format constructor does NOT skip them)
            try (com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer(decrypted)) {
                com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                return compileRawModel(rawModel);
            }
        } catch (Exception e) {
            throw new Exception("File is neither a ZIP archive, nor a legacy model, nor a valid encrypted Crypto V3 model.", e);
        }
    }

    private byte[] compileRawModel(com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel rawModel) throws Exception {
        try (rip.ysm.security.YSMByteBuf serialized = com.elfmcys.yesstevemodel.resource.YSMBinarySerializer.serialize(rawModel, 32, true)) {
            io.netty.buffer.ByteBuf raw = serialized.getRawBuf();
            byte[] subArr = new byte[raw.readableBytes()];
            raw.getBytes(raw.readerIndex(), subArr);
            return subArr;
        }
    }

    private ServerModel createServerModel(String modelId, byte[] data) throws Exception {
        long[] hashes = YsmCrypt.calculateModelHashes(sha256Hex(data), SERVER_KEY);

        ServerModel sm = new ServerModel();
        sm.modelId = modelId;
        sm.hash1 = hashes[0];
        sm.hash2 = hashes[1];
        sm.data = data;
        populateAnimationMetadata(sm);
        return sm;
    }

    private void populateAnimationMetadata(ServerModel model) {
        byte[] serialized = model.data;
        if (serialized.length >= 4 && serialized[0] == 'Y' && serialized[1] == 'S' && serialized[2] == 'M' && serialized[3] == 'M') {
            serialized = Arrays.copyOfRange(serialized, 4, serialized.length);
        }

        try (com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer deserializer =
                     new com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer(serialized, 32)) {
            com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel rawModel = deserializer.deserializeKeepOpen();
            model.defaultTexture = rawModel.properties.defaultTexture;
            model.textureIds = List.copyOf(rawModel.mainEntity.textures.keySet());
            model.extraAnimationIds = List.copyOf(rawModel.properties.extraAnimations.keySet());

            Map<String, List<String>> classifiedAnimations = new LinkedHashMap<>();
            for (com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel.ExtraAnimationClassify classify
                    : rawModel.properties.extraAnimationClassifies) {
                if (classify.id != null && !classify.id.isEmpty()) {
                    classifiedAnimations.put(classify.id, List.copyOf(classify.extras.keySet()));
                }
            }
            model.classifiedAnimationIds = Map.copyOf(classifiedAnimations);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read animation metadata for " + model.modelId + ": " + e.getMessage());
        }
    }

    private static String sha256Hex(byte[] data) throws java.security.NoSuchAlgorithmException {
        byte[] hashBytes = java.security.MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public void onPlayerJoin(Player player) {
        PlayerSyncState state = new PlayerSyncState();
        sessions.put(player.getUniqueId(), state);
        
        // Delay sending S2CVersionCheckPacket to ensure client registered the channel
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> sendVersionCheck(player), 40L);
    }

    public void onPlayerQuit(Player player) {
        clearTrackingFor(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        
        // Clean up pending uploads from this player
        uploadSessions.entrySet().removeIf(entry -> entry.getValue().uploaderId.equals(player.getUniqueId()));
    }

    private void sendVersionCheck(Player player) {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeByte(51); // Packet discriminator for S2CVersionCheckPacket
            
            writeString(buf, "2.6.0");
            writeString(buf, "open_ysm:v1");
            buf.writeBoolean(plugin.getConfig().getBoolean("upload.allow-model-upload", true));
            
            sendRaw(player, buf);
            plugin.getLogger().info("Sent Version Check to " + player.getName());
        } finally {
            buf.release();
        }
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    private void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = buf.readByte();
            value |= (currentByte & 127) << position;
            if ((currentByte & 128) == 0) break;
            position += 7;
            if (position >= 32) throw new RuntimeException("VarInt is too big");
        }

        return value;
    }

    private String readString(ByteBuf buf) {
        int len = readVarInt(buf);
        if (len < 0 || len > 32767) throw new RuntimeException("String length out of bounds: " + len);
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeVarLong(ByteBuf buf, long value) {
        while ((value & -128L) != 0L) {
            buf.writeByte((int)(value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int)value);
    }

    private long readVarLong(ByteBuf buf) {
        long value = 0L;
        int position = 0;
        byte currentByte;
        while (true) {
            currentByte = buf.readByte();
            value |= (long)(currentByte & 127) << position;
            if ((currentByte & 128) == 0) break;
            position += 7;
            if (position >= 64) throw new RuntimeException("VarLong is too big");
        }
        return value;
    }

    public void handleIncomingPacket(Player player, byte[] data) {
        if (data.length == 0) return;
        
        PlayerSyncState state = sessions.get(player.getUniqueId());
        if (state == null) return;
        state.lastActiveMs = System.currentTimeMillis();

        ByteBuf buf = Unpooled.wrappedBuffer(data);
        int discriminator = buf.readUnsignedByte();

        try {
            if (discriminator == 52) { // C2SVersionCheckPacket
                String clientVersion = readString(buf);
                plugin.getLogger().info("Received Version Check from " + player.getName() + ": " + clientVersion + ". Initiating Crypto Handshake.");
                initiateHandshake(player, state);
            } else if (discriminator == 2) { // C2SModelSyncPayload (Crypto Handshake Pong)
                byte[] payload = new byte[buf.readableBytes()];
                buf.readBytes(payload);
                handleCryptoPayload(player, state, payload);
            } else if (state.step < 2) {
                return;
            } else if (discriminator == 5) { // C2SRequestSwitchModelPacket
                handleModelSwitch(player, state, buf);
            } else if (discriminator == 7) { // C2SPlayAnimationPacket
                handlePlayAnimation(player, state, buf);
            } else if (discriminator == 18) { // C2SSyncAnimationExpressionPacket
                handleAnimationExpression(player, buf);
            } else if (discriminator == 23) { // C2SSwingArmPacket
                handleSwingArm(player, buf);
            } else if (discriminator == 70) { // C2SModelUploadStartPacket
                handleUploadStart(player, state, buf);
            } else if (discriminator == 72) { // C2SModelUploadChunkPacket
                handleUploadChunk(player, buf);
            } else if (discriminator == 73) { // C2SModelUploadFinishPacket
                handleUploadFinish(player, buf);
            } else {
                // FIX: Only relay known non-crypto animation/state packets.
                // Discriminator 2 is the crypto handshake channel — never relay it raw.
                // Known safe relayable packets: 7 (PlayAnimation), 23 (SwingArm), 6 (SyncPlayerState)
                // Unknown packets are ignored. Sending C2S packets to clients is invalid.
                // Unknown discriminators are silently dropped to prevent crypto payload leaking
            }
        } catch (Exception e) {
            // FIX: Don't spam stack traces for bad packets
            if (e instanceof IndexOutOfBoundsException || e instanceof RuntimeException) {
                plugin.getLogger().warning("Invalid packet data received from " + player.getName() + " (Discriminator: " + discriminator + ")");
            } else {
                plugin.getLogger().severe("Error handling packet ID " + discriminator + " from " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void handleModelSwitch(Player player, PlayerSyncState state, ByteBuf buf) {
        String modelId = readString(buf);
        String textureId = readString(buf);
        if (modelId.isEmpty() || modelId.length() > MAX_MODEL_ID_LENGTH || textureId.length() > MAX_MODEL_ID_LENGTH) {
            return;
        }
        if (!"default".equals(modelId) && !serverModels.containsKey(modelId)) {
            plugin.getLogger().warning(player.getName() + " requested an unknown model: " + modelId);
            setModelChoice(player, state, "default", "default");
            sendModelChoice(player, state);
            return;
        }
        if (plugin.getConfig().getBoolean("models.require-model-permission", false)
                && !"default".equals(modelId)
                && !player.hasPermission("bpm.model." + modelId.toLowerCase())) {
            plugin.getLogger().warning(player.getName() + " attempted to use an unpermitted model: " + modelId);
            setModelChoice(player, state, "default", "default");
            sendModelChoice(player, state);
            return;
        }

        String resolvedTexture = "default".equals(modelId)
                ? "default"
                : resolveTextureOrDefault(serverModels.get(modelId), textureId);
        if (resolvedTexture == null) {
            plugin.getLogger().warning(player.getName() + " requested a model without a valid texture: " + modelId);
            setModelChoice(player, state, "default", "default");
        } else {
            if (!resolvedTexture.equals(textureId)) {
                plugin.getLogger().warning("Replaced invalid texture '" + textureId + "' for model '" + modelId
                        + "' on player '" + player.getName() + "' with '" + resolvedTexture + "'.");
            }
            setModelChoice(player, state, modelId, resolvedTexture);
        }
        sendModelChoice(player, state);
    }

    private void setModelChoice(Player player, PlayerSyncState state, String modelId, String textureId) {
        state.modelId = modelId;
        state.textureId = textureId;
        state.animationId = "";

        org.bukkit.persistence.PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(MODEL_KEY, org.bukkit.persistence.PersistentDataType.STRING, modelId);
        pdc.set(TEXTURE_KEY, org.bukkit.persistence.PersistentDataType.STRING, textureId);
    }

    private void sendModelChoice(Player player, PlayerSyncState state) {
        broadcastModelChange(player, state.modelId, state.textureId, player);
        broadcastModelChange(player, state.modelId, state.textureId, null);
    }

    private String resolveTextureOrDefault(ServerModel model, String requestedTexture) {
        if (model == null || model.textureIds.isEmpty()) {
            return null;
        }

        String normalizedRequested = normalizeTextureId(requestedTexture);
        if (normalizedRequested != null && model.textureIds.contains(normalizedRequested)) {
            return normalizedRequested;
        }
        String defaultTexture = normalizeTextureId(model.defaultTexture);
        if (defaultTexture != null && model.textureIds.contains(defaultTexture)) {
            return defaultTexture;
        }
        return model.textureIds.get(0);
    }

    private String normalizeTextureId(String textureId) {
        if (textureId == null) {
            return null;
        }
        if (textureId.toLowerCase(Locale.ROOT).endsWith(".png") && textureId.length() > 4) {
            return textureId.substring(0, textureId.length() - 4);
        }
        return textureId;
    }

    private void handlePlayAnimation(Player player, PlayerSyncState state, ByteBuf buf) {
        int animationIndex = readVarInt(buf);
        String category = readString(buf);
        int entityId = buf.isReadable() ? readVarInt(buf) : -1;

        // Paper cannot emulate modded entities such as Touhou Little Maid. Player models are supported.
        if (entityId != -1 && entityId != player.getEntityId()) {
            return;
        }

        String animationId = resolveAnimationId(state.modelId, category, animationIndex);
        if (animationId == null) {
            return;
        }
        state.animationId = animationId;
        broadcastPlayerState(player, animationId);
    }

    private String resolveAnimationId(String modelId, String category, int animationIndex) {
        if (animationIndex == -1) {
            return "";
        }
        if (animationIndex < 0) {
            return null;
        }

        ServerModel model = serverModels.get(modelId);
        if (model == null) {
            return null;
        }
        List<String> animationIds = category.isEmpty()
                ? model.extraAnimationIds
                : model.classifiedAnimationIds.get(category);
        if (animationIds == null || animationIndex >= animationIds.size()) {
            return null;
        }
        return animationIds.get(animationIndex);
    }

    private void handleAnimationExpression(Player player, ByteBuf buf) {
        int count = buf.readUnsignedByte();
        if (count > MAX_EXPRESSION_VALUES || buf.readableBytes() < count * Float.BYTES) {
            return;
        }

        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = buf.readFloat();
        }

        ByteBuf outBuf = Unpooled.buffer(2 + count * Float.BYTES);
        try {
            outBuf.writeByte(19); // S2CSyncAnimationExpressionPacket
            writeVarInt(outBuf, player.getEntityId());
            outBuf.writeByte(count);
            for (float value : values) {
                outBuf.writeFloat(value);
            }
            broadcastToReadyPlayers(player, copyReadableBytes(outBuf), true);
        } finally {
            outBuf.release();
        }
    }

    private void handleSwingArm(Player player, ByteBuf buf) {
        int hand = readVarInt(buf);
        if (hand == 0) {
            player.swingMainHand();
        } else if (hand == 1) {
            player.swingOffHand();
        }
    }

    private void handleUploadStart(Player player, PlayerSyncState state, ByteBuf buf) {
        String requestedModelId = readString(buf);
        String fileName = readString(buf);
        int totalBytes = readVarInt(buf);
        String sha256 = readString(buf);
        int maxTotalBytes = getUploadMaxBytes();

        if (!plugin.getConfig().getBoolean("upload.allow-model-upload", true)) {
            sendUploadStartResult(player, 0L, (byte) 6, maxTotalBytes, "Model import disabled.");
            return;
        }
        if (state.step < 2) {
            sendUploadStartResult(player, 0L, (byte) 3, maxTotalBytes, "No import permission.");
            return;
        }

        String modelId = normalizeUploadedModelId(requestedModelId);
        UploadImportKind importKind = importKindFromFileName(fileName);
        if (requestedModelId.length() > MAX_MODEL_ID_LENGTH || modelId == null || fileName.length() > MAX_FILE_NAME_LENGTH
                || importKind == UploadImportKind.UNKNOWN || !isSha256(sha256)) {
            sendUploadStartResult(player, 0L, (byte) 5, maxTotalBytes, "Invalid model id or hash.");
            return;
        }
        if (importKind == UploadImportKind.SEVEN_ZIP) {
            sendUploadStartResult(player, 0L, (byte) 7, maxTotalBytes, "7z import is not supported yet.");
            return;
        }
        if (totalBytes <= 0 || totalBytes > maxTotalBytes) {
            sendUploadStartResult(player, 0L, (byte) 2, maxTotalBytes, "File exceeds server limit.");
            return;
        }
        if (serverModels.containsKey(modelId) || processingModelIds.contains(modelId) || hasActiveUploadForModel(modelId)) {
            sendUploadStartResult(player, 0L, (byte) 1, maxTotalBytes, "Model ID already exists.");
            return;
        }
        if (countUploadsFor(player.getUniqueId()) >= MAX_ACTIVE_UPLOADS_PER_PLAYER
                || uploadSessions.size() >= MAX_ACTIVE_UPLOAD_SESSIONS
                || reservedUploadBytes() + totalBytes > getMaxActiveUploadBytes()) {
            sendUploadStartResult(player, 0L, (byte) 8, maxTotalBytes, "Upload memory budget exceeded.");
            return;
        }

        long uploadId = nextUploadId();
        UploadSession session = new UploadSession();
        session.uploadId = uploadId;
        session.uploaderId = player.getUniqueId();
        session.modelId = modelId;
        session.fileName = fileName;
        session.totalBytes = totalBytes;
        session.sha256 = sha256.toLowerCase(Locale.ROOT);
        session.fileData = new byte[totalBytes];
        session.createdAtMs = System.currentTimeMillis();
        session.lastActivityMs = session.createdAtMs;
        uploadSessions.put(uploadId, session);

        plugin.getLogger().info("Accepting model upload from " + player.getName() + " for model " + modelId + " (" + totalBytes + " bytes)");
        sendUploadStartResult(player, uploadId, (byte) 0, maxTotalBytes, "");
    }

    private void handleUploadChunk(Player player, ByteBuf buf) {
        long uploadId = readVarLong(buf);
        UploadSession session = uploadSessions.get(uploadId);
        if (session == null || !session.uploaderId.equals(player.getUniqueId())) {
            return;
        }

        session.lastActivityMs = System.currentTimeMillis();
        int chunkOffset = readVarInt(buf);
        int chunkLength = readVarInt(buf);
        if (chunkLength < 1 || chunkLength > UPLOAD_CHUNK_SIZE || chunkLength > buf.readableBytes()
                || chunkOffset < 0 || (long) chunkOffset + chunkLength > session.totalBytes || chunkOffset != session.bytesReceived) {
            session.failed = true;
            return;
        }
        if (!tryAcceptUploadChunk(session)) {
            session.failed = true;
            return;
        }

        buf.readBytes(session.fileData, chunkOffset, chunkLength);
        session.bytesReceived += chunkLength;
    }

    private void handleUploadFinish(Player player, ByteBuf buf) {
        long uploadId = readVarLong(buf);
        UploadSession session = uploadSessions.remove(uploadId);
        if (session == null || !session.uploaderId.equals(player.getUniqueId())) {
            return;
        }
        if (session.failed || session.bytesReceived != session.totalBytes) {
            sendUploadResult(player, uploadId, (byte) 5, "", 0L, 0L, "Incomplete upload.");
            return;
        }
        try {
            if (!sha256Hex(session.fileData).equals(session.sha256)) {
                sendUploadResult(player, uploadId, (byte) 1, "", 0L, 0L, "SHA-256 verification failed.");
                return;
            }
        } catch (Exception e) {
            sendUploadResult(player, uploadId, (byte) 1, "", 0L, 0L, "Could not verify the uploaded file.");
            return;
        }

        if (!processingModelIds.add(session.modelId)) {
            sendUploadResult(player, uploadId, (byte) 1, "", 0L, 0L, "Model ID already exists.");
            return;
        }
        if (!modelProcessingPermits.tryAcquire()) {
            processingModelIds.remove(session.modelId);
            sendUploadResult(player, uploadId, (byte) 8, "", 0L, 0L, "Server is processing too many model imports.");
            return;
        }

        plugin.getLogger().info("Completed model upload from " + player.getName() + " for model " + session.modelId + ". Compiling asynchronously...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> processUploadedModel(player, session));
    }

    private void processUploadedModel(Player player, UploadSession session) {
        try {
            byte[] data = session.fileData;
            byte[] compiled = compileModelToYsm(data, session.modelId);
            if (compiled != null) {
                data = new byte[compiled.length + 4];
                data[0] = 'Y';
                data[1] = 'S';
                data[2] = 'M';
                data[3] = 'M';
                System.arraycopy(compiled, 0, data, 4, compiled.length);
            }

            ServerModel model = createServerModel(session.modelId, data);
            writeAtomically(resolveModelOutput(session.modelId), model.data);
            completeUploadProcessing(player, session, model, null);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to process uploaded model " + session.modelId + ": " + e.getMessage());
            completeUploadProcessing(player, session, null, "The uploaded model could not be processed.");
        }
    }

    private void completeUploadProcessing(Player player, UploadSession session, ServerModel model, String errorMessage) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (model != null) {
                        serverModels.put(model.modelId, model);
                        broadcastPartialCatalog(model);
                        if (player.isOnline()) {
                            sendUploadResult(player, session.uploadId, (byte) 0, model.modelId, model.hash1, model.hash2, "");
                        }
                    } else if (player.isOnline()) {
                        sendUploadResult(player, session.uploadId, (byte) 2, "", 0L, 0L, errorMessage);
                    }
                } finally {
                    processingModelIds.remove(session.modelId);
                    modelProcessingPermits.release();
                }
            });
        } catch (IllegalStateException ignored) {
            processingModelIds.remove(session.modelId);
            modelProcessingPermits.release();
        }
    }

    private java.nio.file.Path resolveModelOutput(String modelId) throws java.io.IOException {
        java.nio.file.Path root = modelsDir.toPath().toAbsolutePath().normalize();
        java.nio.file.Path output = root.resolve(modelId + ".ysm").normalize();
        if (!output.startsWith(root)) {
            throw new java.io.IOException("Model path escapes the models directory.");
        }
        return output;
    }

    private void writeAtomically(java.nio.file.Path output, byte[] data) throws java.io.IOException {
        java.nio.file.Files.createDirectories(output.getParent());
        java.nio.file.Path temp = java.nio.file.Files.createTempFile(output.getParent(), output.getFileName().toString(), ".tmp");
        try {
            java.nio.file.Files.write(temp, data);
            try {
                java.nio.file.Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                java.nio.file.Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    private void sendUploadStartResult(Player player, long uploadId, byte status, int maxTotalBytes, String message) {
        ByteBuf outBuf = Unpooled.buffer();
        try {
            outBuf.writeByte(71);
            writeVarLong(outBuf, uploadId);
            outBuf.writeByte(status);
            writeVarInt(outBuf, status == 0 ? UPLOAD_CHUNK_SIZE : 0);
            writeVarInt(outBuf, status == 0 ? maxTotalBytes : 0);
            writeVarInt(outBuf, status == 0 ? getUploadChunksPerTick() : 0);
            writeString(outBuf, message);
            plugin.sendYsmPacket(player, copyReadableBytes(outBuf));
        } finally {
            outBuf.release();
        }
    }

    private void sendUploadResult(Player player, long uploadId, byte status, String modelId, long hash1, long hash2, String message) {
        ByteBuf outBuf = Unpooled.buffer();
        try {
            outBuf.writeByte(74);
            writeVarLong(outBuf, uploadId);
            outBuf.writeByte(status);
            writeString(outBuf, modelId);
            writeVarLong(outBuf, hash1);
            writeVarLong(outBuf, hash2);
            writeString(outBuf, message);
            plugin.sendYsmPacket(player, copyReadableBytes(outBuf));
        } finally {
            outBuf.release();
        }
    }

    private int getUploadMaxBytes() {
        int configuredMiB = plugin.getConfig().getInt("upload.model-upload-max-mib", 128);
        int clampedMiB = Math.max(1, Math.min(configuredMiB, MAX_CLIENT_UPLOAD_BYTES / (1024 * 1024)));
        return clampedMiB * 1024 * 1024;
    }

    private int getUploadChunksPerTick() {
        int configured = plugin.getConfig().getInt("upload.model-upload-chunks-per-tick", 4);
        return Math.max(1, configured);
    }

    private int getMaxActiveUploadBytes() {
        int maxBytes = getUploadMaxBytes();
        return Math.max(maxBytes, Math.min(MAX_CLIENT_UPLOAD_BYTES, maxBytes * 2));
    }

    private int countUploadsFor(UUID playerId) {
        int count = 0;
        for (UploadSession session : uploadSessions.values()) {
            if (session.uploaderId.equals(playerId)) {
                count++;
            }
        }
        return count;
    }

    private long reservedUploadBytes() {
        long bytes = 0L;
        for (UploadSession session : uploadSessions.values()) {
            bytes += session.totalBytes;
        }
        return bytes;
    }

    private void expireUploadSessions() {
        long now = System.currentTimeMillis();
        uploadSessions.entrySet().removeIf(entry -> now - entry.getValue().lastActivityMs > UPLOAD_SESSION_TIMEOUT_MS);
    }

    private long nextUploadId() {
        long uploadId;
        do {
            uploadId = RANDOM.nextLong();
        } while (uploadId == 0L || uploadSessions.containsKey(uploadId));
        return uploadId;
    }

    private boolean hasActiveUploadForModel(String modelId) {
        return uploadSessions.values().stream().anyMatch(session -> session.modelId.equals(modelId));
    }

    private boolean tryAcceptUploadChunk(UploadSession session) {
        long now = System.currentTimeMillis();
        if (now - session.chunkWindowStartedMs >= UPLOAD_RATE_WINDOW_MS) {
            session.chunkWindowStartedMs = now;
            session.chunksInWindow = 0;
        }

        int expectedPerSecond = getUploadChunksPerTick() * 20;
        int burstAllowance = Math.max(400, expectedPerSecond * 10);
        if (session.chunksInWindow >= expectedPerSecond + burstAllowance) {
            return false;
        }

        session.chunksInWindow++;
        return true;
    }

    private String normalizeUploadedModelId(String modelId) {
        if (modelId == null) {
            return null;
        }

        String normalized = modelId.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        boolean stripped;
        do {
            stripped = false;
            for (String extension : new String[]{".ysm", ".zip", ".7z"}) {
                if (normalized.endsWith(extension)) {
                    normalized = normalized.substring(0, normalized.length() - extension.length());
                    stripped = true;
                }
            }
        } while (stripped);

        normalized = normalized.replaceAll("[^a-z0-9_./-]+", "_");
        normalized = normalized.replaceAll("/+", "/");
        if (normalized.isBlank() || normalized.contains("..") || !MODEL_ID_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private UploadImportKind importKindFromFileName(String fileName) {
        if (fileName == null) {
            return UploadImportKind.UNKNOWN;
        }

        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".ysm")) {
            return UploadImportKind.YSM;
        }
        if (normalized.endsWith(".zip")) {
            return UploadImportKind.ZIP;
        }
        if (normalized.endsWith(".7z")) {
            return UploadImportKind.SEVEN_ZIP;
        }
        return UploadImportKind.UNKNOWN;
    }

    private boolean isSha256(String value) {
        if (value.length() != 64) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private void broadcastPlayerState(Player subject, String animationId) {
        ByteBuf outBuf = Unpooled.buffer();
        try {
            outBuf.writeByte(21); // S2CSyncPlayerStatePacket
            writeVarInt(outBuf, subject.getEntityId());
            outBuf.writeShort(MODEL_SWITCH_FLAG);
            writeString(outBuf, animationId);
            broadcastToReadyPlayers(subject, copyReadableBytes(outBuf), true);
        } finally {
            outBuf.release();
        }
    }

    private void broadcastToReadyPlayers(Player subject, byte[] data, boolean includeSubject) {
        Set<UUID> receivers = new HashSet<>(trackedViewersByTarget.getOrDefault(subject.getUniqueId(), Set.of()));
        if (includeSubject) {
            receivers.add(subject.getUniqueId());
        }
        for (UUID receiverId : receivers) {
            Player receiver = plugin.getServer().getPlayer(receiverId);
            if (receiver == null || !receiver.isOnline() || receiver.getWorld() != subject.getWorld()) {
                continue;
            }
            PlayerSyncState receiverState = sessions.get(receiver.getUniqueId());
            if (receiverState != null && receiverState.step >= 2) {
                plugin.sendYsmPacket(receiver, data);
            }
        }
    }

    private byte[] copyReadableBytes(ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        return data;
    }

    private void handleCryptoPayload(Player player, PlayerSyncState state, byte[] data) throws Exception {
        if (state.step == 1) {
            byte[] decrypted = YsmCrypt.decrypt(data, state.key1);
            if (decrypted == null || decrypted.length < 56) return;

            state.clientNextKey = Arrays.copyOfRange(decrypted, decrypted.length - 56, decrypted.length);
            byte[] payload = Arrays.copyOfRange(decrypted, 0, decrypted.length - 56);

            try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(payload))) {
                buf.skipGarbageHeader();
                if (buf.getRawBuf().readByte() != 0x02) return;
            }

            plugin.getLogger().info("Received Packet 02 (Pong) from " + player.getName() + ". Sending Catalog (Packet 03)...");
            
            state.step = 2;
            sendPacket03(player, state);
            
            // Restore saved model choice from PersistentDataContainer
            org.bukkit.persistence.PersistentDataContainer pdc = player.getPersistentDataContainer();
            if (pdc.has(MODEL_KEY, org.bukkit.persistence.PersistentDataType.STRING)) {
                state.modelId = pdc.get(MODEL_KEY, org.bukkit.persistence.PersistentDataType.STRING);
                String storedTexture = pdc.get(TEXTURE_KEY, org.bukkit.persistence.PersistentDataType.STRING);
                state.textureId = storedTexture == null ? "default" : storedTexture;
            }
            if (!"default".equals(state.modelId)) {
                String resolvedTexture = resolveTextureOrDefault(serverModels.get(state.modelId), state.textureId);
                if (resolvedTexture == null) {
                    setModelChoice(player, state, "default", "default");
                } else if (!resolvedTexture.equals(state.textureId)) {
                    setModelChoice(player, state, state.modelId, resolvedTexture);
                }
            }

            broadcastModelChange(player, state.modelId, state.textureId, player);
            resyncPlayer(player);
            scheduleModelStateResyncs(player, state, 10L, 40L, 100L);
            
            // Note: After packet 03, the client is Synced. 
            // It might send Packet 04 via discriminator 2.
        } else if (state.step == 2 || state.step == 3) {
            byte[] decrypted = YsmCrypt.decrypt(data, state.key1);
            if (decrypted == null) return;
            
            try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                buf.skipGarbageHeader();
                byte packetId = buf.getRawBuf().readByte();
                
                if (packetId != 0x04) {
                    return;
                }

                int numRequests = buf.readVarInt();
                java.util.List<ServerModel> requested = new java.util.ArrayList<>();
                for (int i = 0; i < numRequests; i++) {
                    long h1 = readVarLong(buf.getRawBuf());
                    long h2 = readVarLong(buf.getRawBuf());
                    for (ServerModel sm : serverModels.values()) {
                        if (sm.hash1 == h1 && sm.hash2 == h2) {
                            requested.add(sm);
                            break;
                        }
                    }
                }
                state.step = 3;
                plugin.getLogger().info("" + player.getName() + " requested " + requested.size() + " models.");
                sendPacket05(player, state, requested);
                state.resyncAfterTransfers = !requested.isEmpty();
                broadcastModelChange(player, state.modelId, state.textureId, player);
                resyncPlayer(player);
                if (requested.isEmpty()) {
                    scheduleModelStateResyncs(player, state, 2L, 20L, 60L);
                }
            }
        }
    }

    private void initiateHandshake(Player player, PlayerSyncState state) throws Exception {
        int garbageLen = 16 + RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        RANDOM.nextBytes(garbage);

        ByteBuf buf = Unpooled.buffer();
        try (YSMByteBuf outBuf = new YSMByteBuf(buf)) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.writeByte((byte) 0x01); // Packet 01: Public Key Exchange
            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), YsmCrypt.publicKey, true);

            state.step = 1;
            state.key1 = result.nextKey();
            state.lastActiveMs = System.currentTimeMillis();

            sendEncrypted(player, result.data());
            plugin.getLogger().info("Sent Handshake Packet 01 to " + player.getName());
        }
    }

    public void onPlayerRespawn(Player player) {
        resyncPlayer(player);
    }

    public void onPlayerChangedWorld(Player player) {
        clearTrackingFor(player.getUniqueId());
        resyncPlayer(player);
    }

    private void resyncPlayer(Player player) {
        PlayerSyncState state = sessions.get(player.getUniqueId());
        if (state == null || state.step < 2) return;

        for (UUID viewerId : trackedViewersByTarget.getOrDefault(player.getUniqueId(), Set.of())) {
            Player viewer = plugin.getServer().getPlayer(viewerId);
            PlayerSyncState viewerState = sessions.get(viewerId);
            if (viewer != null && viewer.isOnline() && viewer.getWorld() == player.getWorld()
                    && viewerState != null && viewerState.step >= 2) {
                broadcastModelChange(player, state.modelId, state.textureId, viewer);
            }
        }

        for (UUID targetId : trackedTargetsByViewer.getOrDefault(player.getUniqueId(), Set.of())) {
            Player target = plugin.getServer().getPlayer(targetId);
            PlayerSyncState targetState = sessions.get(targetId);
            if (target != null && target.isOnline() && target.getWorld() == player.getWorld()
                    && targetState != null && targetState.step >= 2) {
                broadcastModelChange(target, targetState.modelId, targetState.textureId, player);
            }
        }
    }

    private void scheduleModelStateResyncs(Player player, PlayerSyncState state, long... delays) {
        UUID playerId = player.getUniqueId();
        long generation = ++state.modelStateResyncGeneration;

        // Model payloads are decoded on a client worker thread while ordinary state
        // packets are applied on the game thread. A few small, bounded retries make
        // the plugin bridge robust without changing the existing client protocol.
        for (long delay : delays) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player currentPlayer = plugin.getServer().getPlayer(playerId);
                PlayerSyncState currentState = sessions.get(playerId);
                if (currentPlayer == null || !currentPlayer.isOnline()
                        || currentState != state
                        || currentState.step < 2
                        || currentState.modelStateResyncGeneration != generation) {
                    return;
                }

                broadcastModelChange(currentPlayer, currentState.modelId, currentState.textureId, currentPlayer);
                resyncPlayer(currentPlayer);
            }, Math.max(1L, delay));
        }
    }

    public void onPlayerTrack(Player viewer, Player target) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return;
        }

        trackedViewersByTarget.computeIfAbsent(target.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(viewer.getUniqueId());
        trackedTargetsByViewer.computeIfAbsent(viewer.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(target.getUniqueId());

        PlayerSyncState targetState = sessions.get(target.getUniqueId());
        PlayerSyncState viewerState = sessions.get(viewer.getUniqueId());

        if (targetState != null && targetState.step >= 2 && viewerState != null && viewerState.step >= 2) {
            broadcastModelChange(target, targetState.modelId, targetState.textureId, viewer);
        }
    }

    public void onPlayerUntrack(Player viewer, Player target) {
        removeTracking(viewer.getUniqueId(), target.getUniqueId());
    }

    private void clearTrackingFor(UUID playerId) {
        Set<UUID> viewers = trackedViewersByTarget.remove(playerId);
        if (viewers != null) {
            for (UUID viewerId : viewers) {
                Set<UUID> targets = trackedTargetsByViewer.get(viewerId);
                if (targets != null) {
                    targets.remove(playerId);
                    if (targets.isEmpty()) {
                        trackedTargetsByViewer.remove(viewerId, targets);
                    }
                }
            }
        }

        Set<UUID> targets = trackedTargetsByViewer.remove(playerId);
        if (targets != null) {
            for (UUID targetId : targets) {
                Set<UUID> viewersByTarget = trackedViewersByTarget.get(targetId);
                if (viewersByTarget != null) {
                    viewersByTarget.remove(playerId);
                    if (viewersByTarget.isEmpty()) {
                        trackedViewersByTarget.remove(targetId, viewersByTarget);
                    }
                }
            }
        }
    }

    private void removeTracking(UUID viewerId, UUID targetId) {
        Set<UUID> viewers = trackedViewersByTarget.get(targetId);
        if (viewers != null) {
            viewers.remove(viewerId);
            if (viewers.isEmpty()) {
                trackedViewersByTarget.remove(targetId, viewers);
            }
        }

        Set<UUID> targets = trackedTargetsByViewer.get(viewerId);
        if (targets != null) {
            targets.remove(targetId);
            if (targets.isEmpty()) {
                trackedTargetsByViewer.remove(viewerId, targets);
            }
        }
    }

    private void sendPacket03(Player player, PlayerSyncState state) throws Exception {
        int garbageLen = 16 + RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        RANDOM.nextBytes(garbage);

        ByteBuf buf = Unpooled.buffer();
        try (YSMByteBuf outBuf = new YSMByteBuf(buf)) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.writeVarInt(3); // Packet 03
            outBuf.writeVarLong(0L); // Full server catalog

            outBuf.getRawBuf().writeBytes(SERVER_KEY);
            if (state.clientKey == null) state.clientKey = new byte[56];
            outBuf.getRawBuf().writeBytes(state.clientKey);

            outBuf.writeVarInt(serverModels.size()); // models
            for (ServerModel sm : serverModels.values()) {
                writeVarLong(outBuf.getRawBuf(), sm.hash1);
                writeVarLong(outBuf.getRawBuf(), sm.hash2);
                outBuf.writeString(sm.modelId);
                outBuf.writeVarInt(0); // isAuth
                outBuf.writeVarInt(0); // isCustomSkinModel
                outBuf.writeVarInt(1); // version
            }
            
            outBuf.writeVarInt(0); // 0 deleted models

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), state.clientNextKey, false);
            sendEncrypted(player, result.data());
        }
    }
    
    private void broadcastPartialCatalog(ServerModel sm) {
        for (Player receiver : plugin.getServer().getOnlinePlayers()) {
            PlayerSyncState receiverState = sessions.get(receiver.getUniqueId());
            if (receiverState == null || receiverState.step < 2) continue;

            try {
                int garbageLen = 16 + RANDOM.nextInt(48);
                byte[] garbage = new byte[garbageLen];
                RANDOM.nextBytes(garbage);

                ByteBuf outBuf = Unpooled.buffer();
                try (YSMByteBuf ysmOut = new YSMByteBuf(outBuf)) {
                    ysmOut.writeGarbageHeader(garbageLen, garbage);
                    ysmOut.writeVarInt(3); // Discriminator 03
                    ysmOut.writeVarLong(-1L); // Partial sync indicator (folderHash = -1)
                    
                    ysmOut.getRawBuf().writeBytes(SERVER_KEY);
                    if (receiverState.clientKey == null) receiverState.clientKey = new byte[56];
                    ysmOut.getRawBuf().writeBytes(receiverState.clientKey);

                    ysmOut.writeVarInt(1); // 1 new model
                    writeVarLong(ysmOut.getRawBuf(), sm.hash1);
                    writeVarLong(ysmOut.getRawBuf(), sm.hash2);
                    ysmOut.writeString(sm.modelId);
                    ysmOut.writeVarInt(0); // isAuth
                    ysmOut.writeVarInt(0); // isCustomSkinModel
                    ysmOut.writeVarInt(1); // version
                    
                    ysmOut.writeVarInt(0); // 0 deleted models
                    
                    YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(ysmOut.toArray(), receiverState.clientNextKey, false);
                    sendEncrypted(receiver, result.data());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendPacket05(Player player, PlayerSyncState state, java.util.List<ServerModel> requested) throws Exception {
        for (ServerModel sm : requested) {
            byte[] payloadData = sm.data;
            try {
                if (sm.data.length >= 4 && sm.data[0] == 'Y' && sm.data[1] == 'S' && sm.data[2] == 'M' && sm.data[3] == 'M') {
                    payloadData = new byte[sm.data.length - 4];
                    System.arraycopy(sm.data, 4, payloadData, 0, payloadData.length);
                } else {
                    // Fallback for legacy encrypted models that weren't compiled for some reason
                    byte[] decompressed = rip.ysm.security.YsmCrypt.decryptYsmFile(sm.data);
                    try (com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer(decompressed, 32)) {
                        com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                        try (rip.ysm.security.YSMByteBuf serialized = com.elfmcys.yesstevemodel.resource.YSMBinarySerializer.serialize(rawModel, 32, true)) {
                            io.netty.buffer.ByteBuf raw = serialized.getRawBuf();
                            if (raw.hasArray()) {
                                int off = raw.arrayOffset() + raw.readerIndex();
                                int len = raw.readableBytes();
                                byte[] subArr = new byte[len];
                                System.arraycopy(raw.array(), off, subArr, 0, len);
                                payloadData = subArr;
                            } else {
                                payloadData = serialized.toArray();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not prepare model file " + sm.modelId + " for format 32. Sending raw data. Reason: " + e.getMessage());
                e.printStackTrace();
            }
            byte[] encryptedPayload = rip.ysm.security.YsmCrypt.encryptServerCache(payloadData, SERVER_KEY, sm.hash1, sm.hash2);
            state.transferQueue.add(new PendingTransfer(sm, encryptedPayload));
        }
    }

    private int roundRobinIndex = 0;
    private int lastActiveCount = 0;

    private void processNetworkQueues() {
        expireUploadSessions();
        globalTokens += globalBytesPerTick;
        if (globalTokens > globalBytesPerTick * 20) globalTokens = globalBytesPerTick * 20;

        java.util.List<java.util.Map.Entry<Player, PlayerSyncState>> activeStates = new java.util.ArrayList<>();

        for (java.util.Map.Entry<java.util.UUID, PlayerSyncState> entry : sessions.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            
            PlayerSyncState state = entry.getValue();
            state.tokens += playerBytesPerTick;
            if (state.tokens > playerBytesPerTick * 20) state.tokens = playerBytesPerTick * 20;
            
            if (state.currentTransfer != null || !state.transferQueue.isEmpty()) {
                activeStates.add(new java.util.AbstractMap.SimpleEntry<>(player, state));
            }
        }

        if (activeStates.isEmpty()) return;

        // FIX: reset round-robin index when player count changes to avoid index-out-of-bounds
        if (activeStates.size() != lastActiveCount) {
            roundRobinIndex = 0;
            lastActiveCount = activeStates.size();
        }

        boolean madeProgress = true;
        while (globalTokens > 0 && madeProgress) {
            madeProgress = false;
            
            for (int i = 0; i < activeStates.size(); i++) {
                roundRobinIndex = (roundRobinIndex + 1) % activeStates.size();
                java.util.Map.Entry<Player, PlayerSyncState> activeEntry = activeStates.get(roundRobinIndex);
                Player player = activeEntry.getKey();
                PlayerSyncState state = activeEntry.getValue();
                
                if (state.currentTransfer == null && !state.transferQueue.isEmpty()) {
                    state.currentTransfer = state.transferQueue.poll();
                }
                
                PendingTransfer pt = state.currentTransfer;
                if (pt == null) continue;
                
                int remaining = pt.encryptedPayload.length - pt.offset;
                int sendSize = Math.min(32000, remaining);
                
                if (state.tokens < sendSize && state.tokens < playerBytesPerTick * 5 && sendSize == 32000) continue;
                if (globalTokens < sendSize && globalTokens < globalBytesPerTick * 5 && sendSize == 32000) continue;
                
                try {
                    int garbageLen = 16 + RANDOM.nextInt(48);
                    byte[] garbage = new byte[garbageLen];
                    RANDOM.nextBytes(garbage);

                    io.netty.buffer.ByteBuf outBuf = io.netty.buffer.Unpooled.buffer();
                    try (rip.ysm.security.YSMByteBuf ysmOut = new rip.ysm.security.YSMByteBuf(outBuf)) {
                        ysmOut.writeGarbageHeader(garbageLen, garbage);
                        ysmOut.writeVarInt(5); // Discriminator 05
                        
                        writeVarLong(ysmOut.getRawBuf(), pt.sm.hash1);
                        writeVarLong(ysmOut.getRawBuf(), pt.sm.hash2);
                        
                        ysmOut.writeVarInt(pt.encryptedPayload.length);
                        ysmOut.writeVarInt(pt.offset);
                        ysmOut.writeVarInt(sendSize);
                        ysmOut.getRawBuf().writeBytes(pt.encryptedPayload, pt.offset, sendSize);
                        
                        rip.ysm.security.YsmCrypt.EncryptedPacket result = rip.ysm.security.YsmCrypt.encrypt(ysmOut.toArray(), state.key1, false);
                        sendEncrypted(player, result.data());
                    }
                    
                    pt.offset += sendSize;
                    state.tokens -= sendSize;
                    globalTokens -= sendSize;
                    madeProgress = true;
                    
                    if (pt.offset >= pt.encryptedPayload.length) {
                        state.currentTransfer = null;
                        if (state.transferQueue.isEmpty() && state.resyncAfterTransfers) {
                            state.resyncAfterTransfers = false;
                            scheduleModelStateResyncs(player, state, 1L, 20L, 60L);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    state.currentTransfer = null;
                }
            }
        }
    }

    private void sendEncrypted(Player player, byte[] encryptedData) {
        ByteBuf buf = Unpooled.buffer();
        try {
            buf.writeByte(1); // discriminator for S2CModelSyncPayload
            buf.writeBytes(encryptedData);
            sendRaw(player, buf);
        } finally {
            buf.release();
        }
    }

    private void sendRaw(Player player, ByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        plugin.sendYsmPacket(player, data);
    }

    private void broadcastModelChange(Player subject, String modelId, String textureId, Player receiverOrNull) {
        ByteBuf outBuf = Unpooled.buffer();
        try {
            outBuf.writeByte(4); // ID 4: S2CSetModelAndTexturePacket
            writeVarInt(outBuf, subject.getEntityId());
            writeString(outBuf, modelId);
            writeString(outBuf, textureId);
            outBuf.writeBoolean(false); // disabled
            
            PlayerSyncState subjectState = sessions.get(subject.getUniqueId());
            String animationId = subjectState == null ? "" : subjectState.animationId;

            // Embedded S2CSyncPlayerStatePacket
            writeVarInt(outBuf, subject.getEntityId());
            outBuf.writeShort(MODEL_SWITCH_FLAG);
            writeString(outBuf, animationId);
            
            byte[] finalData = copyReadableBytes(outBuf);

            if (receiverOrNull != null) {
                PlayerSyncState receiverState = sessions.get(receiverOrNull.getUniqueId());
                if (receiverState != null && receiverState.step >= 2) {
                    plugin.sendYsmPacket(receiverOrNull, finalData);
                }
            } else {
                broadcastToReadyPlayers(subject, finalData, false);
            }
        } finally {
            outBuf.release(); // FIX: release ByteBuf to prevent off-heap memory leak
        }
    }
}
