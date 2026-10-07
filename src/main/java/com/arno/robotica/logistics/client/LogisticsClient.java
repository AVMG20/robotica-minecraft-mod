package com.arno.robotica.logistics.client;

import com.arno.robotica.logistics.LogisticsContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only entry point of the logistics module. Called from RoboticaClient. */
public final class LogisticsClient {
    private LogisticsClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(LogisticsClient::registerScreens);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LogisticsContent.PIPE_MENU.get(), PipeScreen::new);
    }
}
