package com.elfmcys.yesstevemodel.geckolib3.core.builder;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * еЉЁз”»жЋ§е€¶е™Ёи§Јжћђ
 */
public class AnimationController {
    // initial_state
    private final int stateId;

    // жЋ§е€¶е™Ёе†…е®№
    private final Int2ReferenceMap<AnimationState> states;

    public AnimationController(String initialState, AnimationState[] animationStates) {
        this.stateId = StringPool.computeIfAbsent(initialState);
        this.states = Int2ReferenceMaps.unmodifiable(new Int2ReferenceOpenHashMap<>(Arrays.stream(animationStates).collect(Collectors.toMap(AnimationState::getHashId, state -> state))));
    }

    public int getStateId() {
        return this.stateId;
    }

    public Int2ReferenceMap<AnimationState> getStates() {
        return this.states;
    }
}
