package com.elfmcys.yesstevemodel;

import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.ModSoundEvents;
import com.elfmcys.yesstevemodel.config.ServerConfig;
import com.elfmcys.yesstevemodel.event.YsmEventBootstrap;
import com.elfmcys.yesstevemodel.util.obfuscate.Keep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import rip.ysm.api.PlatformAPI;
import rip.ysm.api.config.ConfigRegistration;

import java.io.File;
import java.io.IOException;

/**
 * TODO:
 * ж¦›жЁїо…»е¦ЇвЂізЂ·жђґж—‡о‡љзЃЏеЌћж№Єе¦Їпј„зІЌйЏ‹и·єе§ћжќћз•Њж®‘йЏѓи·єв‚¬ж¬Џж°ЁжЈ°е‹«е§ћжќћж€’з°Ў
 * йЌЏи·єз• е¦ЇвЂізЂ·зјЃз†єзІєй–®иЉҐж§ёжќ©ж¶еЏ†ж¶“ж «ж™«йЌљеєЎе§ћжќћ? */
public class YesSteveModel {

    public static final String MOD_ID = "better_player_model";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .registerTypeHierarchyAdapter(com.elfmcys.yesstevemodel.util.data.OrderedStringMap.class, new com.elfmcys.yesstevemodel.util.data.OrderedStringMapAdapter())
            .create();

    private YesSteveModel() {
    }

    public static void init() {
        LOGGER.info("Initializing YesSteveModel, platform: " + PlatformAPI.getPlatformName());
        initConfig();
        YsmEventBootstrap.register();
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static void initConfig() {
        File oldConfig = Platform.getConfigFolder().resolve("better_player_model-common.toml").toFile();
        if (oldConfig.isFile()) {
            File file2 = Platform.getConfigFolder().resolve("better_player_model-client.toml").toFile();
            if (!file2.isFile()) {
                oldConfig.renameTo(file2);
            } else {
                oldConfig.delete();
            }
        }
        ConfigRegistration.register(MOD_ID, ModConfig.Type.CLIENT, GeneralConfig.buildSpec());
        ConfigRegistration.register(MOD_ID, ModConfig.Type.SERVER, ServerConfig.buildSpec());
        ModSoundEvents.REGISTER.register();
    }

    @Keep
    public static boolean isAvailable() {
        return true;
    }

    public static boolean isOnAndroid() {
        return false;
    }

    public static Component getUnavailableComponent() {
        return null;
    }

    public static String getErrorMessage() {
        return null;
    }
}
