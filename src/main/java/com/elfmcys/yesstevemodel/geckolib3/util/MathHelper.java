package com.elfmcys.yesstevemodel.geckolib3.util;

public class MathHelper {
    /**
     * Reduce an angle to the range -180..+180, with a 360-degree check
     */
    public static float wrapDegrees(float value) {
        value = value % 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    /**
     * Reduce an angle to the range -180..+180, with a 360-degree check
     */
    public static double wrapDegrees(double value) {
        value = value % 360.0D;
        if (value >= 180.0D) {
            value -= 360.0D;
        }
        if (value < -180.0D) {
            value += 360.0D;
        }
        return value;
    }

    /**
     * Adjust the angle so that its value is in [-180, 180]
     */
    public static int wrapDegrees(int angle) {
        angle = angle % 360;
        if (angle >= 180) {
            angle -= 360;
        }
        if (angle < -180) {
            angle += 360;
        }
        return angle;
    }
}
