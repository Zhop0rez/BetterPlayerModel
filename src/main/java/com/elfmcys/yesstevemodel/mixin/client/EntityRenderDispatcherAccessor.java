package com.elfmcys.yesstevemodel.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {
    @Accessor("blockRenderDispatcher")
    BlockRenderDispatcher ysm$getBlockRenderDispatcher();

    @Accessor("itemRenderer")
    ItemRenderer ysm$getItemRenderer();

    @Accessor("itemInHandRenderer")
    ItemInHandRenderer ysm$getItemInHandRenderer();

    @Accessor("font")
    Font ysm$getFont();

    @Accessor("entityModels")
    EntityModelSet ysm$getEntityModels();
}
