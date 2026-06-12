package com.elfmcys.yesstevemodel.geckolib3.util.json;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.MolangParser;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import org.jetbrains.annotations.Nullable;

public class JsonMolangUtils {
    // й»и®¤дёЌеђ€е№¶
    public static IValue[] getExpressions(@Nullable JsonElement element, MolangParser parser, boolean mergeMultilineExpr) {
        if (element == null) return new IValue[]{};

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new IValue[]{parser.parseExpression(getJsonString(element), false)};
        }
        if (!element.isJsonArray()) return new IValue[]{};

        JsonArray array = element.getAsJsonArray();

        if (mergeMultilineExpr) {
            StringBuilder parserText = new StringBuilder();

            for (int i = 0; i < array.size(); i++) {
                parserText.append(getJsonString(array.get(i)));
                // е¦‚жћњдёЌжЇжњЂеђЋдёЂиЎЊпјЊе°±иїЅеЉ дёЂдёЄжЌўиЎЊз¬¦
                if (i < array.size() - 1) {
                    parserText.append("\n");
                }
            }

            return new IValue[]{parser.parseExpression(parserText.toString(), false)};
        } else {
            IValue[] values = new IValue[array.size()];
            for (int i = 0; i < array.size(); i++) {
                String parserText = getJsonString(array.get(i));
                values[i] = parser.parseExpression(parserText, false);
            }
            return values;
        }
    }

    private static String getJsonString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }
}
