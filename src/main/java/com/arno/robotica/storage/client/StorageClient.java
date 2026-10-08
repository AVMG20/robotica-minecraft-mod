package com.arno.robotica.storage.client;

import com.arno.robotica.storage.StorageContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only entry point of the storage module. Called from RoboticaClient. */
public final class StorageClient {
    private StorageClient() {}

    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(StorageClient::registerScreens);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                com.arno.robotica.storage.StorageClientConfig.SPEC, "robotica-storage-client.toml");
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(StorageContent.MENU.get(), StorageScreen::new);
    }
}
