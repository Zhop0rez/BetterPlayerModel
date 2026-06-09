package rip.ysm.api.config.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.neoforged.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void register(String modId, ModConfig.Type type, Object spec) {
        NeoForgeConfigRegistry.INSTANCE.register(modId, type, (net.neoforged.fml.config.IConfigSpec) spec);
    }
}




