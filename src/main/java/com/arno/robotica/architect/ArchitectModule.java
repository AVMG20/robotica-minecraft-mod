package com.arno.robotica.architect;

import com.arno.robotica.architect.matter.MatterTable;
import com.arno.robotica.architect.net.ArchitectActionPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Common (both sides) entry point of the architect module.
 * Architect Table, matter conversion, 24 style blocks, 9x9 building shells on a plot grid, builder drones.
 */
public final class ArchitectModule {
    private ArchitectModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        ArchitectRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, ArchitectConfig.SPEC, "robotica-architect-server.toml");
        modBus.addListener(ArchitectModule::registerCapabilities);
        modBus.addListener(ArchitectModule::registerPayloads);
        NeoForge.EVENT_BUS.addListener(MatterTable::onTagsUpdated);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // FE from any side, items inserted from any side (matter items only, nothing can be extracted).
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ArchitectRegistry.ARCHITECT_TABLE_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ArchitectRegistry.ARCHITECT_TABLE_BE.get(), (be, side) -> be.automation());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ArchitectActionPayload.TYPE, ArchitectActionPayload.STREAM_CODEC, ArchitectActionPayload::handle);
    }
}
