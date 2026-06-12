package dev.architectury.registry;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Fabric-backed replacement for Architectury's reload listener registry.
 */
public class ReloadListenerRegistry {
    public static void register(PackType packType, ResourceManagerReloadListener listener, ResourceLocation id) {
        ResourceManagerHelper.get(packType).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                listener.onResourceManagerReload(resourceManager);
            }
        });
    }
}

