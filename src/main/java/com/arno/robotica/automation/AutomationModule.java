package com.arno.robotica.automation;

import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SurveyOrePool;
import com.arno.robotica.core.RoboticaTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Common (both sides) entry point of the automation module. */
public final class AutomationModule {
    private AutomationModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        registerTextArgs();
        AutomationContent.BLOCKS.register(modBus);
        AutomationContent.ITEMS.register(modBus);
        AutomationContent.BLOCK_ENTITIES.register(modBus);
        AutomationContent.MENUS.register(modBus);
        com.arno.robotica.automation.rancher.RancherContent.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, AutomationConfig.SPEC, "robotica-automation-server.toml");
        modBus.addListener(AutomationModule::registerCapabilities);
        // the Survey Rig's ore pool comes from the c:ores tag and the config: rebuild it after either changes
        modBus.addListener(ModConfigEvent.Loading.class, e -> SurveyOrePool.invalidate());
        modBus.addListener(ModConfigEvent.Reloading.class, e -> SurveyOrePool.invalidate());
        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, e -> SurveyOrePool.invalidate());

        RoboticaTab.add(AutomationContent.STUMPY_ITEM);
        RoboticaTab.add(AutomationContent.SPROUT_ITEM);
        RoboticaTab.add(com.arno.robotica.automation.rancher.RancherContent.RANCHER);
        RoboticaTab.add(AutomationContent.FARM_KIT_MK2);
        RoboticaTab.add(AutomationContent.FARM_KIT_MK3);
        RoboticaTab.add(AutomationContent.FARM_KIT_MK4);
        RoboticaTab.add(AutomationContent.EXCAVATOR_ITEM);
        RoboticaTab.add(AutomationContent.EXCAVATOR_MK2_ITEM);
        RoboticaTab.add(AutomationContent.EXCAVATOR_MK3_ITEM);
        RoboticaTab.add(AutomationContent.EXCAVATOR_MK4_ITEM);
        RoboticaTab.add(com.arno.robotica.automation.rancher.RancherContent.RANCHER_MK2);
        RoboticaTab.add(AutomationContent.SURVEY_RIG_ITEM);
        RoboticaTab.add(AutomationContent.SURVEY_RIG_MK2_ITEM);
        RoboticaTab.add(AutomationContent.SURVEY_RIG_MK3_ITEM);
        RoboticaTab.add(AutomationContent.SURVEY_RIG_MK4_ITEM);
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
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.sides.access(side));
    }

    /** Config numbers shown in card and Shift-details texts. */
    private static void registerTextArgs() {
        com.arno.robotica.core.upgrade.UpgradeText.register(com.arno.robotica.core.upgrade.UpgradeText.EXCAVATOR_RANGE_STEP,
                AutomationConfig::excavatorRangeStep);
        com.arno.robotica.core.item.DetailArgs.register("rancher", () -> new Object[]{com.arno.robotica.automation.rancher.Rancher.actionFe(),
                com.arno.robotica.automation.rancher.Rancher.cullFe()});
        com.arno.robotica.core.item.DetailArgs.register("rancher_mk2", () -> new Object[]{com.arno.robotica.automation.rancher.Rancher.actionFe(),
                com.arno.robotica.automation.rancher.Rancher.cullFe()});
        com.arno.robotica.core.item.DetailArgs.register("survey_rig", () -> {
            boolean loaded = AutomationConfig.SPEC.isLoaded();
            int fePerTick = loaded ? AutomationConfig.SURVEY_FE_PER_TICK.get() : 200;
            int ticks = loaded ? AutomationConfig.SURVEY_INTERVAL.get() : 400;
            int cards = Math.min(8, 4 * com.arno.robotica.core.CoreConfig.speedCapPerMk());
            int speed = com.arno.robotica.core.upgrade.Upgrades.speedMultiplier(cards);
            long feFactor = Math.round(speed * com.arno.robotica.core.upgrade.Upgrades.steepEnergyMultiplier(cards, 0));
            return new Object[]{com.arno.robotica.core.CoreConfig.scaleEnergy(fePerTick), Math.round(ticks / 20.0), speed, feFactor};
        });
    }
}
