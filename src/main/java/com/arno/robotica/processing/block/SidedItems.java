package com.arno.robotica.processing.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Automation view of a machine inventory: per-slot rules for what pipes and hoppers may put in and take out. */
final class SidedItems implements IItemHandler {
    interface Rule {
        boolean test(int slot, ItemStack stack);
    }

    static final Rule NEVER = (slot, stack) -> false;

    private final IItemHandler inner;
    private final Rule canInsert;
    private final Rule canExtract;

    SidedItems(IItemHandler inner, Rule canInsert, Rule canExtract) {
        this.inner = inner;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    @Override
    public int getSlots() {
        return inner.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inner.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !canInsert.test(slot, stack)) return stack;
        return inner.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack current = inner.getStackInSlot(slot);
        if (current.isEmpty() || !canExtract.test(slot, current)) return ItemStack.EMPTY;
        return inner.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inner.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canInsert.test(slot, stack) && inner.isItemValid(slot, stack);
    }
}
