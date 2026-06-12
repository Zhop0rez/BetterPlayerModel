package net.minecraft.client.renderer;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
public class RenderType {
    public static RenderType entityCutoutNoCull(ResourceLocation id) { return new RenderType(); }
    public Optional<RenderType> outline() { return Optional.empty(); }
}

