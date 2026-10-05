package com.arno.robotica.boss.client;

import com.arno.robotica.boss.BossRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only entry point of the boss module. Called from RoboticaClient. */
public final class BossClient {
    private BossClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, BossClient::registerRenderers);
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, BossClient::registerLayers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BossRegistry.SCRAP_COLOSSUS.get(), BossRenderers.Colossus::new);
        event.registerEntityRenderer(BossRegistry.SCRAP_DRONE.get(), BossRenderers.Drone::new);
        event.registerEntityRenderer(BossRegistry.SCRAP_CHUNK.get(), BossRenderers.Chunk::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ScrapColossusModel.LAYER, ScrapColossusModel::createLayer);
        event.registerLayerDefinition(ScrapDroneModel.LAYER, ScrapDroneModel::createLayer);
    }
}
