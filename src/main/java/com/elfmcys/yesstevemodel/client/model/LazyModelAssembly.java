package com.elfmcys.yesstevemodel.client.model;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.ClientModelInfo;
import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.client.gui.metadata.ModelDisplayAssets;
import com.elfmcys.yesstevemodel.client.gui.metadata.LazyModelDisplayAssets;
import com.elfmcys.yesstevemodel.model.format.ServerModelInfo;
import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.YSMClientMapper;
import com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import rip.ysm.security.YsmCrypt;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class LazyModelAssembly extends ModelAssembly {
    private final String modelId;
    private final File cachedFile;
    private final byte[] clientKey;
    private final boolean isAuth;
    private final LazyModelDisplayAssets lazyTextureRegistry;
    private final boolean isLocal;
    private final boolean isLocalDirectory;

    private ModelAssembly resolved;
    private boolean isResolving = false;

    public LazyModelAssembly(String modelId, File cachedFile, byte[] clientKey, boolean isAuth) {
        super(null, null, null, null, null, null, null);
        this.modelId = modelId;
        this.cachedFile = cachedFile;
        this.clientKey = clientKey != null ? clientKey.clone() : null;
        this.isAuth = isAuth;
        this.isLocal = false;
        this.isLocalDirectory = false;
        this.lazyTextureRegistry = new LazyModelDisplayAssets(this, isAuth);
    }

    public LazyModelAssembly(String modelId, File cachedFile, boolean isLocalDirectory) {
        super(null, null, null, null, null, null, null);
        this.modelId = modelId;
        this.cachedFile = cachedFile;
        this.clientKey = null;
        this.isAuth = false;
        this.isLocal = true;
        this.isLocalDirectory = isLocalDirectory;
        this.lazyTextureRegistry = new LazyModelDisplayAssets(this, false);
    }

    public boolean isResolved() {
        return resolved != null;
    }

    public synchronized ModelAssembly resolve() {
        if (resolved != null) {
            return resolved;
        }
        ModelAssembly fallback = getFallback();
        if (isResolving) {
            return fallback;
        }
        isResolving = true;
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                YesSteveModel.LOGGER.info("[BPM] Lazily loading model async: " + modelId);
                long start = System.currentTimeMillis();
                
                RawYsmModel rawModel;
                if (isLocal) {
                    if (isLocalDirectory) {
                        try (com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMFolderDeserializer(cachedFile.toPath())) {
                            rawModel = deserializer.deserialize();
                        }
                    } else {
                        byte[] fileBytes = java.nio.file.Files.readAllBytes(cachedFile.toPath());
                        rawModel = ClientModelManager.parseImportModel(cachedFile.getName(), fileBytes);
                    }
                } else {
                    byte[] fileBytes = java.nio.file.Files.readAllBytes(cachedFile.toPath());
                    byte[] decompressed = rip.ysm.security.YsmCrypt.read(fileBytes, clientKey);
                    try (com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer(decompressed, 32)) {
                        rawModel = deserializer.deserializeKeepOpen();
                        
                        rawModel.footer.version = deserializer.getReader().readVarInt();
                        rawModel.footer.unkInt1 = deserializer.getReader().readVarInt();
                        if (rawModel.footer.unkInt1 != 0) {
                            rawModel.footer.rand = deserializer.getReader().readString();
                        }
                        rawModel.footer.time = deserializer.getReader().readVarLong();
                        if (rawModel.footer.unkInt1 != 0) {
                            rawModel.footer.extra = deserializer.getReader().readString();
                            rawModel.footer.unkInt2 = deserializer.getReader().readVarInt();
                        }
                    }
                }
                
                ClientModelInfo parsedBundle = com.elfmcys.yesstevemodel.resource.YSMClientMapper.buildParsedBundle(rawModel, modelId);
                
                boolean success = ClientModelManager.processModelData(parsedBundle, modelId, false, isAuth);
                if (success) {
                    long duration = System.currentTimeMillis() - start;
                    YesSteveModel.LOGGER.info("[BPM] Successfully built and queued model async: " + modelId + " in " + duration + " ms");
                } else {
                    YesSteveModel.LOGGER.error("[BPM] Failed to build model async: " + modelId);
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[BPM] Failed to lazily resolve model async: " + modelId, e);
                synchronized (this) {
                    resolved = getFallback();
                    isResolving = false;
                }
            }
        });
        
        return fallback;
    }

    private ModelAssembly getFallback() {
        ModelAssembly defaultModel = ClientModelManager.getLocalModelContext();
        if (defaultModel != null && defaultModel != this && !(defaultModel instanceof LazyModelAssembly)) {
            return defaultModel;
        } else {
            return new ModelAssembly(
                null,
                java.util.Collections.emptyMap(),
                java.util.Collections.emptyMap(),
                null,
                new ServerModelInfo(
                    null,
                    new com.elfmcys.yesstevemodel.resource.models.ModelProperties(1.0f, 1.0f, "", "idle", new com.elfmcys.yesstevemodel.util.data.OrderedStringMap<>(new String[0], new String[0]), new com.elfmcys.yesstevemodel.client.gui.custom.ExtraAnimationButtons[0], new com.elfmcys.yesstevemodel.util.data.StringMapPair[0], true, false, false),
                    new com.elfmcys.yesstevemodel.resource.models.MainModelInfo(0, 0, 0),
                    65535,
                    "",
                    "",
                    0L,
                    ""
                ),
                new ModelDisplayAssets(null, false, null, null),
                java.util.Collections.emptyList()
            );
        }
    }

    @Override
    public PlayerModelBundle getAnimationBundle() {
        return resolve().getAnimationBundle();
    }

    @Override
    public List<AbstractTexture> getTextures() {
        return resolve().getTextures();
    }

    @Override
    public ModelResourceBundle getExpressionCache() {
        return resolve().getExpressionCache();
    }

    @Override
    public Map<Identifier, ProjectileModelBundle> getProjectileModels() {
        return resolve().getProjectileModels();
    }

    @Override
    public Map<Identifier, VehicleModelBundle> getVehicleModels() {
        return resolve().getVehicleModels();
    }

    @Override
    public ServerModelInfo getModelData() {
        return resolve().getModelData();
    }

    @Override
    public ModelDisplayAssets getTextureRegistry() {
        return lazyTextureRegistry;
    }

    @Override
    public String getDisplayName(String str) {
        return resolve().getDisplayName(str);
    }

    public synchronized void setResolved(ModelAssembly assembly) {
        this.resolved = assembly;
        this.isResolving = false;
    }

    public synchronized void unresolve() {
        this.resolved = null;
    }
}
