package com.arno.robotica.energy.client;

import com.arno.robotica.energy.EnergyRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point of the energy module. Called from RoboticaClient. */
public final class EnergyClient {
    private EnergyClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, com.arno.robotica.energy.EnergyClientConfig.SPEC, "robotica-energy-client.toml");
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, StructureFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, StructureFx::onRenderLevel);
        modBus.addListener(RegisterMenuScreensEvent.class, event -> {
            event.register(EnergyRegistry.REACTOR_MENU.get(), CoreReactorScreen::new);
            event.register(EnergyRegistry.BANK_MENU.get(), BankScreen::new);
            event.register(EnergyRegistry.SPIRE_MENU.get(), SpireScreen::new);
            event.register(EnergyRegistry.COLLIDER_MENU.get(), ColliderScreen::new);
        });
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> {
            event.registerBlockEntityRenderer(EnergyRegistry.REACTOR_BE.get(), CoreReactorRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.BANK_BE.get(), ControllerHighlightRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.SPIRE_BE.get(), SpireRenderer::new);
            event.registerBlockEntityRenderer(EnergyRegistry.COLLIDER_BE.get(), ColliderRenderer::new);
        });
        modBus.addListener(ModelEvent.RegisterAdditional.class, PartModel::register);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> StructureHighlight.clear());
    }
}
