package com.arno.robotica.replicator;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Common (both sides) entry point of the replicator module. See docs/DESIGN.md.
 * Essence Vial, Replicator Controller / Frame / Glass (3x3x3 multiblock), harvest and spawn modes.
 */
public final class ReplicatorModule {
    private ReplicatorModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        ReplicatorRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ReplicatorConfig.SPEC, "robotica-replicator-server.toml");
        modBus.addListener(RegisterCapabilitiesEvent.class, ReplicatorModule::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ReplicatorModePayload::register);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // FE on every side, items extract-only (the output), so hoppers, pipes and storage networks can empty it.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ReplicatorRegistry.CONTROLLER_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ReplicatorRegistry.CONTROLLER_BE.get(), (be, side) -> be.automation());
    }
}
