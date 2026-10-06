package com.arno.robotica.architect.plan;

import net.minecraft.core.BlockPos;

/**
 * Plot grid geometry. The table is the middle floor block of the centre plot: that plot spans table.x-4..table.x+4 and
 * table.z-4..table.z+4 with its floor at table.y, and the other plots continue on the same 9 block grid. A plot is 9x9
 * blocks and 6 high (floor layer 0, walls 1-4, roof 5; Height cards make it up to 12). Plot index = (pz + 2) * 5 + (px + 2) for px, pz in -2..2.
 */
public final class Plots {
    private Plots() {}

    public static final int SIZE = 9;
    /** Base height: floor, 4 wall layers, roof. Every Height card adds a wall layer, up to {@link #MAX_HEIGHT}. */
    public static final int HEIGHT = 6;
    public static final int MAX_HEIGHT = 12;
    public static final int GRID = 5;
    public static final int RADIUS = GRID / 2;
    public static final int COUNT = GRID * GRID;
    public static final int CENTER = RADIUS * GRID + RADIUS;

    // Sides: N is -z, E is +x, S is +z, W is -x. Side masks use bit (1 << side).
    public static final int N = 0, E = 1, S = 2, W = 3;
    public static final int[] DX = {0, 1, 0, -1};
    public static final int[] DZ = {-1, 0, 1, 0};

    // Corners: bit (1 << corner) in corner masks. NW is local (0, 0), NE (8, 0), SW (0, 8), SE (8, 8).
    public static final int NW = 0, NE = 1, SW = 2, SE = 3;

    public static int bit(int side) {
        return 1 << side;
    }

    public static int opposite(int side) {
        return (side + 2) & 3;
    }

    public static int index(int px, int pz) {
        return (pz + RADIUS) * GRID + (px + RADIUS);
    }

    public static int px(int index) {
        return index % GRID - RADIUS;
    }

    public static int pz(int index) {
        return index / GRID - RADIUS;
    }

    public static boolean valid(int index) {
        return index >= 0 && index < COUNT;
    }

    private static int at(int px, int pz) {
        return px < -RADIUS || px > RADIUS || pz < -RADIUS || pz > RADIUS ? -1 : index(px, pz);
    }

    /** Index of the neighbouring plot on a side, or -1 at the edge of the grid. */
    public static int neighbour(int index, int side) {
        return at(px(index) + DX[side], pz(index) + DZ[side]);
    }

    /** Index of the diagonal neighbour beyond a corner, or -1 at the edge of the grid. */
    public static int diagonal(int index, int corner) {
        return at(px(index) + cornerDx(corner), pz(index) + cornerDz(corner));
    }

    public static int cornerDx(int corner) {
        return (corner & 1) == 0 ? -1 : 1;
    }

    public static int cornerDz(int corner) {
        return (corner & 2) == 0 ? -1 : 1;
    }

    /** The two sides that meet at a corner: {x side, z side}. */
    public static int[] cornerSides(int corner) {
        return new int[]{(corner & 1) == 0 ? W : E, (corner & 2) == 0 ? N : S};
    }

    /** The side of a plot that faces the table (south for the table's own plot). */
    public static int sideTowardTable(int index) {
        int x = px(index), z = pz(index);
        if (x == 0 && z == 0) return S;
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? W : E;
        return z > 0 ? N : S;
    }

    public static int distance(int index) {
        return Math.abs(px(index)) + Math.abs(pz(index));
    }

    /** World position of local (0, 0, 0) of a plot. */
    public static BlockPos origin(BlockPos table, int index) {
        return table.offset(px(index) * SIZE - SIZE / 2, 0, pz(index) * SIZE - SIZE / 2);
    }

    /** True for the local cell the table occupies: the middle floor block of the centre plot. */
    public static boolean isTableCell(int index, int x, int y, int z) {
        return index == CENTER && x == SIZE / 2 && y == 0 && z == SIZE / 2;
    }
}
