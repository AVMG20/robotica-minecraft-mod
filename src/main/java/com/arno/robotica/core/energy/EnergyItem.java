package com.arno.robotica.core.energy;

import net.minecraft.world.item.ItemStack;

/**
 * Implement on any Item class (any module) to make it an FE item. The core registers the
 * {@code Capabilities.EnergyStorage.ITEM} capability automatically, backed by {@code CoreComponents.ENERGY}.
 * Use {@link ItemEnergy} for bar, tooltip and consumption helpers.
 */
public interface EnergyItem {
    int getEnergyCapacity(ItemStack stack);

    /** FE/t accepted from chargers and other mods. Return 0 to block outside charging. */
    default int getMaxReceive(ItemStack stack) {
        return getEnergyCapacity(stack);
    }

    /** FE/t other blocks (robots, mod machines) may pull out. Tools usually return 0. */
    default int getMaxExtract(ItemStack stack) {
        return 0;
    }
}
