package com.arno.robotica.power.conduit;

import com.arno.robotica.power.block.ConduitBlock;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side conduit networks, one {@link LevelData} per level.
 * <ul>
 *   <li>Conduit block entities register themselves while their chunk is loaded ({@link #register}/{@link #unregister}).</li>
 *   <li>Placement, removal and chunk (un)loading only invalidate the affected network and mark its conduits dirty.</li>
 *   <li>At the end of the level tick the dirty conduits are flood-filled into new networks (rebuilt lazily, once).</li>
 *   <li>Every network is then ticked once. Conduits themselves never tick.</li>
 * </ul>
 */
public final class ConduitManager {
    private ConduitManager() {}

    static final class LevelData {
        final LongSet loaded = new LongOpenHashSet();
        final LongSet dirty = new LongOpenHashSet();
        final Long2ObjectMap<ConduitNetwork> byPos = new Long2ObjectOpenHashMap<>();
        final Set<ConduitNetwork> networks = new LinkedHashSet<>();

        boolean isConduit(long pos) {
            return loaded.contains(pos);
        }
    }

    private static final Map<Level, LevelData> DATA = new HashMap<>();

    private static LevelData data(ServerLevel level) {
        return DATA.computeIfAbsent(level, l -> new LevelData());
    }

    // ---- registration, called by ConduitBlockEntity ----

    public static void register(ServerLevel level, BlockPos pos) {
        LevelData d = data(level);
        long key = pos.asLong();
        d.loaded.add(key);
        d.dirty.add(key);
        for (Direction dir : Direction.values()) {
            ConduitNetwork neighbour = d.byPos.get(pos.relative(dir).asLong());
            if (neighbour != null) invalidate(d, neighbour);
        }
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        LevelData d = DATA.get(level);
        if (d == null) return;
        long key = pos.asLong();
        d.loaded.remove(key);
        d.dirty.remove(key);
        ConduitNetwork network = d.byPos.get(key);
        if (network != null) invalidate(d, network);
    }

    /**
     * A block next to a conduit changed. Ignored unless it can add or remove an endpoint: known endpoints and known
     * non-endpoint block entities (furnaces, chests) only trigger a rescan when their FE capability appears.
     */
    public static void neighborChanged(ServerLevel level, BlockPos conduit, BlockPos from, Block neighbourBlock) {
        LevelData d = DATA.get(level);
        if (d == null) return;
        ConduitNetwork network = d.byPos.get(conduit.asLong());
        if (network == null || network.endpointsDirty) return;
        long fromKey = from.asLong();
        if (network.hasEndpointAt(fromKey)) {
            if (network.knowsEndpoint(fromKey, neighbourBlock)) return;
        } else if (!(neighbourBlock instanceof EntityBlock)) {
            return;
        } else if (network.knowsNonEndpoint(fromKey, neighbourBlock)) {
            // Same non-endpoint as at the last scan: only rescan if it gained the FE capability since.
            Direction side = Direction.fromDelta(conduit.getX() - from.getX(), conduit.getY() - from.getY(), conduit.getZ() - from.getZ());
            if (side == null || level.getCapability(Capabilities.EnergyStorage.BLOCK, from, side) == null) return;
        }
        network.endpointsDirty = true;
    }

    private static void invalidate(LevelData d, ConduitNetwork network) {
        d.networks.remove(network);
        for (int i = 0; i < network.members.size(); i++) {
            long member = network.members.getLong(i);
            d.byPos.remove(member);
            if (d.loaded.contains(member)) d.dirty.add(member);
        }
    }

    // ---- ticking ----

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelData d = DATA.get(level);
        if (d == null) return;
        if (!d.dirty.isEmpty()) rebuild(level, d);
        for (ConduitNetwork network : d.networks) {
            if (network.endpointsDirty) network.scan(level, d);
            network.tick();
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) DATA.remove(level);
    }

    private static void rebuild(ServerLevel level, LevelData d) {
        long[] pending = d.dirty.toLongArray();
        d.dirty.clear();
        for (long start : pending) {
            if (!d.loaded.contains(start) || d.byPos.containsKey(start)) continue;
            ConduitNetwork network = new ConduitNetwork();
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            d.byPos.put(start, network);
            while (!queue.isEmpty()) {
                long current = queue.poll();
                BlockPos pos = BlockPos.of(current);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof ConduitBlock conduit)) {
                    d.byPos.remove(current);
                    d.loaded.remove(current);
                    continue;
                }
                network.members.add(current);
                if (conduit.tier().ordinal() < network.lowest.ordinal()) network.lowest = conduit.tier();
                for (Direction dir : Direction.values()) {
                    long next = pos.relative(dir).asLong();
                    if (d.loaded.contains(next) && !d.byPos.containsKey(next)) {
                        d.byPos.put(next, network);
                        queue.add(next);
                    }
                }
            }
            if (network.members.isEmpty()) continue;
            network.scan(level, d);
            d.networks.add(network);
        }
    }

    /** Test helper: number of conduits in the network containing pos, or 0. */
    public static int networkSize(ServerLevel level, BlockPos pos) {
        LevelData d = DATA.get(level);
        if (d == null) return 0;
        ConduitNetwork network = d.byPos.get(pos.asLong());
        return network == null ? 0 : network.members.size();
    }

    /** Test helper: whether the network at pos is waiting for an endpoint rescan. */
    public static boolean endpointsDirty(ServerLevel level, BlockPos pos) {
        LevelData d = DATA.get(level);
        if (d == null) return false;
        ConduitNetwork network = d.byPos.get(pos.asLong());
        return network != null && network.endpointsDirty;
    }
}
