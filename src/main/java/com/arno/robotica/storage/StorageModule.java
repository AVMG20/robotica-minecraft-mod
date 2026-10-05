package com.arno.robotica.storage;

import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.storage.net.StorageViewPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Common (both sides) entry point of the storage module: the Storage Terminal and its Storage Expansions. */
public final class StorageModule {
    private StorageModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        StorageContent.BLOCKS.register(modBus);
        StorageContent.ITEMS.register(modBus);
        StorageContent.BLOCK_ENTITIES.register(modBus);
        StorageContent.MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, StorageConfig.SPEC, "robotica-storage-server.toml");
        modBus.addListener(StorageModule::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, StorageViewPayload::register);
        modBus.addListener(RegisterPayloadHandlersEvent.class, com.arno.robotica.storage.net.StorageCraftPayload::register);

        RoboticaTab.add(StorageContent.TERMINAL_ITEM);
        StorageContent.EXPANSIONS.forEach(RoboticaTab::add);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Items from every side (hoppers, drones, pipes); FE from every side, receive only.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, StorageContent.TERMINAL_BE.get(), (be, side) -> be.access());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, StorageContent.TERMINAL_BE.get(), (be, side) -> be.energy);
    }
}
