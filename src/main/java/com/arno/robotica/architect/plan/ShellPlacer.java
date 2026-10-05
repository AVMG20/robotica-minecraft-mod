package com.arno.robotica.architect.plan;

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
                level.setBlock(origin.offset(op.x(), op.y(), op.z()), op.piece().resolve(layout.style(plot)), Block.UPDATE_CLIENTS);
            }
        }
    }
}
