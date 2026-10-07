package com.elfmcys.yesstevemodel.model.format;

import it.unimi.dsi.fastutil.objects.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ServerAnimationInfo {

    // Animation file prefix + all animation names inside it
    private final Map<String, Set<String>> animations;

    // The model's texture name
    private final List<String> textures;

    public ServerAnimationInfo(Map<String, String[]> animations, String[] textures) {
        this.animations = Object2ObjectMaps.unmodifiable(new Object2ObjectOpenHashMap<>(animations.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> ObjectSets.unmodifiable(ObjectOpenHashSet.of(entry.getValue()))))));
        this.textures = ObjectLists.unmodifiable(ObjectArrayList.of(textures));
    }

    public Map<String, Set<String>> getAnimations() {
        return this.animations;
    }

    public List<String> getTextures() {
        return this.textures;
    }
}
