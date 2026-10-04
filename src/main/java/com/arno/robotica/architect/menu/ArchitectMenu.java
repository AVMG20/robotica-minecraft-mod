package com.arno.robotica.architect.menu;

import com.arno.robotica.architect.ArchitectConfig;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.Upgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Architect Table menu. The GUI has two tabs: the plan (plot grid, module list, queue) and the storage (27 input slots,
 * casing slot, upgrade slots). Slots only exist visibly on the storage tab ({@link #tab} is client side state).
 * Everything the plan shows (grid, queue, matter, status) is synced as tracked ints, so it needs no extra packets.
 */
public class ArchitectMenu extends MachineMenu {
    public static final int WIDTH = 288;
    public static final int HEIGHT = 246;
    public static final int INV_X = 63;
    public static final int INV_Y = 164;
    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 30;
    public static final int STYLE_X = 8;
    public static final int STYLE_Y = 98;
    public static final int UPGRADE_X = 84;
    public static final int UPGRADE_Y = 98;

    /** Client side: 0 = plan, 1 = storage. */
    public int tab;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final ArchitectTableBlockEntity table;
    private final int energyIndex, capacityIndex, matterCapIndex, statusIndex, progressIndex, flagsIndex, queueSizeIndex;
    private final int gridIndex, queueIndex;

    /** Client side. */
    public ArchitectMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(ArchitectTableBlockEntity.INPUT_SLOTS), new ItemStackHandler(1),
                new Upgrades(2, Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY), () -> {}), null);
    }

    /** Server side. */
    public ArchitectMenu(int id, Inventory inv, ArchitectTableBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.input, be.styleSlot, be.upgrades, be);
    }

    private ArchitectMenu(int id, Inventory inv, BlockPos pos, IItemHandler input, IItemHandler style, IItemHandler upgrades, ArchitectTableBlockEntity be) {
        super(ArchitectRegistry.ARCHITECT_MENU.get(), id);
        this.pos = pos;
        this.table = be;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new StorageSlot(input, col + row * 9, INPUT_X + col * 18, INPUT_Y + row * 18));
            }
        }
        addSlot(new StorageSlot(style, 0, STYLE_X, STYLE_Y));
        addSlot(new StorageSlot(upgrades, 0, UPGRADE_X, UPGRADE_Y));
        addSlot(new StorageSlot(upgrades, 1, UPGRADE_X + 20, UPGRADE_Y));
        addPlayerInventory(inv, INV_X, INV_Y);

        IntSupplier zero = () -> 0;
        energyIndex = track(be == null ? zero : () -> be.energy.getEnergyStored());
        capacityIndex = track(be == null ? zero : () -> be.energy.getMaxEnergyStored());
        matterCapIndex = track(be == null ? zero : ArchitectConfig::matterCap);
        track(be == null ? zero : () -> be.matter(Matter.Grade.RUSTIC));
        track(be == null ? zero : () -> be.matter(Matter.Grade.REFINED));
        track(be == null ? zero : () -> be.matter(Matter.Grade.EXOTIC));
        statusIndex = track(be == null ? zero : be::status);
        progressIndex = track(be == null ? zero : be::progressPermille);
        flagsIndex = track(be == null ? zero : be::flags);
        queueSizeIndex = track(be == null ? zero : be::queueSize);
        int firstGrid = -1;
        for (int w = 0; w < 7; w++) {
            final int word = w;
            int index = track(be == null ? zero : () -> be.gridWord(word));
            if (firstGrid < 0) firstGrid = index;
        }
        gridIndex = firstGrid;
        int firstQueue = -1;
        for (int w = 0; w < 3; w++) {
            final int word = w;
            int index = track(be == null ? zero : () -> be.queueWord(word));
            if (firstQueue < 0) firstQueue = index;
        }
        queueIndex = firstQueue;
    }

    /** Slot of the storage tab: only usable and drawn while that tab is open. */
    private class StorageSlot extends SlotItemHandler {
        StorageSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean isActive() {
            return tab == 1;
        }
    }

    public BlockPos pos() {
        return pos;
    }

    /** Server side: the table this menu is bound to, null on the client. */
    public ArchitectTableBlockEntity table() {
        return table;
    }

    public int energy() {
        return synced(energyIndex);
    }

    public int capacity() {
        return synced(capacityIndex);
    }

    public int matterCap() {
        return synced(matterCapIndex);
    }

    public int matter(Matter.Grade grade) {
        return synced(matterCapIndex + 1 + grade.ordinal());
    }

    public int status() {
        return synced(statusIndex);
    }

    /** Progress of the current build, 0-1. */
    public float progress() {
        return synced(progressIndex) / 1000.0F;
    }

    public boolean clearTerrain() {
        return (synced(flagsIndex) & 1) != 0;
    }

    public BuildStyle selectedStyle() {
        return BuildStyle.byOrdinal((synced(flagsIndex) >> 1) & 3);
    }

    public boolean styleUnlocked(BuildStyle style) {
        return ((synced(flagsIndex) >> 3) & (1 << style.ordinal())) != 0;
    }

    public int queueSize() {
        return synced(queueSizeIndex);
    }

    /** Module id (0 = empty) of a plot. */
    public int plotModule(int plot) {
        return plotCell(plot) & 15;
    }

    /** Plot status: 0 empty, 1 queued, 2 building, 3 built. */
    public int plotStatus(int plot) {
        return plotModule(plot) == 0 ? 0 : ((plotCell(plot) >> 4) & 3) + 1;
    }

    public BuildStyle plotStyle(int plot) {
        return BuildStyle.byOrdinal((plotCell(plot) >> 6) & 3);
    }

    private int plotCell(int plot) {
        if (!Plots.valid(plot)) return 0;
        int word = synced(gridIndex + plot / 4);
        return (word >>> (8 * (plot % 4))) & 0xFF;
    }

    /** Queue entry as {plot, module id, patch (0/1), style}, or null past the end (shows up to 6). */
    public int[] queueEntry(int index) {
        if (index < 0 || index >= 6) return null;
        int word = synced(queueIndex + index / 2);
        int entry = (word >>> (16 * (index % 2))) & 0xFFFF;
        if ((entry & (1 << 10)) == 0) return null;
        return new int[]{entry & 31, (entry >> 5) & 15, (entry >> 9) & 1, (entry >> 11) & 3};
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ArchitectRegistry.ARCHITECT_TABLE.get()) && (table == null || table.canUse(player));
    }
}
