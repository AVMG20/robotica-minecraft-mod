package com.arno.robotica.power.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.MetalPressBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Set;

public class MetalPressMenu extends MachineMenu {
    private final ContainerLevelAccess access;
    private final int energyIndex;
    private final int capacityIndex;
    private final int progressIndex;
    private final int neededIndex;

    /** Client side. */
    public MetalPressMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(2),
                new Upgrades(2, Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY), () -> {}), null);
    }

    /** Server side. */
    public MetalPressMenu(int id, Inventory inv, MetalPressBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.items, be.upgrades, be);
    }

    private MetalPressMenu(int id, Inventory inv, BlockPos pos, IItemHandler items, IItemHandler upgrades, MetalPressBlockEntity be) {
        super(PowerRegistry.METAL_PRESS_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        addSlot(new SlotItemHandler(items, 0, 44, 36));
        addSlot(new SlotItemHandler(items, 1, 108, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new SlotItemHandler(upgrades, 0, 134, 20));
        addSlot(new SlotItemHandler(upgrades, 1, 134, 44));
        addPlayerInventory(inv, 8, 84);
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        progressIndex = track(be == null ? () -> 0 : be::progress);
        neededIndex = track(be == null ? () -> 0 : be::needed);
        trackSides(be == null ? null : be.sides);
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    public float progress() {
        int needed = synced(neededIndex);
        return needed <= 0 ? 0 : Math.min(1.0F, (float) synced(progressIndex) / needed);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PowerRegistry.METAL_PRESS.get());
    }
}
