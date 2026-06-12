package com.elfmcys.yesstevemodel.client.animation.condition;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

final class ConditionResourceUtil {

    private ConditionResourceUtil() {
    }

    static ResourceLocation parseIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(value);
    }

    static <T> TagKey<T> parseTag(ResourceKey<? extends Registry<T>> registry, String value) {
        ResourceLocation id = parseIdentifier(value);
        return id == null ? null : TagKey.create(registry, id);
    }
}
