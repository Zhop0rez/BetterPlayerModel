package com.elfmcys.yesstevemodel.util;

import net.minecraft.util.Util;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

public class PlatformUtil {

    public static long getMillis() {
        return Util.getMillis();
    }

    // 26.3: Util.getPlatform() больше не предоставляет openUri/openFile — работаем через Desktop напрямую
    public static void openUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        try {
            openUri(URI.create(uri));
        } catch (IllegalArgumentException ignored) {
        }
    }

    public static void openUri(URI uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (Exception ignored) {
        }
    }

    public static void openFile(File file) {
        if (file == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception ignored) {
        }
    }
}
