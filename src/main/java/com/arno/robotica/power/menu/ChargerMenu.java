package com.arno.robotica.power.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.ChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ChargerMenu extends MachineMenu {
    private final ContainerLevelAccess access;
    private final int energyIndex;
    private final int capacityIndex;

    /** Client side. */
    public ChargerMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(1), null);
    }

    /** Server side. */
    public ChargerMenu(int id, Inventory inv, ChargerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.slot, be);
    }

    private ChargerMenu(int id, Inventory inv, BlockPos pos, IItemHandler slot, ChargerBlockEntity be) {
        super(PowerRegistry.CHARGER_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        addSlot(new SlotItemHandler(slot, 0, 80, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ChargerBlockEntity.canCharge(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addPlayerInventory(inv, 8, 84);
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    /** The item in the slot (synced by the normal slot sync, including its energy component). */
    public ItemStack chargedItem() {
        return slots.get(0).getItem();
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PowerRegistry.CHARGER.get());
    }
}
