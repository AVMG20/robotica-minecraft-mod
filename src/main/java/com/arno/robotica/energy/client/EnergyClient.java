package com.arno.robotica.energy.client;

import com.arno.robotica.energy.EnergyRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point of the energy module. Called from RoboticaClient. */
public final class EnergyClient {
    private EnergyClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(EnergyRegistry.REACTOR_MENU.get(), ReactorScreen::new);
            event.register(EnergyRegistry.BANK_MENU.get(), BankScreen::new);
            event.register(EnergyRegistry.FUSION_MENU.get(), FusionScreen::new);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(EnergyRegistry.REACTOR_BE.get(), ControllerHighlightRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.BANK_BE.get(), ControllerHighlightRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.FUSION_BE.get(), ControllerHighlightRenderer::new);
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> StructureHighlight.clear());
    }
}
