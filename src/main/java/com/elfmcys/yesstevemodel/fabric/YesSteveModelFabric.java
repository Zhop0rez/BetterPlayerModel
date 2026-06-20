package com.elfmcys.yesstevemodel.fabric;

import com.elfmcys.yesstevemodel.YesSteveModel;
import dev.ysm.architectury.event.events.common.LifecycleEvent;
import net.fabricmc.api.ModInitializer;

public final class YesSteveModelFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        YesSteveModel.init();
        LifecycleEvent.fireSetup();
    }
}
