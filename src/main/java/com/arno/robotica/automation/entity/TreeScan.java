package com.arno.robotica.automation.entity;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a whole tree: all logs connected through the 26 neighbours (diagonals included, so 2x2 trunks, acacia and
 * modded trees work through the log tags), plus the non-persistent leaves that hang on it.
 */
public final class TreeScan {
    public static final int MAX_LEAVES = 640;

    public final List<BlockPos> logs = new ArrayList<>();
    public final List<BlockPos> leaves = new ArrayList<>();
    /** Non-persistent leaf blocks touching a log. A log structure without them is not treated as a tree. */
    public int naturalLeaves;

    private TreeScan() {}

    public static boolean isLeafLike(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS);
    }

    public static boolean isNatural(BlockState state) {
        return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
    }

    public static TreeScan scan(Level level, BlockPos start, int maxLogs, boolean wantLeaves) {
        TreeScan tree = new TreeScan();
        LongOpenHashSet seen = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.asLong());
        queue.add(start);
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && tree.logs.size() < maxLogs) {
            BlockPos pos = queue.poll();
            tree.logs.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        n.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                        if (seen.contains(n.asLong()) || !level.isLoaded(n)) continue;
                        if (level.getBlockState(n).is(BlockTags.LOGS)) {
                            seen.add(n.asLong());
                            queue.add(n.immutable());
                        }
                    }
                }
            }
        }
        // Leaves touching the logs.
        LongOpenHashSet leafSeen = new LongOpenHashSet();
        ArrayDeque<BlockPos> leafQueue = new ArrayDeque<>();
        ArrayDeque<Integer> leafDepth = new ArrayDeque<>();
        for (BlockPos log : tree.logs) {
            for (Direction dir : Direction.values()) {
                BlockPos p = log.relative(dir);
                if (leafSeen.contains(p.asLong()) || !level.isLoaded(p)) continue;
                BlockState state = level.getBlockState(p);
                if (!isLeafLike(state) || !isNatural(state)) continue;
                leafSeen.add(p.asLong());
                tree.naturalLeaves++;
                if (wantLeaves) {
                    leafQueue.add(p);
                    leafDepth.add(1);
                }
            }
        }
        while (!leafQueue.isEmpty() && tree.leaves.size() < MAX_LEAVES) {
            BlockPos pos = leafQueue.poll();
            int depth = leafDepth.poll();
            BlockState state = level.getBlockState(pos);
            tree.leaves.add(pos);
            int distance = state.hasProperty(LeavesBlock.DISTANCE) ? state.getValue(LeavesBlock.DISTANCE) : -1;
            for (Direction dir : Direction.values()) {
                BlockPos p = pos.relative(dir);
                if (leafSeen.contains(p.asLong()) || !level.isLoaded(p)) continue;
                BlockState next = level.getBlockState(p);
                if (!isLeafLike(next) || !isNatural(next)) continue;
                boolean follows;
                if (distance >= 0 && next.hasProperty(LeavesBlock.DISTANCE)) {
                    // Only leaves held up by this one (distance grows away from the logs).
                    follows = next.getValue(LeavesBlock.DISTANCE) == distance + 1;
                } else {
                    follows = depth < 6;
                }
                if (follows) {
                    leafSeen.add(p.asLong());
                    leafQueue.add(p);
                    leafDepth.add(depth + 1);
                }
            }
        }
        return tree;
    }
}
