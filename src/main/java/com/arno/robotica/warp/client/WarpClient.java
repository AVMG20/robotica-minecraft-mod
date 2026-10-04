package com.arno.robotica.warp.client;

import com.arno.robotica.warp.WarpRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only entry point of the warp module. Called from RoboticaClient. */
public final class WarpClient {
    private WarpClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(WarpClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(WarpRegistry.PAD_MENU.get(), PadScreen::new);
        event.register(WarpRegistry.DESTINATION_MENU.get(), DestinationScreen::new);
    }
}
