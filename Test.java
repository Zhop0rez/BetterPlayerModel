import java.lang.reflect.Method;
public class Test {
    public static void main(String[] args) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.gui.screens.Screen");
            for (Method m : clazz.getDeclaredMethods()) {
                System.out.println(m.getName() + " - " + m.getReturnType().getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
