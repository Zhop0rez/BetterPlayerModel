package com.elfmcys.yesstevemodel.event;

public final class YsmEventBootstrap {

    private YsmEventBootstrap() {
    }

    public static void register() {
        ServerStartupEvent.register();
        EnterServerEvent.register();
        PlayerLogoutEvent.register();
        CommonEvent.register();
        CommandRegistry.register();

        CapabilityEvent.register();
    }
}
