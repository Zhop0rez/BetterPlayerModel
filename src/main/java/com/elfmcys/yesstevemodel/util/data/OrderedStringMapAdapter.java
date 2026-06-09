package com.elfmcys.yesstevemodel.util.data;

import com.google.gson.*;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OrderedStringMapAdapter implements JsonSerializer<OrderedStringMap<?, ?>>, JsonDeserializer<OrderedStringMap<?, ?>> {

    @Override
    public JsonElement serialize(OrderedStringMap<?, ?> src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<?, ?> entry : src.entrySet()) {
            if (entry.getKey() != null) {
                obj.add(entry.getKey().toString(), context.serialize(entry.getValue()));
            }
        }
        return obj;
    }

    @Override
    public OrderedStringMap<?, ?> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        
        Type[] typeArgs = null;
        if (typeOfT instanceof ParameterizedType) {
            typeArgs = ((ParameterizedType) typeOfT).getActualTypeArguments();
        }
        
        Type keyType = (typeArgs != null && typeArgs.length > 0) ? typeArgs[0] : String.class;
        Type valType = (typeArgs != null && typeArgs.length > 1) ? typeArgs[1] : String.class;
        
        List<Object> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            Object key = context.deserialize(new JsonPrimitive(entry.getKey()), keyType);
            Object val = context.deserialize(entry.getValue(), valType);
            keys.add(key);
            values.add(val);
        }
        
        Class<?> keyClass = keyType instanceof Class ? (Class<?>) keyType : Object.class;
        Class<?> valClass = valType instanceof Class ? (Class<?>) valType : Object.class;
        
        Object[] keysArray = (Object[]) Array.newInstance(keyClass, keys.size());
        Object[] valuesArray = (Object[]) Array.newInstance(valClass, values.size());
        
        keys.toArray(keysArray);
        values.toArray(valuesArray);
        
        return new OrderedStringMap<>(keysArray, valuesArray);
    }
}
