import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;

public class Helper {
    public static void setupLight(PoseStack poseStack) {
        RenderSystem.applyModelViewMatrix();
        Lighting.setupForEntityInInventory();
    }
    public static void teardownLight() {
        Lighting.setupFor3DItems();
    }
}
