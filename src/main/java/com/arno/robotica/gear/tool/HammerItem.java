package com.arno.robotica.gear.tool;

import net.minecraft.world.item.ItemStack;

/** Tinker's Hammer: a 3x3 mining tool that also stays in the crafting grid (losing 1 durability) for hand plate recipes. */
public class HammerItem extends GearToolItem {
    public HammerItem(Properties props, ToolSpec spec) {
        super(props, spec);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        int damage = stack.getDamageValue() + 1;
        if (damage >= stack.getMaxDamage()) return ItemStack.EMPTY;
        ItemStack left = stack.copy();
        left.setCount(1);
        left.setDamageValue(damage);
        return left;
    }
}
