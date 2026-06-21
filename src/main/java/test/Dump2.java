package test;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import java.lang.reflect.*;

public class Dump2 {
    public static void main(String[] args) throws Exception {
        EntityRenderState state = new HumanoidRenderState();
        for (Field f : EntityRenderState.class.getDeclaredFields()) {
            f.setAccessible(true);
            System.out.println(f.getName() + " = " + f.get(state));
        }
    }
}
