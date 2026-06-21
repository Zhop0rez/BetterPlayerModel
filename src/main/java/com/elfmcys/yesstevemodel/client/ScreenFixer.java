package com.elfmcys.yesstevemodel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ScreenFixer {
    public static Screen getScreen(Minecraft mc) {
        try {
            Field guiField = Minecraft.class.getField("gui");
            Object gui = guiField.get(mc);
            Method screenMethod = gui.getClass().getMethod("screen");
            return (Screen) screenMethod.invoke(gui);
        } catch (Exception e) {
            try {
                Field screenField = Minecraft.class.getField("screen");
                return (Screen) screenField.get(mc);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        try {
            Method setScreenAndShow = Minecraft.class.getMethod("setScreenAndShow", Screen.class);
            setScreenAndShow.invoke(mc, screen);
        } catch (Exception e) {
            com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.error("ScreenFixer: setScreenAndShow failed with exception", e);
            try {
                Field guiField = Minecraft.class.getField("gui");
                Object gui = guiField.get(mc);
                Method setScreen = gui.getClass().getMethod("setScreen", Screen.class);
                setScreen.invoke(gui, screen);
            } catch (Exception ex) {
                try {
                    Method setScreenOld = Minecraft.class.getMethod("setScreen", Screen.class);
                    setScreenOld.invoke(mc, screen);
                } catch (Exception exc) {
                    com.elfmcys.yesstevemodel.YesSteveModel.LOGGER.error("ScreenFixer.setScreen failed completely!", exc);
                    exc.printStackTrace();
                }
            }
        }
    }

    public static boolean renderNames() {
        try {
            Method m = Minecraft.class.getMethod("renderNames");
            return (boolean) m.invoke(null);
        } catch (Exception e) {
            try {
                Method m = Minecraft.class.getMethod("method_4085");
                return (boolean) m.invoke(null);
            } catch (Exception ex) {
                return true;
            }
        }
    }

    public static Object getOverlay(Minecraft mc) {
        try {
            Method m = Minecraft.class.getMethod("getOverlay");
            return m.invoke(mc);
        } catch (Exception e) {
            try {
                Method m = Minecraft.class.getMethod("method_1513");
                return m.invoke(mc);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static Object getMainRenderTarget(Minecraft mc) {
        try {
            Method m = Minecraft.class.getMethod("getMainRenderTarget");
            return m.invoke(mc);
        } catch (Exception e) {
            try {
                Method m = Minecraft.class.getMethod("method_27671");
                return m.invoke(mc);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}

