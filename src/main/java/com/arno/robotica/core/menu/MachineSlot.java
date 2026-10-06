package com.arno.robotica.core.menu;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Item handler slot for {@link MachineMenu}s. Unlike a plain {@link SlotItemHandler} it writes every change back
 * through {@link IItemHandlerModifiable#setStackInSlot}, so the handler's {@code onContentsChanged} (and with it the
 * block entity's {@code setChanged}, card caches and recipe checks) always runs, and its stack limit is what the
 * handler would really accept (card caps, {@code getStackLimit} overrides), found with a simulated insert.
 *
 * <p>{@link MachineMenu#addSlot} turns plain {@code new SlotItemHandler(...)} slots into this class automatically;
 * subclass it instead of {@link SlotItemHandler} for custom slots.
 */
public class MachineSlot extends SlotItemHandler {
    public MachineSlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    @Override
    public void set(ItemStack stack) {
        if (getItemHandler() instanceof IItemHandlerModifiable handler) handler.setStackInSlot(index, stack);
    }

    /** Vanilla calls this after growing or shrinking the live stack in place: write it back so the handler notices. */
    @Override
    public void setChanged() {
        if (getItemHandler() instanceof IItemHandlerModifiable handler) handler.setStackInSlot(index, getItem());
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.min(super.getMaxStackSize(stack), handlerLimit(getItemHandler(), index, stack));
    }

    /**
     * How many of {@code stack} this slot may hold in total, for any slot: the slot's own limit and, for item handler
     * slots, what the handler accepts.
     */
    public static int limit(Slot slot, ItemStack stack) {
        int limit = slot.getMaxStackSize(stack);
        if (slot instanceof SlotItemHandler s && !(slot instanceof MachineSlot)) {
            limit = Math.min(limit, handlerLimit(s.getItemHandler(), s.getSlotIndex(), stack));
        }
        return limit;
    }

    /** Total count of {@code stack} the handler slot would hold, by simulating an insert of a full stack. */
    public static int handlerLimit(IItemHandler handler, int index, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int max = stack.getMaxStackSize();
        ItemStack current = handler.getStackInSlot(index);
        if (current.isEmpty()) return max - handler.insertItem(index, stack.copyWithCount(max), true).getCount();
        if (ItemStack.isSameItemSameComponents(current, stack)) {
            return current.getCount() + max - handler.insertItem(index, stack.copyWithCount(max), true).getCount();
        }
        // A different item would swap in: ask the handler as if the slot were empty.
        if (handler instanceof IItemHandlerModifiable modifiable) {
            ItemStack saved = current.copy();
            modifiable.setStackInSlot(index, ItemStack.EMPTY);
            try {
                return max - modifiable.insertItem(index, stack.copyWithCount(max), true).getCount();
            } finally {
                modifiable.setStackInSlot(index, saved);
            }
        }
        return Math.min(max, handler.getSlotLimit(index));
    }
}
