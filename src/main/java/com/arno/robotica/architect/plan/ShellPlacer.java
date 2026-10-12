package com.arno.robotica.architect.plan;

import com.arno.robotica.architect.block.ConnectedPillarBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Places the shells of a plan at once, free of charge (showcase scenes and debug only, never reached by players). */
public final class ShellPlacer {
    private ShellPlacer() {}

    /** Builds every planned plot of {@code layout} around a (virtual) table at {@code table}; the table cell is left alone. */
    public static void placeAll(Level level, BlockPos table, Layout layout) {
        for (int plot = 0; plot < Plots.COUNT; plot++) {
            if (!layout.planned(plot)) continue;
            BlockPos origin = Plots.origin(table, plot);
            for (BlockOp op : Shell.generate(layout.shape(plot), plot == Plots.CENTER)) {
                BlockPos pos = origin.offset(op.x(), op.y(), op.z());
                level.setBlock(pos, ConnectedPillarBlock.placed(level, pos, op.piece().resolve(layout.style(plot))), Block.UPDATE_CLIENTS);
            }
        }
    }
}
