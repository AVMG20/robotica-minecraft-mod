package com.arno.robotica.replicator.logic;

import com.arno.robotica.replicator.ReplicatorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The 3x3x3 shape: the controller replaces the centre of one face, the other 25 shell blocks are frames or glass (at
 * least one glass) and the single block in the middle of the cube is air. A facing here is the controller's outward
 * side: the cube lies behind it.
 */
public final class ReplicatorStructure {
    private ReplicatorStructure() {}

    public enum Status {
        FORMED("formed"),
        /** A shell position holds something other than a frame, glass or this controller. */
        INCOMPLETE("incomplete"),
        NO_GLASS("no_glass"),
        CENTER_BLOCKED("center_blocked"),
        /** Part of the cube is in an unloaded chunk: keep the previous state. */
        UNLOADED("unloaded");

        public final String id;

        Status(String id) {
            this.id = id;
        }

        public boolean formed() {
            return this == FORMED;
        }

        /** Translation key of the status line in the GUI. */
        public String translationKey() {
            return "gui.robotica.replicator.status." + id;
        }
    }

    /**
     * One check of the cube behind {@code facing}: the status, the first wrong shell block (or the blocked centre) and
     * how many shell positions already hold a frame or glass.
     */
    public record Result(Status status, Direction facing, @Nullable BlockPos wrong, int parts) {}

    /** Centre of the cube the controller at {@code controller} belongs to. */
    public static BlockPos center(BlockPos controller, Direction facing) {
        return controller.relative(facing.getOpposite());
    }

    public static Status validate(Level level, BlockPos controller, Direction facing) {
        return check(level, controller, facing).status();
    }

    public static Result check(Level level, BlockPos controller, Direction facing) {
        BlockPos center = center(controller, facing);
        if (!level.hasChunksAt(center.offset(-1, -1, -1), center.offset(1, 1, 1))) return new Result(Status.UNLOADED, facing, null, 0);
        int glass = 0, parts = 0;
        BlockPos wrong = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.equals(controller)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ReplicatorRegistry.REPLICATOR_GLASS.get())) {
                        glass++;
                        parts++;
                    } else if (state.is(ReplicatorRegistry.REPLICATOR_FRAME.get())) {
                        parts++;
                    } else if (wrong == null) {
                        wrong = pos;
                    }
                }
            }
        }
        if (wrong != null) return new Result(Status.INCOMPLETE, facing, wrong, parts);
        if (!level.getBlockState(center).isAir()) return new Result(Status.CENTER_BLOCKED, facing, center, parts);
        return new Result(glass > 0 ? Status.FORMED : Status.NO_GLASS, facing, null, parts);
    }

    /**
     * Checks the cube behind {@code facing} (the side found last time) and, when that one does not form, the cubes on
     * the other five sides of the controller. Returns the cube that forms, else the one with the most frame and glass
     * blocks (the given facing on a tie), whose problem is the one to show.
     */
    public static Result find(Level level, BlockPos controller, Direction facing) {
        Result best = check(level, controller, facing);
        if (best.status() == Status.FORMED || best.status() == Status.UNLOADED) return best;
        for (Direction side : Direction.values()) {
            if (side == facing) continue;
            Result alt = check(level, controller, side);
            if (alt.status() == Status.FORMED) return alt;
            if (alt.status() != Status.UNLOADED && alt.parts() > best.parts()) best = alt;
        }
        return best;
    }
}
