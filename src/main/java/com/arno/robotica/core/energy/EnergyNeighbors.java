package com.arno.robotica.core.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Cached FE capabilities of the six neighbours of one block (server side), for blocks that push into every face each
 * tick. Keep one per block entity; the caches follow block and capability changes by themselves.
 */
public final class EnergyNeighbors {
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<IEnergyStorage, Direction>[] caches = new BlockCapabilityCache[6];
    @Nullable
    private ServerLevel level;
    @Nullable
    private BlockPos pos;

    @Nullable
    public IEnergyStorage get(ServerLevel level, BlockPos pos, Direction dir) {
        if (this.level != level || !pos.equals(this.pos)) {
            java.util.Arrays.fill(caches, null);
            this.level = level;
            this.pos = pos.immutable();
        }
        BlockCapabilityCache<IEnergyStorage, Direction> cache = caches[dir.ordinal()];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, this.pos.relative(dir), dir.getOpposite());
            caches[dir.ordinal()] = cache;
        }
        return cache.getCapability();
    }
}
