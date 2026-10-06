package com.arno.robotica.industry;

import com.arno.robotica.industry.recipe.Machine;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Common (both sides) entry point of the industry module. See docs/DESIGN.md, "Industry".
 * Thorium, Pyrolite and Resonite ores (worldgen is data: data/robotica/worldgen and neoforge/biome_modifier), the
 * Robotica alloys, the Alloy Smelter, Centrifuge and Assembler (Mk1-Mk4), the RTG and the reactor fuel pellets.
 */
public final class IndustryModule {
    private IndustryModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        IndustryRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, IndustryConfig.SPEC, "robotica-industry-server.toml");
        modBus.addListener(IndustryModule::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Machine machine : Machine.values()) {
            var type = IndustryRegistry.machineBlockEntity(machine).get();
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy);
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.automation());
        }
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, IndustryRegistry.RTG_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, IndustryRegistry.RTG_BE.get(), (be, side) -> be.automation());
    }
}
