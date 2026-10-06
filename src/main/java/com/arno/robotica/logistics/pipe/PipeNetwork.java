package com.arno.robotica.logistics.pipe;

import com.arno.robotica.logistics.LogisticsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A group of linked item pipes and the Insert connections they reach. Built once by a walk over the loaded pipes and
 * shared by all of them; any pipe that is placed, removed, relinked, loaded or unloaded marks it invalid, and the next
 * pipe that needs it builds a fresh one. Nothing walks the network per tick.
 */
public final class PipeNetwork {
    /** An Insert connection: the pipe, its face, and the inventory block behind it. */
    public record Endpoint(ItemPipeBlockEntity pipe, Direction side, BlockPos target) {}

    private final List<Endpoint> destinations = new ArrayList<>();
    private int pipes;
    private boolean valid = true;

    private PipeNetwork() {}

    public boolean isValid() {
        return valid;
    }

    public void invalidate() {
        valid = false;
    }

    /** Insert connections in walk order (nearest to the pipe that built the network first). */
    public List<Endpoint> destinations() {
        return destinations;
    }

    public int size() {
        return pipes;
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
        return net;
    }
}
