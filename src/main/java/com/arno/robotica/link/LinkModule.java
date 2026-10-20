package com.arno.robotica.link;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Common (both sides) entry point of the link module: Quantum Links Mk1-3 that move FE and items on named networks
 * (saved data, see {@code network/LinkNetworks}), across dimensions. Links only ever touch their own loaded
 * chunk; each network's buffer carries things while one end is unloaded.
 * Client side ({@code client/LinkClient}): the screen and the network colour renderer.
 */
public final class LinkModule {
    private LinkModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        LinkRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, LinkConfig.SPEC, "robotica-link-server.toml");
        modBus.addListener(LinkModule::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, LinkPayloads::register);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LinkRegistry.LINK_BE.get(), (be, side) -> be.energyFor(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LinkRegistry.LINK_BE.get(), (be, side) -> be.itemsFor(side));
    }
}
