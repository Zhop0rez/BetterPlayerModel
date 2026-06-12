package rip.ysm.api.config.fabric;

import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.minecraftforge.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void register(String modId, ModConfig.Type type, Object spec) {
        ForgeConfigRegistry.INSTANCE.register(modId, type, (net.minecraftforge.fml.config.IConfigSpec) spec);
    }
}
