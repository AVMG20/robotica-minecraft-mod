package com.arno.robotica.core.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** FE transfer helpers for block entities (server side). */
public final class EnergyUtil {
    private EnergyUtil() {}

    /** Pushes up to maxPerSide FE from source into every neighbouring FE receiver. Returns total sent. */
    public static int pushToNeighbors(ServerLevel level, BlockPos pos, MachineEnergyStorage source, int maxPerSide) {
        int sent = 0;
        for (Direction dir : Direction.values()) {
            if (source.getEnergyStored() <= 0) break;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.relative(dir), dir.getOpposite());
            if (target == null || !target.canReceive()) continue;
            int offer = Math.min(maxPerSide, source.getEnergyStored());
            int accepted = target.receiveEnergy(offer, false);
            if (accepted > 0) {
                source.consume(accepted);
                sent += accepted;
            }
        }
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
