package com.elfmcys.yesstevemodel;

import net.minecraft.network.chat.Component;

public final class NativeLibLoader {
    public static void init() {
        // Pure Java build - no native libraries used
    }

    public static boolean isAvailable() {
        return true;
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isOnAndroid() {
        return false;
    }

    public static Component getErrorComponent() {
        return null;
    }

    public static String getErrorMessage() {
        return null;
    }
}
