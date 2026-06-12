package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.animation.AnimationRegister;
import com.elfmcys.yesstevemodel.client.input.AnimationRouletteKey;
import com.elfmcys.yesstevemodel.client.input.DebugAnimationKey;
import com.elfmcys.yesstevemodel.client.input.ExtraAnimationKey;
import com.elfmcys.yesstevemodel.client.input.ExtraPlayerRenderKey;
import com.elfmcys.yesstevemodel.client.input.PlayerModelToggleKey;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import rip.ysm.api.PlatformAPI;
import net.minecraft.client.KeyMapping;

public final class ClientSetupEvent {

    private ClientSetupEvent() {
    }

    public static void register() {
        registerKeyMappings();
        if (YesSteveModel.isAvailable()) {
            AnimationRegister.registerAnimationState();
        }
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            checkNativeInitialization();
        });
    }

    private static void registerKeyMappings() {
        KeyMappingRegistry.register(PlayerModelToggleKey.KEY_MAPPING);
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        KeyMappingRegistry.register(AnimationRouletteKey.KEY_ROULETTE);
        KeyMappingRegistry.register(AnimationRouletteKey.KEY_LOCK);
        KeyMappingRegistry.register(DebugAnimationKey.KEY_MAPPING);
        KeyMappingRegistry.register(ExtraPlayerRenderKey.KEY_MAPPING);
        for (KeyMapping mapping : ExtraAnimationKey.getKeyMappings()) {
            KeyMappingRegistry.register(mapping);
        }
    }

    public static Object nativeClientInit() {
        try {
            int maxTexSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if (maxTexSize <= 0) {
                return Component.literal("YSM: OpenGL context not available");
            }
            // еЋџе§‹C++зўјжЄўжџҐдє†GL20пј€и‘—и‰Іе™Ёпј‰е’Њ GL30пј€VAOпј‰зљ„еЏЇз”ЁжЂ§
            try {
                int testShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
                if (testShader != 0) {
                    GL20.glDeleteShader(testShader);
                }
            } catch (Exception e) {
                return Component.literal("YSM: GL20 (shaders) not available");
            }

            // йў„иј‰е…ҐdefaultжЁЎећ‹пјЊе»¶йЃІи‡із¬¬дёЂж¬ЎжёІжџ“tick
            // дёЌиѓЅењЁFMLClientSetupEventдё­еђЊж­Ґеџ·иЎЊModelAssemblerпјЊжњѓе°Ћи‡ґStackOverflow
            //ClientModelManager.schedulePreloadDefaultModel();
            return null; // ж€ђеЉџ
        } catch (Exception e) {
            return Component.literal("YSM Client Init Failed: " + e.getMessage());
        }
    }

    private static void checkNativeInitialization() {
        Component component = (Component) nativeClientInit();
        if (component != null) {
            throw new RuntimeException("YSM Client Initialization Failed: " + component.getString(256));
        }
    }

    // йЂ™иЈЎжњ¬дѕ†жњ‰дёЂеЂ‹nativeж–№жі•пјЊеЏЇиѓЅжЇйЃ‹иЎЊж™‚жњѓе€ќе§‹еЊ–иј‰е…ҐжЁЎећ‹
}
