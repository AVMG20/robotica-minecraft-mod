package com.arno.robotica.core.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/** FE transfer helpers for block entities (server side). */
public final class EnergyUtil {
    private EnergyUtil() {}

    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * Pushes up to {@code maxPerTick} FE in total from source into the neighbouring FE receivers: one budget shared by
     * all six faces, split evenly over the receivers (what one does not take goes to the next). The face that goes
     * first turns every tick, so remainders even out. Returns total sent.
     */
    public static int pushToNeighbors(ServerLevel level, BlockPos pos, MachineEnergyStorage source, int maxPerTick) {
        return pushToNeighbors(level, pos, source, maxPerTick, null);
    }

    /** As {@link #pushToNeighbors(ServerLevel, BlockPos, MachineEnergyStorage, int)}, with cached neighbour lookups. */
    public static int pushToNeighbors(ServerLevel level, BlockPos pos, MachineEnergyStorage source, int maxPerTick, @Nullable EnergyNeighbors cache) {
        int budget = Math.min(maxPerTick, source.getEnergyStored());
        if (budget <= 0) return 0;
        IEnergyStorage[] targets = new IEnergyStorage[6];
        int count = 0;
        int start = (int) Math.floorMod(level.getGameTime(), 6L);
        for (int i = 0; i < 6; i++) {
            Direction dir = DIRECTIONS[(start + i) % 6];
            IEnergyStorage target = cache != null ? cache.get(level, pos, dir)
                    : level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target != null && target.canReceive()) targets[count++] = target;
        }
        int left = budget;
        // each pass splits what is left over the receivers that took their whole share last time (at most 6 passes)
        while (left > 0 && count > 0) {
            int active = 0;
            for (int i = 0; i < count && left > 0; i++) {
                int share = Math.max(1, left / (count - i));
                int accepted = Math.min(share, targets[i].receiveEnergy(share, false));
                if (accepted > 0) left -= accepted;
                if (accepted >= share) targets[active++] = targets[i];
            }
            if (active == count) break;
            count = active;
        }
        int sent = budget - left;
        if (sent > 0) source.consume(sent);
        return sent;
    }

    /** Charges an FE item from a machine buffer at up to rate FE. Returns amount moved. */
    public static int chargeItem(MachineEnergyStorage source, ItemStack stack, int rate) {
        IEnergyStorage item = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (item == null || !item.canReceive()) return 0;
        int accepted = item.receiveEnergy(Math.min(rate, source.getEnergyStored()), false);
        if (accepted > 0) source.consume(accepted);
        return accepted;
    }

    /** Pulls up to rate FE out of a battery item (cell, Mainspring) into a machine buffer. Returns amount moved. */
    public static int dischargeItem(ItemStack stack, MachineEnergyStorage target, int rate) {
        IEnergyStorage item = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (item == null || !item.canExtract()) return 0;
        int extracted = item.extractEnergy(Math.min(rate, target.getSpace()), false);
        if (extracted > 0) target.generate(extracted);
        return extracted;
    }

    public static boolean isEnergyItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(Capabilities.EnergyStorage.ITEM) != null;
    }
}
