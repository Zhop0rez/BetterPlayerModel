package com.elfmcys.yesstevemodel.client;

import net.minecraft.client.Minecraft;

public class TestFields {
    public static void test() {
        System.out.println("---- Minecraft fields ----");
        for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
            System.out.println(f.getName() + " - " + f.getType().getName());
        }
        System.out.println("--------------------------");
    }
}
