package rip.ysm.compat.oculus.fabric;

import net.fabricmc.loader.api.FabricLoader;

public final class OculusCompatImpl {
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");

    private OculusCompatImpl() {
    }

    public static boolean isLoaded() {
        return IRIS_LOADED;
    }

    public static boolean isPBRActive() {
        return IRIS_LOADED && IrisHolder.shadowPass();
    }

    public static void updatePBRState() {
    }

    public static boolean isShaderPackInUse() {
        return IRIS_LOADED && IrisHolder.shaderPackInUse();
    }

    public static boolean isRenderingShadowPass() {
        return IRIS_LOADED && IrisHolder.shadowPass();
    }

    private static final class IrisHolder {
        static boolean shaderPackInUse() {
            try {
                return false;
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean shadowPass() {
            try {
                return false;
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
