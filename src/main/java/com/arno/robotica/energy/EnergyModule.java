package com.arno.robotica.energy;

import com.arno.robotica.energy.net.ControllerActionPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Common (both sides) entry point of the energy module (big power). See docs/DESIGN.md, "Big energy".
 * Capacitor Bank and Core Reactor (cuboids built on core.multiblock, with ports that expose the standard FE and item
 * capabilities), Tesla Spire (a column) and Ring Collider (a loop), whose controllers expose them directly. Data maps:
 * robotica:spire_conductor, spire_fuel, reactor_core, reactor_fuel, core_modulator and collider_fuel.
 */
public final class EnergyModule {
    private EnergyModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        EnergyRegistry.register(modBus);
        com.arno.robotica.core.item.DetailArgs.register("collider_controller",
                () -> new Object[]{com.arno.robotica.core.util.Fmt.energy(EnergyConfig.colliderSpinupPerSegment())});
        container.registerConfig(ModConfig.Type.SERVER, EnergyConfig.SPEC, "robotica-energy-server.toml");
        modBus.addListener(RegisterDataMapTypesEvent.class, EnergyDataMaps::register);
        modBus.addListener(RegisterCapabilitiesEvent.class, EnergyModule::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ControllerActionPayload::register);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Ports forward to their controller; the objects are fixed, an unlinked port just moves nothing.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyRegistry.PORT_BE.get(), (be, side) -> be.energyView());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, EnergyRegistry.PORT_BE.get(), (be, side) -> be.itemView());
        // the Spire Base and the Collider Controller are their own ports
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyRegistry.SPIRE_BE.get(), (be, side) -> be.energyView(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, EnergyRegistry.SPIRE_BE.get(), (be, side) -> be.itemView());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyRegistry.COLLIDER_BE.get(), (be, side) -> be.energyView());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, EnergyRegistry.COLLIDER_BE.get(), (be, side) -> be.itemView());
    }
}
