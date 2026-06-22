package com.elfmcys.yesstevemodel.model;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import com.elfmcys.yesstevemodel.capability.ModelInfoCapability;
import com.elfmcys.yesstevemodel.client.ExportResult;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.mixin.ConnectionAccessor;
import com.elfmcys.yesstevemodel.mixin.ServerCommonPacketListenerImplAccessor;
import com.elfmcys.yesstevemodel.model.format.ServerAnimationInfo;
import com.elfmcys.yesstevemodel.model.format.ServerModelData;
import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.model.format.UUIDComponentData;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.S2CModelSyncPayload;
import com.elfmcys.yesstevemodel.network.message.S2CSyncAuthModelsPacket;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMBinarySerializer;
import com.elfmcys.yesstevemodel.resource.YSMClientMapper;
import com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.util.DigestUtil;
import com.elfmcys.yesstevemodel.util.YSMNativeHelper;
import com.elfmcys.yesstevemodel.util.YSMThreadPool;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import dev.architectury.utils.GameInstance;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.floats.FloatReferencePair;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rip.ysm.legacy.YesModelUtils;
import rip.ysm.security.YSMByteBuf;
import rip.ysm.security.YsmCrypt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class ServerModelManager {
    private static final String BUILTIN_RESOURCE_ROOT = "/assets/" + YesSteveModel.MOD_ID + "/builtin/";
    private static final String BUILTIN_RESOURCE_INDEX = BUILTIN_RESOURCE_ROOT + "index.txt";
    private static final long UPLOAD_SESSION_TIMEOUT_MS = 120_000L;
    private static final long UPLOAD_RATE_WINDOW_MS = 1_000L;
    private static final int UPLOAD_CHUNK_SIZE = 16_000;
    private static final int MAX_ACTIVE_UPLOADS_PER_PLAYER = 1;
    private static final int MAX_ACTIVE_UPLOAD_SESSIONS = 8;
    private static final long MAX_ACTIVE_UPLOAD_BYTES_HARD_CAP = 512L * 1024L * 1024L;
    private static final long MAX_MODEL_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_PACK_ICON_BYTES = 4L * 1024L * 1024L;
    private static final Pattern MODEL_ID_PATTERN = Pattern.compile("[a-z0-9_./-]+");
    private static final String EXT_YSM = ".ysm";
    private static final String EXT_ZIP = ".zip";
    private static final String EXT_7Z = ".7z";
    /**
     * й…ЌзЅ®з›ёе…іж–‡д»¶е¤№
     */
    public static final Path FOLDER = Platform.getConfigFolder().resolve(YesSteveModel.MOD_ID);

    /**
     * и‡Єе®љд№‰жЁЎећ‹ж‰Ђж”ѕзЅ®зљ„ж–‡д»¶е¤№
     */
    public static final Path BUILT = FOLDER.resolve("built");
    public static final Path CUSTOM = FOLDER.resolve("custom");
    public static final Path AUTH = FOLDER.resolve("auth");
    public static final Path EXPORT = FOLDER.resolve("export");

    /**
     * з”џж€ђзј“е­ж–‡д»¶зљ„ж–‡д»¶е¤№
     */
    public static final Path CACHE = FOLDER.resolve("cache");
    public static final Path CACHE_SERVER_INDEX_FILE = CACHE.resolve("server_index");
    public static final Path CACHE_SERVER = CACHE.resolve("server");
    public static final Path CACHE_CLIENT = CACHE.resolve("client");

    /**
     * жЁЎећ‹еђЌз§° -> жЁЎећ‹йўќе¤–дїЎжЃЇзј“е­
     * еЏЇд»Ґж–№дѕїзљ„йЂљиї‡ж­¤зј“е­пјЊжќҐе€¤ж–­е®ўж€·з«ЇеЏ‘жќҐзљ„ MD5 ењЁдёЌењЁжњЌеЉЎз«Ї
     * д»ЋиЂЊе°†жњЌеЉЎе™Ёж–‡д»¶еЏ‘йЂЃз»™зЋ©е®¶
     * иїеЏЇд»ҐиЋ·еЏ–е…¶д»–жњЌеЉЎз«ЇжЁЎећ‹дїЎжЃЇ
     */
    private static Map<String, ServerModelData> CACHE_NAME_INFO = Maps.newHashMap();

    private static IntOpenHashSet modelHashSet = new IntOpenHashSet();

    /**
     * ж”ѕзЅ®жЋ€жќѓжЁЎећ‹еђЌз§°
     */
    private static Set<String> AUTH_MODELS = Sets.newHashSet();

    private static final Map<UUID, PlayerSyncState> syncStates = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> deliveredModelIds = new ConcurrentHashMap<>();
    private static final Map<Long, ModelUploadState> uploadStates = new ConcurrentHashMap<>();
    private static final Map<String, ServerPackData> packs = new ConcurrentHashMap<>();
    private static final SecureRandom theRandom = new SecureRandom();
    public static byte[] serverKey;
    private static volatile boolean initialized = false;

    private static RateLimiter bandwidthLimiter = null;
    private static Semaphore threadLimiter = null;
    private static boolean limitsInitialized = false;

    private static void initRateLimit() {
        if (!limitsInitialized) {
            try {
                int mbps = ServerConfig.BANDWIDTH_LIMIT.get();
                double bytesPerSec = Math.max(1.0, mbps * 131072.0);
                bandwidthLimiter = RateLimiter.create(bytesPerSec);

                int threads = ServerConfig.THREAD_COUNT.get();
                if (threads <= 0) {
                    threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
                }
                threadLimiter = new Semaphore(threads);

                limitsInitialized = true;
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Failed to initialize limits from config", e);
                bandwidthLimiter = RateLimiter.create(5 * 131072.0);
                threadLimiter = new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
                limitsInitialized = true;
            }
        }
    }

    public static class ServerPackData {
        public String folderPath;
        public byte[] iconData;
        public int iconWidth, iconHeight, iconFormat;
        public String name;
        public String description;
        public Map<String, Map<String, String>> lang;
    }

    public static void reloadPacks() throws IOException {
        CACHE_NAME_INFO.clear();
        AUTH_MODELS.clear();

        createFolder(FOLDER);
        createFolder(BUILT);
        createFolder(CUSTOM);
        createFolder(AUTH);
        createFolder(EXPORT);

        createFolder(CACHE);
        createFolder(CACHE_SERVER);
        createFolder(CACHE_CLIENT);

        extractBuiltinModels();

        Files.writeString(BUILT.resolve("notice.txt"),
                "This directory is cleared every time the game starts!\n" +
                        "иЇҐз›®еЅ•дјљењЁжЇЏж¬Ўжёёж€ЏеђЇеЉЁж—¶жё…з©єпјЃ",
                StandardCharsets.UTF_8);

        Path blacklistFile = FOLDER.resolve("blacklist.txt");
        if (!Files.exists(blacklistFile)) {
            String content =
                    "# Yes Steve Model жЁЎз»„ - е†…зЅ®жЁЎећ‹й»‘еђЌеЌ•й…ЌзЅ®ж–‡д»¶\n" +
                            "# Yes Steve Model Mod - Built-in Model Blacklist Configuration File\n" +
                            "\n" +
                            "# еЉџиѓЅиЇґжЋпјљ\n" +
                            "# йљЏзќЂе†…зЅ®жЁЎећ‹ж•°й‡Џзљ„еўћеЉ пјЊдёєдє†ж»Ўи¶ідёЄжЂ§еЊ–е®ље€¶йњЂж±‚пјЊжњ¬жЁЎз»„жЏђдѕ›дє†й»‘еђЌеЌ•еЉџиѓЅ\n" +
                            "# е…Ѓи®ёз”Ёж€·йЂ‰ж‹©жЂ§ењ°з¦Ѓз”ЁдёЌйњЂи¦Ѓзљ„е†…зЅ®жЁЎећ‹пјЊд»ҐиЉ‚зњЃе­е‚Ёз©єй—ґе’ЊеЉ иЅЅж—¶й—ґ\n" +
                            "#\n" +
                            "# Feature Description:\n" +
                            "# As the number of built-in models increases, this mod provides blacklist functionality\n" +
                            "# to meet customization needs, allowing users to selectively disable unwanted built-in\n" +
                            "# models to save storage space and loading time.\n" +
                            "\n" +
                            "# дЅїз”Ёж–№жі•пјљ\n" +
                            "# 1. ењЁжёёж€ЏеђЇеЉЁе‰Ќзј–иѕ‘ж­¤ж–‡д»¶\n" +
                            "# 2. жё…з©є <жёёж€Џз›®еЅ•>/config/better_player_model/builtin ж–‡д»¶е¤№дё­зљ„е·Іи§ЈеЋ‹жЁЎећ‹ж–‡д»¶\n" +
                            "# 3. й‡Ќж–°еђЇеЉЁжёёж€ЏпјЊжЁЎз»„е°†ж №жЌ®й»‘еђЌеЌ•и§„е€™и·іиї‡жЊ‡е®љжЁЎећ‹зљ„и§ЈеЋ‹\n" +
                            "#\n" +
                            "# Usage Instructions:\n" +
                            "# 1. Edit this file before starting the game\n" +
                            "# 2. Clear extracted model files in <game_directory>/config/better_player_model/builtin folder\n" +
                            "# 3. Restart the game, the mod will skip extracting specified models based on blacklist rules\n" +
                            "\n" +
                            "# жіЁж„Џдє‹йЎ№пјљ\n" +
                            "# - default жЁЎећ‹й‡‡з”Ёз‰№ж®ЉеЉ иЅЅжњєе€¶пјЊж— жі•йЂљиї‡й»‘еђЌеЌ•з¦Ѓз”Ё\n" +
                            "# - й…ЌзЅ®ж–‡д»¶дЅЌзЅ®пјљ<жёёж€Џз›®еЅ•>/config/better_player_model/blacklist.txt\n" +
                            "# - д»Ґ # ејЂе¤ґзљ„иЎЊиў«и§†дёєжіЁй‡ЉпјЊдёЌдјљиў«е¤„зђ†\n" +
                            "# - жЇЏиЎЊдёЂдёЄи§„е€™пјЊдЅїз”Ёж­Је€™иЎЁиѕѕејЏеЊ№й…ЌжЁЎећ‹зљ„е®Њж•ґи§ЈеЋ‹и·Їеѕ„\n" +
                            "#\n" +
                            "# Important Notes:\n" +
                            "# - The default model uses special loading mechanism and cannot be disabled via blacklist\n" +
                            "# - Config file location: <game_directory>/config/better_player_model/blacklist.txt\n" +
                            "# - Lines starting with # are comments and will not be processed\n" +
                            "# - One rule per line, using regular expressions to match the complete extraction path of models\n" +
                            "\n" +
                            "# и·Їеѕ„еЊ№й…Ќи§„е€™пјљ\n" +
                            "# жЁЎз»„и§ЈеЋ‹ж—¶дјљдЅїз”Ёд»Ґдё‹ж јејЏзљ„и·Їеѕ„иї›иЎЊж­Је€™иЎЁиѕѕејЏеЊ№й…Ќпјљ\n" +
                            "#\n" +
                            "# Path Matching Rules:\n" +
                            "# The mod will use the following path formats for regular expression matching during extraction:\n" +
                            "#\n" +
                            "# assets/better_player_model/builtin/misc/2_steve/ysm.json\n" +
                            "\n" +
                            "# й…ЌзЅ®з¤єдѕ‹пјљ\n" +
                            "# й‡Ќи¦ЃжЏђз¤єпјљдё‹йќўзљ„з¤єдѕ‹йѓЅд»Ґ # ејЂе¤ґпјЊиї™иЎЁз¤єе®ѓд»¬з›®е‰ЌжЇжіЁй‡ЉзЉ¶жЂЃпјЊдёЌдјљз”џж•€\n" +
                            "# е¦‚жћњдЅ жѓіи¦ЃеђЇз”ЁжџђдёЄи§„е€™пјЊиЇ·е€ й™¤иЇҐиЎЊејЂе¤ґзљ„ # еЏ·е’Њз©єж ј\n" +
                            "#\n" +
                            "# Configuration Examples:\n" +
                            "# Important Notice: All examples below start with #, meaning they are currently commented out and inactive\n" +
                            "# To enable a rule, delete the # symbol and space at the beginning of that line\n" +
                            "\n" +
                            "# з¤єдѕ‹1пјљз¦Ѓз”Ёжќ‚йЎ№жЁЎећ‹ж–‡д»¶е¤№дё‹зљ„ж‰Ђжњ‰жЁЎећ‹ | Example 1: Disable all models in misc folder\n" +
                            "# assets/better_player_model/builtin/misc/.*\n" +
                            "\n" +
                            "# з¤єдѕ‹2пјљз¦Ѓз”Ёж‰Ђжњ‰е†…зЅ®жЁЎећ‹ | Example 2: Disable all built-in models\n" +
                            "# .*";
            Files.writeString(blacklistFile, content, StandardCharsets.UTF_8);
        }
        processBlacklist(blacklistFile);

        Path serverIndex = CACHE_SERVER_INDEX_FILE;
        byte[] serverKeyBytes;

        if (Files.exists(serverIndex)) {
            try {
                String jsonStr = Files.readString(serverIndex, StandardCharsets.UTF_8);
                JsonObject jsonElement = JsonParser.parseString(jsonStr).getAsJsonObject();

                if (jsonElement.get("server_key") != null && jsonElement.get("server_key").getAsJsonPrimitive().isString()) {
                    serverKeyBytes = Base64.getDecoder().decode(jsonElement.get("server_key").getAsString());
                    if (serverKeyBytes.length != 56) {
                        throw new IllegalStateException("ServerKey length must be 56 bytes, but got " + serverKeyBytes.length);
                    }
                } else {
                    serverKeyBytes = new byte[56];
                    new SecureRandom().nextBytes(serverKeyBytes);
                    jsonElement.addProperty("server_key", Base64.getEncoder().encodeToString(serverKeyBytes));
                    Files.writeString(serverIndex, jsonElement.toString(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                serverKeyBytes = new byte[56];
                new SecureRandom().nextBytes(serverKeyBytes);
                JsonObject jsonElement = new JsonObject();
                jsonElement.addProperty("server_key", Base64.getEncoder().encodeToString(serverKeyBytes));
                Files.writeString(serverIndex, jsonElement.toString(), StandardCharsets.UTF_8);
            }
        } else {
            serverKeyBytes = new byte[56];
            new SecureRandom().nextBytes(serverKeyBytes);
            JsonObject jsonElement = new JsonObject();
            jsonElement.addProperty("server_key", Base64.getEncoder().encodeToString(serverKeyBytes));
            Files.writeString(serverIndex, jsonElement.toString(), StandardCharsets.UTF_8);
        }

        serverKey = serverKeyBytes;
        loadMetadataCache();
        nativeLoadModels(null);
    }

    private static void extractBuiltinModels() {
        if (Files.isDirectory(BUILT)) {
            try (var s = Files.walk(BUILT)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    if (!p.equals(BUILT)) try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
        }
        try {
            Path assetsBuiltin = Platform.getMod(YesSteveModel.MOD_ID).findResource("assets", YesSteveModel.MOD_ID, "builtin").orElse(null);

            boolean extracted = false;
            if (assetsBuiltin != null && Files.isDirectory(assetsBuiltin)) {
                try {
                    extracted = extractBuiltinModelsFromPath(assetsBuiltin);
                } catch (Exception e) {
                    YesSteveModel.LOGGER.warn("[BPM] Failed to extract builtin models from mod path, falling back to resource index", e);
                }
            }

            if (!extracted) {
                extracted = extractBuiltinModelsFromResourceIndex();
            }

            if (!extracted) {
                YesSteveModel.LOGGER.warn("[BPM] No builtin model resources were extracted");
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to extract builtin models", e);
        }
    }

    private static boolean extractBuiltinModelsFromPath(Path assetsBuiltin) throws IOException {
        final boolean[] extracted = {false};
        try (Stream<Path> walker = Files.walk(assetsBuiltin)) {
            walker.forEach(src -> {
                try {
                    Path relative = assetsBuiltin.relativize(src);
                    String relativePath = relative.toString().replace('\\', '/');
                    if (relativePath.equals("index.txt")) {
                        return;
                    }
                    Path dest = ServerModelManager.BUILT.resolve(relative.toString());
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        try (InputStream in = Files.newInputStream(src)) {
                            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                            extracted[0] = true;
                        }
                    }
                } catch (IOException e) {
                    YesSteveModel.LOGGER.warn("Failed to extract builtin: " + src.getFileName(), e);
                }
            });
        }
        return extracted[0];
    }

    private static boolean extractBuiltinModelsFromResourceIndex() throws IOException {
        try (InputStream indexStream = YesSteveModel.class.getResourceAsStream(BUILTIN_RESOURCE_INDEX)) {
            if (indexStream == null) {
                return false;
            }

            String index = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
            Path builtRoot = BUILT.toAbsolutePath().normalize();
            boolean extracted = false;
            for (String line : index.split("\\R")) {
                String relative = line.trim();
                if (relative.isEmpty() || relative.startsWith("#")) {
                    continue;
                }
                Path dest = builtRoot.resolve(relative).normalize();
                if (!dest.startsWith(builtRoot)) {
                    YesSteveModel.LOGGER.warn("[BPM] Skipping invalid builtin resource path: {}", relative);
                    continue;
                }
                String resourcePath = BUILTIN_RESOURCE_ROOT + relative;
                try (InputStream in = YesSteveModel.class.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        YesSteveModel.LOGGER.warn("[BPM] Missing indexed builtin resource: {}", resourcePath);
                        continue;
                    }
                    Files.createDirectories(dest.getParent());
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    extracted = true;
                }
            }
            return extracted;
        }
    }

    private static void processBlacklist(Path blacklistFile) {
        List<Pattern> rules = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(blacklistFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    rules.add(Pattern.compile(line));
                } catch (PatternSyntaxException ignored) {
                }
            }
        } catch (IOException e) {
            return;
        }

        if (rules.isEmpty() || !Files.isDirectory(BUILT)) return;

        try (DirectoryStream<Path> groups = Files.newDirectoryStream(BUILT)) {
            for (Path group : groups) {
                if (!Files.isDirectory(group)) continue;
                boolean hasRemainingModels = false;
                try (DirectoryStream<Path> models = Files.newDirectoryStream(group)) {
                    for (Path model : models) {
                        if (!Files.isDirectory(model)) continue;

                        String matchPath = "assets/better_player_model/builtin/" + group.getFileName() + "/" + model.getFileName() + "/";
                        boolean deleted = false;
                        for (Pattern rule : rules) {
                            if (rule.matcher(matchPath).find()) {
                                deleteRecursively(model);
                                deleted = true;
                                break;
                            }
                        }

                        if (!deleted) {
                            hasRemainingModels = true;
                        }
                    }
                }
                if (!hasRemainingModels) {
                    deleteRecursively(group);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            Files.deleteIfExists(dir);
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                deleteRecursively(entry);
            }
        }
        Files.deleteIfExists(dir);
    }

    private static void createFolder(Path path) {
        File folder = path.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(folder.toPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class PlayerSyncState {
        byte[] clientKey = new byte[56];
        byte[] key1;
        byte[] clientNextKey;
        int step = 0;
        boolean partialSync = false;
        List<ServerModelData> allowedModels = new ArrayList<>();
        long lastActiveMs = System.currentTimeMillis();
        RateLimiter playerBandwidthLimiter;

        // TODO: 未来可基于UUID持久化，这里目前每次加入生成固定clientKey
        PlayerSyncState() {
            new Random(114514).nextBytes(clientKey);
            try {
                int mbps = ServerConfig.PLAYER_BANDWIDTH_LIMIT.get();
                double bytesPerSec = Math.max(1.0, mbps * 131072.0);
                this.playerBandwidthLimiter = RateLimiter.create(bytesPerSec);
            } catch (Exception e) {
                this.playerBandwidthLimiter = RateLimiter.create(5 * 131072.0);
            }
        }
    }

    public static void nativeSendModelData(UUID uuid, @Nullable ByteBuffer data) {
        if (data != null && !data.hasRemaining() && data.position() > 0) {
            data.flip();
        }

        synchronized (syncStates) {
            if (data == null || data.remaining() == 0) {
                syncStates.remove(uuid);
                deliveredModelIds.remove(uuid);
                return;
            }

            PlayerSyncState state = syncStates.get(uuid);
            if (state == null) return;



            try {
                state.lastActiveMs = System.currentTimeMillis();
                byte[] packetBytes = new byte[data.remaining()];
                data.get(packetBytes);
                System.out.println("Server Handle packet, step=" + state.step + ", length=" + packetBytes.length);

                if (state.step == 1) {
                    // 等待Pong
                    byte[] decrypted = YsmCrypt.decrypt(packetBytes, state.key1);
                    if (decrypted == null || decrypted.length < 56) return;

                    // 客户端生成的密钥
                    state.clientNextKey = Arrays.copyOfRange(decrypted, decrypted.length - 56, decrypted.length);
                    byte[] payload = Arrays.copyOfRange(decrypted, 0, decrypted.length - 56);

                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(payload))) {
                        buf.skipGarbageHeader();
                        if (buf.getRawBuf().readByte() != 0x02) return;
                    }

                    YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Received Packet 02 (Pong) from player {}. Handshake step 1 complete, client next key exchanged. Decrypted packet length: {}", getPlayerName(uuid), packetBytes.length);

                    // з™јйЂЃеЏЇз”ЁжЁЎећ‹
                    state.step = 2;
                    sendPacket03(uuid, state, state.allowedModels);
                } else if (state.step == 2 || state.step == 3) {
                    byte[] decrypted = YsmCrypt.decrypt(packetBytes, state.key1);
                    if (decrypted == null) return;

                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                        buf.skipGarbageHeader();
                        if (buf.getRawBuf().readByte() != 0x04) return;

                        int numRequests = buf.readVarInt();
                        List<long[]> requestedHashes = new ArrayList<>();
                        for (int i = 0; i < numRequests; i++) {
                            requestedHashes.add(new long[]{buf.readVarLong(), buf.readVarLong()});
                        }
                        YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Received Packet 04 from player {}. Player requested {} models for download. Proceeding to send Packet 05.", getPlayerName(uuid), numRequests);
                        state.step = 3;
                        sendPacket05(uuid, state, requestedHashes);
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Server sync error for " + uuid, e);
            }
        }
    }

    public static boolean nativeLoadModels(Object callback) {
        try {
            Map<String, ServerModelData> loadedModels = Collections.synchronizedMap(new LinkedHashMap<>());
            Set<String> authIds = Collections.synchronizedSet(new HashSet<>());
            Set<String> validCacheFiles = Collections.synchronizedSet(new HashSet<>());

            packs.clear();
            scanDirectoryPacks(BUILT);
            scanDirectoryPacks(CUSTOM);
            scanDirectoryPacks(AUTH);

            scanDirectoryModels(BUILT, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(CUSTOM, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(AUTH, CACHE_SERVER, loadedModels, authIds, validCacheFiles, true);
            try (Stream<Path> stream = Files.list(CACHE_SERVER)) {
                stream.forEach(file -> {
                    if (!validCacheFiles.contains(file.getFileName().toString())) {
                        try { Files.deleteIfExists(file); } catch (Exception ignored) {}
                    }
                });
            } catch (Exception ignored) {}

            ModelLoadResult result = new ModelLoadResult(true, null, loadedModels, authIds.toArray(new String[0]));
            AUTH_MODELS = authIds;

            onModelLoadComplete(result, callback);
            saveMetadataCache();
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Model loading failed", e);
            return false;
        }
    }

    private static void scanDirectoryModels(Path baseDir, Path cacheDir, Map<String, ServerModelData> loaded, Set<String> authIds, Set<String> validCaches, boolean isAuth) {
        if (baseDir == null || !Files.isDirectory(baseDir)) return;

        List<ScanTask> tasks = new ArrayList<>();
        gatherTasks(baseDir, baseDir, tasks, isAuth);

        tasks.parallelStream().forEach(task -> {
            try {
                if (task.isDir) {
                    long[] fingerprint = getDirectoryFingerprint(task.path);
                    long dirSize = fingerprint[0];
                    long dirLastModified = fingerprint[1];
                    String cacheKey = baseDir.getFileName().toString() + ":" + baseDir.relativize(task.path).toString().replace('\\', '/');

                    ModelCacheEntry cacheEntry;
                    synchronized (metadataCache) {
                        cacheEntry = metadataCache.get(cacheKey);
                    }
                    if (cacheEntry != null && cacheEntry.fileSize == dirSize && cacheEntry.lastModified == dirLastModified) {
                        Path cacheFile = cacheDir.resolve(cacheEntry.cacheFileName);
                        if (Files.exists(cacheFile)) {
                            ServerModelData data = fromCachedData(cacheEntry.cachedData);
                            if (data != null) {
                                loaded.put(task.modelId, data);
                                if (isAuth) authIds.add(task.modelId);
                                validCaches.add(cacheEntry.cacheFileName);
                                return;
                            }
                        }
                    }

                    RawYsmModel rawModel = null;
                    try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(task.path)) {
                        rawModel = deserializer.deserialize();
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("Failed to load model at: " + task.path, e);
                    }

                    if (rawModel != null) {
                        ServerModelData data = processAndCacheModel(task.modelId, rawModel, cacheDir, isAuth, validCaches);
                        if (data != null) {
                            loaded.put(task.modelId, data);
                            if (isAuth) authIds.add(task.modelId);

                            long[] hashes = YsmCrypt.calculateModelHashes(rawModel.properties.sha256, serverKey);
                            String cacheFileName = String.format("%016x%016x", hashes[0], hashes[1]);

                            ModelCacheEntry newEntry = new ModelCacheEntry();
                            newEntry.fileSize = dirSize;
                            newEntry.lastModified = dirLastModified;
                            newEntry.cacheFileName = cacheFileName;
                            newEntry.cachedData = toCachedData(data);
                            synchronized (metadataCache) {
                                metadataCache.put(cacheKey, newEntry);
                                cacheModified = true;
                            }
                        }
                    }
                } else {
                    ImportKind importKind = importKindFromFileName(task.path.getFileName().toString());
                    long fileSize = Files.size(task.path);
                    long lastModified = Files.getLastModifiedTime(task.path).toMillis();
                    String cacheKey = baseDir.getFileName().toString() + ":" + baseDir.relativize(task.path).toString().replace('\\', '/');

                    ModelCacheEntry cacheEntry;
                    synchronized (metadataCache) {
                        cacheEntry = metadataCache.get(cacheKey);
                    }
                    if (cacheEntry != null && cacheEntry.fileSize == fileSize && cacheEntry.lastModified == lastModified) {
                        Path cacheFile = cacheDir.resolve(cacheEntry.cacheFileName);
                        if (Files.exists(cacheFile)) {
                            ServerModelData data = fromCachedData(cacheEntry.cachedData);
                            if (data != null) {
                                loaded.put(task.modelId, data);
                                if (isAuth) authIds.add(task.modelId);
                                validCaches.add(cacheEntry.cacheFileName);
                                return;
                            }
                        }
                    }

                    byte[] raw = readModelFileBytes(task.path);
                    RawYsmModel rawModel = parseUploadedModel(raw, task.path.toString(), importKind);
                    ServerModelData data = processAndCacheModel(task.modelId, rawModel, cacheDir, isAuth, validCaches);
                    if (data != null) {
                        loaded.put(task.modelId, data);
                        if (isAuth) authIds.add(task.modelId);

                        long[] hashes = YsmCrypt.calculateModelHashes(rawModel.properties.sha256, serverKey);
                        String cacheFileName = String.format("%016x%016x", hashes[0], hashes[1]);

                        ModelCacheEntry newEntry = new ModelCacheEntry();
                        newEntry.fileSize = fileSize;
                        newEntry.lastModified = lastModified;
                        newEntry.cacheFileName = cacheFileName;
                        newEntry.cachedData = toCachedData(data);
                        synchronized (metadataCache) {
                            metadataCache.put(cacheKey, newEntry);
                            cacheModified = true;
                        }
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("Failed to load model at: " + task.path, e);
            }
        });
    }

    private static void scanDirectoryPacks(Path baseDir) {
        if (baseDir == null || !Files.isDirectory(baseDir)) return;
        try (var stream = Files.walk(baseDir, 1)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                if (path.equals(baseDir)) return;
                Path packJson = path.resolve("ysm-pack.json");
                if (Files.exists(packJson)) {
                    try {
                        ServerPackData packData = new ServerPackData();
                        packData.folderPath = baseDir.toFile().toURI().relativize(path.toFile().toURI()).getPath();

                        String jsonStr = Files.readString(packJson, StandardCharsets.UTF_8);
                        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
                        if (json.has("name")) packData.name = json.get("name").toString();
                        if (json.has("description")) packData.description = json.get("description").toString();

                        if (json.has("lang") && json.get("lang").isJsonObject()) {
                            packData.lang = new HashMap<>();
                            JsonObject langObj = json.getAsJsonObject("lang");
                            for (Map.Entry<String, JsonElement> entry : langObj.entrySet()) {
                                if (entry.getValue().isJsonObject()) {
                                    Map<String, String> translations = new HashMap<>();
                                    for (Map.Entry<String, JsonElement> transEntry : entry.getValue().getAsJsonObject().entrySet()) {
                                        translations.put(transEntry.getKey(), transEntry.getValue().toString());
                                    }
                                    packData.lang.put(entry.getKey(), translations);
                                }
                            }
                        }

                        Path packPng = path.resolve("ysm-pack.png");
                        if (Files.exists(packPng)) {
                            byte[] data = readLimitedFileBytes(packPng, MAX_PACK_ICON_BYTES);
                            int[] dims = getPngDimensions(data);
                            packData.iconData = data;
                            packData.iconWidth = dims[0];
                            packData.iconHeight = dims[1];
                            packData.iconFormat = 2; // 2=PNG
                        }
                        packs.put(packData.folderPath, packData);
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("Failed to load pack metadata: " + packJson, e);
                    }
                }
            });
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to walk directory for packs: " + baseDir, e);
        }
    }

    private static byte[] readModelFileBytes(Path file) throws IOException {
        try {
            return readLimitedFileBytes(file, MAX_MODEL_FILE_BYTES);
        } catch (AccessDeniedException accessDenied) {
            try {
                File ioFile = file.toFile();
                if (!ioFile.canRead()) {
                    ioFile.setReadable(true, false);
                }
                try (FileInputStream in = new FileInputStream(ioFile)) {
                    return readLimitedStream(in, MAX_MODEL_FILE_BYTES);
                }
            } catch (IOException | SecurityException fallbackError) {
                accessDenied.addSuppressed(fallbackError);
                throw accessDenied;
            }
        }
    }

    private static RawYsmModel parseBinaryModel(byte[] raw, String source) throws Exception {
        int ysmCryptoVersion = YesModelUtils.getYsmCryptoVersion(raw);
        if (ysmCryptoVersion == -1) {
            throw new IllegalStateException("Unknown YSM crypto version for file: " + source);
        }

        if (ysmCryptoVersion == 1 || ysmCryptoVersion == 2) {
            Map<String, byte[]> input = YesModelUtils.input(raw);
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(input)) {
                return deserializer.deserialize();
            }
        }

        byte[] decrypted = YsmCrypt.decryptYsmFile(raw);
        try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
            RawYsmModel rawModel = deserializer.deserializeKeepOpen();
            deserializer.parseYSMFooter(rawModel);
            return rawModel;
        }
    }

    private static RawYsmModel parseArchiveModel(byte[] raw, String source) throws Exception {
        Path temp = Files.createTempFile("ysm-import-", ".zip");
        try {
            Files.write(temp, raw);
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(temp)) {
                return deserializer.deserialize();
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                YesSteveModel.LOGGER.warn("[BPM] Failed to remove temporary model archive {}", temp, e);
            }
        }
    }

    private static RawYsmModel parseUploadedModel(byte[] raw, String source, ImportKind importKind) throws Exception {
        return switch (importKind) {
            case YSM -> parseBinaryModel(raw, source);
            case ZIP -> parseArchiveModel(raw, source);
            case SEVEN_ZIP -> throw new UnsupportedOperationException("7z import is not supported yet");
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported model import type for file: " + source);
        };
    }

    private static String stripImportExtension(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{EXT_YSM, EXT_ZIP, EXT_7Z}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    private static ImportKind importKindFromFileName(String fileName) {
        if (fileName == null) {
            return ImportKind.UNKNOWN;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXT_YSM)) {
            return ImportKind.YSM;
        }
        if (lower.endsWith(EXT_ZIP)) {
            return ImportKind.ZIP;
        }
        if (lower.endsWith(EXT_7Z)) {
            return ImportKind.SEVEN_ZIP;
        }
        return ImportKind.UNKNOWN;
    }

    private static String extensionFor(ImportKind kind) {
        return switch (kind) {
            case YSM -> EXT_YSM;
            case ZIP -> EXT_ZIP;
            case SEVEN_ZIP -> EXT_7Z;
            case UNKNOWN -> "";
        };
    }

    private static int[] getPngDimensions(byte[] data) {
        if (data == null || data.length < 24) return new int[]{0, 0};
        if ((data[0] & 0xFF) != 0x89 || data[1] != 0x50 || data[2] != 0x4E || data[3] != 0x47) return new int[]{0, 0};
        int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
        int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
        return new int[]{width, height};
    }

    private static ServerModelData processAndCacheModel(String modelId, RawYsmModel model, Path serverCacheDir, boolean isAuth, Set<String> validCacheFiles) {
        String sha256 = model.properties.sha256;
        if (sha256 == null || sha256.isEmpty()) return null;

        try {
            long[] hashes = YsmCrypt.calculateModelHashes(sha256, serverKey);
            String cacheFileName = String.format("%016x%016x", hashes[0], hashes[1]);
            Path cacheFile = serverCacheDir.resolve(cacheFileName);
            if (!serverCacheDir.toFile().isDirectory()) {
                Files.createDirectories(serverCacheDir);
            }
            boolean needsUpdate = true;
            if (Files.exists(cacheFile)) {
                byte[] existingData = readLimitedFileBytes(cacheFile, MAX_MODEL_FILE_BYTES);
                if (YsmCrypt.verifyServerCache(existingData, hashes[0], hashes[1]) && canReadServerCache(existingData, modelId)) {
                    needsUpdate = false;
                }
            }
            if (needsUpdate) {
                byte[] encryptedCache;
                try (YSMByteBuf serialized = YSMBinarySerializer.serialize(model, 32, true)) {
                    io.netty.buffer.ByteBuf raw = serialized.getRawBuf();
                    if (raw.hasArray()) {
                        int off = raw.arrayOffset() + raw.readerIndex();
                        int len = raw.readableBytes();
                        encryptedCache = YsmCrypt.encryptServerCache(raw.array(), off, len, serverKey, hashes[0], hashes[1]);
                    } else {
                        encryptedCache = YsmCrypt.encryptServerCache(serialized.toArray(), serverKey, hashes[0], hashes[1]);
                    }
                }
                Files.write(cacheFile, encryptedCache);
            }
            validCacheFiles.add(cacheFileName);

            boolean isCustomSkinModel = "misc/2_steve".equals(modelId) || "misc/1_alex".equals(modelId); // еЇ№жІЎй”™е°±жЇе†™ж­»зљ„

            return mapToDataClass(modelId, model, isAuth, isCustomSkinModel);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to process and cache model: " + modelId, e);
            return null;
        }
    }

    private static boolean canReadServerCache(byte[] existingData, String modelId) {
        try {
            YsmCrypt.read(existingData, serverKey);
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[BPM] Rebuilding unreadable server model cache: {}", modelId);
            return false;
        }
    }


    private static ServerModelData mapToDataClass(String modelId, RawYsmModel raw, boolean isAuth, boolean isCustomSkinModel) {
        ServerModelInfo serverModelInfo = YSMClientMapper.buildModelInfo(raw);
        // Animations
        Map<String, String[]> animMap = new HashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> e : raw.mainEntity.animationFiles.entrySet()) {
            animMap.put(e.getKey(), e.getValue().animations.keySet().toArray(new String[0]));
        }
        String[] texArr = raw.mainEntity.textures.keySet().toArray(new String[0]);
        ServerAnimationInfo animInfo = new ServerAnimationInfo(animMap, texArr);

        // Sub Entities
        Object[] projectiles = raw.projectiles.values().stream().map(v -> v.matchIds != null ? v.matchIds : new String[]{v.identifier}).toArray();
        Object[] vehicles = raw.vehicles.values().stream().map(v -> v.matchIds != null ? v.matchIds : new String[]{v.identifier}).toArray();
        return new ServerModelData(modelId, animInfo, projectiles, vehicles, serverModelInfo, isCustomSkinModel, isAuth);
    }

    public static void nativeSyncModels(UUID[] uuids, String[] playerNames, String[] modelIds, Object callback) {
        nativeSyncModels(uuids, playerNames, modelIds, callback, null);
    }

    private static void nativeSyncModels(UUID[] uuids, String[] playerNames, String[] modelIds, Object callback, @Nullable Collection<ServerModelData> modelOverride) {
        initRateLimit();
        YSMThreadPool.submitSync(() -> {
            try {
                MinecraftServer currentServer = GameInstance.getServer();
                if (currentServer == null) return;

                for (UUID uuid : uuids) {
                    PlayerSyncState state;
                    boolean shouldSendPacket01 = false;
                    synchronized (syncStates) {
                        state = syncStates.get(uuid);
                        if (state == null) {
                            state = new PlayerSyncState();
                            syncStates.put(uuid, state);
                            shouldSendPacket01 = true;
                        }

                        if (modelOverride != null) {
                            state.allowedModels.addAll(modelOverride);
                        } else if (shouldSendPacket01) {
                            state.allowedModels.addAll(CACHE_NAME_INFO.values());
                        }
                        state.partialSync = modelOverride != null || state.partialSync;
                    }

                    if (shouldSendPacket01) {
                        int garbageLen = 16 + theRandom.nextInt(48);
                        byte[] garbage = new byte[garbageLen];
                        theRandom.nextBytes(garbage);

                        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                            outBuf.writeGarbageHeader(garbageLen, garbage);
                            outBuf.writeByte((byte) 0x01);
                            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), YsmCrypt.publicKey, true);
                            
                            synchronized (syncStates) {
                                state.step = 1;
                                state.key1 = result.nextKey();
                                state.lastActiveMs = System.currentTimeMillis();
                            }

                            YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Initiating model sync handshake with player {}. Sending Packet 01 (Public Key Exchange). Size: {} bytes.", getPlayerName(uuid), result.data().length);

                            if (sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer())) {
                                Set<String> delivered = deliveredModelIds.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
                                for (ServerModelData model : state.allowedModels) {
                                    delivered.add(model.getModelId());
                                }
                            }
                        }
                    } else {
                        // Already synced or in progress. 
                        // If modelOverride is present, and we are at step >= 2, we can push Packet03!
                        if (modelOverride != null && state.step >= 2) {
                            synchronized (syncStates) {
                                state.step = 2;
                                sendPacket03(uuid, state, modelOverride);
                                Set<String> delivered = deliveredModelIds.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
                                for (ServerModelData model : modelOverride) {
                                    delivered.add(model.getModelId());
                                }
                            }
                        }
                    }
                }
//                if (callback != null) onAuthDataReceived(null, callback);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Sync initiation failed", e);
            }
        });
    }

    private static void sendPacket03(UUID uuid, PlayerSyncState state, java.util.Collection<ServerModelData> modelsToSend) {
        int garbageLen = 16 + theRandom.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        theRandom.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);

            outBuf.writeVarInt(3); // Type
            outBuf.writeVarLong(state.partialSync ? -1L : 0L); // -1 means targeted model sync, not the full server catalog.

            outBuf.getRawBuf().writeBytes(serverKey);
            outBuf.getRawBuf().writeBytes(state.clientKey);

            outBuf.writeVarInt(modelsToSend.size());
            for (ServerModelData model : modelsToSend) {
                String sha256 = model.getLoadedModelData().getModelHash();
                long[] hashes = YsmCrypt.calculateModelHashes(sha256, serverKey);
                outBuf.writeVarLong(hashes[0]);
                outBuf.writeVarLong(hashes[1]);
                outBuf.writeString(model.getModelId());
                outBuf.writeVarInt(model.isAuth() ? 1 : 0);
                outBuf.writeVarInt(model.isCustomSkinModel() ? 1 : 0);
                outBuf.writeVarInt(32); // format
            }

            outBuf.writeVarInt(packs.size());
            for (ServerPackData pack : packs.values()) {
                outBuf.writeString(pack.folderPath);

                // еЇ«е…Ґењ–жЁ™иі‡иЁЉ
                if (pack.iconData != null) {
                    outBuf.writeVarInt(1);
                    outBuf.writeByteArray(pack.iconData);
                    outBuf.writeVarInt(pack.iconWidth);
                    outBuf.writeVarInt(pack.iconHeight);
                    outBuf.writeVarInt(pack.iconFormat);
                    outBuf.writeVarInt(1); // unkImageData
                } else {
                    outBuf.writeVarInt(0);
                }

                // еЇ«е…Ґеџєз¤Ћиі‡иЁЉ
                if (pack.name != null || pack.description != null) {
                    outBuf.writeVarInt(1);
                    outBuf.writeString(pack.name != null ? pack.name : "");
                    outBuf.writeString(pack.description != null ? pack.description : "");
                } else {
                    outBuf.writeVarInt(0);
                }

                // еЇ«е…ҐиЄћиЁЂжњ¬ењ°еЊ–
                if (pack.lang != null && !pack.lang.isEmpty()) {
                    outBuf.writeVarInt(pack.lang.size());
                    for (Map.Entry<String, Map<String, String>> langEntry : pack.lang.entrySet()) {
                        outBuf.writeString(langEntry.getKey());
                        outBuf.writeVarInt(langEntry.getValue().size());
                        for (Map.Entry<String, String> kv : langEntry.getValue().entrySet()) {
                            outBuf.writeString(kv.getKey());
                            outBuf.writeString(kv.getValue());
                        }
                    }
                } else {
                    outBuf.writeVarInt(0);
                }
            }

            outBuf.writeVarInt(0);  // \0

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), state.clientNextKey, false);
            YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Sending Packet 03 (Catalog) to player {}. Catalog contains {} allowed models, {} packs. Total encrypted packet size: {} bytes.", getPlayerName(uuid), state.allowedModels.size(), packs.size(), result.data().length);
            sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendPacket05(UUID uuid, PlayerSyncState state, List<long[]> requestedHashes) {
        YSMThreadPool.submitSync(() -> {
            try {
                threadLimiter.acquire();

                PendingTransfer transfer = new PendingTransfer();

                for (long[] hashes : requestedHashes) {
                    long hash1 = hashes[0];
                    long hash2 = hashes[1];
                    String fileName = String.format("%016x%016x", hash1, hash2);
                    Path file = ServerModelManager.CACHE_SERVER.resolve(fileName);

                    if (!Files.exists(file)) continue;

                    byte[] fileData = readLimitedFileBytes(file, MAX_MODEL_FILE_BYTES);
                    int totalSize = fileData.length;
                    int maxChunkSize = 30720;
                    int chunkCount = (totalSize + maxChunkSize - 1) / maxChunkSize;
                    int chunkSize = (totalSize + chunkCount - 1) / chunkCount;

                    YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Sending model file '{}' (hash: {}) to player {}. Total size: {} bytes, chunks: {}, chunk size: {} bytes.", fileName, hash1 + "_" + hash2, getPlayerName(uuid), totalSize, chunkCount, chunkSize);

                    int offset = 0;

                    while (offset < totalSize) {
                        int length = Math.min(chunkSize, totalSize - offset);

                        int garbageLen = 16 + theRandom.nextInt(48);
                        byte[] garbage = new byte[garbageLen];
                        theRandom.nextBytes(garbage);

                        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                            outBuf.writeGarbageHeader(garbageLen, garbage);
                            outBuf.writeVarInt(5); // Type
                            outBuf.writeVarLong(hash1);
                            outBuf.writeVarLong(hash2);
                            outBuf.writeVarInt(totalSize);
                            outBuf.writeVarInt(offset);
                            outBuf.writeVarInt(length);
                            outBuf.getRawBuf().writeBytes(fileData, offset, length);
                            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), state.key1, false);

                            if (bandwidthLimiter != null) {
                                bandwidthLimiter.acquire(result.data().length);
                            }
                            if (state.playerBandwidthLimiter != null) {
                                state.playerBandwidthLimiter.acquire(result.data().length);
                            }


                            // Stream chunks
                            boolean success = sendModelData(uuid, ByteBuffer.wrap(result.data()), transfer);
                            if (success) {
                                YesSteveModel.LOGGER.debug("[BPM-NET] SERVER: Sent chunk {}/{} ({} bytes) of model '{}' to player {}.", offset + length, totalSize, result.data().length, fileName, getPlayerName(uuid));
                                offset += length;
                            } else {
                                if (getPlayerConnection(uuid) == null) {
                                    YesSteveModel.LOGGER.warn("[BPM-NET] SERVER: Player {} disconnected during model transfer. Aborting.", uuid);
                                    return;
                                }
                                try { Thread.sleep(5); } catch (InterruptedException e) {}
                            }
                        }
                    }
                    YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Finished sending all chunks of model '{}' to player {}.", fileName, getPlayerName(uuid));
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("Failed to send model chunks to " + uuid, e);
            } finally {
                threadLimiter.release();
            }
        });
    }

    public static void nativeExportModel(String modelID, @Nullable String extra, @Nullable Consumer<ExportResult> callback) {
        YSMThreadPool.submit(() -> {
            try {
                ServerModelData modelData = CACHE_NAME_INFO.get(modelID);
                if (modelData == null) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, (Component) YSMNativeHelper.createTranslatableComponent("commands.better_player_model.export.failure",new Object[]{": " + modelID + "\n Model not found"}), "", "", 0));
                    }
                    return;
                }

                String sha256 = modelData.getLoadedModelData().getModelHash();
                long[] hashes = YsmCrypt.calculateModelHashes(sha256, serverKey);
                String cacheFileName = String.format("%016x%016x", hashes[0], hashes[1]);
                Path cacheFile = CACHE_SERVER.resolve(cacheFileName);

                if (!Files.exists(cacheFile)) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("Cache file missing for: " + modelID), "", "", 0));
                    }
                    return;
                }

                byte[] cacheData = readLimitedFileBytes(cacheFile, MAX_MODEL_FILE_BYTES);
                byte[] clearText = YsmCrypt.read(cacheData, serverKey);

                int coreDataLength;
                try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(clearText, 32)) {
                    deserializer.deserializeKeepOpen();
                    coreDataLength = deserializer.getReader().getOffset();
                }

                try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                    outBuf.writeDword(32);
                    outBuf.getRawBuf().writeBytes(clearText, 0, coreDataLength);
                    outBuf.writeVarInt(32); // version
                    outBuf.writeVarInt(1);
                    byte[] randBytes = new byte[8];
                    theRandom.nextBytes(randBytes);
                    StringBuilder sb = new StringBuilder(16);
                    for (byte b : randBytes) {
                        sb.append(String.format("%02x", b));
                    }
                    outBuf.writeString(sb.toString());
                    outBuf.writeVarLong(java.time.Instant.now().getEpochSecond());
                    outBuf.writeString(extra != null ? extra : "");
                    outBuf.writeVarInt(0);
                    byte[] rawBytes = new byte[outBuf.getRawBuf().readableBytes()];
                    outBuf.getRawBuf().readBytes(rawBytes);
                    byte[] finalEncrypted = YsmCrypt.encryptYsmFile(rawBytes);
                    Path exportPath = EXPORT.resolve(modelID + ".ysm");
                    Files.createDirectories(exportPath.getParent());
                    Files.write(exportPath, finalEncrypted);
                    if (callback != null) {
                        String displayPath = Paths.get("export", modelID + ".ysm").toString();
                        callback.accept(new ExportResult(true, null, displayPath, "", 0));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.accept(new ExportResult(false, Component.literal("Export failed: " + e.getMessage()), "", "", 0));
                }
            }
        });
    }

    public static Optional<ServerModelData> getModelDefinition(String str) {
        return Optional.ofNullable(CACHE_NAME_INFO.get(str));
    }

    public static Map<String, ServerModelData> getServerModelInfo() {
        return CACHE_NAME_INFO;
    }

    public static Set<String> getAuthModels() {
        return AUTH_MODELS;
    }

    public static boolean isModelUploadAllowed() {
        try {
            return ServerConfig.ALLOW_MODEL_UPLOAD.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int getModelUploadMaxBytes() {
        try {
            return Math.max(1, ServerConfig.MODEL_UPLOAD_MAX_MB.get()) * 1024 * 1024;
        } catch (IllegalStateException e) {
            return 128 * 1024 * 1024;
        }
    }

    public static int getModelUploadChunksPerTick() {
        try {
            return Math.max(1, ServerConfig.MODEL_UPLOAD_CHUNKS_PER_TICK.get());
        } catch (IllegalStateException e) {
            return 4;
        }
    }

    public static UploadStartResult beginModelUpload(ServerPlayer sender, String requestedModelId, String fileName, int totalBytes, String sha256) {
        cleanupExpiredUploads();
        if (!isModelUploadAllowed()) {
            return UploadStartResult.reject((byte) 6, "Model import disabled");
        }
        if (sender == null || !NetworkHandler.isPlayerConnected(sender)) {
            return UploadStartResult.reject((byte) 3, "No import permission");
        }
        String modelId = normalizeUploadedModelId(requestedModelId);
        ImportKind importKind = importKindFromFileName(fileName);
        if (modelId == null || importKind == ImportKind.UNKNOWN || sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            return UploadStartResult.reject((byte) 5, "Invalid model id or hash");
        }
        if (importKind == ImportKind.SEVEN_ZIP) {
            return UploadStartResult.reject((byte) 7, "7z import is not supported yet");
        }
        int maxBytes = getModelUploadMaxBytes();
        if (totalBytes <= 0 || totalBytes > maxBytes) {
            return UploadStartResult.reject((byte) 2, "File exceeds server limit");
        }
        synchronized (uploadStates) {
            if (CACHE_NAME_INFO.containsKey(modelId) || uploadStates.values().stream().anyMatch(state -> state.modelId.equals(modelId))) {
                return UploadStartResult.reject((byte) 1, "Model ID already exists");
            }
            long senderUploads = uploadStates.values().stream().filter(state -> state.owner.equals(sender.getUUID())).count();
            if (senderUploads >= MAX_ACTIVE_UPLOADS_PER_PLAYER) {
                return UploadStartResult.reject((byte) 8, "Too many active uploads");
            }
            if (uploadStates.size() >= MAX_ACTIVE_UPLOAD_SESSIONS) {
                return UploadStartResult.reject((byte) 8, "Too many active uploads");
            }
            long reservedBytes = getActiveUploadBytes();
            long maxReservedBytes = Math.max((long) maxBytes, Math.min(MAX_ACTIVE_UPLOAD_BYTES_HARD_CAP, (long) maxBytes * 2L));
            if (reservedBytes + (long) totalBytes > maxReservedBytes) {
                return UploadStartResult.reject((byte) 8, "Upload memory budget exceeded");
            }

            long uploadId;
            do {
                uploadId = theRandom.nextLong();
            } while (uploadId == 0L || uploadStates.containsKey(uploadId));

            ModelUploadState state = new ModelUploadState(uploadId, sender.getUUID(), modelId, fileName, importKind, totalBytes, sha256.toLowerCase(Locale.ROOT));
            uploadStates.put(uploadId, state);
            YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Player '{}' (UUID {}) initiated upload for model ID '{}', file: '{}', total bytes: {}, upload ID: {}", sender.getScoreboardName(), sender.getUUID(), modelId, fileName, totalBytes, uploadId);
            return new UploadStartResult(uploadId, (byte) 0, UPLOAD_CHUNK_SIZE, maxBytes, getModelUploadChunksPerTick(), "");
        }
    }

    private static byte[] readLimitedFileBytes(Path file, long maxBytes) throws IOException {
        long size = Files.size(file);
        if (size < 0 || size > maxBytes) {
            throw new IOException("File exceeds limit: " + file);
        }
        return Files.readAllBytes(file);
    }

    private static byte[] readLimitedStream(InputStream in, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        long total = 0L;
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Stream exceeds limit");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    public static void receiveModelUploadChunk(ServerPlayer sender, long uploadId, int offset, byte[] data) {
        ModelUploadState state = uploadStates.get(uploadId);
        if (state == null || sender == null || !state.owner.equals(sender.getUUID())) {
            return;
        }
        state.touch();
        if (data == null || data.length <= 0 || data.length > UPLOAD_CHUNK_SIZE || offset < 0 || (long) offset + (long) data.length > state.data.length || offset != state.receivedBytes) {
            YesSteveModel.LOGGER.warn("[BPM] Rejected invalid model upload chunk player={} modelId={} uploadId={} offset={} length={} received={} total={}",
                    sender.getScoreboardName(), state.modelId, uploadId, offset, data == null ? -1 : data.length, state.receivedBytes, state.data.length);
            state.failed = true;
            return;
        }
        if (!state.tryAcceptChunk(getModelUploadChunksPerTick())) {
            YesSteveModel.LOGGER.warn("[BPM] Rejected too-fast model upload player={} modelId={} uploadId={} received={} total={}",
                    sender.getScoreboardName(), state.modelId, uploadId, state.receivedBytes, state.data.length);
            state.failed = true;
            return;
        }
        System.arraycopy(data, 0, state.data, offset, data.length);
        state.receivedBytes += data.length;
        YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Received chunk offset {}/{} ({} bytes) from player '{}' for upload ID {}.", offset, state.data.length, data.length, sender.getScoreboardName(), uploadId);
    }

    public static UploadFinishResult finishModelUpload(ServerPlayer sender, long uploadId) {
        ModelUploadState state = uploadStates.remove(uploadId);
        if (state == null || sender == null || !state.owner.equals(sender.getUUID())) {
            return UploadFinishResult.reject(uploadId, (byte) 4, "Session expired");
        }
        YesSteveModel.LOGGER.info("[BPM-NET] SERVER: Player '{}' requested upload completion for upload ID {}. Verifying hash...", sender.getScoreboardName(), uploadId);
        if (state.failed) {
            return UploadFinishResult.reject(uploadId, (byte) 5, "Incomplete upload");
        }
        if (state.receivedBytes != state.data.length) {
            return UploadFinishResult.reject(uploadId, (byte) 5, "Incomplete upload");
        }
        String actualSha256 = DigestUtil.sha256Hex(state.data);
        if (!state.sha256.equals(actualSha256)) {
            YesSteveModel.LOGGER.warn("[BPM] Import transfer hash mismatch modelId={} file={} type={} declaredSha256={} actualSha256={} bytes={} received={}",
                    state.modelId, state.fileName, state.importKind, state.sha256, actualSha256, state.data.length, state.receivedBytes);
            return UploadFinishResult.reject(uploadId, (byte) 1, "Hash mismatch");
        }

        RawYsmModel rawModel;
        try {
            rawModel = parseUploadedModel(state.data, "import:" + state.fileName, state.importKind);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Failed to parse imported model modelId={} file={} type={} rawSha256={} bytes={}",
                    state.modelId, state.fileName, state.importKind, actualSha256, state.data.length, e);
            return UploadFinishResult.reject(uploadId, (byte) 2, e.getMessage());
        }
        YesSteveModel.LOGGER.info("[BPM] Parsed import modelId={} file={} type={} cryptoVersion={} rawSha256={} contentHash={} metadataName='{}' authors={}",
                state.modelId,
                state.fileName,
                state.importKind,
                YesModelUtils.getYsmCryptoVersion(state.data),
                actualSha256,
                rawModel.properties.sha256,
                rawModel.metadata.name,
                rawModel.metadata.authors.size());

        try {
            if (processAndCacheModel(state.modelId, rawModel, CACHE_SERVER, false, new HashSet<>()) == null) {
                return UploadFinishResult.reject(uploadId, (byte) 2, "Server failed to cache model");
            }
            Path target = CUSTOM.resolve(state.modelId + extensionFor(state.importKind)).normalize();
            Path customRoot = CUSTOM.toAbsolutePath().normalize();
            Path absoluteTarget = target.toAbsolutePath().normalize();
            if (!absoluteTarget.startsWith(customRoot)) {
                return UploadFinishResult.reject(uploadId, (byte) 6, "Server rejected write");
            }
            Files.createDirectories(absoluteTarget.getParent());
            Path temp = Files.createTempFile(absoluteTarget.getParent(), absoluteTarget.getFileName().toString(), ".tmp");
            Files.write(temp, state.data);
            try {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Failed to store imported model: " + state.modelId, e);
            return UploadFinishResult.reject(uploadId, (byte) 3, e.getMessage());
        }

        ModelLoadResult reloadResult = reloadModelsAfterImport();
        if (!reloadResult.isSuccess()) {
            Component errorMessage = reloadResult.getErrorMessage();
            return UploadFinishResult.reject(uploadId, (byte) 8, errorMessage == null ? "Imported model scan failed" : errorMessage.getString());
        }
        String matchedModelId = null;
        for (String loadedId : reloadResult.getModelDefinitions().keySet()) {
            if (loadedId.equalsIgnoreCase(state.modelId)) {
                matchedModelId = loadedId;
                break;
            }
        }
        if (matchedModelId == null) {
            YesSteveModel.LOGGER.warn("[BPM] Imported model was written but not visible after scan: modelId={} file={} type={} rawSha256={} contentHash={}",
                    state.modelId, state.fileName, state.importKind, actualSha256, rawModel.properties.sha256);
            return UploadFinishResult.reject(uploadId, (byte) 8, "Imported model is not visible after scan");
        }

        YesSteveModel.LOGGER.info("[BPM] Imported model '{}' from {} as {}", matchedModelId, sender.getScoreboardName(), state.importKind);
        syncImportedModelToOtherPlayers(sender, matchedModelId);
        long[] hashes = YsmCrypt.calculateModelHashes(rawModel.properties.sha256, serverKey);
        return new UploadFinishResult(uploadId, (byte) 0, matchedModelId, hashes[0], hashes[1], "");
    }

    private static ModelLoadResult reloadModelsAfterImport() {
        Map<String, ServerModelData> loadedModels = Collections.synchronizedMap(new LinkedHashMap<>());
        Set<String> authIds = Collections.synchronizedSet(new HashSet<>());
        Set<String> validCacheFiles = Collections.synchronizedSet(new HashSet<>());

        try {
            packs.clear();
            scanDirectoryPacks(BUILT);
            scanDirectoryPacks(CUSTOM);
            scanDirectoryPacks(AUTH);

            scanDirectoryModels(BUILT, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(CUSTOM, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(AUTH, CACHE_SERVER, loadedModels, authIds, validCacheFiles, true);
            cleanupServerCache(validCacheFiles);
            ModelLoadResult result = new ModelLoadResult(true, null, loadedModels, authIds.toArray(new String[0]));
            onModelLoadComplete(result, null);
            return result;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Failed to reload models after import", e);
            return new ModelLoadResult(false, Component.literal(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), null, null);
        }
    }

    private static void cleanupServerCache(Set<String> validCacheFiles) {
        try (Stream<Path> stream = Files.list(CACHE_SERVER)) {
            stream.forEach(file -> {
                if (!validCacheFiles.contains(file.getFileName().toString())) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void syncLoadedModelsToPlayers() {
        MinecraftServer currentServer = GameInstance.getServer();
        if (currentServer == null) {
            return;
        }
        currentServer.execute(() -> {
            List<ServerPlayer> players = currentServer.getPlayerList().getPlayers();
            for (ServerPlayer player : players) {
                validatePlayerModel(player);
            }
            nativeSyncModels(players.stream().filter(NetworkHandler::isPlayerConnected).map(ServerPlayer::getUUID).toArray(UUID[]::new),
                    players.stream().filter(NetworkHandler::isPlayerConnected).map(ServerPlayer::getScoreboardName).toArray(String[]::new),
                    collectPlayerModelIds(players),
                    null);
        });
    }

    private static void syncImportedModelToOtherPlayers(ServerPlayer owner, String modelId) {
        MinecraftServer currentServer = GameInstance.getServer();
        if (currentServer == null || owner == null || modelId == null || modelId.isBlank()) {
            return;
        }
        currentServer.execute(() -> {
            ServerModelData model = CACHE_NAME_INFO.get(modelId);
            if (model == null) {
                return;
            }
            List<ServerPlayer> receivers = currentServer.getPlayerList().getPlayers().stream()
                    .filter(NetworkHandler::isPlayerConnected)
                    .filter(player -> !player.getUUID().equals(owner.getUUID()))
                    .toList();
            if (receivers.isEmpty()) {
                return;
            }
            nativeSyncModels(
                    receivers.stream().map(ServerPlayer::getUUID).toArray(UUID[]::new),
                    receivers.stream().map(ServerPlayer::getScoreboardName).toArray(String[]::new),
                    new String[]{modelId},
                    null,
                    Collections.singletonList(model)
            );
        });
    }

    public static void syncModelToPlayersIfNeeded(Collection<ServerPlayer> players, String modelId) {
        if (players == null || players.isEmpty() || modelId == null || modelId.isBlank()) {
            return;
        }
        ServerModelData model = CACHE_NAME_INFO.get(modelId);
        if (model == null) {
            return;
        }
        List<ServerPlayer> receivers = players.stream()
                .filter(NetworkHandler::isPlayerConnected)
                .filter(player -> deliveredModelIds.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet()).add(modelId))
                .toList();
        if (receivers.isEmpty()) {
            return;
        }
        nativeSyncModels(
                receivers.stream().map(ServerPlayer::getUUID).toArray(UUID[]::new),
                receivers.stream().map(ServerPlayer::getScoreboardName).toArray(String[]::new),
                new String[]{modelId},
                null,
                Collections.singletonList(model)
        );
    }

    @Nullable
    private static String normalizeUploadedModelId(@Nullable String modelId) {
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
            for (String extension : new String[]{EXT_YSM, EXT_ZIP, EXT_7Z}) {
                if (normalized.endsWith(extension)) {
                    normalized = normalized.substring(0, normalized.length() - extension.length());
                    stripped = true;
                }
            }
        } while (stripped);
        normalized = normalized.replaceAll("[^a-z0-9_./-]+", "_");
        normalized = normalized.replaceAll("/+", "/");
        if (normalized.isBlank() || normalized.contains("..") ) {
            return null;
        }
        return normalized;
    }

    private static void cleanupExpiredUploads() {
        long now = System.currentTimeMillis();
        uploadStates.entrySet().removeIf(entry -> now - entry.getValue().lastTouchedMs > UPLOAD_SESSION_TIMEOUT_MS);
    }

    private static long getActiveUploadBytes() {
        long total = 0L;
        for (ModelUploadState state : uploadStates.values()) {
            total += state.data.length;
        }
        return total;
    }

    public static void requestPlayerAuth(ServerPlayer serverPlayer, @Nullable Consumer<UUIDComponentData> consumer) {
        MinecraftServer currentServer = GameInstance.getServer();
        currentServer.execute(() -> {
            List<ServerPlayer> players = currentServer.getPlayerList().getPlayers();
            ArrayList<FloatReferencePair<ServerPlayer>> arrayList = new ArrayList<>();
            for (ServerPlayer serverPlayer2 : players) {
                if (serverPlayer2.level().dimensionType() == serverPlayer.level().dimensionType()) {
                    arrayList.add(FloatReferencePair.of(serverPlayer2.distanceTo(serverPlayer), serverPlayer2));
                }
            }
            arrayList.sort((a, b) -> Float.compare(a.firstFloat(), b.firstFloat()));
            nativeSyncModels(new UUID[]{serverPlayer.getUUID()}, new String[]{serverPlayer.getName().getString()}, collectPlayerModelIds(arrayList.stream().map(it.unimi.dsi.fastutil.Pair::second).toList()), consumer);
        });
    }

    public static boolean loadModels(@Nullable Consumer<ModelLoadResult> consumer, @Nullable Consumer<UUIDComponentData> consumer2) {
        Consumer<ModelLoadResult> action = modelLoadResult -> {
            if (consumer != null) {
                consumer.accept(modelLoadResult);
            }
            MinecraftServer currentServer = GameInstance.getServer();
            if (currentServer == null) {
                return;
            }
            currentServer.execute(() -> {
                List<ServerPlayer> players = currentServer.getPlayerList().getPlayers();
                for (ServerPlayer value : players) {
                    validatePlayerModel(value);
                }
                nativeSyncModels(players.stream().filter(NetworkHandler::isPlayerConnected).map((player) -> player.getUUID()).toArray(i -> new UUID[i]), players.stream().filter(NetworkHandler::isPlayerConnected).map(serverPlayer -> serverPlayer.getName().getString()).toArray(i2 -> new String[i2]), collectPlayerModelIds(players), consumer2);
            });
        };
        return nativeLoadModels(action);
    }

    private static String[] collectPlayerModelIds(Collection<ServerPlayer> collection) {
        return collection.stream().filter(NetworkHandler::isPlayerConnected).map(serverPlayer -> ModelInfoCapability.get(serverPlayer).map(ModelInfoCapability::getModelId)).filter(Optional::isPresent).map(Optional::get).distinct().toArray(String[]::new);
    }

    private static void onModelLoadComplete(ModelLoadResult modelLoadResult, @Nullable Object obj) {
        Consumer<ModelLoadResult> consumer = (Consumer<ModelLoadResult>) obj;
        MinecraftServer currentServer = GameInstance.getServer();
        initialized = true;
        if (currentServer != null) {
            currentServer.execute(() -> {
                if (modelLoadResult.isSuccess()) {
                    IntOpenHashSet intOpenHashSet = new IntOpenHashSet(modelLoadResult.getModelDefinitions().size());
                    for (ServerModelData data : modelLoadResult.getModelDefinitions().values()) {
                        intOpenHashSet.add(data.getLoadedModelData().getHashId());
                    }
                    CACHE_NAME_INFO = modelLoadResult.getModelDefinitions();
                    modelHashSet = intOpenHashSet;
                    AUTH_MODELS = modelLoadResult.getAuthModelIds();
                }
                if (consumer != null) {
                    YSMThreadPool.submit(() -> consumer.accept(modelLoadResult));
                }
            });
            return;
        }
        if (modelLoadResult.isSuccess()) {
            CACHE_NAME_INFO = modelLoadResult.getModelDefinitions();
            AUTH_MODELS = modelLoadResult.getAuthModelIds();
        }
        if (consumer != null) {
            consumer.accept(modelLoadResult);
        }
    }

    public static void syncModelToPlayer(UUID uuid) {
        nativeSendModelData(uuid, null);
        uploadStates.entrySet().removeIf(entry -> entry.getValue().owner.equals(uuid));
    }

    private static String getPlayerName(UUID uuid) {
        net.minecraft.server.MinecraftServer currentServer = dev.architectury.utils.GameInstance.getServer();
        if (currentServer != null) {
            ServerPlayer player = currentServer.getPlayerList().getPlayer(uuid);
            if (player != null) {
                return player.getScoreboardName();
            }
        }
        return "Unknown (" + uuid + ")";
    }

    private static Connection getPlayerConnection(UUID uuid) {
        ServerPlayer player;
        MinecraftServer currentServer = GameInstance.getServer();
        if (currentServer == null || (player = currentServer.getPlayerList().getPlayer(uuid)) == null) {
            return null;
        }
        ServerGamePacketListenerImpl serverGamePacketListenerImpl = player.connection;
        if (!serverGamePacketListenerImpl.isAcceptingMessages() || !serverGamePacketListenerImpl.getClass().equals(ServerGamePacketListenerImpl.class)) {
            return null;
        }
        return ((ServerCommonPacketListenerImplAccessor) serverGamePacketListenerImpl).ysm$getConnection();
    }

    private static boolean sendModelData(UUID uuid, ByteBuffer byteBuffer, PendingTransfer pendingTransfer) {
        Connection connection = getPlayerConnection(uuid);
        if (connection != null) {
            return sendPacketReliably(connection, NetworkHandler.toClientboundPacket(new S2CModelSyncPayload(byteBuffer)), pendingTransfer);
        }
        return false;
    }

    private static Object createModelPacket(ByteBuffer byteBuffer) {
        return NetworkHandler.toClientboundPacket(new S2CModelSyncPayload(byteBuffer));
    }

    private static boolean sendPacketToPlayer(UUID uuid, Object obj, PendingTransfer pendingTransfer) {
        Connection connection = getPlayerConnection(uuid);
        if (connection != null) {
            return sendPacketReliably(connection, obj, pendingTransfer);
        }
        return false;
    }

    private static boolean sendPacketReliably(Connection connection, Object obj, PendingTransfer pendingTransfer) {
        if (!pendingTransfer.hasStarted) {
            pendingTransfer.hasStarted = true;
            pendingTransfer.pendingBytes = ((ConnectionAccessor) connection).ysm$getChannel().unsafe().outboundBuffer().totalPendingWriteBytes() + 65536;
        }

        final AtomicInteger atomicInteger = new AtomicInteger(0);
        while (connection.isConnected()) {
            if (((ConnectionAccessor) connection).ysm$getChannel().unsafe().outboundBuffer().size() > pendingTransfer.pendingBytes) {
                if (!YSMThreadPool.awaitTermination(10)) {
                    return false;
                }
            } else {
                try {
                    // MC 26.x: connection.send signature changed (expects ChannelFutureListener)
                    connection.send((Packet<?>) obj);
                    atomicInteger.set(1);
                    /* new PacketSendListener() {
                        public void onSuccess() {
                            atomicInteger.set(1);
                            // PacketSendListener.super.onSuccess();
                        }
                        @Nullable
                        public Packet<?> onFailure() {
                            atomicInteger.set(-1);
                            return null;
                        }
                    }); */
                    while (atomicInteger.get() == 0) {
                        if (!YSMThreadPool.awaitTermination(5)) {
                            return false;
                        }
                    }
                    if (atomicInteger.get() == 1) {
                        return true;
                    }
                    if (!YSMThreadPool.awaitTermination(100)) {
                        return false;
                    }
                    atomicInteger.set(0);
                } catch (Throwable th) {
                    th.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }

    public static Pair<String, String> getDefaultModelConfig() {
        String defaultModelId;
        String defaultTexture;
        try {
            defaultModelId = ServerConfig.DEFAULT_MODEL_ID.get();
            defaultTexture = normalizeTextureId(ServerConfig.DEFAULT_MODEL_TEXTURE.get());
        } catch (IllegalStateException e) {
            return Pair.of("default", "default");
        }
        if (!initialized) {
            return Pair.of(defaultModelId, defaultTexture);
        }
        String resolvedTexture = resolveTextureOrDefault(defaultModelId, defaultTexture);
        if (resolvedTexture == null) {
            return Pair.of("default", "default");
        }
        return Pair.of(defaultModelId, resolvedTexture);
    }

    @Nullable
    public static String resolveTextureOrDefault(String modelId, @Nullable String requestedTexture) {
        ServerModelData modelData = CACHE_NAME_INFO.get(modelId);
        if (modelData == null) {
            return null;
        }
        List<String> textures = modelData.getModelInfo().getTextures();
        if (textures.isEmpty()) {
            return null;
        }
        String normalizedRequested = normalizeTextureId(requestedTexture);
        if (normalizedRequested != null && textures.contains(normalizedRequested)) {
            return normalizedRequested;
        }
        String modelDefault = normalizeTextureId(modelData.getLoadedModelData().getModelProperties().getDefaultTexture());
        if (modelDefault != null && textures.contains(modelDefault)) {
            return modelDefault;
        }
        return textures.get(0);
    }

    @Nullable
    private static String normalizeTextureId(@Nullable String textureId) {
        if (textureId == null) {
            return null;
        }
        if (textureId.toLowerCase(Locale.ROOT).endsWith(".png") && textureId.length() > 4) {
            return textureId.substring(0, textureId.length() - 4);
        }
        return textureId;
    }

    private static void onAuthDataReceived(UUIDComponentData uuidComponentData, @Nullable Object obj) {
        Consumer consumer = (Consumer) obj;
        if (consumer != null) {
            consumer.accept(uuidComponentData);
        }
    }

    public static void validatePlayerModel(ServerPlayer serverPlayer) {
        if (!CACHE_NAME_INFO.isEmpty()) {
            ModelInfoCapability.get(serverPlayer).ifPresent(modelInfoCap -> {
                AuthModelsCapability.get(serverPlayer).ifPresent(authModelsCap -> {
                    if (authModelsCap.getAuthModels().removeIf(str -> !CACHE_NAME_INFO.containsKey(str))) {
                        NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(authModelsCap.getAuthModels()), serverPlayer);
                    }
                    String modelId = modelInfoCap.getModelId();
                    if (!getServerModelInfo().containsKey(modelId) || (AUTH_MODELS.contains(modelId) && !authModelsCap.containsModel(modelInfoCap.getModelId()))) {
                        modelInfoCap.resetToDefault();
                    } else {
                        String resolvedTexture = resolveTextureOrDefault(modelId, modelInfoCap.getSelectTexture());
                        if (resolvedTexture == null) {
                            modelInfoCap.resetToDefault();
                        } else if (!resolvedTexture.equals(modelInfoCap.getSelectTexture())) {
                            YesSteveModel.LOGGER.warn("[BPM] Fixed invalid texture '{}' for model '{}' on player '{}', using '{}'", modelInfoCap.getSelectTexture(), modelId, serverPlayer.getScoreboardName(), resolvedTexture);
                            modelInfoCap.setModelAndTexture(modelId, resolvedTexture);
                        }
                    }
                    modelInfoCap.retainAnimationKeys(modelHashSet);
                });
            });
        }
    }

    private static class PendingTransfer {
        public long pendingBytes;

        public boolean hasStarted = false;

        private PendingTransfer() {
        }
    }

    public record UploadStartResult(long uploadId, byte status, int chunkSize, int maxTotalBytes, int chunksPerTick, String message) {
        private static UploadStartResult reject(byte status, String message) {
            return new UploadStartResult(0L, status, UPLOAD_CHUNK_SIZE, getModelUploadMaxBytes(), getModelUploadChunksPerTick(), message == null ? "" : message);
        }
    }

    public record UploadFinishResult(long uploadId, byte status, String modelId, long hash1, long hash2, String message) {
        private static UploadFinishResult reject(long uploadId, byte status, String message) {
            return new UploadFinishResult(uploadId, status, "", 0L, 0L, message == null ? "" : message);
        }
    }

    private static class ModelUploadState {
        private final long uploadId;
        private final UUID owner;
        private final String modelId;
        private final String fileName;
        private final ImportKind importKind;
        private final byte[] data;
        private final String sha256;
        private int receivedBytes;
        private boolean failed;
        private long lastTouchedMs;
        private long chunkWindowStartedMs;
        private int chunksInWindow;

        private ModelUploadState(long uploadId, UUID owner, String modelId, String fileName, ImportKind importKind, int totalBytes, String sha256) {
            this.uploadId = uploadId;
            this.owner = owner;
            this.modelId = modelId;
            this.fileName = fileName;
            this.importKind = importKind;
            this.data = new byte[totalBytes];
            this.sha256 = sha256;
            touch();
        }

        private void touch() {
            this.lastTouchedMs = System.currentTimeMillis();
        }

        private boolean tryAcceptChunk(int chunksPerTick) {
            long now = System.currentTimeMillis();
            if (now - this.chunkWindowStartedMs >= UPLOAD_RATE_WINDOW_MS) {
                this.chunkWindowStartedMs = now;
                this.chunksInWindow = 0;
            }
            int expectedPerSecond = Math.max(1, chunksPerTick) * 20;
            int burstAllowance = Math.max(400, expectedPerSecond * 10);
            if (this.chunksInWindow >= expectedPerSecond + burstAllowance) {
                return false;
            }
            this.chunksInWindow++;
            return true;
        }
    }

    private enum ImportKind {
        YSM,
        ZIP,
        SEVEN_ZIP,
        UNKNOWN
    }

    // --- METADATA CACHE OPTIMIZATION ---
    private static final Path METADATA_CACHE_FILE = CACHE.resolve("server_metadata_cache.json");
    private static final Map<String, ModelCacheEntry> metadataCache = new HashMap<>();
    private static boolean cacheModified = false;

    public static class ModelCacheEntry {
        public long fileSize;
        public long lastModified;
        public String cacheFileName;
        public CachedModelData cachedData;
    }

    public static class MetadataCacheWrapper {
        public String serverKeyBase64;
        public Map<String, ModelCacheEntry> entries = new HashMap<>();
    }

    public static class CachedModelData {
        public String modelId;
        public Map<String, String[]> animMap;
        public String[] texArr;
        public String[][] projectiles;
        public String[][] vehicles;
        public boolean isCustomSkinModel;
        public boolean isAuth;

        // ServerModelInfo fields
        public com.elfmcys.yesstevemodel.resource.models.Metadata metadata;
        public com.elfmcys.yesstevemodel.resource.models.ModelProperties modelProperties;
        public com.elfmcys.yesstevemodel.resource.models.MainModelInfo mainModelInfo;
        public int formatVersion;
        public String modelHash;
        public String extra;
        public long timestamp;
        public String rand;
    }

    private static void loadMetadataCache() {
        metadataCache.clear();
        if (Files.exists(METADATA_CACHE_FILE)) {
            try {
                String jsonStr = Files.readString(METADATA_CACHE_FILE, StandardCharsets.UTF_8);
                MetadataCacheWrapper wrapper = YesSteveModel.GSON.fromJson(jsonStr, MetadataCacheWrapper.class);
                String currentKeyBase64 = Base64.getEncoder().encodeToString(serverKey);
                if (wrapper != null && currentKeyBase64.equals(wrapper.serverKeyBase64)) {
                    metadataCache.putAll(wrapper.entries);
                } else {
                    YesSteveModel.LOGGER.warn("[BPM] Server key mismatch or cache empty, invalidating server model metadata cache.");
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.warn("[BPM] Failed to load server metadata cache, rebuilding...", e);
            }
        }
    }

    private static void saveMetadataCache() {
        if (!cacheModified) return;
        try {
            MetadataCacheWrapper wrapper = new MetadataCacheWrapper();
            wrapper.serverKeyBase64 = Base64.getEncoder().encodeToString(serverKey);
            wrapper.entries = metadataCache;
            String jsonStr = YesSteveModel.GSON.toJson(wrapper);
            Files.writeString(METADATA_CACHE_FILE, jsonStr, StandardCharsets.UTF_8);
            cacheModified = false;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[BPM] Failed to save server metadata cache", e);
        }
    }

    private static CachedModelData toCachedData(ServerModelData data) {
        CachedModelData cached = new CachedModelData();
        cached.modelId = data.getModelId();

        Map<String, String[]> animMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : data.getModelInfo().getAnimations().entrySet()) {
            animMap.put(e.getKey(), e.getValue().toArray(new String[0]));
        }
        cached.animMap = animMap;
        cached.texArr = data.getModelInfo().getTextures().toArray(new String[0]);

        // projectiles
        Object[] projs = data.getProjectiles();
        if (projs != null) {
            cached.projectiles = new String[projs.length][];
            for (int i = 0; i < projs.length; i++) {
                cached.projectiles[i] = (String[]) projs[i];
            }
        } else {
            cached.projectiles = new String[0][];
        }

        // vehicles
        Object[] vehs = data.getVehicles();
        if (vehs != null) {
            cached.vehicles = new String[vehs.length][];
            for (int i = 0; i < vehs.length; i++) {
                cached.vehicles[i] = (String[]) vehs[i];
            }
        } else {
            cached.vehicles = new String[0][];
        }

        cached.isCustomSkinModel = data.isCustomSkinModel();
        cached.isAuth = data.isAuth();

        ServerModelInfo info = data.getLoadedModelData();
        cached.metadata = info.getExtraInfo();
        cached.modelProperties = info.getModelProperties();
        cached.mainModelInfo = info.getMainModelInfo();
        cached.formatVersion = info.getFormatVersion();
        cached.modelHash = info.getModelHash();
        cached.extra = info.getExtra();
        cached.timestamp = info.getTimestamp();
        cached.rand = info.getRand();

        return cached;
    }

    private static ServerModelData fromCachedData(CachedModelData cached) {
        ServerAnimationInfo animInfo = new ServerAnimationInfo(cached.animMap, cached.texArr);
        ServerModelInfo info = new ServerModelInfo(
            cached.metadata,
            cached.modelProperties,
            cached.mainModelInfo,
            cached.formatVersion,
            cached.modelHash,
            cached.extra,
            cached.timestamp,
            cached.rand
        );
        return new ServerModelData(
            cached.modelId,
            animInfo,
            cached.projectiles,
            cached.vehicles,
            info,
            cached.isCustomSkinModel,
            cached.isAuth
        );
    }

    private static long[] getDirectoryFingerprint(Path dir) {
        long[] result = new long[]{0L, 0L}; // [totalSize, maxLastModified]
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.forEach(p -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    if (attrs.isRegularFile()) {
                        result[0] += attrs.size();
                        long lastMod = attrs.lastModifiedTime().toMillis();
                        if (lastMod > result[1]) {
                            result[1] = lastMod;
                        }
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
        return result;
    }

    private static class ScanTask {
        final Path path;
        final boolean isDir;
        final boolean isAuth;
        final String modelId;

        ScanTask(Path path, boolean isDir, boolean isAuth, String modelId) {
            this.path = path;
            this.isDir = isDir;
            this.isAuth = isAuth;
            this.modelId = modelId;
        }
    }

    private static void gatherTasks(Path baseDir, Path current, List<ScanTask> tasks, boolean isAuth) {
        if (current == null || !Files.exists(current)) return;
        if (Files.isDirectory(current)) {
            if (YSMFolderDeserializer.isModelFolder(current)) {
                String modelId = normalizeUploadedModelId(baseDir.relativize(current).toString());
                tasks.add(new ScanTask(current, true, isAuth, modelId));
            } else {
                try (Stream<Path> stream = Files.list(current)) {
                    stream.forEach(p -> gatherTasks(baseDir, p, tasks, isAuth));
                } catch (IOException ignored) {}
            }
        } else {
            String fileName = current.getFileName().toString();
            ImportKind importKind = importKindFromFileName(fileName);
            if (importKind != ImportKind.UNKNOWN && importKind != ImportKind.SEVEN_ZIP) {
                String modelId = stripImportExtension(normalizeUploadedModelId(baseDir.relativize(current).toString()));
                tasks.add(new ScanTask(current, false, isAuth, modelId));
            }
        }
    }
}




