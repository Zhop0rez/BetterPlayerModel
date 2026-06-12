package rip.ysm.api.client.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyMappingFactoryImpl {

    private static final Map<String, KeyMapping.Category> CATEGORY_CACHE = new ConcurrentHashMap<>();

    private KeyMappingFactoryImpl() {
    }

    private static KeyMapping.Category getOrCreateCategory(String categoryKey) {
        return CATEGORY_CACHE.computeIfAbsent(categoryKey, k ->
            KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("better_player_model", "keys")));
    }

    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) {
        return new KeyMapping(name, type, keyCode, getOrCreateCategory(category));
    }

    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) {
        return new KeyMapping(name, type, keyCode, getOrCreateCategory(category));
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) {
        return keyMapping.matches(new KeyEvent(keyCode, scanCode, 0));
    }
}


