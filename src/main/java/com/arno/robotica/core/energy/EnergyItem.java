package com.arno.robotica.core.energy;

import com.arno.robotica.core.CoreComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

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

    /** The FE item capability of this stack. Override to add more storage behind the item's own battery. */
    default IEnergyStorage energyStorage(ItemStack stack) {
        return new ComponentEnergyStorage(stack, CoreComponents.ENERGY.get(), getEnergyCapacity(stack), getMaxReceive(stack), getMaxExtract(stack));
    }
}
