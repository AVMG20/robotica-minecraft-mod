package com.arno.robotica.power.conduit;

import com.arno.robotica.power.block.ConduitBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One connected group of conduits. Holds the member positions, the lowest tier and the list of FE endpoints
 * (blocks touching a conduit that expose an FE capability on the touching face). The manager ticks it once per
 * server tick; {@link #tick} moves energy from endpoints that can extract to endpoints that can receive,
 * at most the lowest tier's rate in total.
 */
final class ConduitNetwork {
    /** An FE block next to a conduit. The cache keeps the capability lookup cheap and self-invalidating. */
    private record Endpoint(long pos, Direction side, BlockCapabilityCache<IEnergyStorage, Direction> cache) {
        IEnergyStorage storage() {
            return cache.getCapability();
        }
    }

    private record EndpointKey(long pos, Direction side) {}

    final LongList members = new LongArrayList();
    ConduitTier lowest = ConduitTier.GOLD;
    /** Set when a neighbouring block of a member changed in a way that may add or remove endpoints. */
    boolean endpointsDirty;

    private final List<Endpoint> endpoints = new ArrayList<>();
    /** Block seen at every endpoint position, so neighbour updates that change nothing relevant are ignored. */
    private final Long2ObjectMap<Block> endpointBlocks = new Long2ObjectOpenHashMap<>();
    private int rotation;

    boolean knowsEndpoint(long pos, Block block) {
        return endpointBlocks.get(pos) == block;
    }

    boolean hasEndpointAt(long pos) {
        return endpointBlocks.containsKey(pos);
    }

    int endpointCount() {
        return endpoints.size();
    }

    /** Rebuilds the endpoint list and writes the connection properties of every member for rendering. */
    void scan(ServerLevel level, ConduitManager.LevelData data) {
        endpointsDirty = false;
        endpoints.clear();
        endpointBlocks.clear();
        Set<EndpointKey> seen = new HashSet<>();
        for (int i = 0; i < members.size(); i++) {
            long memberLong = members.getLong(i);
            BlockPos pos = BlockPos.of(memberLong);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ConduitBlock)) continue;
            BlockState updated = state;
            for (Direction dir : Direction.values()) {
                BlockPos neighbour = pos.relative(dir);
                ConduitBlock.Conn conn = ConduitBlock.Conn.NONE;
                if (data.isConduit(neighbour.asLong())) {
                    conn = ConduitBlock.Conn.CONDUIT;
                } else if (level.isLoaded(neighbour)
                        && level.getCapability(Capabilities.EnergyStorage.BLOCK, neighbour, dir.getOpposite()) != null) {
                    conn = ConduitBlock.Conn.BLOCK;
                    if (seen.add(new EndpointKey(neighbour.asLong(), dir.getOpposite()))) {
                        endpoints.add(new Endpoint(neighbour.asLong(), dir.getOpposite(),
                                BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, neighbour, dir.getOpposite())));
                        endpointBlocks.put(neighbour.asLong(), level.getBlockState(neighbour).getBlock());
                    }
                }
                updated = updated.setValue(ConduitBlock.CONN.get(dir), conn);
            }
            if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
        }
    }

    /** The only per-tick work of a network. Cheap when nothing can move. */
    void tick() {
        int n = endpoints.size();
        if (n < 2) return;
        int remaining = lowest.rate();
        int start = rotation++ % n;
        for (int i = 0; i < n && remaining > 0; i++) {
            Endpoint source = endpoints.get((i + start) % n);
            IEnergyStorage from = source.storage();
            if (from == null || !from.canExtract()) continue;
            int available = from.extractEnergy(remaining, true);
            if (available <= 0) continue;
            for (int j = 0; j < n && available > 0 && remaining > 0; j++) {
                Endpoint target = endpoints.get((j + start) % n);
                if (target.pos == source.pos) continue;
                IEnergyStorage to = target.storage();
                if (to == null || !to.canReceive()) continue;
                int accepted = to.receiveEnergy(available, true);
                if (accepted <= 0) continue;
                int extracted = from.extractEnergy(accepted, false);
                if (extracted <= 0) break;
                int inserted = to.receiveEnergy(extracted, false);
                if (inserted < extracted) from.receiveEnergy(extracted - inserted, false);
                available -= inserted;
                remaining -= inserted;
            }
        }
    }
}
