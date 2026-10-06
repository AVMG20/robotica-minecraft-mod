package com.arno.robotica.core.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Menu base for Robotica machines. Add machine slots first, then call {@link #addPlayerInventory}.
 * Use {@link #track} for ints larger than 32767 (energy): vanilla data slots only sync 16 bits.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    private final List<int[]> synced = new ArrayList<>();
    private int machineSlotCount = -1;

    protected MachineMenu(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** Standard 176-wide layout: x = 8, y = 84 for a 166-high GUI. Must be called after all machine slots. */
    protected void addPlayerInventory(Inventory inv, int x, int y) {
        machineSlotCount = slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, x + col * 18, y + 58));
        }
    }

    /**
     * Syncs a full 32-bit int to the client. On the server pass a supplier reading the block entity;
     * on the client pass {@code () -> 0}. Returns an index for {@link #synced(int)}.
     */
    protected int track(IntSupplier server) {
        int index = synced.size();
        int[] holder = new int[1];
        synced.add(holder);
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return server.getAsInt() & 0xFFFF;
            }

            @Override
            public void set(int value) {
                holder[0] = (holder[0] & 0xFFFF0000) | (value & 0xFFFF);
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return (server.getAsInt() >>> 16) & 0xFFFF;
            }

            @Override
            public void set(int value) {
                holder[0] = (holder[0] & 0x0000FFFF) | ((value & 0xFFFF) << 16);
            }
        });
        return index;
    }

    /** Client side only: the last synced value. On the server, read the block entity directly. */
    public int synced(int index) {
        return synced.get(index)[0];
    }

    /**
     * Plain {@code new SlotItemHandler(...)} slots become {@link MachineSlot}s, so changes made through the menu always
     * reach the handler's {@code onContentsChanged} and card caps hold for normal clicks too.
     */
    @Override
    protected Slot addSlot(Slot slot) {
        if (slot.getClass() == SlotItemHandler.class) {
            SlotItemHandler plain = (SlotItemHandler) slot;
            slot = new MachineSlot(plain.getItemHandler(), plain.getSlotIndex(), plain.x, plain.y);
        }
        return super.addSlot(slot);
    }

    /**
     * Shift-click between the machine slots and the player inventory. Works on a copy and writes both ends back with
     * {@link Slot#set}, so item handlers see every change (vanilla shrinks the live stack and calls
     * {@link Slot#setChanged}, which a {@link SlotItemHandler} sends nowhere).
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (machineSlotCount < 0 || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        ItemStack stack = original.copy();
        boolean moved = index < machineSlotCount
                ? moveItemStackTo(stack, machineSlotCount, slots.size(), true)
                : moveItemStackTo(stack, 0, machineSlotCount, false);
        int count = original.getCount() - stack.getCount();
        if (!moved || count <= 0) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, original);
        else slot.set(stack);
        if (!(slot instanceof MachineSlot)) slot.setChanged();
        slot.onTake(player, original.copyWithCount(count));
        return original;
    }

    /**
     * Same contract as vanilla (shrinks {@code stack}, returns true if anything moved), but it skips inactive slots,
     * asks {@link Slot#mayPlace} for merges too, respects what an item handler really accepts ({@link MachineSlot#limit})
     * and writes targets back with {@link Slot#set} instead of growing their live stacks.
     */
    @Override
    protected boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        boolean moved = false;
        if (stack.isStackable()) moved = fill(stack, startIndex, endIndex, reverseDirection, true);
        if (!stack.isEmpty()) moved |= fill(stack, startIndex, endIndex, reverseDirection, false);
        return moved;
    }

    private boolean fill(ItemStack stack, int start, int end, boolean reverse, boolean merge) {
        boolean moved = false;
        for (int k = 0; k < end - start && !stack.isEmpty(); k++) {
            Slot slot = slots.get(reverse ? end - 1 - k : start + k);
            if (!slot.isActive()) continue;
            ItemStack current = slot.getItem();
            if (merge ? current.isEmpty() || !ItemStack.isSameItemSameComponents(current, stack) : !current.isEmpty()) continue;
            if (!slot.mayPlace(stack)) continue;
            int n = Math.min(stack.getCount(), MachineSlot.limit(slot, stack) - current.getCount());
            if (n <= 0) continue;
            if (merge) slot.set(current.copyWithCount(current.getCount() + n));
            else slot.setByPlayer(stack.copyWithCount(n));
            if (!(slot instanceof MachineSlot)) slot.setChanged();
            stack.shrink(n);
            moved = true;
        }
        return moved;
    }
}
