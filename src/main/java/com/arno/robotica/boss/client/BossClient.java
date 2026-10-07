package com.arno.robotica.boss.client;

import com.arno.robotica.boss.BossRegistry;
import com.arno.robotica.boss.entity.MagmaGlob;
import com.arno.robotica.boss.entity.ScrapChunk;
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
        event.registerEntityRenderer(BossRegistry.SCRAP_CHUNK.get(), ctx -> new BossRenderers.Chunk<>(ctx, ScrapChunk.LOOK, false));
        event.registerEntityRenderer(BossRegistry.FORGE_TYRANT.get(), BossRenderers.Tyrant::new);
        event.registerEntityRenderer(BossRegistry.MAGMA_GLOB.get(), ctx -> new BossRenderers.Chunk<>(ctx, MagmaGlob.LOOK, true));
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ScrapColossusModel.LAYER, ScrapColossusModel::createLayer);
        event.registerLayerDefinition(ScrapDroneModel.LAYER, ScrapDroneModel::createLayer);
        event.registerLayerDefinition(ForgeTyrantModel.LAYER, ForgeTyrantModel::createLayer);
    }
}
