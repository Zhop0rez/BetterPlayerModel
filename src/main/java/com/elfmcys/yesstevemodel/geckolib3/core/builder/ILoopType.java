package com.elfmcys.yesstevemodel.geckolib3.core.builder;

import com.elfmcys.yesstevemodel.util.obfuscate.Keep;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public interface ILoopType {
    /**
     * д»ЋеЉЁз”»ж–‡д»¶иЇ»еЏ–ж’­ж”ѕз±»ећ‹
     *
     * @param json json ж–‡д»¶
     * @return ж’­ж”ѕз±»ећ‹
     */
    static ILoopType fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) {
            return EDefaultLoopTypes.PLAY_ONCE;
        }
        JsonPrimitive primitive = json.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? EDefaultLoopTypes.LOOP : EDefaultLoopTypes.PLAY_ONCE;
        }
        if (primitive.isString()) {
            String string = primitive.toString();
            if ("false".equalsIgnoreCase(string)) {
                return EDefaultLoopTypes.PLAY_ONCE;
            }
            if ("true".equalsIgnoreCase(string)) {
                return EDefaultLoopTypes.LOOP;
            }
            try {
                return EDefaultLoopTypes.valueOf(string.toUpperCase(Locale.ROOT));
            } catch (Exception ignore) {
            }
        }
        return EDefaultLoopTypes.PLAY_ONCE;
    }

    /**
     * жЇеђ¦ењЁеЉЁз”»з»“жќџеђЋй‡Ќе¤Ќ
     *
     * @return жЇеђ¦ењЁеЉЁз”»з»“жќџеђЋй‡Ќе¤Ќ
     */
    @Keep
    boolean isRepeatingAfterEnd();

    enum EDefaultLoopTypes implements ILoopType {
        /**
         * еЉЁз”»ж’­ж”ѕз±»ећ‹
         */
        LOOP(true),
        PLAY_ONCE,
        HOLD_ON_LAST_FRAME;

        private final boolean looping;

        EDefaultLoopTypes(boolean looping) {
            this.looping = looping;
        }

        EDefaultLoopTypes() {
            this(false);
        }

        @Override
        @Keep
        public boolean isRepeatingAfterEnd() {
            return this.looping;
        }
    }
}
