package com.arno.robotica.architect.plan;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Procedural module shapes (no NBT). Every module fills a 9x9x6 plot: floor on layer 0, interior above, roof on layer 5
 * (corridors are lower, hangars open). Doors are 3 wide and 3 high in the middle of a side, set by the door mask.
 * Generation is deterministic, so the table can resume a build from a saved cursor.
 */
final class ModuleGenerator {
    private ModuleGenerator() {}

    private static final int S = Plots.SIZE;
    private static final int H = Plots.HEIGHT;

    private static final class Grid {
        final Piece[][][] cells = new Piece[S][H][S];

        void set(int x, int y, int z, Piece piece) {
            if (x >= 0 && x < S && y >= 0 && y < H && z >= 0 && z < S) cells[x][y][z] = piece;
        }

        void setIfEmpty(int x, int y, int z, Piece piece) {
            if (x >= 0 && x < S && y >= 0 && y < H && z >= 0 && z < S && cells[x][y][z] == null) cells[x][y][z] = piece;
        }

        Piece get(int x, int y, int z) {
            return cells[x][y][z];
        }

        List<BlockOp> ops() {
            List<BlockOp> list = new ArrayList<>();
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < S; x++) {
                    for (int z = 0; z < S; z++) {
                        if (cells[x][y][z] != null) list.add(new BlockOp(x, y, z, cells[x][y][z]));
                    }
                }
            }
            // Bottom-up; liquids last within a layer so the floor around them exists first.
            list.sort(Comparator.<BlockOp>comparingInt(BlockOp::y).thenComparingInt(op -> op.piece().isLiquid() ? 1 : 0));
            return list;
        }
    }

    static List<BlockOp> generate(ModuleType type, int mask) {
        Grid g = new Grid();
        switch (type) {
            case CORRIDOR -> corridor(g, mask);
            case HALL -> hall(g, mask);
            case WORKSHOP -> workshop(g, mask);
            case STORAGE_ROOM -> storage(g, mask);
            case MACHINE_HALL -> machineHall(g, mask);
            case GREENHOUSE -> greenhouse(g, mask);
            case HANGAR -> hangar(g, mask);
            case STAIRWELL -> stairwell(g, mask);
        }
        return g.ops();
    }

    // ---------- shared pieces ----------

    private static boolean ring(int x, int z) {
        return x == 0 || x == S - 1 || z == 0 || z == S - 1;
    }

    private static boolean corner(int x, int z) {
        return (x == 0 || x == S - 1) && (z == 0 || z == S - 1);
    }

    private static void floor(Grid g) {
        for (int x = 0; x < S; x++) for (int z = 0; z < S; z++) g.set(x, 0, z, Piece.FLOOR);
    }

    private static void roof(Grid g, int y) {
        for (int x = 0; x < S; x++) for (int z = 0; z < S; z++) g.set(x, y, z, Piece.ROOF);
    }

    /** Outer walls up to topY with corner pillars and two windows per side on layers 2-3. */
    private static void walls(Grid g, int topY, boolean windows) {
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                if (!ring(x, z)) continue;
                for (int y = 1; y <= topY; y++) g.set(x, y, z, corner(x, z) ? Piece.PILLAR_Y : Piece.WALL);
                if (windows && !corner(x, z)) {
                    int along = (x == 0 || x == S - 1) ? z : x;
                    if (along == 2 || along == 6) {
                        for (int y = 2; y <= 3 && y <= topY; y++) g.set(x, y, z, Piece.WINDOW);
                    }
                }
            }
        }
    }

    private static void interior(Grid g, int topY) {
        for (int x = 1; x < S - 1; x++) {
            for (int z = 1; z < S - 1; z++) {
                for (int y = 1; y <= topY; y++) g.setIfEmpty(x, y, z, Piece.AIR);
            }
        }
    }

    /** Local cell on a side's wall at offset 0-8 along it. */
    private static int[] wallCell(int side, int off) {
        return switch (side) {
            case Plots.N -> new int[]{off, 0};
            case Plots.S -> new int[]{off, S - 1};
            case Plots.W -> new int[]{0, off};
            default -> new int[]{S - 1, off};
        };
    }

    /** Doorways: 3 wide, 3 high, with a trim lintel above when the wall is 4 high. */
    private static void doors(Grid g, int mask, boolean lintel) {
        for (int side = 0; side < 4; side++) {
            if ((mask & Plots.bit(side)) == 0) continue;
            Direction.Axis along = (side == Plots.N || side == Plots.S) ? Direction.Axis.X : Direction.Axis.Z;
            for (int off = 3; off <= 5; off++) {
                int[] c = wallCell(side, off);
                for (int y = 1; y <= 3; y++) g.set(c[0], y, c[1], Piece.AIR);
                if (lintel) g.set(c[0], 4, c[1], Piece.pillar(along));
            }
        }
    }

    private static void lights(Grid g, int y, int[][] cells) {
        for (int[] c : cells) g.set(c[0], y, c[1], Piece.LIGHT);
    }

    private static BlockState facing(net.minecraft.world.level.block.Block block, Direction dir) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
    }

    // ---------- modules ----------

    private static void hall(Grid g, int mask) {
        floor(g);
        walls(g, 4, true);
        for (int[] c : new int[][]{{2, 2}, {6, 2}, {2, 6}, {6, 6}}) {
            for (int y = 1; y <= 4; y++) g.set(c[0], y, c[1], Piece.PILLAR_Y);
        }
        interior(g, 4);
        roof(g, 5);
        lights(g, 5, new int[][]{{4, 4}, {4, 2}, {4, 6}, {2, 4}, {6, 4}});
        doors(g, mask, true);
    }

    private static void workshop(Grid g, int mask) {
        floor(g);
        walls(g, 4, true);
        // Basic stations in the corners, clear of the doorways. Anvils, grindstones, smithing tables and blast furnaces are
        // not built: their materials (31 iron for an anvil) are worth far more than a module block, so the player crafts them.
        g.set(1, 1, 1, Piece.fixed(Blocks.CRAFTING_TABLE.defaultBlockState()));
        g.set(2, 1, 1, Piece.fixed(facing(Blocks.FURNACE, Direction.SOUTH)));
        g.set(7, 1, 7, Piece.fixed(facing(Blocks.CHEST, Direction.WEST)));
        g.set(6, 1, 7, Piece.fixed(Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)));
        interior(g, 4);
        roof(g, 5);
        lights(g, 5, new int[][]{{3, 3}, {5, 3}, {3, 5}, {5, 5}, {4, 4}});
        doors(g, mask, true);
    }

    private static void storage(Grid g, int mask) {
        floor(g);
        walls(g, 4, false);
        int[][] corners = {{1, 1}, {7, 1}, {1, 7}, {7, 7}};
        for (int[] c : corners) {
            int sx = c[0] == 1 ? 1 : -1;
            int sz = c[1] == 1 ? 1 : -1;
            // L shaped shelf: the corner plus one cell along each wall, barrels below, chests on top.
            int[][] shelf = {{c[0], c[1]}, {c[0] + sx, c[1]}, {c[0], c[1] + sz}};
            for (int i = 0; i < shelf.length; i++) {
                int x = shelf[i][0], z = shelf[i][1];
                g.set(x, 1, z, Piece.fixed(Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)));
                Direction face = i == 2 ? (sx > 0 ? Direction.EAST : Direction.WEST) : (sz > 0 ? Direction.SOUTH : Direction.NORTH);
                g.set(x, 2, z, Piece.fixed(facing(Blocks.CHEST, face)));
            }
        }
        interior(g, 4);
        roof(g, 5);
        lights(g, 5, new int[][]{{4, 4}, {4, 2}, {4, 6}, {2, 4}, {6, 4}});
        doors(g, mask, true);
    }

    private static void machineHall(Grid g, int mask) {
        floor(g);
        // Trim stripes let in the floor, two machine platforms per side.
        for (int i = 1; i < S - 1; i++) {
            g.set(4, 0, i, Piece.pillar(Direction.Axis.Z));
            g.set(i, 0, 4, Piece.pillar(Direction.Axis.X));
        }
        walls(g, 4, true);
        for (int[] c : new int[][]{{1, 1}, {6, 1}, {1, 6}, {6, 6}}) {
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) g.set(c[0] + dx, 1, c[1] + dz, Piece.FLOOR);
        }
        for (int x = 1; x < S - 1; x++) {
            g.set(x, 4, 1, Piece.pillar(Direction.Axis.X));
            g.set(x, 4, S - 2, Piece.pillar(Direction.Axis.X));
        }
        interior(g, 4);
        roof(g, 5);
        for (int z = 1; z < S - 1; z++) g.set(4, 5, z, Piece.LIGHT);
        doors(g, mask, true);
    }

    private static void greenhouse(Grid g, int mask) {
        floor(g);
        // Beds along the north and south walls. Seeds and water are not free: the beds are bare farmland, and a marker block
        // in the floor in the middle of each bed shows where the player puts a water source and sows the seeds.
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);
        for (int x = 1; x < S - 1; x++) {
            for (int z : new int[]{1, 2, 6, 7}) g.set(x, 0, z, Piece.fixed(farmland));
        }
        g.set(4, 0, 1, Piece.LIGHT);
        g.set(4, 0, 7, Piece.LIGHT);
        // Low solid wall, glass above, glass roof in a frame.
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                if (!ring(x, z)) continue;
                boolean cornerCell = corner(x, z);
                for (int y = 1; y <= 4; y++) {
                    Piece p = cornerCell ? Piece.PILLAR_Y : (y <= 2 ? Piece.WALL : Piece.WINDOW);
                    g.set(x, y, z, p);
                }
            }
        }
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                boolean beamX = z % 4 == 0;
                boolean beamZ = x % 4 == 0;
                Piece p = beamX ? Piece.pillar(Direction.Axis.X) : beamZ ? Piece.pillar(Direction.Axis.Z) : Piece.WINDOW;
                g.set(x, 5, z, p);
            }
        }
        lights(g, 5, new int[][]{{2, 2}, {6, 2}, {2, 6}, {6, 6}});
        interior(g, 4);
        doors(g, mask, true);
    }

    private static void hangar(Grid g, int mask) {
        floor(g);
        // Landing pad lights set into the floor.
        lights(g, 0, new int[][]{{2, 2}, {4, 2}, {6, 2}, {2, 4}, {6, 4}, {2, 6}, {4, 6}, {6, 6}});
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                if (!ring(x, z)) continue;
                if (corner(x, z)) {
                    for (int y = 1; y <= 3; y++) g.set(x, y, z, Piece.PILLAR_Y);
                    g.set(x, 4, z, Piece.LIGHT);
                } else {
                    g.set(x, 1, z, Piece.WALL);
                }
            }
        }
        // Open sky: clear everything above the parapet.
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                for (int y = 1; y <= 5; y++) g.setIfEmpty(x, y, z, Piece.AIR);
            }
        }
        for (int side = 0; side < 4; side++) {
            if ((mask & Plots.bit(side)) == 0) continue;
            for (int off = 3; off <= 5; off++) {
                int[] c = wallCell(side, off);
                g.set(c[0], 1, c[1], Piece.AIR);
            }
        }
    }

    private static void stairwell(Grid g, int mask) {
        floor(g);
        walls(g, 4, true);
        // Straight flight along the north wall, rising east, ending on a roof landing.
        for (int k = 0; k < 4; k++) {
            int x = 1 + k;
            int y = 1 + k;
            for (int below = 1; below < y; below++) g.set(x, below, 1, Piece.WALL);
            g.set(x, y, 1, Piece.stairs(Direction.EAST));
        }
        for (int x = 5; x <= 7; x++) {
            for (int z = 1; z <= 2; z++) {
                for (int y = 1; y <= 3; y++) g.set(x, y, z, Piece.WALL);
                g.set(x, 4, z, Piece.FLOOR);
            }
        }
        interior(g, 4);
        roof(g, 5);
        for (int x = 4; x <= 7; x++) for (int z = 1; z <= 2; z++) g.set(x, 5, z, Piece.AIR);
        lights(g, 5, new int[][]{{4, 4}, {2, 6}, {6, 6}, {2, 3}, {6, 4}});
        doors(g, mask, true);
    }

    private static void corridor(Grid g, int mask) {
        boolean[][] lane = new boolean[S][S];
        for (int x = 2; x <= 6; x++) for (int z = 2; z <= 6; z++) lane[x][z] = true;
        for (int side = 0; side < 4; side++) {
            if ((mask & Plots.bit(side)) == 0) continue;
            for (int a = 2; a <= 6; a++) {
                for (int d = 0; d <= 1; d++) {
                    switch (side) {
                        case Plots.N -> lane[a][d] = true;
                        case Plots.S -> lane[a][S - 1 - d] = true;
                        case Plots.W -> lane[d][a] = true;
                        default -> lane[S - 1 - d][a] = true;
                    }
                }
            }
        }
        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                if (!lane[x][z]) continue;
                g.set(x, 0, z, Piece.FLOOR);
                g.set(x, 4, z, Piece.ROOF);
                // A lane cell is wall when a neighbour inside the plot is not part of the lane.
                boolean wallX = false;
                boolean wallZ = false;
                for (int side = 0; side < 4; side++) {
                    int nx = x + Plots.DX[side];
                    int nz = z + Plots.DZ[side];
                    if (nx < 0 || nx >= S || nz < 0 || nz >= S || lane[nx][nz]) continue;
                    if (Plots.DX[side] != 0) wallX = true;
                    else wallZ = true;
                }
                if (!wallX && !wallZ) {
                    for (int y = 1; y <= 3; y++) g.set(x, y, z, Piece.AIR);
                    boolean lit = (x == 4 && (z == 1 || z == 4 || z == 7)) || (z == 4 && (x == 1 || x == 7));
                    if (lit) g.set(x, 4, z, Piece.LIGHT);
                    continue;
                }
                int along = wallX && !wallZ ? z : !wallX ? x : -1;
                for (int y = 1; y <= 3; y++) {
                    Piece p;
                    if (along < 0 || along == 2 || along == 6) p = Piece.PILLAR_Y;
                    else if (y == 2 && (along == 1 || along == 7)) p = Piece.WINDOW;
                    else p = Piece.WALL;
                    g.set(x, y, z, p);
                }
            }
        }
    }
}
