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
 * Fission Reactor, Capacitor Bank and Fusion Reactor: cuboid multiblocks built on core.multiblock, with ports that
 * expose the standard FE and item capabilities, and the data maps robotica:reactor_fuel, robotica:fusion_fuel and
 * robotica:reactor_coolant.
 */
public final class EnergyModule {
    private EnergyModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        EnergyRegistry.register(modBus);
        com.arno.robotica.core.item.DetailArgs.register("fusion_controller",
                () -> new Object[]{com.arno.robotica.core.util.Fmt.energy(EnergyConfig.fusionIgnitionEnergy())});
        container.registerConfig(ModConfig.Type.SERVER, EnergyConfig.SPEC, "robotica-energy-server.toml");
        modBus.addListener(RegisterDataMapTypesEvent.class, EnergyDataMaps::register);
        modBus.addListener(RegisterCapabilitiesEvent.class, EnergyModule::registerCapabilities);
        modBus.addListener(RegisterPayloadHandlersEvent.class, ControllerActionPayload::register);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Ports forward to their controller; the objects are fixed, an unlinked port just moves nothing.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyRegistry.PORT_BE.get(), (be, side) -> be.energyView());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, EnergyRegistry.PORT_BE.get(), (be, side) -> be.itemView());
    }
}
