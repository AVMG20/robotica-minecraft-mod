package com.arno.robotica.power;

import com.arno.robotica.power.block.WindingCrankBlock;
import com.arno.robotica.power.conduit.ConduitManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Common (both sides) entry point of the power module. See docs/DESIGN.md.
 * Winding Crank, Combustion Generator, Solar Panels, Accumulators, Conduits, Charger, Metal Press.
 */
public final class PowerModule {
    private PowerModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        PowerRegistry.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, PowerConfig.SPEC, "robotica-power-server.toml");
        modBus.addListener(PowerModule::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(ConduitManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ConduitManager::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(WindingCrankBlock::onLogout);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.WINDING_CRANK_BE.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.WINDING_CRANK_BE.get(), (be, side) -> be.automation());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.COMBUSTION_GENERATOR_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.COMBUSTION_GENERATOR_BE.get(), (be, side) -> be.automation());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.SOLAR_MK1_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.SOLAR_MK2_BE.get(), (be, side) -> be.energy);

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.ACCUMULATOR_BE.get(), (be, side) -> be.energyFor(side));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.CHARGER_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.CHARGER_BE.get(), (be, side) -> be.automation());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PowerRegistry.METAL_PRESS_BE.get(), (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PowerRegistry.METAL_PRESS_BE.get(), (be, side) -> be.automation());
    }
}
