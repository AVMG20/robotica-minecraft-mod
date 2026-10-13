package com.arno.robotica.link.client;

import com.arno.robotica.link.LinkRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only entry point of the link module. Called from RoboticaClient. */
public final class LinkClient {
    private LinkClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(LinkRegistry.LINK_MENU.get(), LinkScreen::new));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(LinkRegistry.LINK_BE.get(), LinkRenderer::new));
    }
}
