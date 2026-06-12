package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.MultiBufferSource;

public final class SubmitRenderContext {

    private static final ThreadLocal<MultiBufferSource> CURRENT = new ThreadLocal<>();

    private SubmitRenderContext() {
    }

    public static void set(MultiBufferSource collector) {
        if (collector == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(collector);
        }
    }

    public static MultiBufferSource get() {
        return CURRENT.get();
    }
}

