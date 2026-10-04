package com.arno.robotica.automation.entity;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Small helpers for item stack lists. */
public final class Drops {
    private Drops() {}

    /** Adds a stack to the list, merging into matching stacks first. The input is not modified. */
    public static void merge(List<ItemStack> list, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack existing : list) {
            if (rest.isEmpty()) return;
            if (ItemStack.isSameItemSameComponents(existing, rest)) {
                int room = existing.getMaxStackSize() - existing.getCount();
                int moved = Math.min(room, rest.getCount());
                if (moved > 0) {
                    existing.grow(moved);
                    rest.shrink(moved);
                }
            }
        }
        if (!rest.isEmpty()) list.add(rest);
    }

    /** Removes one item matching the predicate item from the list. Returns true if one was taken. */
    public static boolean takeOne(List<ItemStack> list, net.minecraft.world.item.Item item) {
        for (ItemStack stack : list) {
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
