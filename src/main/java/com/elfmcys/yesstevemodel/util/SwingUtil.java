package com.elfmcys.yesstevemodel.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

public class SwingUtil {

    // 26.3: поля swinging/swingingArm/swingTime и getAttackAnim заменены приватным SwingState + getSwingAnimation
    public static boolean isSwinging(LivingEntity entity) {
        return entity.isSwinging();
    }

    public static InteractionHand getSwingingArm(LivingEntity entity) {
        LivingEntity.SwingDescription swing = entity.getCurrentSwing();
        return swing == null ? InteractionHand.MAIN_HAND : swing.hand();
    }

    public static int getSwingTime(LivingEntity entity) {
        LivingEntity.SwingDescription swing = entity.getCurrentSwing();
        if (swing == null || swing.durationTicks() <= 0) {
            return 0;
        }
        return (int) (entity.getSwingAnimation(1.0f) * swing.durationTicks());
    }

    public static float getSwingProgress(LivingEntity entity, float partialTick) {
        return entity.getSwingAnimation(partialTick);
    }
}
