package com.arno.robotica.storage.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/** Client-only entry point of the storage module. Called from RoboticaClient. */
public final class StorageClient {
    private StorageClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
    }
}
