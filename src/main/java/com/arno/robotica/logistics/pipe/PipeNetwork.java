package com.arno.robotica.logistics.pipe;

import com.arno.robotica.logistics.LogisticsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A group of linked item pipes and the Insert connections they reach. Built once by a walk over the loaded pipes and
 * shared by all of them; any pipe that is placed, removed, relinked, loaded or unloaded marks it invalid, and the next
 * pipe that needs it builds a fresh one. Nothing walks the network per tick.
 */
public final class PipeNetwork {
    /** An Insert connection: the pipe, its face, the inventory block behind it, and the face's priority. */
    public record Endpoint(ItemPipeBlockEntity pipe, Direction side, BlockPos target, PipePriority priority) {
        Endpoint(ItemPipeBlockEntity pipe, Direction side, BlockPos target) {
            this(pipe, side, target, pipe.priority(side));
        }
    }

    /** Highest priority first; the sort is stable, so walk or distance order stays within a priority. */
    private static final Comparator<Endpoint> BY_PRIORITY = Comparator.comparingInt(e -> e.priority().ordinal());

    private final List<Endpoint> destinations = new ArrayList<>();
    /** Closest First order per Extract pipe, built on first use and kept until the network is rebuilt. */
    private final Map<BlockPos, List<Endpoint>> nearest = new HashMap<>();
    private final Set<BlockPos> members = new HashSet<>();
    /** Priority group ends of each sorted endpoint list handed out, so a pull never re-scans them. */
    private final Map<List<Endpoint>, int[]> groupEnds = new IdentityHashMap<>();
    private int pipes;
    private boolean valid = true;

    private PipeNetwork() {}

    public boolean isValid() {
        return valid;
    }

    public void invalidate() {
        valid = false;
    }

    /** Insert connections, highest priority first, then in walk order (nearest to the pipe that built the network first). */
    public List<Endpoint> destinations() {
        return destinations;
    }

    public int size() {
        return pipes;
    }

    /** Insert connections, highest priority first, then by how many pipes away they are from {@code from}, its own faces first. */
    public List<Endpoint> byDistance(ServerLevel level, ItemPipeBlockEntity from) {
        return nearest.computeIfAbsent(from.getBlockPos(), pos -> {
            List<Endpoint> order = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(pos);
            seen.add(pos);
            while (!queue.isEmpty()) {
                BlockPos at = queue.poll();
                if (!(level.getBlockEntity(at) instanceof ItemPipeBlockEntity pipe) || pipe.isRemoved()) continue;
                for (Direction dir : Direction.values()) {
                    PipeConnection link = pipe.getBlockState().getValue(ItemPipeBlock.prop(dir));
                    BlockPos next = at.relative(dir);
                    if (link == PipeConnection.INSERT) order.add(new Endpoint(pipe, dir, next));
                    else if (link == PipeConnection.PIPE && members.contains(next) && seen.add(next)) queue.add(next);
                }
            }
            order.sort(BY_PRIORITY);
            return order;
        });
    }

    /**
     * For a list from {@link #destinations()} or {@link #byDistance}: the exclusive end index of each priority group,
     * in order. Computed once per list.
     */
    public int[] groupEnds(List<Endpoint> sorted) {
        return groupEnds.computeIfAbsent(sorted, list -> {
            int[] ends = new int[list.size()];
            int groups = 0;
            for (int i = 1; i <= list.size(); i++) {
                if (i == list.size() || list.get(i).priority() != list.get(i - 1).priority()) ends[groups++] = i;
            }
            return Arrays.copyOf(ends, groups);
        });
    }

    /** Walks the loaded pipes linked to {@code start}, gives each of them the new network and collects the Insert faces. */
    static PipeNetwork build(ServerLevel level, ItemPipeBlockEntity start) {
        PipeNetwork net = new PipeNetwork();
        ArrayDeque<ItemPipeBlockEntity> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start);
        seen.add(start.getBlockPos());
        int max = LogisticsConfig.maxPipes();
        while (!queue.isEmpty()) {
            ItemPipeBlockEntity pipe = queue.poll();
            pipe.joinNetwork(net);
            net.pipes++;
            net.members.add(pipe.getBlockPos());
            for (Direction dir : Direction.values()) {
                PipeConnection link = pipe.getBlockState().getValue(ItemPipeBlock.prop(dir));
                BlockPos at = pipe.getBlockPos().relative(dir);
                if (link == PipeConnection.INSERT) {
                    net.destinations.add(new Endpoint(pipe, dir, at));
                } else if (link == PipeConnection.PIPE && seen.size() < max && seen.add(at) && level.isLoaded(at)
                        && level.getBlockEntity(at) instanceof ItemPipeBlockEntity next && !next.isRemoved()) {
                    queue.add(next);
                }
            }
        }
        net.destinations.sort(BY_PRIORITY);
        return net;
    }
}
