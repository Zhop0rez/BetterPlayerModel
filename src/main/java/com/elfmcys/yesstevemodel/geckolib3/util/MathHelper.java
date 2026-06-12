package com.elfmcys.yesstevemodel.geckolib3.util;

public class MathHelper {
    /**
     * е°†и§’еє¦е‡Џе°Џе€° -180 е€° +180 д№‹й—ґзљ„и§’еє¦пјЊе№¶иї›иЎЊ 360 еє¦жЈЂжџҐ
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
     * е°†и§’еє¦е‡Џе°Џе€° -180 е€° +180 д№‹й—ґзљ„и§’еє¦пјЊе№¶иї›иЎЊ 360 еє¦жЈЂжџҐ
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
     * и°ѓж•ґи§’еє¦пјЊдЅїе…¶еЂјењЁ [-180, 180]
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
