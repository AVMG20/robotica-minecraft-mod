package com.arno.robotica.industry.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.block.IndustryBlockEntity;
import com.arno.robotica.industry.block.ProcessingBlock;
import com.arno.robotica.industry.block.ProcessingBlockEntity;
import com.arno.robotica.industry.recipe.Machine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Menu of every processing machine. Slot order: inputs, outputs, battery, upgrades, then the player inventory.
 * The layout comes from {@link #inputPos}/{@link #outputPos}; the GUI is 184 px high (player inventory at y 102).
 */
public class ProcessingMenu extends MachineMenu {
    public static final int HEIGHT = 184;
    public static final int UPGRADE_Y = 70;

    private final ContainerLevelAccess access;
    private final Block block;
    public final Machine machine;
    public final int tier;
    private final int energyIdx, capacityIdx, progressIdx, neededIdx, useIdx, statusIdx;

    /** Client side: machine and Mk follow from the block at the position. */
    public ProcessingMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, (ProcessingBlock) inv.player.level().getBlockState(pos).getBlock(), null);
    }

    /** Server side. */
    public ProcessingMenu(int id, Inventory inv, ProcessingBlockEntity be) {
        this(id, inv, be.getBlockPos(), (ProcessingBlock) be.getBlockState().getBlock(), be);
    }

    private ProcessingMenu(int id, Inventory inv, BlockPos pos, ProcessingBlock block, ProcessingBlockEntity be) {
        super(IndustryRegistry.PROCESSING_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.block = block;
        this.machine = block.machine();
        this.tier = block.tier();
        IItemHandler items = be != null ? be.items : new ItemStackHandler(machine.slots());
        IItemHandler battery = be != null ? be.battery : new ItemStackHandler(1);
        IItemHandler upgrades = be != null ? be.upgrades : new Upgrades(tier + 1, ProcessingBlockEntity.acceptedKinds(machine),
                k -> ProcessingBlockEntity.cardCap(machine, tier, k), () -> {});

        for (int i = 0; i < machine.inputs; i++) {
            int[] p = inputPos(machine, i);
            addSlot(new SlotItemHandler(items, i, p[0], p[1]));
        }
        for (int i = 0; i < machine.outputs; i++) {
            int[] p = outputPos(machine, i);
            addSlot(new SlotItemHandler(items, machine.inputs + i, p[0], p[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addSlot(new SlotItemHandler(battery, 0, 8, 66) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return IndustryBlockEntity.isBattery(stack);
            }
        });
        int n = upgrades.getSlots();
        for (int i = 0; i < n; i++) addSlot(new SlotItemHandler(upgrades, i, 152 - 18 * (n - 1 - i), UPGRADE_Y));
        addPlayerInventory(inv, 8, HEIGHT - 82);

        energyIdx = track(be == null ? () -> 0 : () -> be.energy.getEnergyStored());
        capacityIdx = track(be == null ? () -> 0 : () -> be.energy.getMaxEnergyStored());
        progressIdx = track(be == null ? () -> 0 : be::progress);
        neededIdx = track(be == null ? () -> 0 : be::needed);
        useIdx = track(be == null ? () -> 0 : be::lastUse);
        statusIdx = track(be == null ? () -> 0 : be::status);
    }

    /** Item position of input slot i. */
    public static int[] inputPos(Machine machine, int i) {
        return switch (machine) {
            case ALLOY_SMELTER -> new int[]{32 + 18 * i, 29};
            case CENTRIFUGE -> new int[]{44, 29};
            case ASSEMBLER -> new int[]{32 + 18 * (i % 3), 20 + 18 * (i / 3)};
        };
    }

    /** Item position of output slot i. */
    public static int[] outputPos(Machine machine, int i) {
        return switch (machine) {
            case ALLOY_SMELTER, ASSEMBLER -> new int[]{122, 29};
            case CENTRIFUGE -> new int[]{100 + 18 * (i % 2), 20 + 18 * (i / 2)};
        };
    }

    /** X of the progress arrow. */
    public static int arrowX(Machine machine) {
        return machine == Machine.CENTRIFUGE ? 68 : 92;
    }

    public int energy() {
        return synced(energyIdx);
    }

    public int capacity() {
        return synced(capacityIdx);
    }

    public float progress() {
        int needed = synced(neededIdx);
        return needed <= 0 ? 0 : Math.min(1.0F, (float) synced(progressIdx) / needed);
    }

    public int needed() {
        return synced(neededIdx);
    }

    public int lastUse() {
        return synced(useIdx);
    }

    public int status() {
        return synced(statusIdx);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
