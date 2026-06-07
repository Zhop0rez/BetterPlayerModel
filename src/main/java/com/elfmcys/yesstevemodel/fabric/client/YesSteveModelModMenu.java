package com.elfmcys.yesstevemodel.fabric.client;

import com.elfmcys.yesstevemodel.client.gui.ExtraPlayerConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class YesSteveModelModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ExtraPlayerConfigScreen::new;
    }
}
