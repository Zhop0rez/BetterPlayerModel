package com.elfmcys.yesstevemodel.client.gui;

import com.elfmcys.yesstevemodel.config.ExtraPlayerRenderConfig;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.config.LoadingStateConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import rip.ysm.gui.Option;
import rip.ysm.gui.OptionGroup;
import rip.ysm.gui.OptionScreen;
import rip.ysm.gui.components.BooleanOptionRow;
import rip.ysm.gui.components.EnumOptionRow;
import rip.ysm.gui.components.RadioOptionRow;
import rip.ysm.gui.components.SliderOptionRow;

import java.util.List;

public class ExtraPlayerConfigScreen extends OptionScreen {

    public ExtraPlayerConfigScreen(@Nullable Screen modelScreen) {
        super(Component.translatable("gui.better_player_model.config.title"), modelScreen);
    }

    @Override
    protected void registerGroups() {
        OptionGroup general = new OptionGroup("general")
                .add(new SliderOptionRow(0, 0, 0, 22, Option.ofDouble("sound_volume", GeneralConfig.SOUND_VOLUME), 0.0d, 100.0d, 1.0d, "%"))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_self_model", GeneralConfig.DISABLE_SELF_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_other_model", GeneralConfig.DISABLE_OTHER_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_self_hands", GeneralConfig.DISABLE_SELF_HANDS)));

        OptionGroup rendering = new OptionGroup("rendering")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_player_render", ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_projectile_model", GeneralConfig.DISABLE_PROJECTILE_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_vehicle_model", GeneralConfig.DISABLE_VEHICLE_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_external_first_person_anim", GeneralConfig.DISABLE_EXTERNAL_FP_ANIM)));

        OptionGroup performance = new OptionGroup("performance")
                .add(new RadioOptionRow(0, 0, 0, 22, rendererOption(), rendererLabels()))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_model_glow_in_shaderpack", GeneralConfig.DISABLE_MODEL_GLOW_IN_SHADERPACK)));

        OptionGroup debug = new OptionGroup("debug")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("model_memory_profiler", GeneralConfig.MODEL_MEMORY_PROFILER)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("animation_frame_profiler", GeneralConfig.ANIMATION_FRAME_PROFILER)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("animation_debug_log", GeneralConfig.ANIMATION_DEBUG_LOG)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("warn_repeated_animation_evaluation", GeneralConfig.WARN_REPEATED_ANIMATION_EVALUATION)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("resource_station_monitor_log", GeneralConfig.RESOURCE_STATION_MONITOR_LOG)));

        OptionGroup experimentalTesting = new OptionGroup("experimental_testing")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("experimental_fallback_elytra_without_locator", GeneralConfig.EXPERIMENTAL_FALLBACK_ELYTRA_WITHOUT_LOCATOR)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("experimental_enable_elytra_for_default_and_misc_models", GeneralConfig.EXPERIMENTAL_ENABLE_ELYTRA_FOR_DEFAULT_AND_MISC_MODELS)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("release_texture_bytes_after_upload", GeneralConfig.RELEASE_TEXTURE_BYTES_AFTER_UPLOAD)))
                .add(new SliderOptionRow(0, 0, 0, 22, intOption("max_cached_gpu_models", GeneralConfig.MAX_CACHED_GPU_MODELS), 0.0d, 128.0d, 1.0d, ""))
                .add(new SliderOptionRow(0, 0, 0, 22, intOption("unused_model_ttl_seconds", GeneralConfig.UNUSED_MODEL_TTL_SECONDS), 30.0d, 1800.0d, 30.0d, "s"));

        OptionGroup misc = new OptionGroup("misc")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("print_animation_roulette_msg", GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_loading_state_screen", LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN)))
                .add(new EnumOptionRow<>(0, 0, 0, 22, Option.ofEnum("loading_state_position", LoadingStateConfig.LOADING_STATE_POSITION), LoadingStateConfig.Position.values()));

        groups.add(general);
        groups.add(rendering);
        groups.add(performance);
        groups.add(debug);
        groups.add(experimentalTesting);
        groups.add(misc);
    }

    private static Option<Double> intOption(String key, ForgeConfigSpec.IntValue cfg) {
        return new Option<>(key, () -> cfg.get().doubleValue(), value -> {
            cfg.set(value == null ? 0 : value.intValue());
            cfg.save();
        });
    }

    private static Option<Integer> rendererOption() {
        return new Option<>("renderer", ExtraPlayerConfigScreen::getRendererOption, ExtraPlayerConfigScreen::setRendererOption);
    }

    private static int getRendererOption() {
        return !GeneralConfig.USE_COMPATIBILITY_RENDERER.get() && GeneralConfig.USE_GPU_RENDERER.get() ? 1 : 0;
    }

    private static void setRendererOption(Integer value) {
        boolean useGpuRenderer = value != null && value == 1;
        GeneralConfig.USE_COMPATIBILITY_RENDERER.set(!useGpuRenderer);
        GeneralConfig.USE_COMPATIBILITY_RENDERER.save();
        GeneralConfig.USE_GPU_RENDERER.set(useGpuRenderer);
        GeneralConfig.USE_GPU_RENDERER.save();
    }

    private static List<String> rendererLabels() {
        return List.of(
                Component.translatable("gui.better_player_model.config.renderer.compatibility").getString(),
                Component.translatable("gui.better_player_model.config.renderer.gpu").getString()
        );
    }
}


