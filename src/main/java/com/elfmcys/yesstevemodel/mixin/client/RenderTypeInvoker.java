package com.elfmcys.yesstevemodel.mixin.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses Minecraft's factory so BPM can submit a normal RenderType to its frame graph. */
@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Invoker(remap = false, value = "create")
    static RenderType bpm$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
