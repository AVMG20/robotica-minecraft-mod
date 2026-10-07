package com.arno.robotica.processing.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.menu.MachineSlot;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.ElectricFurnaceBlockEntity;
import com.arno.robotica.processing.block.MachineUpgrades;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import com.arno.robotica.processing.block.ProcessingMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/** Electric Furnace GUI: one input and output slot per lane of its Mk, battery and upgrade slots. 200 px tall. */
public class ElectricFurnaceMenu extends MachineMenu {
    public static final int HEIGHT = 200;
    public static final int INPUT_Y = 18;
    public static final int OUTPUT_Y = 46;
    public static final int ROW_Y = 70;
    public static final int UPGRADE_X = 44;

    private final ContainerLevelAccess access;
    private final Block block;
    private final int tier;
    private final int lanes;
    private final int laneX;
    private final int energyIndex, capacityIndex, statusIndex, useIndex, xpIndex;
    private final int[] laneIndex = new int[ElectricFurnaceBlockEntity.MAX_LANES];

    /** Client side. */
    public ElectricFurnaceMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(ElectricFurnaceBlockEntity.SLOTS), null);
    }

    /** Server side. */
    public ElectricFurnaceMenu(int id, Inventory inv, ElectricFurnaceBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.items, be);
    }

    private ElectricFurnaceMenu(int id, Inventory inv, BlockPos pos, IItemHandler items, @Nullable ElectricFurnaceBlockEntity be) {
        super(ProcessingRegistry.ELECTRIC_FURNACE_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.block = inv.player.level().getBlockState(pos).getBlock();
        this.tier = block instanceof ProcessingMachineBlock m ? m.tier() : 1;
        this.lanes = Math.max(1, Math.min(ElectricFurnaceBlockEntity.MAX_LANES, ProcessingConfig.lanes(tier)));
        this.laneX = 8 + (ElectricFurnaceBlockEntity.MAX_LANES - lanes) * 9;
        IItemHandler upgrades = be != null ? be.upgrades
                : new MachineUpgrades(ElectricFurnaceBlockEntity.KINDS, k -> ProcessingMachineBlockEntity.cap(k, tier), () -> ProcessingConfig.upgradeSlots(tier), () -> {});

        for (int i = 0; i < ElectricFurnaceBlockEntity.MAX_LANES; i++) {
            final boolean on = i < lanes;
            int x = laneX + Math.min(i, lanes - 1) * 18;
            addSlot(new MachineSlot(items, i, x, INPUT_Y) {
                @Override
                public boolean isActive() {
                    return on;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return on && super.mayPlace(stack);
                }
            });
        }
        for (int i = 0; i < ElectricFurnaceBlockEntity.MAX_LANES; i++) {
            final boolean on = i < lanes;
            int x = laneX + Math.min(i, lanes - 1) * 18;
            addSlot(new MachineSlot(items, ElectricFurnaceBlockEntity.OUT_FIRST + i, x, OUTPUT_Y) {
                @Override
                public boolean isActive() {
                    return on;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                /** Taking the output by hand pays out the stored experience, like a vanilla furnace. */
                @Override
                public void onTake(Player player, ItemStack stack) {
                    if (be != null && player.level() instanceof ServerLevel level) be.popExperience(level, player.position());
                    super.onTake(player, stack);
                }
            });
        }
        addSlot(new SlotItemHandler(items, ElectricFurnaceBlockEntity.BATTERY, 8, ROW_Y));
        int active = ProcessingConfig.upgradeSlots(tier);
        for (int i = 0; i < MachineUpgrades.MAX_SLOTS; i++) {
            final boolean on = i < active;
            addSlot(new MachineSlot(upgrades, i, UPGRADE_X + i * 18, ROW_Y) {
                @Override
                public boolean isActive() {
                    return on;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return on && super.mayPlace(stack);
                }
            });
        }
        addPlayerInventory(inv, 8, HEIGHT - 82);
        energyIndex = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        statusIndex = track(be == null ? () -> 0 : () -> be.status().ordinal());
        useIndex = track(be == null ? () -> 0 : be::lastUse);
        xpIndex = track(be == null ? () -> 0 : () -> (int) be.storedXp());
        trackSides(be == null ? null : be.sides);
        for (int i = 0; i < ElectricFurnaceBlockEntity.MAX_LANES; i++) {
            final int lane = i;
            laneIndex[i] = track(be == null ? () -> 0 : () -> be.needed(lane) <= 0 ? 0 : be.progress(lane) * 1000 / be.needed(lane));
        }
    }

    public int tier() {
        return tier;
    }

    public int lanes() {
        return lanes;
    }

    public int laneX() {
        return laneX;
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    public ProcessingMachineBlockEntity.Status status() {
        ProcessingMachineBlockEntity.Status[] all = ProcessingMachineBlockEntity.Status.values();
        int i = synced(statusIndex);
        return i >= 0 && i < all.length ? all[i] : ProcessingMachineBlockEntity.Status.IDLE;
    }

    public int lastUse() {
        return synced(useIndex);
    }

    public int storedXp() {
        return synced(xpIndex);
    }

    public float laneProgress(int lane) {
        return Math.min(1.0F, synced(laneIndex[lane]) / 1000.0F);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
