package com.arno.robotica.gear.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure geometry of the box shaped area modes. No world access, usable on both sides and in tests. */
public final class AreaShape {
    private AreaShape() {}

    /**
     * All positions of the box for a hit block, origin included, nearest to the origin first.
     * Width and height lie in the plane of the hit face and are centred on the origin (even sizes extend one block
     * further in the positive direction). On a wall the box instead starts one row below the origin, so aiming at eye
     * level keeps the floor and aiming at the bottom row digs one deeper. Depth runs from the origin straight into the
     * block, away from the player.
     */
    public static List<BlockPos> positions(BlockPos origin, Direction face, AreaMode mode) {
        List<BlockPos> out = new ArrayList<>();
        if (!mode.isBox()) {
            out.add(origin);
            return out;
        }
        Direction u;
        Direction v;
        switch (face.getAxis()) {
            case Y -> {
                u = Direction.EAST;
                v = Direction.SOUTH;
            }
            case X -> {
                u = Direction.SOUTH;
                v = Direction.UP;
            }
            default -> {
                u = Direction.EAST;
                v = Direction.UP;
            }
        }
        Direction in = face.getOpposite();
        int jLo = face.getAxis().isHorizontal() ? -1 : lo(mode.height);
        int jHi = jLo + mode.height - 1;
        for (int k = 0; k < mode.depth; k++) {
            for (int j = jLo; j <= jHi; j++) {
                for (int i = lo(mode.width); i <= hi(mode.width); i++) {
                    BlockPos p = origin.offset(
                            u.getStepX() * i + v.getStepX() * j + in.getStepX() * k,
                            u.getStepY() * i + v.getStepY() * j + in.getStepY() * k,
                            u.getStepZ() * i + v.getStepZ() * j + in.getStepZ() * k);
                    out.add(p);
                }
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return out;
    }

    private static int lo(int size) {
        return -((size - 1) / 2);
    }

    private static int hi(int size) {
        return size / 2;
    }
}
