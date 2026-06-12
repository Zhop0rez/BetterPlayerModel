package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.util.data.MemoizationCache;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public class CustomEntityTranslucentRenderType {

    private static final Function<ResourceLocation, CustomEntityTranslucentRenderType> CACHE = MemoizationCache.memoize(CustomEntityTranslucentRenderType::new);

    private CustomEntityTranslucentRenderType(ResourceLocation ResourceLocation) {
    }

    public static CustomEntityTranslucentRenderType get(ResourceLocation ResourceLocation) {
        return CACHE.apply(ResourceLocation);
    }
}

