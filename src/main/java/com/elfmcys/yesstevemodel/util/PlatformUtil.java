package com.elfmcys.yesstevemodel.util;

import net.minecraft.Util;
import java.io.File;
import java.net.URI;

public final class PlatformUtil {

    private PlatformUtil() {
    }

    public static long getMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void openUri(String uri) {
        if (uri != null && !uri.isBlank()) {
            Util.getPlatform().openUri(URI.create(uri));
        }
    }

    public static void openFile(File file) {
        if (file != null) {
            Util.getPlatform().openUri(file.toURI());
        }
    }
}
