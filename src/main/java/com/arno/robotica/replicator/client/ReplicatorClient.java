package com.arno.robotica.replicator.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.logic.Essence;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point of the replicator module. Called from RoboticaClient. */
public final class ReplicatorClient {
    private ReplicatorClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, com.arno.robotica.replicator.ReplicatorClientConfig.SPEC, "robotica-replicator-client.toml");
        modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(ReplicatorRegistry.REPLICATOR_MENU.get(), ReplicatorScreen::new));
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerBlockEntityRenderer(ReplicatorRegistry.CONTROLLER_BE.get(), ReplicatorControllerRenderer::new));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() ->
                // 0 empty, then the vial model switches at the first sample and when it is complete.
                ItemProperties.register(ReplicatorRegistry.ESSENCE_VIAL.get(), Robotica.id("essence_fill"),
                        (stack, level, entity, seed) -> (float) Essence.samples(stack) / Essence.SAMPLES_REQUIRED)));
        // Cached hologram entities hold a reference to the client level: drop them when leaving the world.
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> HologramMobs.clear());
    }
}
