package rip.ysm.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import com.elfmcys.yesstevemodel.mixin.client.KeyMappingAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyMappingFactory {

    private static final Map<String, KeyMapping.Category> CATEGORY_CACHE = new ConcurrentHashMap<>();
    private static final List<KeyMapping> CREATED = new ArrayList<>();

    private KeyMappingFactory() {
    }

    private static KeyMapping.Category getOrCreateCategory(String categoryKey) {
        return CATEGORY_CACHE.computeIfAbsent(categoryKey, k -> {
            // MC 26.x: Category uses Identifier; label() generates "key.category.<ns>.<path>"
            return KeyMapping.Category.register(Identifier.fromNamespaceAndPath("better_player_model", "keys"));
        });
    }

    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, String category) {
        KeyMapping mapping = new KeyMapping(name, type, keyCode < 0 ? InputConstants.UNKNOWN.getValue() : keyCode, getOrCreateCategory(category));
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
        CREATED.add(mapping);
        return mapping;
    }

    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, String category) {
        KeyMapping mapping = new KeyMapping(name, type, keyCode < 0 ? InputConstants.UNKNOWN.getValue() : keyCode, getOrCreateCategory(category));
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
        CREATED.add(mapping);
        return mapping;
    }

    /**
     * 26.3 (SDL): isKeyDown поллит буфер состояния клавиатуры прямым индексом без проверки границ.
     * Биндинги, сохранённые старыми версиями мода мусорными именами (например "key.keyboard.-1"),
     * восстанавливаются при загрузке options.txt и роняют игру в KeyMapping.setAll. Чиним наши бинды.
     */
    public static void sanitizeBindings() {
        for (KeyMapping mapping : CREATED) {
            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).ysm$getKey();
            if (key == null || key.getType() != InputConstants.Type.KEYBOARD) {
                continue;
            }
            int value = key.getValue();
            if (value < 0 || value > 511) {
                InputConstants.Key def = mapping.getDefaultKey();
                boolean defValid = def != null && def.getValue() >= 0 && def.getValue() <= 511;
                mapping.setKey(defValid ? def : InputConstants.UNKNOWN);
            }
        }
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, int keyCode, int scanCode) {
        return keyMapping.matches(new KeyEvent(keyCode, scanCode, 0));
    }
}
