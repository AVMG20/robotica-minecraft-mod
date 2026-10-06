package com.arno.robotica.architect.plan;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * The one building the table makes: a 9x9 shell, 6 high (up to 12 with Height cards). Generation is a pure function of
 * the shape, so the table can resume a build from a saved cursor and re-walk a finished building when its neighbours
 * change.
 * <pre>
 * layer 0    floor, with a trim line along every outside wall
 * 1..top-1   walls with window bands on layers 2-3, 6-7 (every 4 layers); doors are 3 wide, 3 high, framed by posts
 *            and a lintel
 * top        roof with light panels on a diagonal lattice (3 blocks apart across all plots), trim along the outside edge
 * </pre>
 * A side shared with a planned neighbour has no wall at all, so joined plots form one room. Corner posts only stand at
 * outer corners; a corner where an outside wall continues into the neighbour is plain wall, a corner inside a block of
 * four plots is open, and the inner corner of an L closes the wall.
 */
public final class Shell {
    private Shell() {}

    private static final int S = Plots.SIZE;
    /** Layer of the door lintel: doors are always 3 high. */
    private static final int LINTEL = 4;

    /**
     * @param sides     sides with a planned neighbour (bit per side)
     * @param corners   corners whose diagonal neighbour is planned too (only counts where both sides are planned)
     * @param doors     doors in outside walls (bit per side; ignored on shared sides)
     * @param oddPlot   (px + pz) is odd: shifts the light lattice so it stays regular across plots
     * @param height    layers including floor and roof, {@link Plots#HEIGHT} to {@link Plots#MAX_HEIGHT}
     */
    public record Shape(int sides, int corners, int doors, boolean oddPlot, int height) {
        public Shape(int sides, int corners, int doors, boolean oddPlot) {
            this(sides, corners, doors, oddPlot, Plots.HEIGHT);
        }

        public Shape {
            height = Math.max(Plots.HEIGHT, Math.min(Plots.MAX_HEIGHT, height));
            sides &= 15;
            doors &= 15 & ~sides;
            int c = 0;
            for (int k = 0; k < 4; k++) {
                int[] cs = Plots.cornerSides(k);
                if ((corners & (1 << k)) != 0 && (sides & Plots.bit(cs[0])) != 0 && (sides & Plots.bit(cs[1])) != 0) c |= 1 << k;
            }
            corners = c;
        }

        public boolean shared(int side) {
            return (sides & Plots.bit(side)) != 0;
        }

        public boolean door(int side) {
            return (doors & Plots.bit(side)) != 0;
        }

        /** The roof layer. */
        public int top() {
            return height - 1;
        }

        /** Compact signature: equal shapes give equal signatures (never 0). Bits 14-19 are left for the style. */
        public int signature() {
            return sides | corners << 4 | doors << 8 | (oddPlot ? 1 << 12 : 0) | 1 << 13 | (height - Plots.HEIGHT) << 20;
        }

        /** The height a signature was made for. */
        public static int heightOf(int signature) {
            return Plots.HEIGHT + ((signature >> 20) & 15);
        }
    }

    /** A lone building with one door. */
    public static Shape lone(int doorSide) {
        return new Shape(0, 0, Plots.bit(doorSide), false);
    }

    /** All placements of the shell, bottom-up. The table's cell is left out when {@code tablePlot} is set. */
    public static List<BlockOp> generate(Shape shape, boolean tablePlot) {
        List<BlockOp> ops = new ArrayList<>(S * S * shape.height());
        for (int y = 0; y < shape.height(); y++) {
            for (int z = 0; z < S; z++) {
                for (int x = 0; x < S; x++) {
                    if (tablePlot && Plots.isTableCell(Plots.CENTER, x, y, z)) continue;
                    ops.add(new BlockOp(x, y, z, piece(shape, x, y, z)));
                }
            }
        }
        return ops;
    }

    /** The piece at one local cell. */
    public static Piece piece(Shape shape, int x, int y, int z) {
        boolean edgeX = x == 0 || x == S - 1;
        boolean edgeZ = z == 0 || z == S - 1;
        if (edgeX && edgeZ) return corner(shape, x, y, z);
        if (edgeX) return side(shape, x == 0 ? Plots.W : Plots.E, z, y, Direction.Axis.Z);
        if (edgeZ) return side(shape, z == 0 ? Plots.N : Plots.S, x, y, Direction.Axis.X);
        if (y == 0) return Piece.FLOOR;
        if (y < shape.top()) return Piece.AIR;
        return light(shape, x, z) ? Piece.LIGHT : Piece.ROOF;
    }

    /** Light panels at local 1, 4, 7 on both axes, every other one: a diagonal lattice 3 apart, seamless across plots. */
    public static boolean light(Shape shape, int x, int z) {
        if ((x - 1) % 3 != 0 || (z - 1) % 3 != 0) return false;
        return ((x - 1) / 3 + (z - 1) / 3 + (shape.oddPlot() ? 1 : 0)) % 2 == 0;
    }

    private static Piece side(Shape shape, int side, int along, int y, Direction.Axis axis) {
        int top = shape.top();
        if (shape.shared(side)) return y == 0 ? Piece.FLOOR : y == top ? Piece.ROOF : Piece.AIR;
        if (y == 0 || y == top) return Piece.pillar(axis);
        boolean middle = along >= 2 && along <= 6;
        if (shape.door(side) && middle && y <= LINTEL) {
            if (y == LINTEL) return Piece.pillar(axis);
            return along == 2 || along == 6 ? Piece.POST : Piece.AIR;
        }
        // window bands two layers high, four layers apart, only where the whole band fits below the top wall layer
        int band = y - (y - 2) % 4;
        boolean window = middle && y >= 2 && (y - 2) % 4 < 2 && band + 1 < top - 1;
        return window ? Piece.WINDOW : Piece.WALL;
    }

    private static Piece corner(Shape shape, int x, int y, int z) {
        int top = shape.top();
        int corner = (z == 0 ? 0 : 2) + (x == 0 ? 0 : 1);
        int[] cs = Plots.cornerSides(corner);
        boolean sharedX = shape.shared(cs[0]);
        boolean sharedZ = shape.shared(cs[1]);
        if (!sharedX && !sharedZ) return Piece.POST;
        if (sharedX != sharedZ) {
            // The outside wall continues into the neighbour: plain wall, trim lines run through.
            Direction.Axis axis = sharedX ? Direction.Axis.X : Direction.Axis.Z;
            return y == 0 || y == top ? Piece.pillar(axis) : Piece.WALL;
        }
        if ((shape.corners() & (1 << corner)) != 0) return y == 0 ? Piece.FLOOR : y == top ? Piece.ROOF : Piece.AIR;
        // Inner corner of an L: closes the two outside walls that meet here.
        return y == 0 || y == top ? Piece.POST : Piece.WALL;
    }
}
