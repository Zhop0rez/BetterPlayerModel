package com.elfmcys.yesstevemodel.client;

import net.minecraft.client.Minecraft;

public class MinecraftInspector {
    public static void inspect() {
        Minecraft mc = Minecraft.getInstance();
        System.out.println("Minecraft Fields:");
        for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
            System.out.println(f.getName() + " - " + f.getType().getName());
        }
        System.out.println("Minecraft Methods:");
        for (java.lang.reflect.Method m : Minecraft.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}
