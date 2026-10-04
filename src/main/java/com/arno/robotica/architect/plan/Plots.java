package com.arno.robotica.architect.plan;

import net.minecraft.core.BlockPos;

/**
 * Plot grid geometry. The table sits in the middle plot at floor level; a plot is 9x9 blocks and 6 high
 * (floor layer at the table's Y, interior above, roof on layer 5). Plot index = (pz + 2) * 5 + (px + 2) for px, pz in -2..2.
 */
public final class Plots {
    private Plots() {}

    public static final int SIZE = 9;
    public static final int HEIGHT = 6;
    public static final int GRID = 5;
    public static final int COUNT = GRID * GRID;
    public static final int CENTER = (GRID / 2) * GRID + GRID / 2;

    // Sides: N is -z, E is +x, S is +z, W is -x. Door masks use bit (1 << side).
    public static final int N = 0, E = 1, S = 2, W = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DZ = {-1, 0, 1, 0};

    public static int bit(int side) {
        return 1 << side;
    }

    public static int opposite(int side) {
        return (side + 2) & 3;
    }

    public static int index(int px, int pz) {
        return (pz + 2) * GRID + (px + 2);
    }

    public static int px(int index) {
        return index % GRID - 2;
    }

    public static int pz(int index) {
        return index / GRID - 2;
    }

    public static boolean valid(int index) {
        return index >= 0 && index < COUNT;
    }

    /** Index of the neighbouring plot on a side, or -1 at the edge of the grid. */
    public static int neighbour(int index, int side) {
        int x = px(index) + DX[side];
        int z = pz(index) + DZ[side];
        if (x < -2 || x > 2 || z < -2 || z > 2) return -1;
        return index(x, z);
    }

    /** World position of local (0, 0, 0) of a plot. */
    public static BlockPos origin(BlockPos table, int index) {
        return table.offset(px(index) * SIZE - SIZE / 2, 0, pz(index) * SIZE - SIZE / 2);
    }

    /** Local cell the table occupies when it is in this plot (only the centre plot), else null. */
    public static boolean isTableCell(int index, int x, int y, int z) {
        return index == CENTER && x == SIZE / 2 && y == 0 && z == SIZE / 2;
    }
}
