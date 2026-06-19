package com.elfmcys.yesstevemodel;

import net.minecraft.network.chat.Component;
import java.io.IOException;

public final class NativeLibLoader {
    public static void init() throws IOException {
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
