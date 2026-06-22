package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.NativeLibLoader;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.gui.IGuiWidget;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.model.LazyModelAssembly;
import com.elfmcys.yesstevemodel.client.model.ModelAssemblyFactory;
import com.elfmcys.yesstevemodel.client.model.ProjectileModelBundle;
import com.elfmcys.yesstevemodel.client.model.VehicleModelBundle;
import com.elfmcys.yesstevemodel.client.texture.OuterFileTexture;
import com.elfmcys.yesstevemodel.client.upload.IResourceLocatable;
import com.elfmcys.yesstevemodel.client.upload.ModelUploadSession;
import com.elfmcys.yesstevemodel.client.upload.UploadManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SModelSyncPayload;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMClientMapper;
import com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer;
import com.elfmcys.yesstevemodel.resource.models.ModelPackData;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.elfmcys.yesstevemodel.util.ModelMemoryProfiler;
import com.elfmcys.yesstevemodel.util.YSMThreadPool;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.elfmcys.yesstevemodel.mixin.client.MinecraftAccessor;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.jetbrains.annotations.Nullable;
import rip.ysm.legacy.YesModelUtils;
import rip.ysm.security.YSMByteBuf;
import rip.ysm.security.YSMClientCache;
import rip.ysm.security.YsmCrypt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Environment(EnvType.CLIENT)
public class ClientModelManager {
    private static int syncStep = 1;
    private static byte[] key1;
    private static byte[] lastKey;
    private static byte[] serverKey;
    private static byte[] clientKey;
    public static final byte[] FIXED_CACHE_KEY = new byte[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
            32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47,
            48, 49, 50, 51, 52, 53, 54, 55
    };
    private static String currentCacheFolderName;
    private static final AtomicInteger pendingModelsCount = new AtomicInteger(0);
    private static final AtomicInteger syncSessionId = new AtomicInteger(0);
    private static final int MAX_SERVER_MODEL_BYTES = 512 * 1024 * 1024;

    private static final ThreadPoolExecutor modelPhraseExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "YSM-Model-Parse-Thread");
                t.setDaemon(true);
                return t;
            }
    );

    private static final Map<UUID, ServerModelContext> serverModels = new ConcurrentHashMap<>();

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    private static volatile ModelAssembly localModelContext;
    private static volatile Runnable pendingModelCallback;
    private static IResourceLocatable defaultTexture;
    private static volatile Connection serverConnection;

    private static volatile Map<String, ModelAssembly> modelAssemblyMap = Object2ReferenceMaps.emptyMap();
    private static volatile Map<String, ModelPackData> modelPackMap = new Object2ReferenceOpenHashMap<>();
    private static final ConcurrentHashMap<String, Long> modelLastUsedAt = new ConcurrentHashMap<>();
    private static final Set<String> gpuCacheTrimmedModels = ConcurrentHashMap.newKeySet();

    private static final ConcurrentLinkedQueue<Pair<ModelAssembly, String>> pendingModelQueue = new ConcurrentLinkedQueue<>();
    private static final WeakHashMap<IGuiWidget, Object> guiWidgets = new WeakHashMap<>();
    private static final Set<String> localOnlyModelIds = ConcurrentHashMap.newKeySet();
    private static final Set<String> knownServerModelIds = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, LocalModelSource> localModelSources = new ConcurrentHashMap<>();
    private static final AtomicBoolean localReloadInProgress = new AtomicBoolean(false);
    private static final AtomicBoolean localModelsLoadedOnce = new AtomicBoolean(false);
    private static final SyncStatus syncState = new SyncStatus();
    private static volatile boolean isOysmServer = false;
    private static volatile boolean allowUpload = false;

    public enum SyncState {
        WAITING, LOADING, IDLE, PREPARING, SYNCING
    }

    public static class ServerModelContext {
        public final UUID uuid;
        public final long hash1;
        public final long hash2;
        public final String modelId;
        public final boolean isAuth;
        public final int isCustomSkinModel;
        public final int version;

        public byte[] fileBuffer;
        public int totalSize;
        public int bytesReceived;

        public ServerModelContext(long hash1, long hash2, String modelId, boolean isAuth, int isCustomSkinModel, int version) {
            this.uuid = new UUID(hash1, hash2);
            this.hash1 = hash1;
            this.hash2 = hash2;
            this.modelId = modelId;
            this.isAuth = isAuth;
            this.isCustomSkinModel = isCustomSkinModel;
            this.version = version;
        }
    }

    public static void loadDefaultModel() {
        YesSteveModel.LOGGER.info("[BPM] Loading builtin default model...");
        try {
            String resourcePath = "/assets/better_player_model/builtin/default";
            URL resourceUrl = YesSteveModel.class.getResource(resourcePath);
            if (resourceUrl == null) {
                YesSteveModel.LOGGER.error("[BPM] Builtin default model not found in classpath: " + resourcePath);
                return;
            }
            URI uri = resourceUrl.toURI();
            Path defaultPath;
            FileSystem jarFs = null;
            if ("jar".equals(uri.getScheme())) {
                try {
                    jarFs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    jarFs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                defaultPath = jarFs.getPath(resourcePath);
            } else {
                defaultPath = Paths.get(uri);
            }

            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(defaultPath)) {
                RawYsmModel rawModel = deserializer.deserialize();

                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, "default");


                onModelDataReceived(parsedBundle, "default", true, false);
                YesSteveModel.LOGGER.info("[BPM] Successfully pushed Default Model to render queue.");
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Failed to dispatch Default Model", e);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Failed to load builtin default model", e);
        }
    }

    private static void processServerData(ByteBuffer data) {
        if (data == null) {
            resetClientState();
            return;
        }
        try {
            if (!data.hasRemaining() && data.position() > 0) {
                data.flip();
            }
            if (!data.hasRemaining()) return;

            byte[] packetBytes = new byte[data.remaining()];
            data.get(packetBytes);

            boolean processed = false;
            
            if (syncStep == 3) {
                // Expecting Packet 05 (chunks)
                if (key1 != null) {
                    byte[] d = YsmCrypt.decrypt(packetBytes.clone(), key1);
                    if (d != null && d.length > 0) {
                        try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(d))) {
                            buf.getRawBuf().markReaderIndex();
                            buf.skipGarbageHeader();
                            if (buf.getRawBuf().readableBytes() >= 1) {
                                int type = buf.readVarInt();
                                if (type == 5) {
                                    buf.getRawBuf().resetReaderIndex();
                                    handlePacket05(buf);
                                    processed = true;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (!processed && (syncStep == 1 || syncStep == 2 || syncStep == 3)) {
                // Expecting Packet 03 (Catalog)
                if (lastKey != null) {
                    byte[] d = YsmCrypt.decrypt(packetBytes.clone(), lastKey);
                    if (d != null && d.length > 0) {
                        try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(d))) {
                            buf.getRawBuf().markReaderIndex();
                            buf.skipGarbageHeader();
                            if (buf.getRawBuf().readableBytes() >= 1) {
                                int type = buf.readVarInt();
                                if (type == 3) {
                                    buf.getRawBuf().resetReaderIndex();
                                    handlePacket03(buf);
                                    processed = true;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Fallback: Always check if the server is resetting the connection (Packet 01)
            if (!processed) {
                byte[] d = YsmCrypt.decrypt(packetBytes.clone(), YsmCrypt.publicKey);
                if (d != null && d.length > 0) {
                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(d))) {
                        buf.getRawBuf().markReaderIndex();
                        buf.skipGarbageHeader();
                        if (buf.getRawBuf().readableBytes() >= 1) {
                            int type = buf.readVarInt();
                            if (type == 1) {
                                handlePacket01(d);
                                processed = true;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Sync Error at step " + syncStep, e);
        }
    }

    private static void handlePacket01(byte[] decryptedBuffer) throws Exception {
        key1 = new byte[56];
        System.arraycopy(decryptedBuffer, decryptedBuffer.length - 56, key1, 0, 56);
        syncStep = 2;

        YesSteveModel.LOGGER.info("[BPM-NET] CLIENT: Received Packet 01 from server. Handshake initiated. Public key decrypted. Exchanged Key1.");
        onSyncProgress(-1); // Preparing GUI stage

        int garbageLen = 16 + SECURE_RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        SECURE_RANDOM.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.getRawBuf().writeByte(0x02);
            outBuf.getRawBuf().writeByte(0x00);

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), key1, true);
            lastKey = result.nextKey();

            YesSteveModel.LOGGER.info("[BPM-NET] CLIENT: Sent Packet 02 (Pong) to server. Packet size: {} bytes.", result.data().length);
            sendModelFile(ByteBuffer.wrap(result.data()));
        }
    }

    private record ModelHash(long hash1, long hash2) {
    }

    private record LocalModelSource(Path path, String fileName, boolean directory) {
        byte[] readBytes() throws IOException {
            if (directory) {
                return zipDirectory(path);
            }
            return readLimitedFileBytes(path, MAX_SERVER_MODEL_BYTES);
        }
    }

    private static final List<ModelHash> cachedModelHashes = new ArrayList<>();

    private static void handlePacket03(YSMByteBuf buf) throws Exception {
        int currentSession = syncSessionId.get();
        buf.skipGarbageHeader();
        int type = buf.readVarInt(); // expect 3
        long folderHash = buf.readVarLong();
        boolean partialSync = folderHash == -1L;
        if (!partialSync || currentCacheFolderName == null) {
            currentCacheFolderName = partialSync ? "0" : Long.toHexString(folderHash);
        }

        serverKey = new byte[56];
        buf.getRawBuf().readBytes(serverKey);

        clientKey = new byte[56];
        buf.getRawBuf().readBytes(clientKey);

        File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(currentCacheFolderName).toFile();
        if (!cacheDir.exists()) cacheDir.mkdirs();

        Map<UUID, File> localCacheMap = YSMClientCache.buildCacheIndex(cacheDir, FIXED_CACHE_KEY);
        List<ModelHash> modelsToRequest = new ArrayList<>();

        int unkSize = buf.readVarInt();
        YesSteveModel.LOGGER.info("[BPM-NET] CLIENT: Received Packet 03 (Catalog) from server. Catalog has {} allowed models. Reading definitions...", unkSize);
        onSyncProgress(unkSize);
        modelsToRequest.clear();
        cachedModelHashes.clear();

        Set<String> validServerModelIds = new HashSet<>();
        List<String> previousModelIds = new ArrayList<>();
        List<String> updatedModelIds = new ArrayList<>();
        List<Boolean> isModelReadyList = new ArrayList<>();
        if (!partialSync) {
            knownServerModelIds.clear();
        }

        for (int i = 0; i < unkSize; i++) {
            long hash1 = buf.readVarLong();
            long hash2 = buf.readVarLong();
            ModelHash mHash = new ModelHash(hash1, hash2);
            cachedModelHashes.add(mHash);

            String modelId = buf.readString();
            boolean isAuth = buf.readVarInt() == 1;// isAuth
            int isCustomSkinModel = buf.readVarInt();// is misc/2_steve misc/1_alex
//            System.out.println("Received model hash: " + mHash + ", id: " + modelId + ", unk1: " + isAuth + ", unk2: " + isCustomSkinModel);
            int version = buf.readVarInt(); // зЂµйЂ›з°¬йЏ‚е›¦ж¬ўжѕ¶и§„ж№­йЌ”зЉІз‘йђЁе‹¬ДЃйЌЁе¬¶зґќж¶“?5535

            ServerModelContext ctx = new ServerModelContext(hash1, hash2, modelId, isAuth, isCustomSkinModel, version);
            ServerModelContext existing = serverModels.putIfAbsent(ctx.uuid, ctx);
            if (existing != null) {
                ctx = existing;
            }
            validServerModelIds.add(modelId);
            knownServerModelIds.add(modelId);
            localOnlyModelIds.remove(modelId);
            localModelSources.remove(modelId);

            File cachedFile = localCacheMap.get(ctx.uuid);
            boolean isFileValid = YSMClientCache.verifyFileContent(cachedFile, hash1, hash2, FIXED_CACHE_KEY);

            boolean alreadyInMemory = modelAssemblyMap != null && modelAssemblyMap.containsKey(modelId);

            if (isFileValid) {
                YesSteveModel.LOGGER.info("[BPM] Cache HIT & Validated: " + ctx.uuid);
                if (alreadyInMemory) {
                    previousModelIds.add(modelId);
                    updatedModelIds.add(modelId);
                    isModelReadyList.add(isAuth);
                    incrementSyncProgress();
                } else {
                    LazyModelAssembly lazyAssembly = new LazyModelAssembly(modelId, cachedFile, FIXED_CACHE_KEY, isAuth);
                    pendingModelQueue.add(Pair.of(lazyAssembly, modelId));
                    incrementSyncProgress();
                }
            } else if (ctx.fileBuffer != null) {
                YesSteveModel.LOGGER.info("[BPM] Model is already downloading: " + ctx.uuid + " -> Skipping request.");
            } else {
                YesSteveModel.LOGGER.info("[BPM] Cache MISS or Invalid: " + ctx.uuid + " -> Requesting...");
                modelsToRequest.add(mHash);
            }
        }

        int unkSize2 = buf.readVarInt();
        List<ModelPackData> parsedPacks = new ArrayList<>();

        for (int i = 0; i < unkSize2; i++) {
            String folderPath = buf.readString();

            OuterFileTexture iconTexture = null;
            if (buf.readVarInt() != 0) {
                byte[] textureData = buf.readByteArray();
                int textureWidth = buf.readVarInt();
                int textureHeight = buf.readVarInt();
                int imageFormat = buf.readVarInt();
                int unkImageData = buf.readVarInt();

                byte[] png = YSMClientMapper.toPng(textureData, imageFormat, textureWidth, textureHeight);

                iconTexture = new OuterFileTexture(png);
            }

            String folderName = "";
            String folderDesc = "";
            int hasYSMPackInfo = buf.readVarInt();
            if (hasYSMPackInfo != 0) {
                folderName = buf.readString();
                folderDesc = buf.readString();
            }

            Map<String, Map<String, String>> languageData = new HashMap<>();
            int languageSize = buf.readVarInt();
            for (int j = 0; j < languageSize; j++) {
                String languageType = buf.readString();
                int translateKeySize = buf.readVarInt();
                Map<String, String> translationMap = new HashMap<>();
                for (int k = 0; k < translateKeySize; k++) {
                    translationMap.put(buf.readString(), buf.readString());
                }
                languageData.put(languageType, translationMap);
            }
            parsedPacks.add(new ModelPackData(folderPath, folderName, folderDesc, iconTexture, languageData));
        }

        if (!partialSync && !parsedPacks.isEmpty()) {
            onModelPacksReceived(parsedPacks.toArray(new ModelPackData[0]));
        }

        List<String> modelsToRemove = new ArrayList<>();
        if (!partialSync && modelAssemblyMap != null) {
            for (String loadedId : modelAssemblyMap.keySet()) {
                if ("default".equals(loadedId)) continue;

                if (!validServerModelIds.contains(loadedId) && !localOnlyModelIds.contains(loadedId)) {
                    modelsToRemove.add(loadedId);
                } else if (modelsToRequest.stream().anyMatch(h -> serverModels.containsKey(new UUID(h.hash1, h.hash2)) && serverModels.get(new UUID(h.hash1, h.hash2)).modelId.equals(loadedId))) {
                    modelsToRemove.add(loadedId);
                }
            }
        }

        if (!modelsToRemove.isEmpty() || !previousModelIds.isEmpty()) {
            boolean[] readyArr = new boolean[isModelReadyList.size()];
            for (int j = 0; j < isModelReadyList.size(); j++) {
                readyArr[j] = isModelReadyList.get(j);
            }

            onModelContextsUpdated(
                    modelsToRemove.isEmpty() ? null : modelsToRemove.toArray(new String[0]),
                    previousModelIds.isEmpty() ? null : previousModelIds.toArray(new String[0]),
                    updatedModelIds.isEmpty() ? null : updatedModelIds.toArray(new String[0]),
                    readyArr
            );
            YesSteveModel.LOGGER.info("[BPM] Cleaned up {} outdated models and updated {} existing models during sync.", modelsToRemove.size(), previousModelIds.size());
        }

        syncStep = 3;
        
        if (modelsToRequest.isEmpty()) {
            if (pendingModelsCount.get() == 0) {
                modelPhraseExecutor.submit(() -> {
                    if (syncSessionId.get() != currentSession) return;
                    YesSteveModel.LOGGER.info("[BPM-NET] CLIENT: All models loaded from local cache. Handshake complete!");
                    onSyncComplete();
                });
            }
        } else {
            if (syncSessionId.get() != currentSession) return;
            pendingModelsCount.addAndGet(modelsToRequest.size());
            
            int garbageLen = 16 + SECURE_RANDOM.nextInt(48);
            byte[] garbage = new byte[garbageLen];
            SECURE_RANDOM.nextBytes(garbage);

            try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                outBuf.writeGarbageHeader(garbageLen, garbage);
                outBuf.getRawBuf().writeByte(0x04);

                outBuf.writeVarInt(modelsToRequest.size());
                for (ModelHash h : modelsToRequest) {
                    outBuf.writeVarLong(h.hash1);
                    outBuf.writeVarLong(h.hash2);
                }

                YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), key1, false);
                YesSteveModel.LOGGER.info("[BPM-NET] CLIENT: Cache validation complete. Hits: {}, Misses: {}. Sending Packet 04 to request {} models. Size: {} bytes.", unkSize - modelsToRequest.size(), modelsToRequest.size(), modelsToRequest.size(), result.data().length);
                sendModelFile(ByteBuffer.wrap(result.data()));
            }
        }
    }

    private static void handlePacket05(YSMByteBuf buf) throws Exception {
        int currentSession = syncSessionId.get();
        buf.skipGarbageHeader();
        int type = buf.readVarInt();
        if (type != 5) return;

        long hash1 = buf.readVarLong();
        long hash2 = buf.readVarLong();
        UUID uuid = new UUID(hash1, hash2);

        ServerModelContext ctx = serverModels.get(uuid);
        if (ctx == null) {
            YesSteveModel.LOGGER.warn("[BPM] Received unexpected file chunk for model: " + uuid);
            return;
        }

        int totalSize = buf.readVarInt();
        int chunkOffset = buf.readVarInt();
        int chunkLength = buf.readVarInt();
        if (totalSize <= 0 || totalSize > MAX_SERVER_MODEL_BYTES) {
            throw new IOException("Invalid server model size: " + totalSize);
        }
        if (chunkOffset < 0 || chunkLength < 0 || (long) chunkOffset + (long) chunkLength > totalSize || chunkLength > buf.getRawBuf().readableBytes()) {
            throw new IOException("Invalid server model chunk: offset=" + chunkOffset + ", length=" + chunkLength + ", total=" + totalSize);
        }

        // Initialize buffer on first reception
        if (ctx.fileBuffer == null) {
            ctx.fileBuffer = new byte[totalSize];
            ctx.totalSize = totalSize;
            ctx.bytesReceived = 0;
        } else if (ctx.totalSize != totalSize) {
            throw new IOException("Server model size changed during transfer");
        }

        buf.getRawBuf().readBytes(ctx.fileBuffer, chunkOffset, chunkLength);
        ctx.bytesReceived += chunkLength;
        YesSteveModel.LOGGER.debug("[BPM-NET] CLIENT: Received model chunk offset {}/{} ({} bytes) for model '{}' (hash: {})", chunkOffset, totalSize, chunkLength, ctx.modelId, uuid);

        if (ctx.bytesReceived >= totalSize) {
            byte[] fileBuffer = ctx.fileBuffer;
            ctx.fileBuffer = null;

            modelPhraseExecutor.submit(() -> {
                if (syncSessionId.get() != currentSession) return;
                if (clientKey == null) return;
                try {
                    String folder = currentCacheFolderName != null ? currentCacheFolderName : "default_cache";
                    File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();
                    if (!cacheDir.exists()) cacheDir.mkdirs();

                    byte[] cachedFileData = YsmCrypt.transcodeServerDataToClientCache(fileBuffer, serverKey, FIXED_CACHE_KEY, hash1, hash2);
                    ModelMemoryProfiler.logBytes("download-transcoded-cache", ctx.modelId, cachedFileData);

                    String legitFileName = YSMClientCache.generateCacheFileName(hash1, hash2, FIXED_CACHE_KEY);
                    File outFile = new File(cacheDir, legitFileName);

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(cachedFileData);
                    }

                    YesSteveModel.LOGGER.info("[BPM-NET] CLIENT: Downloaded & Cached model: " + ctx.modelId + " -> " + outFile.getAbsolutePath());
                    LazyModelAssembly lazyAssembly = new LazyModelAssembly(ctx.modelId, outFile, FIXED_CACHE_KEY, ctx.isAuth);
                    pendingModelQueue.add(Pair.of(lazyAssembly, ctx.modelId));
                    touchModel(ctx.modelId);
                    incrementSyncProgress();
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[BPM] Failed to save/parse downloaded model: " + ctx.modelId, e);
                } finally {
                    if (syncSessionId.get() == currentSession) {
                        if (pendingModelsCount.decrementAndGet() <= 0) {
                            YesSteveModel.LOGGER.info("[BPM-NET] CLIENT: All missing models downloaded and loaded successfully! Handshake complete.");
                            onSyncComplete();
                        }
                    }
                }
            });
        }
    }


    private static void parseAndLoadModel(byte[] decompressed, String modelId, boolean isAuth) {
        try {
//            if (true) return;
            // IR

            ModelMemoryProfiler.logBytes("binary-parse-start", modelId, decompressed);
            try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decompressed, 32)) {
                RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                YSMByteBuf reader = deserializer.getReader();

                // Read version number
                rawModel.footer.version = reader.readVarInt(); // 65535 or 32

                rawModel.footer.unkInt1 = reader.readVarInt(); // Analyze
                if (rawModel.footer.unkInt1 != 0) {
                    rawModel.footer.rand = reader.readString();
                }

                rawModel.footer.time = reader.readVarLong();

                if (rawModel.footer.unkInt1 != 0) {
                    rawModel.footer.extra = reader.readString();
                    rawModel.footer.unkInt2 = reader.readVarInt();
                }

                // Assemble to client model
                ModelMemoryProfiler.log("client-map-start", modelId);
                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
                ModelMemoryProfiler.log("client-map-finished", modelId);
                onModelDataReceived(parsedBundle, modelId, false, isAuth);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Failed to parse and load model: " + modelId, e);
        }
    }

    private static OrderedStringMap<String, OuterFileTexture> toOrderedTextureMap(Map<String, OuterFileTexture> textures) {
        if (textures == null || textures.isEmpty()) {
            return new OrderedStringMap<>(new String[0], new OuterFileTexture[0]);
        }
        return new OrderedStringMap<>(
                textures.keySet().toArray(new String[0]),
                textures.values().toArray(new OuterFileTexture[0])
        );
    }

    private static void resetClientState() {
        syncStep = 1;
        key1 = null;
        lastKey = null;
        serverKey = null;
        clientKey = null;
        serverConnection = null;

        modelPhraseExecutor.getQueue().clear();
        pendingModelQueue.clear();
        syncSessionId.incrementAndGet();

        currentCacheFolderName = null;
        pendingModelsCount.set(0);
        cachedModelHashes.clear();

        serverModels.clear();
        knownServerModelIds.clear();

        Map<String, ModelPackData> oldPreviews = modelPackMap;
        if (oldPreviews != null && !oldPreviews.isEmpty()) {
            for (ModelPackData preview : oldPreviews.values()) {
                if (preview.getTexture() != null) {
                    Identifier loc = FileTypeUtil.getPackIconLocation(preview.getPath());
                    ((Executor) Minecraft.getInstance()).execute(() -> {
                        ((MinecraftAccessor) Minecraft.getInstance()).ysm$getTextureManager().release(loc);
                    });
                }
            }
        }

        modelPackMap = new Object2ReferenceOpenHashMap<>();
        pendingModelCallback = null;
        pendingModelQueue.clear();
        localOnlyModelIds.clear();
        localModelSources.clear();
        modelLastUsedAt.clear();
        gpuCacheTrimmedModels.clear();

        forEachGuiWidget(l -> {
            try {
                l.onSyncBegin();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static SyncStatus getSyncStatus() {
        return syncState;
    }

    public static Map<String, ModelAssembly> getModelAssemblyMap() {
        return modelAssemblyMap;
    }

    public static Map<String, ModelPackData> getModelPackMap() {
        return modelPackMap;
    }

    public static Optional<ModelAssembly> getModelContext(String str) {
        ModelAssembly assembly = modelAssemblyMap.get(str);
        if (assembly != null) {
            touchModel(str);
        }
        return Optional.ofNullable(assembly);
    }

    public static boolean isLocalOnlyModel(String modelId) {
        return modelId != null && localOnlyModelIds.contains(modelId);
    }

    public static void removeLocalModels(Collection<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }
        ((Executor) Minecraft.getInstance()).execute(() -> {
            Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            ArrayList<ModelAssembly> removed = new ArrayList<>();
            for (String modelId : modelIds) {
                localOnlyModelIds.remove(modelId);
                modelLastUsedAt.remove(modelId);
                gpuCacheTrimmedModels.remove(modelId);
                ModelAssembly assembly = map.remove(modelId);
                if (assembly != null) {
                    removed.add(assembly);
                }
            }
            modelAssemblyMap = map;
            for (ModelAssembly assembly : removed) {
                releaseModelAssembly(assembly);
            }
            if (!removed.isEmpty()) {
                forEachGuiWidget(guiWidget -> guiWidget.onModelsLoaded(map));
            }
        });
    }

    public static void importLocalModel(String modelId, String fileName, byte[] data, @Nullable Consumer<Component> callback) {
        byte[] importData = data;
        modelPhraseExecutor.submit(() -> {
            Component error = null;
            try {
                ModelMemoryProfiler.logBytes("local-import-read", modelId, importData);
                RawYsmModel rawModel = parseImportModel(fileName, importData);
                ModelMemoryProfiler.log("local-import-parsed", modelId);
                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
                ModelMemoryProfiler.log("local-import-mapped", modelId);
                localOnlyModelIds.add(modelId);
                touchModel(modelId);
                runPendingModelCallback();
                if (!processModelData(parsedBundle, modelId, false, false)) {
                    localOnlyModelIds.remove(modelId);
                    throw new IllegalStateException("Failed to build local model");
                }
                YesSteveModel.LOGGER.info("[BPM] Imported local model: {}", modelId);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Failed to import local model: {}", modelId, e);
                error = Component.translatable("gui.better_player_model.import.error.local_import_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (callback != null) {
                Component result = error;
                ((Executor) Minecraft.getInstance()).execute(() -> callback.accept(result));
            }
        });
    }

    public static void ensureLocalModelsLoaded() {
        if (localModelsLoadedOnce.get()) {
            return;
        }
        if (!localReloadInProgress.compareAndSet(false, true)) {
            return;
        }
        reloadLocalModels(error -> {
            localModelsLoadedOnce.set(true);
            localReloadInProgress.set(false);
            if (error != null && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(error);
            }
        });
    }

    public static Component uploadLocalModelForSelection(String modelId) {
        if (!isLocalOnlyModel(modelId)) {
            return null;
        }
        if (!NetworkHandler.isClientConnected() || !isOysmServer()) {
            return null;
        }
        if (!isAllowUpload()) {
            return Component.translatable("gui.better_player_model.import.error.disabled_by_server");
        }
        if (knownServerModelIds.contains(modelId)) {
            localOnlyModelIds.remove(modelId);
            return null;
        }
        LocalModelSource source = localModelSources.get(modelId);
        if (source == null) {
            return Component.translatable("gui.better_player_model.import.error.local_source_missing");
        }
        try {
            byte[] data = source.readBytes();
            return ModelUploadSession.start(modelId, source.fileName(), data);
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("[BPM] Failed to read local model source for upload: {}", modelId, e);
            return Component.translatable("gui.better_player_model.import.error.local_source_read_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public static void markLocalModelUploaded(String modelId) {
        if (modelId == null) {
            return;
        }
        knownServerModelIds.add(modelId);
        localOnlyModelIds.remove(modelId);
        localModelSources.remove(modelId);
    }

    public static void reloadLocalModels(@Nullable Consumer<Component> callback) {
        modelPhraseExecutor.submit(() -> {
            Component error = null;
            try {
                loadDirectoryModels(ServerModelManager.BUILT);
                loadDirectoryModels(ServerModelManager.CUSTOM);
                loadDirectoryModels(ServerModelManager.AUTH);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Failed to reload local model folders", e);
                error = Component.translatable("gui.better_player_model.import.error.local_reload_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (callback != null) {
                Component result = error;
                ((Executor) Minecraft.getInstance()).execute(() -> callback.accept(result));
            }
        });
    }

    public static ModelAssembly getLocalModelContext() {
        runPendingModelCallback();
        flushPendingModels();

        ModelAssembly model = localModelContext;
        if (model != null) {
            touchAssembly(model);
            return model;
        }

        // з‘™п№ЂеЅ‚жЈ°е‹«е§ћжќћ?        loadDefaultModel();
        model = localModelContext;
        if (model != null) {
            touchAssembly(model);
            return model;
        }

        Map<String, ModelAssembly> reg = modelAssemblyMap;
        if (reg != null && !reg.isEmpty()) {
            model = reg.get("default");
            if (model == null) {
                for (ModelAssembly v : reg.values()) {
                    if (v != null) {
                        model = v;
                        break;
                    }
                }
            }
            if (model != null) {
                localModelContext = model;
                touchAssembly(model);
                return model;
            }
        }
        return null;
    }

    public static Identifier getDefaultTexture() {
        return defaultTexture.getResourceLocation().get();
    }

    public static <T extends IGuiWidget> T registerGuiWidget(T t) {
        guiWidgets.put(t, null);
        return t;
    }

    public static void unregisterGuiWidget(IGuiWidget guiWidget) {
        guiWidgets.remove(guiWidget, null);
    }

    private static void forEachGuiWidget(Consumer<IGuiWidget> consumer) {
        Iterator<IGuiWidget> it = guiWidgets.keySet().iterator();
        while (it.hasNext()) {
            try {
                consumer.accept(it.next());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    public static void resetSync() {
        isOysmServer = false;
        allowUpload = false;
        processServerData(null);
        NetworkHandler.resetClientHandshake();
        ((Executor) Minecraft.getInstance()).execute(() -> {
            syncState.setState(SyncState.WAITING);
        });
    }

    public static boolean isAllowUpload() {
        return allowUpload;
    }

    public static boolean isOysmServer() {
        return isOysmServer;
    }

    private static void sendModelFile(ByteBuffer byteBuffer) {
        if (Minecraft.getInstance().player != null) {
            try {
                NetworkHandler.sendToServer(new C2SModelSyncPayload(byteBuffer));
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        Connection connection = serverConnection;
        if (!connection.isConnected()) {
            return;
        }
        try {
            connection.send(NetworkHandler.toServerboundPacket(new C2SModelSyncPayload(byteBuffer)));
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public static void startSync(Connection connection, ByteBuffer byteBuffer) {
        serverConnection = connection;
        modelPhraseExecutor.submit(() -> processServerData(byteBuffer));
    }

    public static void onSyncConnected() {
        if (((MinecraftAccessor) Minecraft.getInstance()).ysm$isLocalServer()) {
            syncState.setState(SyncState.LOADING);
        } else {
            syncState.setState(SyncState.IDLE);
        }
        forEachGuiWidget(IGuiWidget::onSyncBegin);
    }

    private static void onSyncProgress(int totalModels) {
        if (totalModels == -1) {
            ((Executor) Minecraft.getInstance()).execute(() -> {
                syncState.setState(SyncState.PREPARING);
                forEachGuiWidget(IGuiWidget::onSyncError);
            });
        } else {
            ((Executor) Minecraft.getInstance()).execute(() -> {
                if (totalModels > 0) {
                    syncState.startSyncing(totalModels);
                } else {
                    syncState.setState(SyncState.IDLE);
                }
                forEachGuiWidget(guiWidget -> guiWidget.onSyncProgress(totalModels, 0));
            });
        }
    }

    private static void onModelPacksReceived(ModelPackData[] packDataArr) {
        Object2ReferenceOpenHashMap<String, ModelPackData> newPackMap = new Object2ReferenceOpenHashMap<>();

        for (ModelPackData packData : packDataArr) {
            if (StringUtils.isBlank(packData.getName())) {
                packData = new ModelPackData(packData.getPath(), FileTypeUtil.getFinalPathSegment(packData.getPath()), packData.getDescription(), packData.getTexture(), packData.getTranslations());
            }
            newPackMap.put(packData.getPath(), packData);
                OuterFileTexture iconTexture = packData.getTexture();
                if (iconTexture != null) {
                    Identifier location2 = FileTypeUtil.getPackIconLocation(packData.getPath());
                    ((Executor) Minecraft.getInstance()).execute(() -> {
                        iconTexture.doLoad();
                        ((MinecraftAccessor) Minecraft.getInstance()).ysm$getTextureManager().register(location2, iconTexture);
                    });
                }
            }

        for (ModelPackData packData : modelPackMap.values()) {
            if (!newPackMap.containsKey(packData.getPath()) && packData.getTexture() != null) {
                Identifier location = FileTypeUtil.getPackIconLocation(packData.getPath());
                ((Executor) Minecraft.getInstance()).execute(() -> ((MinecraftAccessor) Minecraft.getInstance()).ysm$getTextureManager().release(location));
            }
        }
        modelPackMap = newPackMap;
    }

    private static void onModelContextsUpdated(String[] removedModelIds, String[] previousModelIds, String[] updatedModelIds, boolean[] isModelReady) {
        ((Executor) Minecraft.getInstance()).execute(() -> {
            Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            if (removedModelIds != null) {
                ArrayList<ModelAssembly> removed = new ArrayList<>(removedModelIds.length);
                for (String str : removedModelIds) {
                    if (localOnlyModelIds.contains(str)) {
                        continue;
                    }
                    ModelAssembly assembly = map.remove(str);
                    if (assembly != null) {
                        removed.add(assembly);
                    }
                }
                ((Executor) Minecraft.getInstance()).execute(() -> {
                    for (ModelAssembly assembly : removed) {
                        releaseModelAssembly(assembly);
                    }
                });
            }
            if (previousModelIds != null) {
                ModelAssembly[] modelAssemblies = new ModelAssembly[previousModelIds.length];
                for (int i = 0; i < previousModelIds.length; i++) {
                    localOnlyModelIds.remove(previousModelIds[i]);
                    modelAssemblies[i] = map.remove(previousModelIds[i]);
                }
                for (int i = 0; i < modelAssemblies.length; i++) {
                    ModelAssembly modelAssembly = modelAssemblies[i];
                    if (modelAssembly != null) {
                        modelAssembly.getTextureRegistry().setAuthModel(isModelReady[i]);
                        map.put(updatedModelIds[i], modelAssembly);
                    }
                }
            }
            modelAssemblyMap = map;
            if ((removedModelIds != null && removedModelIds.length > 0) || (previousModelIds != null && previousModelIds.length > 0)) {
                forEachGuiWidget(guiWidget -> {
                    guiWidget.onModelsLoaded(map);
                });
            }
        });
    }

    private static void onModelDataReceived(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) throws Exception {
        if (isPrimary) {
            pendingModelCallback = () -> {
                processModelData(parsedBundle, modelId, true, false);
            };
        } else {
            localOnlyModelIds.remove(modelId);
            localModelSources.remove(modelId);
            runPendingModelCallback();
            processModelData(parsedBundle, modelId, false, isAuth);
        }
    }

    public static RawYsmModel parseImportModel(String fileName, byte[] data) throws Exception {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ysm")) {
            return parseYsmImport(data, fileName);
        }
        if (lower.endsWith(".zip")) {
            return parseZipImport(data);
        }
        if (lower.endsWith(".7z")) {
            throw new UnsupportedOperationException("7z import is not supported yet");
        }
        throw new IllegalArgumentException("Unsupported model import type: " + fileName);
    }

    public static RawYsmModel parseYsmImport(byte[] data, String source) throws Exception {
        int ysmCryptoVersion = YesModelUtils.getYsmCryptoVersion(data);
        if (ysmCryptoVersion == 1 || ysmCryptoVersion == 2) {
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(YesModelUtils.input(data))) {
                return deserializer.deserialize();
            }
        }
        try {
            byte[] decrypted = YsmCrypt.decryptYsmFile(data);
            try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
                RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                deserializer.parseYSMFooter(rawModel);
                return rawModel;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid YSM model: " + source, e);
        }
    }

    public static RawYsmModel parseZipImport(byte[] data) throws Exception {
        Path temp = Files.createTempFile("ysm-local-import-", ".zip");
        try {
            Files.write(temp, data);
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(temp)) {
                return deserializer.deserialize();
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                YesSteveModel.LOGGER.warn("[BPM] Failed to remove temporary local import archive {}", temp, e);
            }
        }
    }

    private static boolean loadDirectoryModels(Path baseDir) throws IOException {
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return false;
        }
        boolean[] loadedAny = new boolean[]{false};
        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(baseDir)) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    if (YSMFolderDeserializer.isModelFolder(dir)) {
                        String modelId = normalizeLocalModelId(baseDir.relativize(dir).toString());
                        if (registerLocalModelLazy(modelId, dir.toFile(), true)) {
                            localModelSources.put(modelId, new LocalModelSource(dir.toAbsolutePath().normalize(), FileTypeUtil.getFinalPathSegment(modelId) + ".zip", true));
                            loadedAny[0] = true;
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[BPM] Failed to load local model folder: {}", dir, e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".ysm") && !lower.endsWith(".zip")) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    String modelId = stripImportExtension(normalizeLocalModelId(baseDir.relativize(file).toString()));
                    if (registerLocalModelLazy(modelId, file.toFile(), false)) {
                         localModelSources.put(modelId, new LocalModelSource(file.toAbsolutePath().normalize(), fileName, false));
                         loadedAny[0] = true;
                    }
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[BPM] Failed to load local model file: {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return loadedAny[0];
    }

    private static boolean registerLocalModelLazy(String modelId, File file, boolean isDirectory) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        if (knownServerModelIds.contains(modelId)) {
            localOnlyModelIds.remove(modelId);
            localModelSources.remove(modelId);
            return false;
        }
        localOnlyModelIds.add(modelId);
        touchModel(modelId);
        runPendingModelCallback();
        LazyModelAssembly lazyAssembly = new LazyModelAssembly(modelId, file, isDirectory);
        pendingModelQueue.add(Pair.of(lazyAssembly, modelId));
        return true;
    }

    private static boolean loadLocalModelFolder(String modelId, Path dir) throws Exception {
        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(dir)) {
            return loadLocalModel(modelId, deserializer.deserialize());
        }
    }

    private static boolean loadLocalModel(String modelId, RawYsmModel rawModel) throws Exception {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        if (knownServerModelIds.contains(modelId)) {
            localOnlyModelIds.remove(modelId);
            localModelSources.remove(modelId);
            return false;
        }
        ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
        localOnlyModelIds.add(modelId);
        touchModel(modelId);
        runPendingModelCallback();
        if (!processModelData(parsedBundle, modelId, false, false)) {
            localOnlyModelIds.remove(modelId);
            throw new IllegalStateException("Failed to build local model");
        }
        return true;
    }

    private static String stripImportExtension(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".7z"}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    private static String normalizeLocalModelId(String modelId) {
        return stripImportExtension(modelId.replace('\\', '/').toLowerCase(Locale.ROOT).replaceAll("/+", "/"));
    }

    private static byte[] readLimitedFileBytes(Path file, long maxBytes) throws IOException {
        long size = Files.size(file);
        if (size < 0 || size > maxBytes) {
            throw new IOException("File exceeds limit: " + file);
        }
        return Files.readAllBytes(file);
    }

    private static byte[] zipDirectory(Path dir) throws IOException {
        Path root = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("Model folder is missing: " + dir);
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            final long[] totalInputBytes = new long[]{0L};
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path absoluteFile = file.toAbsolutePath().normalize();
                    if (!absoluteFile.startsWith(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String entryName = root.relativize(absoluteFile).toString().replace('\\', '/');
                    if (entryName.isBlank()) {
                        return FileVisitResult.CONTINUE;
                    }
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (java.io.InputStream input = Files.newInputStream(absoluteFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            totalInputBytes[0] += read;
                            if (totalInputBytes[0] > MAX_SERVER_MODEL_BYTES) {
                                throw new IOException("Model folder exceeds limit: " + dir);
                            }
                            zip.write(buffer, 0, read);
                        }
                    } finally {
                        zip.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            zip.finish();
            byte[] result = bytes.toByteArray();
            if (result.length <= 0 || result.length > MAX_SERVER_MODEL_BYTES) {
                throw new IOException("Packed model folder exceeds limit: " + dir);
            }
            return result;
        }
    }

    public static void runPendingModelCallback() {
        Runnable runnable = pendingModelCallback;
        if (runnable != null) {
            synchronized (runnable) {
                Runnable runnable2 = pendingModelCallback;
                if (runnable2 != null) {
                    runnable2.run();
                    pendingModelCallback = null;
                }
            }
        }
    }

    public static boolean processModelData(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) {
        if (parsedBundle != null) {
            try {
                ModelMemoryProfiler.log("assembly-build-start", modelId);
                ModelAssembly runtimeModel = ModelAssemblyFactory.buildAssembly(parsedBundle, isPrimary, isAuth);
                ModelMemoryProfiler.log("assembly-build-finished", modelId);
                pendingModelQueue.add(Pair.of(runtimeModel, modelId));
                touchModel(modelId);
                if (isPrimary) {
                    localModelContext = runtimeModel;

                    ((Executor) Minecraft.getInstance()).execute(() -> {
                        defaultTexture = UploadManager.getOrCreateLocatable(runtimeModel.getAnimationBundle().getTextures().getValueAt(0), true);
                    });
                    return true;
                }
            } catch (Exception e) {
                if (isPrimary) throw e;
                YesSteveModel.LOGGER.error(
                        new StringFormattedMessage("Failed to process {}", modelId), e);
                return false;
            }
        }
        return parsedBundle != null;
    }

    public static void incrementSyncProgress() {
        ((Executor) Minecraft.getInstance()).execute(() -> {
            if (syncState.currentState == SyncState.SYNCING) {
                syncState.syncedModels++;
                int loaded = syncState.syncedModels;
                if (loaded == syncState.totalModels) {
                    syncState.setState(SyncState.IDLE);
                }
                forEachGuiWidget(guiWidget -> {
                    guiWidget.onSyncProgress(syncState.getTotalModels(), loaded);
                });
            }
        });
    }

    private static void onSyncComplete() {
        syncStep = 1;
        serverModels.clear();
        cachedModelHashes.clear();

        ((Executor) Minecraft.getInstance()).execute(() -> {
            syncState.setState(SyncState.IDLE);
            forEachGuiWidget(IGuiWidget::onSyncComplete);
        });
    }

    public static void setAllowUpload(boolean allowUpload) {
        ClientModelManager.allowUpload = allowUpload;
    }

    public static void setOysmServer(boolean isOysmServer) {
        ClientModelManager.isOysmServer = isOysmServer;
    }

    private static void onSyncError(@Nullable Object obj) {
        ((Executor) Minecraft.getInstance()).execute(() -> {
            syncState.setState(SyncState.IDLE);
            forEachGuiWidget(guiWidget -> {
                guiWidget.onSyncMessage(obj == null ? null : (Component) obj);
            });
            if (obj instanceof Component component) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(component);
                }
                YesSteveModel.LOGGER.error(component.getString(256));
            }
        });
    }

    public static void flushPendingModels() {
        if (pendingModelQueue.isEmpty())
            return;

        Object2ReferenceOpenHashMap<String, ModelAssembly> object2ReferenceOpenHashMap = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
        while (true) {
            Pair<ModelAssembly, String> pairPoll = pendingModelQueue.poll();
            if (pairPoll != null) {
                ModelAssembly previous = object2ReferenceOpenHashMap.get(pairPoll.getRight());
                if (previous instanceof com.elfmcys.yesstevemodel.client.model.LazyModelAssembly lazy && !(pairPoll.getLeft() instanceof com.elfmcys.yesstevemodel.client.model.LazyModelAssembly)) {
                    lazy.setResolved(pairPoll.getLeft());
                    touchModel(pairPoll.getRight());
                    gpuCacheTrimmedModels.remove(pairPoll.getRight());
                } else {
                    previous = object2ReferenceOpenHashMap.put(pairPoll.getRight(), pairPoll.getLeft());
                    touchModel(pairPoll.getRight());
                    gpuCacheTrimmedModels.remove(pairPoll.getRight());
                    if (previous != null && previous != pairPoll.getLeft()) {
                        releaseModelAssembly(previous);
                    }
                }
            } else {
                modelAssemblyMap = object2ReferenceOpenHashMap;
                forEachGuiWidget(guiWidget -> guiWidget.onModelsUpdated(object2ReferenceOpenHashMap));
                return;
            }
        }
    }

    private static void releaseModelAssembly(ModelAssembly assembly) {
        if (assembly == null) {
            return;
        }
        if (assembly == modelAssemblyMap.get("default")) {
            return;
        }
        if (assembly instanceof LazyModelAssembly lazy && !lazy.isResolved()) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> releaseModelAssembly(assembly));
            return;
        }
        if (assembly.getTextures() != null) {
            for (AbstractTexture tex : assembly.getTextures()) {
                if (tex != null) {
                    UploadManager.removeTexture(tex);
                    tex.close();
                }
            }
        }
        if (assembly.getProjectileModels() != null) {
            for (Map.Entry<Identifier, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
                if (entry != null && entry.getValue() != null && entry.getValue().getModel() != null) {
                    entry.getValue().getModel().freeNativeCache();
                }
            }
        }
        if (assembly.getVehicleModels() != null) {
            for (Map.Entry<Identifier, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
                if (entry != null && entry.getValue() != null && entry.getValue().getModel() != null) {
                    entry.getValue().getModel().freeNativeCache();
                }
            }
        }
        if (assembly.getAnimationBundle() != null) {
            if (assembly.getAnimationBundle().getMainModel() != null) {
                assembly.getAnimationBundle().getMainModel().freeNativeCache();
            }
            if (assembly.getAnimationBundle().getArmModel() != null) {
                assembly.getAnimationBundle().getArmModel().freeNativeCache();
            }
        }
        ModelMemoryProfiler.log("assembly-released", null);
    }

    public static void trimUnusedGpuCaches() {
        Minecraft minecraft = Minecraft.getInstance();
        if (com.elfmcys.yesstevemodel.client.ScreenFixer.getScreen(minecraft) != null) {
            return;
        }
        long now = System.currentTimeMillis();
        long ttlMillis = safeInt(GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 300) * 1000L;
        Set<String> protectedModels = collectProtectedModelIds(minecraft);

        // 1. Identify active (resolved and untrimmed) models
        List<Map.Entry<String, ModelAssembly>> activeModels = modelAssemblyMap.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("default"))
                .filter(entry -> !protectedModels.contains(entry.getKey()))
                .filter(entry -> !gpuCacheTrimmedModels.contains(entry.getKey()))
                .filter(entry -> !(entry.getValue() instanceof com.elfmcys.yesstevemodel.client.model.LazyModelAssembly lazy) || lazy.isResolved())
                .toList();

        // 2. Trim expired models (always trim them regardless of cache size limits)
        List<Map.Entry<String, ModelAssembly>> expiredModels = activeModels.stream()
                .filter(entry -> {
                    long lastUsed = modelLastUsedAt.getOrDefault(entry.getKey(), 0L);
                    return lastUsed > 0L && now - lastUsed >= ttlMillis;
                })
                .toList();

        for (Map.Entry<String, ModelAssembly> entry : expiredModels) {
            trimGpuCache(entry.getKey(), entry.getValue());
        }

        // 3. If a maximum cache size is set, enforce it by trimming the oldest remaining active models
        int maxCachedGpuModels = safeInt(GeneralConfig.MAX_CACHED_GPU_MODELS, 0);
        if (maxCachedGpuModels > 0) {
            List<Map.Entry<String, ModelAssembly>> remainingActive = activeModels.stream()
                    .filter(entry -> !gpuCacheTrimmedModels.contains(entry.getKey()))
                    .sorted(Comparator.comparingLong(entry -> modelLastUsedAt.getOrDefault(entry.getKey(), 0L)))
                    .toList();

            if (remainingActive.size() > maxCachedGpuModels) {
                int toTrimCount = remainingActive.size() - maxCachedGpuModels;
                for (int i = 0; i < toTrimCount; i++) {
                    Map.Entry<String, ModelAssembly> entry = remainingActive.get(i);
                    trimGpuCache(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private static Set<String> collectProtectedModelIds(Minecraft minecraft) {
        Set<String> protectedModels = new HashSet<>();
        protectedModels.add("default");
        if (localModelContext != null) {
            touchAssembly(localModelContext);
        }
        if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                PlayerCapability.get(player).ifPresent(cap -> {
                    String modelId = cap.getModelId();
                    if (modelId != null && !modelId.isBlank()) {
                        protectedModels.add(modelId);
                        touchModel(modelId);
                    }
                });
            }
        }
        return protectedModels;
    }

    private static void trimGpuCache(String modelId, ModelAssembly assembly) {
        if (assembly == null || !gpuCacheTrimmedModels.add(modelId)) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> trimGpuCache(modelId, assembly));
            return;
        }
        releaseModelAssembly(assembly);
        if (assembly instanceof LazyModelAssembly lazy) {
            lazy.unresolve();
        }
        ModelMemoryProfiler.log("gpu-cache-trimmed", modelId);
    }

    public static void touchModel(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            modelLastUsedAt.put(modelId, System.currentTimeMillis());
            gpuCacheTrimmedModels.remove(modelId);
        }
    }

    private static void touchAssembly(ModelAssembly assembly) {
        if (assembly == null) {
            return;
        }
        for (Map.Entry<String, ModelAssembly> entry : modelAssemblyMap.entrySet()) {
            if (entry.getValue() == assembly) {
                touchModel(entry.getKey());
                return;
            }
        }
    }

    private static int safeInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static int getPendingModelCount() {
        return pendingModelQueue.size();
    }

    public static class SyncStatus {
        private SyncState currentState = SyncState.WAITING;

        private int totalModels = -1;

        private int syncedModels = -1;

        public SyncState getCurrentState() {
            return this.currentState;
        }

        public int getSyncedModels() {
            return this.syncedModels;
        }

        public int getTotalModels() {
            return this.totalModels;
        }

        public void setState(SyncState syncState) {
            System.out.println("Sync state: " + syncState);
            this.currentState = syncState;
            this.totalModels = -1;
            this.syncedModels = -1;
        }

        public void startSyncing(int totalModels) {
            this.currentState = SyncState.SYNCING;
            this.totalModels = totalModels;
            this.syncedModels = 0;
        }
    }

    public static void exportAllCachedModels(@Nullable String extra, @Nullable Consumer<ExportResult> callback) {
        YSMThreadPool.submit(() -> {
            try {
                if (clientKey == null) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("(unavailable)"), "", "", 0));
                    }
                    return;
                }

                String folder = currentCacheFolderName != null ? currentCacheFolderName : "default_cache";
                File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();

                if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("зЃЏж°­ж№­йђўз†ёећљжµ и®із¶Ќзј‚ж’із“ЁйЋґж «зґ¦зЂ›жЁ»жћѓжµ и·єгЃ™ж¶“е¶…з“ЁйЌ¦? " + folder), "", "", 0));
                    }
                    return;
                }

                File[] files = cacheDir.listFiles();
                if (files == null || files.length == 0) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("(unavailable)"), "", "", 0));
                    }
                    return;
                }

                int successCount = 0;
                for (File file : files) {
                    if (!file.isFile()) continue;

                    try {
                        byte[] fileBytes = readLimitedFileBytes(file.toPath(), MAX_SERVER_MODEL_BYTES);
                        byte[] clearText = YsmCrypt.read(fileBytes, FIXED_CACHE_KEY);

                        int coreDataLength;
                        String exportName = file.getName(); // Fallback name

                        try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(clearText, 32)) {
                            RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                            coreDataLength = deserializer.getReader().getRawBuf().readerIndex();

                            if (rawModel.metadata != null && rawModel.metadata.name != null && !rawModel.metadata.name.trim().isEmpty()) {
                                exportName = rawModel.metadata.name.trim();
                            } else if (rawModel.properties != null && rawModel.properties.sha256 != null && !rawModel.properties.sha256.isEmpty()) {
                                exportName = rawModel.properties.sha256;
                            }
                        }

                        exportName = exportName.replaceAll("[\\\\/:*?\"<>|]", "_");

                        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                            outBuf.writeDword(32);

                            outBuf.getRawBuf().writeBytes(clearText, 0, coreDataLength);

                            outBuf.writeVarInt(32); // Version
                            outBuf.writeVarInt(1);

                            byte[] randBytes = new byte[8];
                            SECURE_RANDOM.nextBytes(randBytes);
                            StringBuilder sb = new StringBuilder(16);
                            for (byte b : randBytes) {
                                sb.append(String.format("%02x", b));
                            }
                            outBuf.writeString(sb.toString()); // rand hash

                            outBuf.writeVarLong(java.time.Instant.now().getEpochSecond()); // time
                            outBuf.writeString(extra != null ? extra : ""); // extra info
                            outBuf.writeVarInt(0); // padding

                            byte[] rawBytes = new byte[outBuf.getRawBuf().readableBytes()];
                            outBuf.getRawBuf().readBytes(rawBytes);

                            byte[] finalEncrypted = YsmCrypt.encryptYsmFile(rawBytes);

                            Path exportPath = ServerModelManager.EXPORT.resolve(exportName + ".ysm");
                            Files.createDirectories(exportPath.getParent());
                            Files.write(exportPath, finalEncrypted);

                            successCount++;
                            YesSteveModel.LOGGER.info("[BPM] Successfully exported cached model to: " + exportPath);
                        }
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("[BPM] Failed to export cached model: " + file.getName(), e);
                    }
                }

                if (callback != null) {
                    String displayPath = Paths.get("export").toString();
                    if (successCount > 0) {
                        callback.accept(new ExportResult(true, null, displayPath, "", 0));
                    } else {
                        callback.accept(new ExportResult(false, Component.literal("(unavailable)"), "", "", 0));
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Error during batch export", e);
                if (callback != null) {
                    callback.accept(new ExportResult(false, Component.literal("йЋµеЅ’е™єзЂµз…Ћељ­жќ©е›©в–јйЌ™ж€ ж•“ж¶“гѓ©е™ёй–їж¬’о‡¤: " + e.getMessage()), "", "", 0));
                }
            }
        });
    }
}


