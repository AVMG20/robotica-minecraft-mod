package com.arno.robotica.core.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (machineSlotCount < 0) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < machineSlotCount) {
            if (!moveItemStackTo(stack, machineSlotCount, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlotCount, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
