package com.elfmcys.yesstevemodel.client.animation;

import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;

public class AnimationFormatValidator {
    public static boolean validate(AnimationEvent<?> event, String animationName, int version) {
        if (version >= 19) { // Corresponds to the format version of the ysm file
            return true;
        }
        Animation animation = event.getAnimatable().getAnimation(animationName);
        if (animation == null) {
            return false;
        }
        return animation.isFromPrimaryAssembly;
    }
}
