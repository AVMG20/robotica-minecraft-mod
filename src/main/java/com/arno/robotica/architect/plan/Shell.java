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
 * top        roof with light panels 4 apart (local 2 and 6, so the grid runs on across plots), trim along the outside edge
 * </pre>
 * Plots sit {@link Plots#PITCH} apart, so neighbours share their edge column (and up to four plots share a corner
 * column). Every plot that covers a shared cell computes the same piece for it, so the build order never matters:
 * <ul>
 *   <li>A shared side has no wall, so joined plots form one room, unless the player put an inner wall there: plain wall
 *       with floor and roof above and below, optionally with the same 3x3 doorway as an outside door. It stands
 *       exactly between the two rooms, so both are 7x7 inside.</li>
 *   <li>A corner column is a post where it is an outer corner (or two buildings touch diagonally), plain wall where an
 *       outside wall runs on into the neighbour, wall with posts top and bottom at the inner corner of an L, and open
 *       in the middle of a block of four unless an inner wall meets there.</li>
 * </ul>
 */
public final class Shell {
    private Shell() {}

    private static final int S = Plots.SIZE;
    /** Layer of the door lintel: doors are always 3 high. */
    private static final int LINTEL = 4;

    /**
     * @param sides     sides with a planned neighbour (bit per side)
     * @param diagonals corners whose diagonal neighbour is planned (bit per corner)
     * @param doors     doors in outside walls (bit per side; ignored on shared sides)
     * @param height    layers including floor and roof, {@link Plots#HEIGHT} to {@link Plots#MAX_HEIGHT}
     * @param walls     inner walls (bit per side; only shared sides count)
     * @param wallDoors inner walls with a doorway (subset of {@code walls})
     * @param closed    corners inside a block of four plots where an inner wall meets (bit per corner)
     */
    public record Shape(int sides, int diagonals, int doors, int height, int walls, int wallDoors, int closed) {
        public Shape(int sides, int diagonals, int doors) {
            this(sides, diagonals, doors, Plots.HEIGHT);
        }

        public Shape(int sides, int diagonals, int doors, int height) {
            this(sides, diagonals, doors, height, 0, 0, 0);
        }

        public Shape {
            height = Math.max(Plots.HEIGHT, Math.min(Plots.MAX_HEIGHT, height));
            sides &= 15;
            diagonals &= 15;
            doors &= 15 & ~sides;
            walls &= sides;
            wallDoors &= walls;
            int c = 0;
            for (int k = 0; k < 4; k++) if ((closed & 1 << k) != 0 && quad(sides, diagonals, k)) c |= 1 << k;
            closed = c;
        }

        private static boolean quad(int sides, int diagonals, int corner) {
            int[] cs = Plots.cornerSides(corner);
            return (diagonals & 1 << corner) != 0 && (sides & Plots.bit(cs[0])) != 0 && (sides & Plots.bit(cs[1])) != 0;
        }

        public boolean shared(int side) {
            return (sides & Plots.bit(side)) != 0;
        }

        public boolean door(int side) {
            return (doors & Plots.bit(side)) != 0;
        }

        /** An inner wall on a shared side. */
        public boolean wall(int side) {
            return (walls & Plots.bit(side)) != 0;
        }

        public boolean diagonal(int corner) {
            return (diagonals & 1 << corner) != 0;
        }

        /** The roof layer. */
        public int top() {
            return height - 1;
        }

        /** True when another planned plot covers this edge or corner cell as well. */
        public boolean overlaps(int x, int z) {
            boolean edgeX = x == 0 || x == S - 1, edgeZ = z == 0 || z == S - 1;
            int sx = x == 0 ? Plots.W : Plots.E, sz = z == 0 ? Plots.N : Plots.S;
            if (edgeX && edgeZ) return shared(sx) || shared(sz) || diagonal((z == 0 ? 0 : 2) + (x == 0 ? 0 : 1));
            return edgeX ? shared(sx) : edgeZ && shared(sz);
        }

        /**
         * Compact signature: shapes that build the same blocks give equal signatures (never 0, never negative). A diagonal
         * only counts where one of its two sides is shared: otherwise that corner is a post either way. Bits 28-29 are
         * left for the style.
         */
        public int signature() {
            int diag = 0;
            for (int k = 0; k < 4; k++) {
                int[] cs = Plots.cornerSides(k);
                if (diagonal(k) && (shared(cs[0]) || shared(cs[1]))) diag |= 1 << k;
            }
            return sides | diag << 4 | doors << 8 | walls << 12 | wallDoors << 16 | (height - Plots.HEIGHT) << 20
                    | closed << 24 | 1 << 30;
        }

        /** The height a signature was made for. */
        public static int heightOf(int signature) {
            return Plots.HEIGHT + ((signature >> 20) & 15);
        }
    }

    /** A lone building with one door. */
    public static Shape lone(int doorSide) {
        return new Shape(0, 0, Plots.bit(doorSide));
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
        return light(x, z) ? Piece.LIGHT : Piece.ROOF;
    }

    /** Light panels at local 2 and 6 on both axes: 4 apart, and on the same grid across plots {@link Plots#PITCH} apart. */
    public static boolean light(int x, int z) {
        return (x == 2 || x == 6) && (z == 2 || z == 6);
    }

    private static Piece side(Shape shape, int side, int along, int y, Direction.Axis axis) {
        int top = shape.top();
        boolean middle = along >= 2 && along <= 6;
        if (shape.shared(side)) {
            if (y == 0) return Piece.FLOOR;
            if (y == top) return Piece.ROOF;
            if (!shape.wall(side)) return Piece.AIR;
            if ((shape.wallDoors() & Plots.bit(side)) != 0 && middle && y <= LINTEL) return doorway(along, y, axis);
            return Piece.WALL;
        }
        if (y == 0 || y == top) return Piece.pillar(axis);
        if (shape.door(side) && middle && y <= LINTEL) return doorway(along, y, axis);
        // window bands two layers high, four layers apart, only where the whole band fits below the top wall layer
        int band = y - (y - 2) % 4;
        boolean window = middle && y >= 2 && (y - 2) % 4 < 2 && band + 1 < top - 1;
        return window ? Piece.WINDOW : Piece.WALL;
    }

    /** A 3 wide, 3 high opening framed by posts at 2 and 6 and a lintel on layer 4. */
    private static Piece doorway(int along, int y, Direction.Axis axis) {
        if (y == LINTEL) return Piece.pillar(axis);
        return along == 2 || along == 6 ? Piece.POST : Piece.AIR;
    }

    private static Piece corner(Shape shape, int x, int y, int z) {
        int top = shape.top();
        int corner = (z == 0 ? 0 : 2) + (x == 0 ? 0 : 1);
        int[] cs = Plots.cornerSides(corner);
        boolean sharedX = shape.shared(cs[0]);
        boolean sharedZ = shape.shared(cs[1]);
        boolean diagonal = shape.diagonal(corner);
        if (!sharedX && !sharedZ) return Piece.POST;
        if (sharedX && sharedZ && diagonal) {
            // Middle of a block of four: open, unless an inner wall meets here.
            boolean closed = (shape.closed() & 1 << corner) != 0;
            return y == 0 ? Piece.FLOOR : y == top ? Piece.ROOF : closed ? Piece.WALL : Piece.AIR;
        }
        if (sharedX != sharedZ && !diagonal) {
            // The outside wall continues into the neighbour: plain wall, trim lines run through.
            Direction.Axis axis = sharedX ? Direction.Axis.X : Direction.Axis.Z;
            return y == 0 || y == top ? Piece.pillar(axis) : Piece.WALL;
        }
        // Inner corner of an L (seen from any of its three plots): closes the two outside walls that meet here.
        return y == 0 || y == top ? Piece.POST : Piece.WALL;
    }
}
