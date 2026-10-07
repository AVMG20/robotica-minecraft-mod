package com.arno.robotica.gear.tool;

import com.arno.robotica.core.energy.EnergyItem;
import com.arno.robotica.core.energy.ItemEnergy;
import net.minecraft.world.item.ItemStack;

/** FE powered tool. Accepts charge from chargers and cells, never gives energy out, never breaks. */
public class GearEnergyToolItem extends GearToolItem implements EnergyItem {
    public GearEnergyToolItem(Properties props, ToolSpec spec) {
        super(props, spec);
        if (!spec.isEnergy()) throw new IllegalArgumentException("energy tool needs an energy capacity");
    }

    /** Charging from a cell in the inventory only changes the energy: keep mining the same block. */
    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !ItemEnergy.onlyEnergyChanged(oldStack, newStack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemEnergy.onlyEnergyChanged(oldStack, newStack);
    }

    @Override
    public int getEnergyCapacity(ItemStack stack) {
        return spec.energyCapacity.getAsInt();
    }

    @Override
    public int getMaxExtract(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return ItemEnergy.barWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ItemEnergy.BAR_COLOR;
    }
}
