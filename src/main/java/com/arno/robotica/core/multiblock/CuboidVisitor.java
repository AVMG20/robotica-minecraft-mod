package com.arno.robotica.core.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Structure specific part of a scan. A fresh visitor is used per scan, so it can collect members (ports, rods,
 * capacitors) while the scanner walks the box. Returning a problem stops the scan there: it is the "first wrong block".
 * Order: all shell positions (frame and walls, y then z then x), then the interior (same order), then {@link #finish}.
 */
public interface CuboidVisitor {
    /** Every wall position that passed the spec's wall rule (the controller is skipped). {@code outward} points out of the box. */
    @Nullable
    default StructureProblem wall(BlockPos pos, BlockState state, Direction outward) {
        return null;
    }

    /** Every interior position. */
    @Nullable
    StructureProblem interior(BlockPos pos, BlockState state, BoundingBox box);

    /** After every block passed: rules about the whole (at least one rod, one port, ...). */
    @Nullable
    default StructureProblem finish(BoundingBox box) {
        return null;
    }
}
