package rip.ysm.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;

/**
 * Detects the active rendering backend (OpenGL vs Vulkan) once and provides
 * guards so that NO LWJGL GL call is ever attempted when there is no GL context.
 * <p>
 * Under the Vulkan backend (Graphics API "Prefer Vulkan", 26.2+) the window is
 * created without a GL context. Calling any GLxx/GlStateManager function then
 * makes LWJGL abort the JVM natively ("No context is current... The JVM will
 * abort execution.") — that crash is NOT catchable with try/catch. Therefore every
 * GL entry point must be gated with {@link #isGl()} / {@link #isVulkan()} BEFORE
 * the first GL call, never wrapped in catch.
 * <p>
 * Detection strategy: prefer the device class name reported by the vanilla
 * device factory; when the name carries no backend marker, fall back to a
 * pure-Java LWJGL probe ({@link GL#getCapabilities()} performs no native calls).
 */
public final class GpuCompat {

    private static volatile Boolean vulkanBackend = null;

    private GpuCompat() {
    }

    /**
     * True when the active backend provides a GL context (direct GL calls allowed).
     */
    public static boolean isGl() {
        Boolean v = vulkanBackend;
        if (v == null) {
            synchronized (GpuCompat.class) {
                if (vulkanBackend == null) {
                    detect();
                }
            }
            v = vulkanBackend;
        }
        return !v;
    }

    /**
     * True when the active backend is Vulkan: all GPU/GL render paths and manual
     * GL state changes must be skipped; rendering falls back to CPU paths.
     */
    public static boolean isVulkan() {
        return !isGl();
    }

    /**
     * Runs detection once. Call early on the render thread (client start event)
     * and let the lazy path re-run it if device init happened later.
     */
    public static synchronized void detect() {
        boolean vulkan;
        String deviceClass = null;
        try {
            var device = RenderSystem.getDevice();
            deviceClass = device.getClass().getName();
            String lower = deviceClass.toLowerCase();
            if (lower.contains("vulkan")) {
                vulkan = true;
            } else if (lower.contains("opengl") || lower.contains(".gl.")) {
                vulkan = false;
            } else {
                // Unrecognized device class: probe LWJGL capabilities (pure Java, no GL calls).
                vulkan = !glCapabilitiesExist();
            }
        } catch (Throwable t) {
            // Device not initialized yet or API changed: fall back to the LWJGL probe.
            vulkan = !glCapabilitiesExist();
        }
        vulkanBackend = vulkan;
        System.out.println("[BPM] Rendering backend: " + (vulkan ? "Vulkan" : "OpenGL")
                + (deviceClass == null ? "" : " (device: " + deviceClass + ")"));
    }

    /**
     * Non-native LWJGL check: on the GL backend the render thread has context
     * capabilities after window creation; under the Vulkan backend it never does.
     */
    private static boolean glCapabilitiesExist() {
        try {
            return GL.getCapabilities() != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
