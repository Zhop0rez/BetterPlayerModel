package test;

import net.minecraft.client.renderer.SubmitNodeCollector;
import java.lang.reflect.*;

public class Dump3 {
    public static void main(String[] args) throws Exception {
        System.out.println("Methods of SubmitNodeCollector:");
        for (Method m : SubmitNodeCollector.class.getMethods()) {
            System.out.println(m.getName() + " " + m.getParameterCount());
            for (Class<?> p : m.getParameterTypes()) {
                System.out.println("  " + p.getName());
            }
        }
    }
}
