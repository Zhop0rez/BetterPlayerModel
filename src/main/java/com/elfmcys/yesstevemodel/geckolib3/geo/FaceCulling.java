package com.elfmcys.yesstevemodel.geckolib3.geo;

import org.joml.Vector4f;

/**
 * Determines front-facing geometry from authored vertex winding in clip space.
 */
public final class FaceCulling {
    private FaceCulling() {
    }

    public static boolean isFrontFacing(Vector4f[] clipPositions) {
        Vector4f p0 = clipPositions[0];
        Vector4f p1 = clipPositions[1];
        Vector4f p2 = clipPositions[2];
        float determinant =
                p0.x() * (p1.y() * p2.w() - p2.y() * p1.w())
                        - p1.x() * (p0.y() * p2.w() - p2.y() * p0.w())
                        + p2.x() * (p0.y() * p1.w() - p1.y() * p0.w());
        return determinant > 0.0f;
    }
}
