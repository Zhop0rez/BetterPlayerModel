package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    @Accessor(remap = false, value = "blockModelResolver")
    BlockModelResolver ysm$getBlockRenderDispatcher();

    @Accessor(remap = false, value = "itemModelResolver")
    ItemModelResolver ysm$getItemModelResolver();

    @Accessor(remap = false, value = "mapRenderer")
    MapRenderer ysm$getMapRenderer();

    @Accessor(remap = false, value = "font")
    Font ysm$getFont();

    @Accessor(remap = false, value = "entityModels")
    Supplier<EntityModelSet> ysm$getEntityModels();

    @Accessor(remap = false, value = "equipmentAssets")
    EquipmentAssetManager ysm$getEquipmentAssets();

    @Accessor(remap = false, value = "atlasManager")
    AtlasManager ysm$getAtlasManager();

    @Accessor(remap = false, value = "playerSkinRenderCache")
    PlayerSkinRenderCache ysm$getPlayerSkinRenderCache();
}
