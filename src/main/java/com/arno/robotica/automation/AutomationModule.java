package com.arno.robotica.automation;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.core.RoboticaTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Common (both sides) entry point of the automation module. See docs/DESIGN.md. */
public final class AutomationModule {
    private AutomationModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        AutomationContent.BLOCKS.register(modBus);
        AutomationContent.ITEMS.register(modBus);
        AutomationContent.BLOCK_ENTITIES.register(modBus);
        AutomationContent.MENUS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, AutomationConfig.SPEC, "robotica-automation-server.toml");
        modBus.addListener(AutomationModule::registerCapabilities);

        RoboticaTab.add(AutomationContent.STUMPY_ITEM);
        RoboticaTab.add(AutomationContent.SPROUT_ITEM);
        RoboticaTab.add(AutomationContent.FARM_KIT_MK2);
        RoboticaTab.add(AutomationContent.FARM_KIT_MK3);
        RoboticaTab.add(AutomationContent.FARM_KIT_MK4);
        RoboticaTab.add(AutomationContent.EXCAVATOR_ITEM);
        RoboticaTab.add(AutomationContent.SURVEY_RIG_ITEM);
        RoboticaTab.add(AutomationContent.SUPPLY_CRATE_ITEM);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        registerWorker(event, AutomationContent.STUMPY_BE.get());
        registerWorker(event, AutomationContent.SPROUT_BE.get());
        registerWorker(event, AutomationContent.EXCAVATOR_BE.get());
        registerWorker(event, AutomationContent.SURVEY_RIG_BE.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AutomationContent.SUPPLY_CRATE_BE.get(), (be, side) -> be.items);
    }

    /** FE from cables on every side (receive only) and the buffer as an item handler for pipes and hoppers. */
    private static <T extends AreaWorkerBlockEntity> void registerWorker(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.externalBuffer);
    }
}
