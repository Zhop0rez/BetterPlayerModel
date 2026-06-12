package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import dev.architectury.event.EventResult;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import rip.ysm.api.PlatformAPI;

public class PlayerSkinTextureManager {

    private static final ResourceLocation STEVE_SKIN = new ResourceLocation("textures/entity/player/wide/steve.png");

    private static final ResourceLocation ALEX_SKIN = new ResourceLocation("textures/entity/player/slim/alex.png");

    private static final String STEVE_TEXTURE_ID = "misc/2_steve";

    private static final String ALEX_TEXTURE_ID = "misc/1_alex";

    private PlayerSkinTextureManager() {
    }

    public static void register() {
        if (PlatformAPI.isServer()) {
            return;
        }
        SpecialPlayerRenderEvent.EVENT.register(PlayerSkinTextureManager::onRenderTexture);
    }

    private static EventResult onRenderTexture(SpecialPlayerRenderEvent event) {
        ResourceLocation location;
        if (!YesSteveModel.isAvailable()) {
            return EventResult.pass();
        }
        Player player = event.getPlayer();
        if (isDefaultSkin(event.getModelId()) && (player instanceof AbstractClientPlayer abstractClientPlayer)) {
            location = abstractClientPlayer.getSkinTextureLocation();
            if (location == null) {
                location = getSkinTexture(event.getModelId());
            }
            event.setTextureLocation(location);
        }
        return EventResult.pass();
    }

    private static boolean isDefaultSkin(String str) {
        return str.equals(STEVE_TEXTURE_ID) || str.equals(ALEX_TEXTURE_ID);
    }

    private static ResourceLocation getSkinTexture(String str) {
        return str.equals(STEVE_TEXTURE_ID) ? STEVE_SKIN : ALEX_SKIN;
    }
}
