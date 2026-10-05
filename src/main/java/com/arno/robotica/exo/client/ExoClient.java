package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point of the Exo-Frame module. Called from RoboticaClient. */
public final class ExoClient {
    private ExoClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterKeyMappingsEvent.class, ExoKeys::register);
        modBus.addListener(RegisterGuiLayersEvent.class, ExoHud::register);
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(ExoRegistry.EXO_MENU.get(), ExoScreen::new));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ExoKeys::onClientTick);
    }
}
