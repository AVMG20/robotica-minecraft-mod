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

/** Shared foundation: data components, materials, cells, upgrade cards, creative tab, core config. */
public final class RoboticaCore {
    private RoboticaCore() {}

    public static void init(IEventBus modBus, ModContainer container) {
        CoreComponents.REGISTER.register(modBus);
        CoreItems.ITEMS.register(modBus);
        RoboticaTab.TABS.register(modBus);
        CoreItems.addToTab();
        container.registerConfig(ModConfig.Type.SERVER, CoreConfig.SPEC, "robotica-core-server.toml");
        modBus.addListener(RoboticaCore::registerCapabilities);
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
