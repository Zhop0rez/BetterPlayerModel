import com.mojang.blaze3d.platform.Lighting;
import java.lang.reflect.Method;

public class TestLighting {
    public static void main(String[] args) {
        for (Method m : Lighting.class.getDeclaredMethods()) {
            System.out.println(m.getName());
        }
    }
}
