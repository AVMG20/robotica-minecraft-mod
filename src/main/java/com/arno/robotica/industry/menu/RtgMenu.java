package com.arno.robotica.industry.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.menu.MachineSlot;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.block.RtgBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** RTG: fuel slot, waste slot, energy, pellet decay. */
public class RtgMenu extends MachineMenu {
    private final ContainerLevelAccess access;
    private final int energyIdx, capacityIdx, decayIdx, totalIdx, genIdx;

    public RtgMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(2), null);
    }

    public RtgMenu(int id, Inventory inv, RtgBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.items, be);
    }

    private RtgMenu(int id, Inventory inv, BlockPos pos, IItemHandler items, RtgBlockEntity be) {
        super(IndustryRegistry.RTG_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        addSlot(new MachineSlot(items, RtgBlockEntity.FUEL, 56, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RtgBlockEntity.isFuel(stack);
            }
        });
        addSlot(new MachineSlot(items, RtgBlockEntity.WASTE, 116, 30) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addPlayerInventory(inv, 8, 84);
        energyIdx = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIdx = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        decayIdx = track(be == null ? () -> 0 : be::decay);
        totalIdx = track(be == null ? () -> 0 : be::decayTotal);
        genIdx = track(be == null ? () -> 0 : () -> be.isGenerating() ? RtgBlockEntity.output() : 0);
        trackSides(be == null ? null : be.sides);
    }

    public int energy() {
        return synced(energyIdx);
    }

    public int capacity() {
        return synced(capacityIdx);
    }

    public int decay() {
        return synced(decayIdx);
    }

    /** Share of the current pellet that is left, 0..1. */
    public float remaining() {
        int total = synced(totalIdx);
        return total <= 0 ? 0 : (float) synced(decayIdx) / total;
    }

    public int generating() {
        return synced(genIdx);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, IndustryRegistry.RTG.get());
    }
}
