package com.arno.robotica.energy.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Formed look of a cuboid shell, shared by the energy multiblocks and the Mob Replicator: frame beams, corner caps
 * and wall panels on its {@link FramedPartBlock}s and {@link StructureGlassBlock}s joined into the frame, or back to
 * the loose blocks. Sent to clients without neighbour updates, so it never wakes a
 * {@link com.arno.robotica.core.multiblock.MultiblockWatcher}. scripts/scene_preview.py draws the same look.
 */
public final class StructureLook {
    private StructureLook() {}

    /** Dresses (or undresses) the shell of {@code box}. Returns false when a part of it was not loaded (and so kept its state). */
    public static boolean dress(Level level, BoundingBox box, boolean on) {
        boolean all = true;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            boolean ex = x == box.minX() || x == box.maxX();
            for (int y = box.minY(); y <= box.maxY(); y++) {
                boolean ey = y == box.minY() || y == box.maxY();
                for (int z = box.minZ(); z <= box.maxZ(); z = ex || ey || z == box.maxZ() ? z + 1 : box.maxZ()) {
                    boolean ez = z == box.minZ() || z == box.maxZ();
                    p.set(x, y, z);
                    if (!level.isLoaded(p)) {
                        all = false;
                        continue;
                    }
                    BlockState state = level.getBlockState(p);
                    BlockState next = state;
                    if (state.getBlock() instanceof FramedPartBlock) {
                        next = state.setValue(FramedPartBlock.FRAME, on ? frameShape(ex, ey, ez) : FramedPartBlock.Shape.NONE);
                    } else if (state.getBlock() instanceof StructureGlassBlock glass) {
                        next = glass.withFormed(level, p, state, on);
                    }
                    if (next != state) level.setBlock(p, next, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        return all;
    }

    /** Corner where three box faces meet, a beam along the one axis that is not on a face, else a wall. */
    public static FramedPartBlock.Shape frameShape(boolean ex, boolean ey, boolean ez) {
        int faces = (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0);
        if (faces == 3) return FramedPartBlock.Shape.CORNER;
        if (faces == 2) return !ex ? FramedPartBlock.Shape.X : !ey ? FramedPartBlock.Shape.Y : FramedPartBlock.Shape.Z;
        return FramedPartBlock.Shape.WALL;
    }
}
