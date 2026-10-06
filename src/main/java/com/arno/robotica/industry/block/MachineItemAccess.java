package com.arno.robotica.industry.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * What pipes, hoppers and storage mods see of a processing machine, on every face: they may insert into the input
 * slots and extract from the output slots. One item kind per input slot: a stack only goes into a slot that already
 * holds it, or into an empty slot when no other input slot holds it, so a hopper full of plates cannot clog every
 * input of a multi-input recipe.
 */
final class MachineItemAccess implements IItemHandler {
    private final ProcessingBlockEntity be;

    MachineItemAccess(ProcessingBlockEntity be) {
        this.be = be;
    }

    private boolean isInput(int slot) {
        return slot < be.machine.inputs;
    }

    @Override
    public int getSlots() {
        return be.items.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return be.items.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isValid(slot, stack)) return stack;
        return be.items.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (isInput(slot)) return ItemStack.EMPTY;
        return be.items.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return be.items.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isValid(slot, stack);
    }

    private boolean isValid(int slot, ItemStack stack) {
        if (!isInput(slot) || stack.isEmpty()) return false;
        ItemStack here = be.items.getStackInSlot(slot);
        if (!here.isEmpty()) return ItemStack.isSameItemSameComponents(here, stack);
        for (int i = 0; i < be.machine.inputs; i++) {
            if (i != slot && ItemStack.isSameItemSameComponents(be.items.getStackInSlot(i), stack)) return false;
        }
        return be.items.isItemValid(slot, stack);
    }
}
