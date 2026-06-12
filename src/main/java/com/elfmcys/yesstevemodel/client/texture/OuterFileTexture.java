package com.elfmcys.yesstevemodel.client.texture;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.util.ModelMemoryProfiler;
import rip.ysm.compat.oculus.ShadersTextureType;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class OuterFileTexture extends AbstractTexture implements ITextureMap {
    private byte[] data;

    private Map<ShadersTextureType, OuterFileTexture> suffixTextures = Reference2ReferenceMaps.emptyMap();

    private boolean uploaded;

    public OuterFileTexture(byte[] data) {
        this.data = data;
    }

    public void load(@NotNull ResourceManager resourceManager) {
        this.uploaded = false;
        doLoad();
    }

    public void doLoad() {
        RenderSystem.assertOnRenderThread();
        if (this.uploaded && super.getId() != -1) {
            return;
        }
        NativeImage image = null;
        byte[] textureData = this.data;
        try {
            if (textureData == null) {
                throw new IOException("Texture source bytes were released");
            }
            ModelMemoryProfiler.logBytes("texture-decode-start", null, textureData);
            image = NativeImage.read(new ByteArrayInputStream(textureData));
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("Failed to decode YSM texture, using fallback texture", e);
            image = createFallbackImage();
        }
        uploadImage(image);
    }

    public boolean isLoaded() {
        return this.uploaded && super.getId() != -1;
    }

    @Override
    public int getId() {
        if (!isLoaded() && RenderSystem.isOnRenderThread()) {
            doLoad();
        }
        return super.getId();
    }

    private void uploadImage(NativeImage image) {
        try (image) {
            TextureUtil.prepareImage(super.getId(), image.getWidth(), image.getHeight());
            image.upload(0, 0, 0, false);
            this.uploaded = true;
            ModelMemoryProfiler.log("texture-uploaded", null);
        }
    }

    private static NativeImage createFallbackImage() {
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixelRGBA(0, 0, 0xFFFF00FF);
        return image;
    }

    public void setSuffixTextures(Map<ShadersTextureType, OuterFileTexture> map) {
        this.suffixTextures = Reference2ReferenceMaps.unmodifiable(new Reference2ReferenceOpenHashMap<>(map));
    }

    public Map<ShadersTextureType, ? extends AbstractTexture> getSuffixTextures() {
        return this.suffixTextures;
    }

    @Override
    public void close() {
        super.close();
        this.uploaded = false;
    }
}
