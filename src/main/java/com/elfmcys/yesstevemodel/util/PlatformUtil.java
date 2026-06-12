package com.elfmcys.yesstevemodel.util;

import com.elfmcys.yesstevemodel.YesSteveModel;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

public final class PlatformUtil {

    private PlatformUtil() {
    }

    public static long getMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void openUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return;
        }
        try {
            URI parsedUri = URI.create(uri);
            if (!isWebUri(parsedUri)) {
                YesSteveModel.LOGGER.warn("Refusing to open non-web URI {}", uri);
                return;
            }
            if (!openWithDesktop(parsedUri)) {
                YesSteveModel.LOGGER.warn("Desktop API is not supported. Could not open URI {}", uri);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to open URI {}", uri, e);
        }
    }

    public static void openFile(File file) {
        if (file == null) {
            return;
        }
        try {
            if (!openWithDesktop(file)) {
                YesSteveModel.LOGGER.warn("Desktop API is not supported. Could not open file {}", file);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("Failed to open file {}", file, e);
        }
    }

    private static boolean openWithDesktop(URI uri) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            return false;
        }
        desktop.browse(uri);
        return true;
    }

    private static boolean isWebUri(URI uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static boolean openWithDesktop(File file) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(file);
            return true;
        }
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(file.toURI());
            return true;
        }
        return false;
    }

}
