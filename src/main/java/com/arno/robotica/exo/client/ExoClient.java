package com.arno.robotica.exo.client;

import com.arno.robotica.exo.ExoRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Client-only entry point of the Exo-Frame module. Called from RoboticaClient. */
public final class ExoClient {
    private ExoClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ExoClientConfig.SPEC, "robotica-exo-client.toml");
        modBus.addListener(RegisterKeyMappingsEvent.class, ExoKeys::register);
        modBus.addListener(RegisterGuiLayersEvent.class, ExoHud::register);
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(ExoRegistry.EXO_MENU.get(), ExoScreen::new));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ExoKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ExoXray::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, ExoXray::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Pre.class, ExoClientEffects::onPlayerTick);
    }
}
