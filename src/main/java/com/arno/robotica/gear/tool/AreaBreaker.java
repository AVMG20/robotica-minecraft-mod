package com.arno.robotica.gear.tool;

import com.arno.robotica.gear.GearConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out which extra blocks an area, vein or tree break would remove. Pure read-only world queries, so the client
 * can use it for the outline and the server for the real break.
 */
public final class AreaBreaker {
    private AreaBreaker() {}

    /** More blocks than this go through the per-player server queue. */
    public static final int QUEUE_THRESHOLD = 27;
    public static final int VEIN_LIMIT = 64;
    private static final int LEAF_LIMIT = 256;
    private static final int LEAF_DEPTH = 6;

    /** Extra blocks (origin excluded) the tool would break when {@code origin} is mined from {@code face}. */
    public static List<BlockPos> collect(Level level, Player player, ItemStack stack, GearToolItem tool, BlockPos origin, Direction face) {
        AreaMode mode = tool.activeMode(stack, player);
        BlockState originState = level.getBlockState(origin);
        return switch (mode) {
            case SINGLE -> List.of();
            case TREE -> tree(level, stack, tool, origin, originState);
            case VEIN -> vein(level, stack, tool, origin, originState);
            default -> box(level, player, stack, tool, origin, originState, face, mode);
        };
    }

    private static List<BlockPos> box(Level level, Player player, ItemStack stack, GearToolItem tool, BlockPos origin,
                                      BlockState originState, Direction face, AreaMode mode) {
        float originHardness = originState.getDestroySpeed(level, origin);
        double limit = Math.max(originHardness, 0.5F) * GearConfig.hardnessRatio();
        boolean keepFloor = ToolSettings.has(stack, ToggleKind.KEEP_FLOOR);
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : AreaShape.positions(origin, face, mode, player.blockPosition().getY(), keepFloor)) {
            if (p.equals(origin) || !level.isLoaded(p) || !level.isInWorldBounds(p)) continue;
            if (breakable(level, p, level.getBlockState(p), stack, tool, limit)) out.add(p);
        }
        return out;
    }

    /** Hardness, tool and block entity filter shared by all modes. */
    public static boolean breakable(Level level, BlockPos pos, BlockState state, ItemStack stack, GearToolItem tool, double hardnessLimit) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > hardnessLimit) return false;
        if (!tool.canMine(state)) return false;
        if (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(stack, state)) return false;
        return level.getBlockEntity(pos) == null || GearConfig.areaBreaksBlockEntities();
    }

    private static List<BlockPos> vein(Level level, ItemStack stack, GearToolItem tool, BlockPos origin, BlockState originState) {
        if (!originState.is(Tags.Blocks.ORES)) return List.of();
        Set<BlockPos> found = new LinkedHashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        found.add(origin);
        open.add(origin);
        while (!open.isEmpty() && found.size() < VEIN_LIMIT) {
            BlockPos cur = open.poll();
            for (BlockPos n : BlockPos.betweenClosed(cur.offset(-1, -1, -1), cur.offset(1, 1, 1))) {
                if (found.size() >= VEIN_LIMIT) break;
                if (!level.isLoaded(n) || found.contains(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.is(originState.getBlock()) && breakable(level, n, s, stack, tool, Double.MAX_VALUE)) {
                    BlockPos fixed = n.immutable();
                    found.add(fixed);
                    open.add(fixed);
                }
            }
        }
        return sortedWithoutOrigin(found, origin);
    }

    private static List<BlockPos> tree(Level level, ItemStack stack, GearToolItem tool, BlockPos origin, BlockState originState) {
        if (!originState.is(BlockTags.LOGS)) return List.of();
        int max = tool.spec.maxLogs;
        Set<BlockPos> logs = new LinkedHashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        logs.add(origin);
        open.add(origin);
        boolean natural = false;
        while (!open.isEmpty()) {
            BlockPos cur = open.poll();
            for (BlockPos n : BlockPos.betweenClosed(cur.offset(-1, -1, -1), cur.offset(1, 1, 1))) {
                if (!level.isLoaded(n) || logs.contains(n)) continue;
                BlockState s = level.getBlockState(n);
                if (s.is(BlockTags.LOGS)) {
                    if (logs.size() < max) {
                        BlockPos fixed = n.immutable();
                        logs.add(fixed);
                        open.add(fixed);
                    }
                } else if (isNaturalLeaf(s)) {
                    natural = true;
                }
            }
        }
        // Without natural leaves this is a log building, not a tree: only the hit log breaks.
        if (!natural) return List.of();
        List<BlockPos> out = new ArrayList<>(sortedWithoutOrigin(logs, origin));
        if (tool.toggleActive(stack, ToggleKind.LEAVES)) {
            out.addAll(sortedWithoutOrigin(leaves(level, logs), origin));
        }
        return out;
    }

    private static Set<BlockPos> leaves(Level level, Set<BlockPos> logs) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Set<BlockPos> seen = new HashSet<>(logs);
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        ArrayDeque<Integer> depth = new ArrayDeque<>();
        for (BlockPos log : logs) {
            open.add(log);
            depth.add(0);
        }
        while (!open.isEmpty() && found.size() < LEAF_LIMIT) {
            BlockPos cur = open.poll();
            int d = depth.poll();
            if (d >= LEAF_DEPTH) continue;
            for (Direction dir : Direction.values()) {
                BlockPos n = cur.relative(dir);
                if (!seen.add(n) || !level.isLoaded(n)) continue;
                if (isNaturalLeaf(level.getBlockState(n))) {
                    BlockPos fixed = n.immutable();
                    found.add(fixed);
                    open.add(fixed);
                    depth.add(d + 1);
                }
            }
        }
        return found;
    }

    private static boolean isNaturalLeaf(BlockState s) {
        if (s.is(BlockTags.WART_BLOCKS)) return true;
        return s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT));
    }

    private static List<BlockPos> sortedWithoutOrigin(Set<BlockPos> set, BlockPos origin) {
        List<BlockPos> list = new ArrayList<>(set);
        list.remove(origin);
        list.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return list;
    }

    /** Face of the block the player is hitting, derived from the player's position (fallback when the real face is unknown). */
    public static Direction faceFromPosition(Player player, BlockPos pos) {
        Vec3 d = player.getEyePosition().subtract(Vec3.atCenterOf(pos));
        return Direction.getNearest(d.x, d.y, d.z);
    }
}
