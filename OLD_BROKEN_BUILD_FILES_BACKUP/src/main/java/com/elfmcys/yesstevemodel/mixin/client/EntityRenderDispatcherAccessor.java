package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.item.ItemColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    @Accessor("blockRenderDispatcher")
    BlockRenderDispatcher ysm$getBlockRenderDispatcher();

    @Accessor("itemRenderer")
    net.minecraft.client.renderer.entity.ItemRenderer ysm$getItemRenderer();

    @Accessor("mapRenderer")
    MapRenderer ysm$getMapRenderer();

    @Accessor("font")
    Font ysm$getFont();

    @Accessor("entityModels")
    EntityModelSet ysm$getEntityModels();
}
