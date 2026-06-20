package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker(remap = false, value = "isLocalServer") boolean ysm$isLocalServer();
    // @Invoker(remap = false, value = "setScreen") void ysm$setScreen(Screen screen);
    @Invoker(remap = false, value = "getTextureManager") TextureManager ysm$getTextureManager();
    @Invoker(remap = false, value = "getEntityRenderDispatcher") EntityRenderDispatcher ysm$getEntityRenderDispatcher();
    @Invoker(remap = false, value = "getSoundManager") SoundManager ysm$getSoundManager();
    @Invoker(remap = false, value = "getResourceManager") ResourceManager ysm$getResourceManager();
    @Invoker(remap = false, value = "getConnection") ClientPacketListener ysm$getConnection();
    // @Invoker(remap = false, value = "renderBuffers") RenderBuffers ysm$renderBuffers();
    @Invoker(remap = false, value = "getDeltaTracker") DeltaTracker ysm$getDeltaTracker();
}

