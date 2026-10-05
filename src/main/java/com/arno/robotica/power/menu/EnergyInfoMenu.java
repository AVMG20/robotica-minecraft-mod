package com.arno.robotica.power.menu;

import com.arno.robotica.core.menu.MachineMenu;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;

import java.util.function.IntSupplier;

/**
 * Read-only status screen for blocks that only store or make energy (Accumulators, Solar Panels): stored, capacity,
 * net FE/t over the last second, max I/O and a state flag. No slots.
 */
public class EnergyInfoMenu extends MachineMenu {
    /** What the block is, so the screen can pick its status words. */
    public static final int KIND_STORAGE = 0, KIND_SOLAR = 1;

    public interface Source {
        int stored();

        int capacity();

        /** Net change per tick over the last second (negative when draining), or current output for generators. */
        int rate();

        int maxIo();

        /** Storage: unused. Solar: 1 when the sun reaches it. */
        int flag();
    }

    private final ContainerLevelAccess access;
    private final Block block;
    private final int kind;
    private final int storedIdx, capacityIdx, rateIdx, ioIdx, flagIdx;

    /** Client side: the kind follows from the block at the position. */
    public EnergyInfoMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, inv.player.level().getBlockState(pos).getBlock() instanceof com.arno.robotica.power.block.SolarPanelBlock
                ? KIND_SOLAR : KIND_STORAGE, null);
    }

    /** Server side. */
    public EnergyInfoMenu(int id, Inventory inv, BlockPos pos, int kind, Source src) {
        super(PowerRegistry.ENERGY_INFO_MENU.get(), id);
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        this.block = inv.player.level().getBlockState(pos).getBlock();
        this.kind = kind;
        storedIdx = track(src == null ? zero() : src::stored);
        capacityIdx = track(src == null ? zero() : src::capacity);
        rateIdx = track(src == null ? zero() : src::rate);
        ioIdx = track(src == null ? zero() : src::maxIo);
        flagIdx = track(src == null ? zero() : src::flag);
    }

    private static IntSupplier zero() {
        return () -> 0;
    }

    public int kind() {
        return kind;
    }

    public int stored() {
        return synced(storedIdx);
    }

    public int capacity() {
        return synced(capacityIdx);
    }

    public int rate() {
        return synced(rateIdx);
    }

    public int maxIo() {
        return synced(ioIdx);
    }

    public boolean flag() {
        return synced(flagIdx) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, block);
    }
}
