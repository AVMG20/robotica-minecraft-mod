package com.arno.robotica.architect.menu;

import com.arno.robotica.architect.ArchitectConfig;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.plan.Layout;
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
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.IntSupplier;

/**
 * Architect Table menu: one page with the plot grid, the material input, the casing slot and two upgrade slots.
 * Everything the grid shows (plan, matter, status) is synced as tracked ints, so it needs no extra packets.
 */
public class ArchitectMenu extends MachineMenu {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 242;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 18;
    public static final int CELL = 18;
    public static final int SLOT_ROW_Y = 113;
    public static final int CASING_X = 8;
    public static final int CHIPS_X = 28;
    public static final int UPGRADE_X = 110;
    public static final int CLEAR_X = 152;
    public static final int INPUT_Y = 136;
    public static final int BATTERY_X = 152;
    public static final int BATTERY_Y = 56;
    public static final int INV_X = 8;
    public static final int INV_Y = 160;

    private static final int PLAN_WORDS = (Plots.COUNT + 2) / 3;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    @Nullable
    private final ArchitectTableBlockEntity table;
    private final int energyIndex, capacityIndex, matterCapIndex, statusIndex, progressIndex, flagsIndex, currentIndex, feIndex, planIndex;

    /** Client side. */
    public ArchitectMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, new ItemStackHandler(ArchitectTableBlockEntity.INPUT_SLOTS), new ItemStackHandler(1),
                new Upgrades(2, Set.of(UpgradeKind.SPEED, UpgradeKind.EFFICIENCY), () -> {}), new ItemStackHandler(1), null);
    }

    /** Server side. */
    public ArchitectMenu(int id, Inventory inv, ArchitectTableBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.input, be.styleSlot, be.upgrades, be.battery, be);
    }

    private ArchitectMenu(int id, Inventory inv, BlockPos pos, IItemHandler input, IItemHandler style, IItemHandler upgrades,
                          IItemHandler battery, @Nullable ArchitectTableBlockEntity be) {
        super(ArchitectRegistry.ARCHITECT_MENU.get(), id);
        this.pos = pos;
        this.table = be;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        for (int i = 0; i < ArchitectTableBlockEntity.INPUT_SLOTS; i++) addSlot(new SlotItemHandler(input, i, INV_X + i * 18, INPUT_Y));
        addSlot(new SlotItemHandler(style, 0, CASING_X, SLOT_ROW_Y));
        addSlot(new SlotItemHandler(upgrades, 0, UPGRADE_X, SLOT_ROW_Y));
        addSlot(new SlotItemHandler(upgrades, 1, UPGRADE_X + 18, SLOT_ROW_Y));
        addSlot(new SlotItemHandler(battery, 0, BATTERY_X, BATTERY_Y));
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
        currentIndex = track(be == null ? zero : be::currentPlot);
        feIndex = track(be == null ? zero : ArchitectTableBlockEntity::baseEnergy);
        int first = -1;
        for (int w = 0; w < PLAN_WORDS; w++) {
            final int word = w;
            int index = track(be == null ? zero : () -> be.planWord(word));
            if (first < 0) first = index;
        }
        planIndex = first;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Server side: the table this menu is bound to, null on the client. */
    @Nullable
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

    /** Progress of the plot being built, 0-1. */
    public float progress() {
        return synced(progressIndex) / 1000.0F;
    }

    public boolean clearTerrain() {
        return (synced(flagsIndex) & 1) != 0;
    }

    public boolean running() {
        return (synced(flagsIndex) & 2) != 0;
    }

    public BuildStyle selectedStyle() {
        return BuildStyle.byOrdinal((synced(flagsIndex) >> 2) & 3);
    }

    public boolean styleUnlocked(BuildStyle style) {
        return ((synced(flagsIndex) >> 4) & (1 << style.ordinal())) != 0;
    }

    /** Base FE per block before the style factor. */
    public int baseEnergy() {
        return synced(feIndex);
    }

    /** Plot being built, or -1. */
    public int currentPlot() {
        return synced(currentIndex);
    }

    private int cell(int plot) {
        if (!Plots.valid(plot)) return 0;
        return (synced(planIndex + plot / 3) >>> (10 * (plot % 3))) & 0x3FF;
    }

    /** {@link Layout#EMPTY}, {@link Layout#QUEUED} or {@link Layout#BUILT}. */
    public int plotState(int plot) {
        return cell(plot) & 3;
    }

    public boolean planned(int plot) {
        return plotState(plot) != Layout.EMPTY;
    }

    public BuildStyle plotStyle(int plot) {
        return BuildStyle.byOrdinal((cell(plot) >> 2) & 3);
    }

    public int plotDoors(int plot) {
        return (cell(plot) >> 4) & 15;
    }

    public boolean plotNeedsWork(int plot) {
        return (cell(plot) & (1 << 8)) != 0;
    }

    /** True when some plot is queued or a built plot needs a re-pass. */
    public boolean hasWork() {
        for (int plot = 0; plot < Plots.COUNT; plot++) if (plotNeedsWork(plot)) return true;
        return false;
    }

    public boolean hasQueued() {
        for (int plot = 0; plot < Plots.COUNT; plot++) if (plotState(plot) == Layout.QUEUED) return true;
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ArchitectRegistry.ARCHITECT_TABLE.get()) && (table == null || table.canUse(player));
    }
}
