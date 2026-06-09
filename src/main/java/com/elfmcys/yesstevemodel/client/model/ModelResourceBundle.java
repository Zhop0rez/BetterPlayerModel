package com.elfmcys.yesstevemodel.client.model;

import com.elfmcys.yesstevemodel.audio.AudioTrackData;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

import java.util.List;
import java.util.Map;

public class ModelResourceBundle {

    private final Map<String, AudioTrackData> soundEffects;

    // setup@player_init.molangеЌіеЏЇе€›е»єдёЂдёЄеђЌдёєsetupзљ„е‡Ѕж•°пјЊе№¶и®ўй…player_initдє‹д»¶
    // иї™й‡ЊжЇе‡Ѕж•°StringжЇ@е‰Ќйќўзљ„
    private final Object2ReferenceOpenHashMap<String, IValue> functions;

    // setup@player_init.molangеЌіеЏЇе€›е»єдёЂдёЄеђЌдёєsetupзљ„е‡Ѕж•°пјЊе№¶и®ўй…player_initдє‹д»¶
    // иї™й‡ЊжЇдє‹д»¶@еђЋйќўзљ„
    // е¦‚жћњжњ‰е¤љдёЄе°±еЉ е…Ґlist
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
