package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.model.AtlasManager;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    @Accessor("blockRenderDispatcher")
    BlockRenderDispatcher ysm$getBlockRenderDispatcher();

    @Accessor("itemModelResolver")
    ItemModelResolver ysm$getItemModelResolver();

    @Accessor("mapRenderer")
    MapRenderer ysm$getMapRenderer();

    @Accessor("font")
    Font ysm$getFont();

    @Accessor("entityModels")
    Supplier<EntityModelSet> ysm$getEntityModels();

    @Accessor("equipmentAssets")
    EquipmentAssetManager ysm$getEquipmentAssets();

    @Accessor("atlasManager")
    AtlasManager ysm$getAtlasManager();

    @Accessor("playerSkinRenderCache")
    PlayerSkinRenderCache ysm$getPlayerSkinRenderCache();
}
