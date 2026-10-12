package com.arno.robotica.logistics.client;

import com.arno.robotica.logistics.LogisticsClientConfig;
import com.arno.robotica.logistics.LogisticsContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point of the logistics module. Called from RoboticaClient. */
public final class LogisticsClient {
    private LogisticsClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, LogisticsClientConfig.SPEC, "robotica-logistics-client.toml");
        modBus.addListener(LogisticsClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ItemPipeGlow::onClientTick);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, ItemPipeGlow::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> ItemPipeGlow.clear());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(LogisticsContent.PIPE_MENU.get(), PipeScreen::new);
    }
}
