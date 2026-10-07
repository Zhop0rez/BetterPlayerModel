package com.elfmcys.yesstevemodel.geckolib3.core.builder;

import com.elfmcys.yesstevemodel.util.obfuscate.Keep;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public interface ILoopType {
    /**
     * Read the play type from the animation file
     *
     * @param json the json file
     * @return the play type
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
     * Whether it repeats after the animation ends
     *
     * @return whether it repeats after the animation ends
     */
    @Keep
    boolean isRepeatingAfterEnd();

    enum EDefaultLoopTypes implements ILoopType {
        /**
         * Animation play type
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
