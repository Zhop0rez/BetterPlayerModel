package rip.ysm.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;

/**
 * Detects whether the active Minecraft device is Vulkan before legacy code
 * touches LWJGL's OpenGL entry points.  A Vulkan window has no GL context, and
 * the first native GL call aborts the JVM instead of throwing a catchable error.
 */
public final class GpuCompat {
    private static volatile Boolean vulkanBackend;

    private GpuCompat() {
    }

    public static boolean isGl() {
        Boolean result = vulkanBackend;
        if (result == null) {
            synchronized (GpuCompat.class) {
                if (vulkanBackend == null) {
                    detect();
                }
                result = vulkanBackend;
            }
        }
        return !result;
    }

    public static boolean isVulkan() {
        return !isGl();
    }

    public static synchronized void detect() {
        boolean vulkan;
        String deviceClass = null;
        try {
            Object device = RenderSystem.getDevice();
            deviceClass = device.getClass().getName();
            String name = deviceClass.toLowerCase(java.util.Locale.ROOT);
            if (name.contains("vulkan")) {
                vulkan = true;
            } else if (name.contains("opengl") || name.contains(".gl.")) {
                vulkan = false;
            } else {
                vulkan = !glCapabilitiesExist();
            }
        } catch (Throwable ignored) {
            vulkan = !glCapabilitiesExist();
        }
        vulkanBackend = vulkan;
        System.out.println("[BPM] Rendering backend: " + (vulkan ? "Vulkan" : "OpenGL")
                + (deviceClass == null ? "" : " (device: " + deviceClass + ")"));
    }

    /* This LWJGL lookup does not invoke a GL function. */
    private static boolean glCapabilitiesExist() {
        try {
            return GL.getCapabilities() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
