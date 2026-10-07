package com.elfmcys.yesstevemodel.geckolib3.core.keyframe;

import com.elfmcys.yesstevemodel.geckolib3.core.controller.AnimationControllerContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.AnimationContext;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import org.joml.Vector3f;

public abstract class AnimationPoint {
    /**
     * Playback progress of the current keyframe
     */
    public final float currentTick;
    /**
     * Total length of the current keyframe
     */
    public final float totalTick;
    /**
     * Molang context related to the animation controller
     */
    private final AnimationControllerContext context;

    public Vector3f cachedValue;

    public AnimationPoint(float currentTick, float totalTick, AnimationControllerContext context) {
        this.currentTick = currentTick;
        this.totalTick = totalTick;
        this.context = context;
    }

    public float getPercentCompleted() {
        return totalTick == 0 ? 1.0f : (currentTick / totalTick);
    }

    public void setupControllerContext(ExpressionEvaluator<AnimationContext<?>> evaluator) {
        evaluator.entity().setAnimationControllerContext(this.context);
    }

    public abstract Vector3f getLerpPoint(ExpressionEvaluator<AnimationContext<?>> evaluator);
}
