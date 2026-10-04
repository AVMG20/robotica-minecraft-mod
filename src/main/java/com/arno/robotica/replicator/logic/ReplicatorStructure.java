package com.arno.robotica.replicator.logic;

import com.arno.robotica.replicator.ReplicatorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The 3x3x3 shape: the controller replaces the centre of one face (facing outward), the other 25 shell blocks are
 * frames or glass (at least one glass) and the single block in the middle of the cube is air.
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

    /** Centre of the cube the controller at {@code controller} belongs to. */
    public static BlockPos center(BlockPos controller, Direction facing) {
        return controller.relative(facing.getOpposite());
    }

    public static Status validate(Level level, BlockPos controller, Direction facing) {
        BlockPos center = center(controller, facing);
        if (!level.hasChunksAt(center.offset(-1, -1, -1), center.offset(1, 1, 1))) return Status.UNLOADED;
        int glass = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.equals(controller)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ReplicatorRegistry.REPLICATOR_GLASS.get())) glass++;
                    else if (!state.is(ReplicatorRegistry.REPLICATOR_FRAME.get())) return Status.INCOMPLETE;
                }
            }
        }
        if (!level.getBlockState(center).isAir()) return Status.CENTER_BLOCKED;
        return glass > 0 ? Status.FORMED : Status.NO_GLASS;
    }
}
