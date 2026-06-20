package test;

import net.minecraft.client.Minecraft;

public class Test {
    public static void main(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        System.out.println(mc.gui);
        mc.setScreenAndShow(null);
    }
}
