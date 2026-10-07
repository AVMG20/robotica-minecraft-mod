package com.arno.robotica.gear.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point of the gear module. Called from RoboticaClient. */
public final class GearClient {
    private GearClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, com.arno.robotica.gear.GearClientConfig.SPEC, "robotica-gear-client.toml");
        modBus.addListener(RegisterKeyMappingsEvent.class, GearKeys::register);
        modBus.addListener(RegisterGuiLayersEvent.class, GearHud::register);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, GearKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(InputEvent.MouseScrollingEvent.class, GearKeys::onScroll);
        NeoForge.EVENT_BUS.addListener(RenderHighlightEvent.Block.class, AreaOutline::onHighlight);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(com.arno.robotica.gear.GearEntities.RIVET.get(), RivetRenderer::new));
        modBus.addListener(RegisterMenuScreensEvent.class, e -> e.register(com.arno.robotica.gear.GearBlocks.TINKERS_BENCH_MENU.get(), TinkersBenchScreen::new));
    }
}
