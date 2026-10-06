package com.arno.robotica.core.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Finds and checks a cuboid multiblock from its controller. The controller sits in a side wall with its front facing
 * out ({@code outward}, a horizontal direction). The scan:
 * <ol>
 *   <li>walks from the controller along the wall, left/right and up/down, over shell blocks: that gives the width and
 *       height of the front wall;</li>
 *   <li>walks from the front edge back along a side wall: that gives the depth;</li>
 *   <li>checks every position of the box: edges and corners against the frame rule, faces against the wall rule,
 *       the inside through the {@link CuboidVisitor}; the first position that fails is the reported problem.</li>
 * </ol>
 * A walk that stops at a wrong block or a gap with more wall behind it reports that block instead of a size error.
 * If the controller's front faces into the structure, the other direction is tried as well. Cost: one pass over the
 * box (at most a few hundred block reads); callers re-scan only when something inside changed (see {@link MultiblockWatcher}).
 */
public final class CuboidScanner {
    private CuboidScanner() {}

    public enum Status { FORMED, INVALID, UNLOADED }

    /**
     * @param box     the box that was checked (also when invalid), null when no box could be found
     * @param visitor the visitor of the scan that produced this result (holds what it collected)
     */
    public record Result(Status status, @Nullable BoundingBox box, @Nullable StructureProblem problem, @Nullable CuboidVisitor visitor) {
        public boolean formed() {
            return status == Status.FORMED;
        }
    }

    public static Result scan(Level level, BlockPos controller, Direction outward, CuboidSpec spec, java.util.function.Supplier<? extends CuboidVisitor> visitors) {
        Result first = scanFacing(level, controller, outward, spec, visitors.get());
        if (first.formed() || first.status == Status.UNLOADED || first.box != null) return first;
        // Placed from the inside: the front faces into the box. Try the other way round before giving up.
        Result second = scanFacing(level, controller, outward.getOpposite(), spec, visitors.get());
        return second.box != null ? second : first;
    }

    private static Result scanFacing(Level level, BlockPos controller, Direction outward, CuboidSpec spec, CuboidVisitor visitor) {
        if (!outward.getAxis().isHorizontal()) outward = Direction.NORTH;
        Direction inward = outward.getOpposite();
        Direction right = outward.getClockWise();
        Direction left = right.getOpposite();
        int maxW = spec.maxWidth().getAsInt(), minW = spec.minWidth().getAsInt();
        int maxH = spec.maxHeight().getAsInt(), minH = spec.minHeight().getAsInt();

        Walk r = walk(level, controller, right, maxW, spec);
        Walk l = walk(level, controller, left, maxW, spec);
        Walk u = walk(level, controller, Direction.UP, maxH, spec);
        Walk d = walk(level, controller, Direction.DOWN, maxH, spec);
        if (r.unloaded || l.unloaded || u.unloaded || d.unloaded) return new Result(Status.UNLOADED, null, null, null);
        // A gap or a stranger in the wall line through the controller: name it right away.
        for (Walk w : new Walk[]{r, l, u, d}) {
            if (w.gap != null) return invalid(null, w.gap);
        }
        if (r.run == 0 && l.run == 0 && u.run == 0 && d.run == 0) {
            return invalid(null, StructureProblem.of(null, "multiblock.robotica.alone", spec.structureName(), spec.frameName()));
        }
        if (r.run == 0 || l.run == 0 || u.run == 0 || d.run == 0) {
            BlockPos stop = r.run == 0 ? r.stop : l.run == 0 ? l.stop : u.run == 0 ? u.stop : d.stop;
            return invalid(null, StructureProblem.of(stop, "multiblock.robotica.controller_edge", StructureProblem.at(controller)));
        }
        int width = r.run + l.run + 1;
        int height = u.run + d.run + 1;
        if (width > maxW) return invalid(null, StructureProblem.of(r.stop, "multiblock.robotica.too_wide", maxW));
        if (height > maxH) return invalid(null, StructureProblem.of(u.stop, "multiblock.robotica.too_tall", maxH));
        if (width < minW) return invalid(null, StructureProblem.of(r.stop, "multiblock.robotica.too_narrow", width, minW));
        if (height < minH) return invalid(null, StructureProblem.of(u.stop, "multiblock.robotica.too_low", height, minH));

        // Depth: from the front edge on the right, walk back along the right side wall.
        BlockPos edge = controller.relative(right, r.run);
        Walk back = walk(level, edge, inward, maxW, spec);
        if (back.unloaded) return new Result(Status.UNLOADED, null, null, null);
        if (back.gap != null) return invalid(null, back.gap);
        int depth = back.run + 1;
        if (depth > maxW) return invalid(null, StructureProblem.of(back.stop, "multiblock.robotica.too_deep", maxW));
        if (depth < minW) {
            return invalid(null, back.run == 0
                    ? StructureProblem.of(edge.relative(inward), "multiblock.robotica.no_side_wall", StructureProblem.at(edge.relative(inward)))
                    : StructureProblem.of(back.stop, "multiblock.robotica.too_shallow", depth, minW));
        }

        BlockPos a = controller.relative(left, l.run).relative(Direction.DOWN, d.run);
        BlockPos b = controller.relative(right, r.run).relative(Direction.UP, u.run).relative(inward, depth - 1);
        BoundingBox box = BoundingBox.fromCorners(a, b);
        if (!level.hasChunksAt(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            return new Result(Status.UNLOADED, box, null, null);
        }
        StructureProblem problem = check(level, controller, box, spec, visitor);
        return problem == null ? new Result(Status.FORMED, box, null, visitor) : new Result(Status.INVALID, box, problem, visitor);
    }

    private static Result invalid(@Nullable BoundingBox box, StructureProblem problem) {
        return new Result(Status.INVALID, box, problem, null);
    }

    /** Shell first, then the interior, then the visitor's whole-structure rules. */
    @Nullable
    private static StructureProblem check(Level level, BlockPos controller, BoundingBox box, CuboidSpec spec, CuboidVisitor visitor) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    int faces = faces(box, x, y, z);
                    if (faces == 0) continue;
                    p.set(x, y, z);
                    BlockState state = level.getBlockState(p);
                    StructureProblem problem;
                    if (faces >= 2) {
                        problem = checkFrame(p, state, spec);
                    } else if (p.equals(controller)) {
                        continue;
                    } else {
                        problem = checkWall(p, state, spec);
                        if (problem == null) problem = visitor.wall(p.immutable(), state, outward(box, x, y, z));
                    }
                    if (problem != null) return problem;
                }
            }
        }
        for (int y = box.minY() + 1; y < box.maxY(); y++) {
            for (int z = box.minZ() + 1; z < box.maxZ(); z++) {
                for (int x = box.minX() + 1; x < box.maxX(); x++) {
                    p.set(x, y, z);
                    StructureProblem problem = visitor.interior(p.immutable(), level.getBlockState(p), box);
                    if (problem != null) return problem;
                }
            }
        }
        return visitor.finish(box);
    }

    @Nullable
    private static StructureProblem checkFrame(BlockPos pos, BlockState state, CuboidSpec spec) {
        if (spec.frame().test(state)) return null;
        if (state.isAir()) return StructureProblem.of(pos, "multiblock.robotica.frame_missing", StructureProblem.at(pos), spec.frameName());
        if (spec.wall().test(state) || spec.controller().test(state)) {
            return StructureProblem.of(pos, "multiblock.robotica.frame_wall_block", StructureProblem.name(state), StructureProblem.at(pos), spec.frameName());
        }
        return StructureProblem.of(pos, "multiblock.robotica.frame_wrong", StructureProblem.name(state), StructureProblem.at(pos), spec.frameName());
    }

    @Nullable
    private static StructureProblem checkWall(BlockPos pos, BlockState state, CuboidSpec spec) {
        if (spec.controller().test(state)) return StructureProblem.of(pos, "multiblock.robotica.second_controller", StructureProblem.at(pos));
        if (spec.wall().test(state)) return null;
        if (state.isAir()) return StructureProblem.of(pos, "multiblock.robotica.wall_missing", StructureProblem.at(pos), spec.wallName());
        return StructureProblem.of(pos, "multiblock.robotica.wall_wrong", StructureProblem.name(state), StructureProblem.at(pos), spec.wallName());
    }

    /** How many box faces a position lies on: 0 inside, 1 wall, 2 edge, 3 corner. */
    public static int faces(BoundingBox box, int x, int y, int z) {
        int n = 0;
        if (x == box.minX() || x == box.maxX()) n++;
        if (y == box.minY() || y == box.maxY()) n++;
        if (z == box.minZ() || z == box.maxZ()) n++;
        return n;
    }

    /** The outward direction of a wall position (exactly one face). */
    public static Direction outward(BoundingBox box, int x, int y, int z) {
        if (x == box.minX()) return Direction.WEST;
        if (x == box.maxX()) return Direction.EAST;
        if (y == box.minY()) return Direction.DOWN;
        if (y == box.maxY()) return Direction.UP;
        if (z == box.minZ()) return Direction.NORTH;
        return Direction.SOUTH;
    }

    /**
     * Walk result: {@code run} shell blocks after the start; {@code stop} the first position that is not shell;
     * {@code gap} a problem when that stop has more shell right behind it (a hole or a stranger in the wall).
     */
    private record Walk(int run, BlockPos stop, @Nullable StructureProblem gap, boolean unloaded) {}

    private static Walk walk(Level level, BlockPos from, Direction dir, int max, CuboidSpec spec) {
        BlockPos.MutableBlockPos p = from.mutable();
        int run = 0;
        while (run < max) {
            p.move(dir);
            if (!level.isLoaded(p)) return new Walk(run, p.immutable(), null, true);
            if (!spec.isShell(level.getBlockState(p))) break;
            run++;
        }
        BlockPos stop = p.immutable();
        if (run >= max) return new Walk(run, stop, null, false);
        BlockPos behind = stop.relative(dir);
        if (level.isLoaded(behind) && spec.isShell(level.getBlockState(behind))) {
            BlockState state = level.getBlockState(stop);
            StructureProblem gap = state.isAir()
                    ? StructureProblem.of(stop, "multiblock.robotica.gap", StructureProblem.at(stop))
                    : StructureProblem.of(stop, "multiblock.robotica.stranger", StructureProblem.name(state), StructureProblem.at(stop));
            return new Walk(run, stop, gap, false);
        }
        return new Walk(run, stop, null, false);
    }
}
