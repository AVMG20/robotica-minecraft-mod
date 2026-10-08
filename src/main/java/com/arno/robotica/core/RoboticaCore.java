package com.arno.robotica.core;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;

/** Shared foundation: data components, materials, cells, upgrade cards, the module framework, creative tab, core config. */
public final class RoboticaCore {
    private RoboticaCore() {}

    public static void init(IEventBus modBus, ModContainer container) {
        CoreComponents.REGISTER.register(modBus);
        CoreSounds.REGISTER.register(modBus);
        com.arno.robotica.core.progress.Milestones.TRIGGERS.register(modBus);
        CoreItems.ITEMS.register(modBus);
        RoboticaTab.TABS.register(modBus);
        CoreItems.addToTab();
        container.registerConfig(ModConfig.Type.SERVER, CoreConfig.SPEC, "robotica-core-server.toml");
        com.arno.robotica.core.module.ModuleComponents.REGISTER.register(modBus);
        com.arno.robotica.core.module.ModuleItems.ITEMS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, com.arno.robotica.core.module.ModuleConfig.SPEC, "robotica-modules-server.toml");
        com.arno.robotica.core.module.ModuleText.register(com.arno.robotica.core.module.ModuleKind.POWER_REGULATOR, level ->
                net.minecraft.network.chat.Component.translatable("module.robotica.power_regulator.desc",
                        Math.round(com.arno.robotica.core.module.ModuleConfig.regulatorSaving(level) * 100)));
        modBus.addListener(RoboticaCore::registerCapabilities);
        modBus.addListener(com.arno.robotica.core.side.SideConfigPayload::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TagsUpdatedEvent.class,
                e -> com.arno.robotica.core.util.RecipeAcceptCache.bump());
    }

    /** Every item implementing {@link EnergyItem} (from any module) gets the FE item capability. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof EnergyItem energyItem) {
                event.registerItem(Capabilities.EnergyStorage.ITEM,
                        (stack, ctx) -> new ComponentEnergyStorage(stack, CoreComponents.ENERGY.get(),
                                energyItem.getEnergyCapacity(stack),
                                energyItem.getMaxReceive(stack),
                                energyItem.getMaxExtract(stack)),
                        item);
            }
        }
    }
}
