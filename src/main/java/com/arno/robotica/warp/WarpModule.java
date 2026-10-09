package com.arno.robotica.warp;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Common (both sides) entry point of the warp module.
 * Warp Pad, Rift Upgrade, Recall / Rift Remote, Portal Projector (registry id gate_controller), Linking Card.
 */
public final class WarpModule {
    private WarpModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        WarpRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, WarpConfig.SPEC, "robotica-warp-server.toml");
        modBus.addListener(WarpModule::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, WarpPayloads::register);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Both buffers accept energy from every side; nothing can be pulled out from outside.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WarpRegistry.WARP_PAD_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WarpRegistry.GATE_CONTROLLER_BE.get(), (be, side) -> be.energy);
    }
}
