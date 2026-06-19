package com.elfmcys.yesstevemodel.util.data;

import com.elfmcys.yesstevemodel.client.gui.custom.AbstractConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.CheckboxConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RadioConfig;
import com.elfmcys.yesstevemodel.client.gui.custom.configs.RangeConfig;
import com.google.gson.*;

import java.lang.reflect.Type;

public class AbstractConfigAdapter implements JsonSerializer<AbstractConfig>, JsonDeserializer<AbstractConfig> {

    @Override
    public AbstractConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        String type = obj.has("type") ? obj.get("type").getAsString() : "";
        if ("checkbox".equals(type)) {
            return context.deserialize(json, CheckboxConfig.class);
        } else if ("radio".equals(type)) {
            return context.deserialize(json, RadioConfig.class);
        } else if ("range".equals(type)) {
            return context.deserialize(json, RangeConfig.class);
        }
        return null;
    }

    @Override
    public JsonElement serialize(AbstractConfig src, Type typeOfSrc, JsonSerializationContext context) {
        return context.serialize(src, src.getClass());
    }
}
