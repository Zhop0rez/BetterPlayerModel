package com.elfmcys.yesstevemodel.client.model;

import com.elfmcys.yesstevemodel.audio.AudioTrackData;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

import java.util.List;
import java.util.Map;

public class ModelResourceBundle {

    private final Map<String, AudioTrackData> soundEffects;

    // setup@player_init.molang creates a function named setup and subscribes to the player_init event
    // This is the function String, i.e. the part before the @
    private final Object2ReferenceOpenHashMap<String, IValue> functions;

    // setup@player_init.molang creates a function named setup and subscribes to the player_init event
    // This is the part after the @ in the event
    // If there are multiple, add them to the list
    private final Object2ReferenceOpenHashMap<String, List<IValue>> events;

    private final Map<String, Map<String, String>> translations;

    public ModelResourceBundle(Map<String, AudioTrackData> soundEffects, Object2ReferenceOpenHashMap<String, IValue> functions, Object2ReferenceOpenHashMap<String, List<IValue>> events, Map<String, Map<String, String>> translations) {
        this.soundEffects = soundEffects;
        this.functions = functions;
        this.events = events;
        this.translations = translations;
    }

    public Map<String, AudioTrackData> getSoundEffects() {
        return this.soundEffects;
    }

    public Object2ReferenceOpenHashMap<String, IValue> getFunctions() {
        return this.functions;
    }

    public Object2ReferenceOpenHashMap<String, List<IValue>> getEvents() {
        return this.events;
    }

    public Map<String, Map<String, String>> getMetadata() {
        return this.translations;
    }
}
