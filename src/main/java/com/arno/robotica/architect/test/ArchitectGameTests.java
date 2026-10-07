package com.arno.robotica.architect.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.architect.matter.Matter;
import com.arno.robotica.architect.matter.MatterTable;
import com.arno.robotica.architect.menu.ArchitectMenu;
import com.arno.robotica.architect.net.ArchitectActionPayload;
import com.arno.robotica.architect.plan.BlockOp;
import com.arno.robotica.architect.plan.Layout;
import com.arno.robotica.architect.plan.Piece;
import com.arno.robotica.architect.plan.Plots;
import com.arno.robotica.architect.plan.Shell;
import com.arno.robotica.architect.style.BuildStyle;
import com.arno.robotica.architect.style.Role;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Headless tests of the architect module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class ArchitectGameTests {
    /**
     * The 9x9 plots do not fit the 3x3x3 template, so the tables stand high above the test structures, each building
     * test in its own height band so that neighbouring tests never build into each other.
     */
    private static BlockPos high(int band) {
        return new BlockPos(1, 30 + band * 8, 1);
    }

    // ---------------------------------------------------------------- matter and styles

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void matterConversionFromInput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);

        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.COBBLESTONE)).equals(new Matter(1, 0, 0)), "cobblestone is 1 rustic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.OAK_LOG)).equals(new Matter(4, 0, 0)), "logs are 4 rustic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.COPPER_INGOT)).equals(new Matter(0, 4, 0)), "copper ingot is 4 refined");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.IRON_INGOT)).equals(new Matter(0, 8, 0)), "iron ingot is 8 refined");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.OBSIDIAN)).equals(new Matter(0, 0, 4)), "obsidian is 4 exotic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.ENDER_PEARL)).equals(new Matter(0, 0, 16)), "ender pearl is 16 exotic");
        helper.assertTrue(MatterTable.valueOf(new ItemStack(Items.DIAMOND)).equals(new Matter(0, 0, 32)), "diamond is 32 exotic");
        helper.assertTrue(!MatterTable.hasValue(new ItemStack(Items.STICK)), "sticks are worthless");

        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "table should expose an item handler");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.STICK, 4), false).getCount() == 4, "worthless items are refused");
        helper.assertTrue(handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 10), false).isEmpty(), "cobblestone is accepted");
        helper.assertTrue(handler.extractItem(0, 10, true).isEmpty(), "nothing can be extracted");
        table.input.insertItem(1, new ItemStack(Items.COPPER_INGOT, 2), false);
        table.input.insertItem(2, new ItemStack(Items.OBSIDIAN, 1), false);
        table.input.insertItem(3, new ItemStack(Items.OAK_LOG, 3), false);

        helper.succeedWhen(() -> {
            Matter m = table.matter();
            helper.assertTrue(m.equals(new Matter(10 + 12, 8, 4)), "matter should be 22/8/4 but is " + m);
            helper.assertTrue(table.input.getStackInSlot(0).isEmpty(), "input should be consumed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void casingUnlocksStyles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(table.unlocked(BuildStyle.TIMBERFRAME), "Timberframe is always unlocked");
        helper.assertTrue(!table.unlocked(BuildStyle.COPPER_WORKS), "Copper Works needs a casing");
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_STYLE, BuildStyle.STEEL_LAB.ordinal(), 0) == null, "style action works");
        helper.assertTrue(table.selectedStyle() == BuildStyle.TIMBERFRAME, "a locked style cannot be selected");
        table.styleSlot.setStackInSlot(0, new ItemStack(CoreItems.IRON_CASING.get()));
        helper.assertTrue(table.unlocked(BuildStyle.COPPER_WORKS) && !table.unlocked(BuildStyle.STEEL_LAB), "Iron Casing unlocks Copper Works only");
        table.styleSlot.setStackInSlot(0, new ItemStack(CoreItems.REINFORCED_CASING.get()));
        helper.assertTrue(table.unlocked(BuildStyle.STEEL_LAB) && !table.unlocked(BuildStyle.NULL_SPIRE), "Reinforced Casing unlocks Steel Lab");
        table.styleSlot.setStackInSlot(0, new ItemStack(CoreItems.NULL_CASING.get()));
        helper.assertTrue(table.unlocked(BuildStyle.NULL_SPIRE), "Null Casing unlocks Null Spire");
        helper.succeed();
    }

    /** Day one: a wound Mainspring in the battery slot runs the table, and Timberframe is the cheapest style per block. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void mainspringPowersTheTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);
        ItemStack spring = new ItemStack(CoreItems.MAINSPRING.get());
        ItemEnergy.fill(spring);
        helper.assertTrue(table.battery.isItemValid(0, spring) && !table.battery.isItemValid(0, new ItemStack(Items.COBBLESTONE)), "battery slot takes energy items only");
        table.battery.setStackInSlot(0, spring);
        int base = ArchitectTableBlockEntity.baseEnergy();
        helper.assertTrue(BuildStyle.TIMBERFRAME.energyPerBlock(base) * 2 == BuildStyle.COPPER_WORKS.energyPerBlock(base), "Timberframe costs half the base FE");
        helper.assertTrue(BuildStyle.TIMBERFRAME.energyPerBlock(base) <= 10, "a Timberframe block costs at most 10 FE by default");
        helper.succeedWhen(() -> {
            helper.assertTrue(table.energy.getEnergyStored() > 0, "the Mainspring should charge the buffer");
            helper.assertTrue(ItemEnergy.get(table.battery.getStackInSlot(0)) < ItemEnergy.capacity(spring), "the Mainspring should run down");
        });
    }

    // ---------------------------------------------------------------- shell generator (no world)

    /** Every shape: inside the plot, every cell exactly once, bottom-up, the table's cell left out on the table plot. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void shellCoversThePlotOnceBottomUp(GameTestHelper helper) {
        int cells = Plots.SIZE * Plots.SIZE * Plots.HEIGHT;
        for (int sides = 0; sides < 16; sides++) {
            for (int corners = 0; corners < 16; corners++) {
                for (int doors = 0; doors < 16; doors += 5) {
                    for (boolean table : new boolean[]{false, true}) {
                        Shell.Shape shape = new Shell.Shape(sides, corners, doors);
                        List<BlockOp> ops = Shell.generate(shape, table);
                        helper.assertTrue(ops.size() == cells - (table ? 1 : 0), "shell " + shape + " has " + ops.size() + " ops");
                        Set<Long> seen = new HashSet<>();
                        int lastY = 0;
                        for (BlockOp op : ops) {
                            helper.assertTrue(op.x() >= 0 && op.x() < Plots.SIZE && op.z() >= 0 && op.z() < Plots.SIZE
                                    && op.y() >= 0 && op.y() < Plots.HEIGHT, "op out of the plot: " + op);
                            helper.assertTrue(seen.add(BlockPos.asLong(op.x(), op.y(), op.z())), "places twice at " + op);
                            helper.assertTrue(op.y() >= lastY, "not built bottom-up at " + op);
                            lastY = op.y();
                            helper.assertTrue(!(table && Plots.isTableCell(Plots.CENTER, op.x(), op.y(), op.z())), "the table's cell must never be built");
                        }
                    }
                }
            }
        }
        helper.succeed();
    }

    /** Openings exactly where neighbours touch or a door is set, posts only on outer corners, trim, windows and lights. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void shellOpeningsAndCorners(GameTestHelper helper) {
        int mid = Plots.SIZE / 2, last = Plots.SIZE - 1;
        for (int sides = 0; sides < 16; sides++) {
            for (int doors = 0; doors < 16; doors++) {
                Shell.Shape shape = new Shell.Shape(sides, 15, doors);
                Map<Long, Piece> at = index(Shell.generate(shape, false));
                for (int side = 0; side < 4; side++) {
                    boolean shared = (sides & Plots.bit(side)) != 0;
                    boolean door = !shared && (doors & Plots.bit(side)) != 0;
                    int[] c = wallCell(side, mid);
                    // The middle of a side is open on layers 1-3 exactly for a neighbour or a door.
                    for (int y = 1; y <= 3; y++) {
                        helper.assertTrue(at.get(key(c[0], y, c[1])).isAir() == (shared || door), "side " + side + " y " + y + " of " + shape);
                    }
                    // A shared side has no wall at all; an outside wall has floor trim and a roof edge.
                    for (int along = 1; along < last; along++) {
                        int[] w = wallCell(side, along);
                        Piece floor = at.get(key(w[0], 0, w[1]));
                        if (shared) {
                            helper.assertTrue(floor.equals(Piece.FLOOR) && at.get(key(w[0], 4, w[1])).isAir(), "shared side " + side + " keeps a wall in " + shape);
                        } else {
                            helper.assertTrue(floor.role() == Role.PILLAR && floor.axis() != Direction.Axis.Y, "outside side " + side + " lacks the floor trim in " + shape);
                            helper.assertTrue(at.get(key(w[0], Plots.HEIGHT - 1, w[1])).role() == Role.PILLAR, "outside side " + side + " lacks the roof edge in " + shape);
                        }
                    }
                    if (!shared && !door) {
                        int[] w = wallCell(side, 3);
                        helper.assertTrue(at.get(key(w[0], 2, w[1])).equals(Piece.WINDOW) && at.get(key(w[0], 1, w[1])).equals(Piece.WALL), "window band on side " + side);
                    }
                }
                // Corner posts only where neither side touching the corner is shared (with every diagonal planned).
                for (int corner = 0; corner < 4; corner++) {
                    int x = (corner & 1) == 0 ? 0 : last, z = (corner & 2) == 0 ? 0 : last;
                    int[] cs = Plots.cornerSides(corner);
                    boolean outer = (sides & Plots.bit(cs[0])) == 0 && (sides & Plots.bit(cs[1])) == 0;
                    helper.assertTrue(at.get(key(x, 2, z)).equals(Piece.POST) == outer, "corner " + corner + " post mismatch in " + shape);
                }
            }
        }
        // A 2x2 block of plots has an open middle; the inner corner of an L is closed with wall.
        Shell.Shape quad = new Shell.Shape(Plots.bit(Plots.E) | Plots.bit(Plots.S), 1 << Plots.SE, Plots.bit(Plots.N));
        Map<Long, Piece> q = index(Shell.generate(quad, false));
        helper.assertTrue(q.get(key(last, 2, last)).isAir() && q.get(key(last, 0, last)).equals(Piece.FLOOR), "middle of a 2x2 hall is open");
        Shell.Shape ell = new Shell.Shape(Plots.bit(Plots.E) | Plots.bit(Plots.S), 0, Plots.bit(Plots.N));
        Map<Long, Piece> l = index(Shell.generate(ell, false));
        helper.assertTrue(l.get(key(last, 2, last)).equals(Piece.WALL), "inner corner of an L is wall");
        // Seam corner: the outside wall runs on as plain wall, its trim continues along the wall.
        helper.assertTrue(l.get(key(last, 2, 0)).equals(Piece.WALL) && l.get(key(last, 0, 0)).equals(Piece.pillar(Direction.Axis.X)), "seam corner is wall with trim");
        // Door frame: posts at 2 and 6, a lintel above.
        Map<Long, Piece> lone = index(Shell.generate(Shell.lone(Plots.S), false));
        helper.assertTrue(lone.get(key(2, 1, last)).equals(Piece.POST) && lone.get(key(6, 3, last)).equals(Piece.POST), "door posts");
        helper.assertTrue(lone.get(key(4, 4, last)).equals(Piece.pillar(Direction.Axis.X)), "door lintel");
        helper.assertTrue(lone.get(key(4, 2, 0)).equals(Piece.WINDOW) && lone.get(key(4, 1, 0)).equals(Piece.WALL), "the other sides keep their windows");
        // Lights: 4 per plot at local 2 and 6, so 4 apart across the shared column too (6 -> 8 + 2).
        long lights = lone.values().stream().filter(Piece.LIGHT::equals).count();
        helper.assertTrue(lights == 4, "light lattice " + lights);
        helper.assertTrue(Shell.light(2, 6) && Shell.light(6, 2) && !Shell.light(4, 4) && Plots.PITCH + 2 - 6 == 4, "lattice continues across plots");
        helper.succeed();
    }

    private static Map<Long, Piece> index(List<BlockOp> ops) {
        Map<Long, Piece> map = new HashMap<>();
        for (BlockOp op : ops) map.put(key(op.x(), op.y(), op.z()), op.piece());
        return map;
    }

    private static long key(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static int[] wallCell(int side, int along) {
        int last = Plots.SIZE - 1;
        return switch (side) {
            case Plots.N -> new int[]{along, 0};
            case Plots.S -> new int[]{along, last};
            case Plots.W -> new int[]{0, along};
            default -> new int[]{last, along};
        };
    }

    // ---------------------------------------------------------------- layout rules (no world)

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void layoutJoinsPlotsAndKeepsOneEntrance(GameTestHelper helper) {
        Layout layout = new Layout();
        int west = Plots.index(-1, 0);
        helper.assertTrue(layout.queue(west, BuildStyle.TIMBERFRAME), "queue a plot");
        helper.assertTrue(layout.doors(west) == Plots.bit(Plots.E), "a lone building's door faces the table");
        helper.assertTrue(!layout.queue(west, BuildStyle.TIMBERFRAME), "a planned plot cannot be queued twice");

        layout.queue(Plots.CENTER, BuildStyle.TIMBERFRAME);
        helper.assertTrue(layout.sides(west) == Plots.bit(Plots.E) && layout.sides(Plots.CENTER) == Plots.bit(Plots.W), "the two plots share a side");
        helper.assertTrue((layout.doors(west) & Plots.bit(Plots.E)) == 0, "no door bit on a shared side");
        int groupDoors = Integer.bitCount(layout.doors(west)) + Integer.bitCount(layout.doors(Plots.CENTER));
        helper.assertTrue(groupDoors == 1, "the joined pair has exactly one entrance, has " + groupDoors);
        helper.assertTrue(layout.doors(Plots.CENTER) == Plots.bit(Plots.S), "the entrance goes to the plot nearest the table");

        // The last door of a group cannot be removed; a second one can be added and removed again.
        helper.assertTrue(layout.toggleDoor(Plots.CENTER, Plots.S) == Layout.DOOR_LAST, "last door stays");
        helper.assertTrue(layout.toggleDoor(Plots.CENTER, Plots.W) == Layout.DOOR_SHARED, "no door on a shared side");
        helper.assertTrue(layout.toggleDoor(west, Plots.N) == Layout.DOOR_OK && layout.toggleDoor(Plots.CENTER, Plots.S) == Layout.DOOR_OK, "move the door");
        helper.assertTrue(layout.doors(Plots.CENTER) == 0 && layout.doors(west) == Plots.bit(Plots.N), "door moved to the west plot");

        // Built plots need a re-pass when a neighbour comes or goes.
        layout.markBuilt(west, layout.signature(west));
        layout.markBuilt(Plots.CENTER, layout.signature(Plots.CENTER));
        helper.assertTrue(!layout.hasWork(), "everything built");
        int north = Plots.index(0, -1);
        layout.queue(north, BuildStyle.STEEL_LAB);
        helper.assertTrue(layout.nextWork() == north, "the new plot is built first");
        helper.assertTrue(layout.needsWork(Plots.CENTER) && layout.needsWork(west), "the centre opens toward the new plot; the west plot's shared corner becomes the inner corner of an L");
        layout.unqueue(north);
        helper.assertTrue(!layout.hasWork(), "unqueueing restores the old shape");
        layout.forget(west);
        helper.assertTrue(layout.needsWork(Plots.CENTER) && layout.doors(Plots.CENTER) != 0, "a forgotten neighbour closes the wall and the centre gets a door back");

        // Persistence of the plan itself.
        Layout copy = new Layout();
        copy.load(layout.save());
        for (int p = 0; p < Plots.COUNT; p++) helper.assertTrue(copy.packed(p) == layout.packed(p), "plot " + p + " survives saving");
        helper.succeed();
    }

    /** Inner walls: a shared side steps open, doorway, wall; it stands in the shared column, so both rooms are 7 wide. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void innerWallsSplitJoinedPlots(GameTestHelper helper) {
        int last = Plots.SIZE - 1, top = Plots.HEIGHT - 1;
        int e = Plots.bit(Plots.E), w = Plots.bit(Plots.W), s = Plots.bit(Plots.S);
        Shell.Shape doorway = new Shell.Shape(e, 0, Plots.bit(Plots.N), Plots.HEIGHT, e, e, 0);
        Shell.Shape solid = new Shell.Shape(e, 0, Plots.bit(Plots.N), Plots.HEIGHT, e, 0, 0);
        Shell.Shape open = new Shell.Shape(e, 0, Plots.bit(Plots.N));
        helper.assertTrue(doorway.signature() != solid.signature() && solid.signature() != open.signature() && doorway.signature() > 0, "inner walls change the signature");
        helper.assertTrue(new Shell.Shape(0, 0, 0, Plots.HEIGHT, e, e, 0).walls() == 0, "no inner wall on an outside side");
        Map<Long, Piece> d = index(Shell.generate(doorway, false));
        Map<Long, Piece> sw = index(Shell.generate(solid, false));
        helper.assertTrue(d.size() == Plots.SIZE * Plots.SIZE * Plots.HEIGHT, "an inner wall keeps the shell complete");
        for (int z = 1; z < last; z++) {
            helper.assertTrue(sw.get(key(last, 0, z)).equals(Piece.FLOOR) && sw.get(key(last, top, z)).equals(Piece.ROOF), "floor and roof run under and over the wall at " + z);
            for (int y = 1; y < top; y++) helper.assertTrue(sw.get(key(last, y, z)).equals(Piece.WALL), "solid inner wall at " + z + "/" + y);
        }
        helper.assertTrue(d.get(key(last, 2, 1)).equals(Piece.WALL) && d.get(key(last, 1, 2)).equals(Piece.POST) && d.get(key(last, 3, 6)).equals(Piece.POST), "doorway posts");
        for (int y = 1; y <= 3; y++) helper.assertTrue(d.get(key(last, y, 4)).isAir(), "doorway open at " + y);
        helper.assertTrue(d.get(key(last, 4, 4)).equals(Piece.pillar(Direction.Axis.Z)), "doorway lintel");
        helper.assertTrue(d.get(key(last, 2, 0)).equals(Piece.WALL) && d.get(key(last, 0, 0)).equals(Piece.pillar(Direction.Axis.X)), "the inner wall meets the outside wall");
        // The plot on the other side builds the same wall in the same column (its x = 0).
        Shell.Shape other = new Shell.Shape(w, 0, 0, Plots.HEIGHT, w, w, 0);
        for (int y = 0; y < Plots.HEIGHT; y++) for (int z = 1; z < last; z++) {
            helper.assertTrue(Shell.piece(other, 0, y, z).equals(d.get(key(last, y, z))), "both plots agree on the shared column at " + y + "/" + z);
        }
        // Middle of a 2x2 hall: closed where an inner wall meets, open otherwise.
        Shell.Shape quad = new Shell.Shape(e | s, 1 << Plots.SE, Plots.bit(Plots.N), Plots.HEIGHT, 0, 0, 1 << Plots.SE);
        helper.assertTrue(Shell.piece(quad, last, 2, last).equals(Piece.WALL) && Shell.piece(quad, last, 0, last).equals(Piece.FLOOR), "inner wall closes the hall's middle");

        Layout layout = new Layout();
        int west = Plots.index(-1, 0);
        layout.queue(west, BuildStyle.TIMBERFRAME);
        helper.assertTrue(layout.cycleWall(west, Plots.E) == -1, "no inner wall toward an empty plot");
        layout.queue(Plots.CENTER, BuildStyle.TIMBERFRAME);
        layout.markBuilt(west, layout.signature(west));
        layout.markBuilt(Plots.CENTER, layout.signature(Plots.CENTER));
        helper.assertTrue(layout.cycleWall(Plots.CENTER, Plots.W) == Layout.EDGE_DOORWAY, "first click: wall with a doorway");
        helper.assertTrue(layout.edge(west, Plots.E) == Layout.EDGE_DOORWAY && layout.edge(Plots.CENTER, Plots.W) == Layout.EDGE_DOORWAY, "both plots see the same seam");
        helper.assertTrue(layout.shape(west).walls() == e && layout.shape(Plots.CENTER).walls() == w, "both plots build it");
        helper.assertTrue(layout.needsWork(west) && layout.needsWork(Plots.CENTER), "both need a re-pass");
        helper.assertTrue(((layout.packed(west) >> 9) & 3) == Layout.EDGE_DOORWAY, "the GUI sees the seam");
        Layout copy = new Layout();
        copy.load(layout.save());
        helper.assertTrue(copy.edge(west, Plots.E) == Layout.EDGE_DOORWAY && copy.signature(west) == layout.signature(west), "inner walls survive saving");
        helper.assertTrue(layout.cycleWall(west, Plots.E) == Layout.EDGE_WALL, "second click: solid wall");
        helper.assertTrue(layout.cycleWall(west, Plots.E) == Layout.EDGE_OPEN && !layout.needsWork(west), "third click: open again, nothing to rebuild");
        layout.cycleWall(west, Plots.E);
        layout.forget(Plots.CENTER);
        helper.assertTrue(layout.edge(west, Plots.E) == Layout.EDGE_OPEN && layout.shape(west).walls() == 0, "the wall goes with the neighbour");
        layout.queue(Plots.CENTER, BuildStyle.TIMBERFRAME);
        helper.assertTrue(layout.edge(west, Plots.E) == Layout.EDGE_OPEN, "a new neighbour starts open");

        // A plot that only touches a corner changes nothing: that corner is a post either way.
        Layout corner = new Layout();
        corner.queue(Plots.CENTER, BuildStyle.TIMBERFRAME);
        corner.markBuilt(Plots.CENTER, corner.signature(Plots.CENTER));
        corner.queue(Plots.index(1, 1), BuildStyle.TIMBERFRAME);
        helper.assertTrue(!corner.needsWork(Plots.CENTER), "a diagonal neighbour needs no re-pass");

        // A wall between two other plots of a 2x2 block closes this plot's corner too.
        Layout block = new Layout();
        int ne = Plots.index(1, -1), nw = Plots.index(0, -1), se = Plots.index(1, 0);
        for (int p : new int[]{Plots.CENTER, ne, nw, se}) block.queue(p, BuildStyle.TIMBERFRAME);
        helper.assertTrue(block.closedCorners(Plots.CENTER) == 0, "an open 2x2 hall");
        block.cycleWall(ne, Plots.S);
        helper.assertTrue(block.closedCorners(Plots.CENTER) == 1 << Plots.NE, "the far seam closes the shared corner");
        helper.succeed();
    }

    /** Neighbours share their edge columns: every plot that covers a world cell must want the same piece there. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sharedColumnsAgreeInEveryLayout(GameTestHelper helper) {
        java.util.Random random = new java.util.Random(20261006L);
        BlockPos table = BlockPos.ZERO;
        for (int round = 0; round < 300; round++) {
            Layout layout = new Layout();
            for (int p = 0; p < Plots.COUNT; p++) if (random.nextInt(3) > 0) layout.queue(p, BuildStyle.TIMBERFRAME);
            for (int p = 0; p < Plots.COUNT; p++) {
                for (int side : new int[]{Plots.E, Plots.S}) for (int k = random.nextInt(3); k > 0; k--) layout.cycleWall(p, side);
            }
            Map<Long, Piece> world = new HashMap<>();
            Map<Long, Integer> owner = new HashMap<>();
            for (int p = 0; p < Plots.COUNT; p++) {
                if (!layout.planned(p)) continue;
                BlockPos origin = Plots.origin(table, p);
                for (BlockOp op : Shell.generate(layout.shape(p), p == Plots.CENTER)) {
                    long at = origin.offset(op.x(), op.y(), op.z()).asLong();
                    Piece before = world.putIfAbsent(at, op.piece());
                    if (before == null) {
                        owner.put(at, p);
                    } else {
                        helper.assertTrue(before.equals(op.piece()), "round " + round + ": plots " + owner.get(at) + " and " + p + " disagree at "
                                + op + ": " + before + " vs " + op.piece());
                    }
                    boolean edge = op.x() == 0 || op.x() == Plots.SIZE - 1 || op.z() == 0 || op.z() == Plots.SIZE - 1;
                    helper.assertTrue(layout.shape(p).overlaps(op.x(), op.z()) == (edge && sharedCell(layout, p, op.x(), op.z())),
                            "overlap flag wrong at " + op + " of plot " + p);
                }
            }
        }
        helper.succeed();
    }

    /** True when another planned plot covers this local cell. */
    private static boolean sharedCell(Layout layout, int plot, int x, int z) {
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            int px = Plots.px(plot) + dx, pz = Plots.pz(plot) + dz;
            if (Math.abs(px) > Plots.RADIUS || Math.abs(pz) > Plots.RADIUS || !layout.planned(Plots.index(px, pz))) continue;
            int lx = x - dx * Plots.PITCH, lz = z - dz * Plots.PITCH;
            if (lx >= 0 && lx < Plots.SIZE && lz >= 0 && lz < Plots.SIZE) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the GUI request path

    /** Plot toggles go through the payload validation: menu open, in reach, allowed to use the table. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void plotTogglesAreValidated(GameTestHelper helper) {
        BlockPos rel = high(0);
        helper.setBlock(rel, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(rel);
        BlockPos pos = table.getBlockPos();
        FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("5e1d2c3b-0000-4000-8000-00000000a001"), "architect_owner"));
        FakePlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("5e1d2c3b-0000-4000-8000-00000000a002"), "architect_stranger"));
        table.setOwner(owner);
        int plot = Plots.index(1, 0);
        ArchitectActionPayload toggle = new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_TOGGLE, plot, 0);
        try {
            owner.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5);
            owner.containerMenu = owner.inventoryMenu;
            helper.assertTrue(!ArchitectActionPayload.process(owner, toggle), "refused without the table's menu open");
            owner.containerMenu = new ArchitectMenu(1, owner.getInventory(), table);
            helper.assertTrue(ArchitectActionPayload.process(owner, toggle), "accepted with the menu open");
            helper.assertTrue(table.layout().state(plot) == Layout.QUEUED, "plot queued");
            helper.assertTrue(ArchitectActionPayload.process(owner, new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_TOGGLE, 99, 0)), "bad plot reaches the table");
            helper.assertTrue(table.layout().queuedCount() == 1, "a bad plot changes nothing");
            ArchitectActionPayload door = new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_DOOR, plot, Plots.N);
            helper.assertTrue(ArchitectActionPayload.process(owner, door) && (table.layout().doors(plot) & Plots.bit(Plots.N)) != 0, "door added");
            ArchitectActionPayload wall = new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_WALL, plot, Plots.W);
            helper.assertTrue(ArchitectActionPayload.process(owner, wall) && table.layout().edge(plot, Plots.W) == Layout.EDGE_OPEN, "no inner wall toward an empty plot");
            ArchitectActionPayload.process(owner, new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0));
            helper.assertTrue(ArchitectActionPayload.process(owner, wall) && table.layout().edge(Plots.CENTER, Plots.E) == Layout.EDGE_DOORWAY, "inner wall added");
            ArchitectActionPayload.process(owner, new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0));

            stranger.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5);
            stranger.containerMenu = new ArchitectMenu(2, stranger.getInventory(), table);
            helper.assertTrue(!ArchitectActionPayload.process(stranger, toggle), "a stranger is refused");
            helper.assertTrue(table.layout().state(plot) == Layout.QUEUED, "the stranger changed nothing");

            owner.setPos(pos.getX() + 30, pos.getY(), pos.getZ());
            helper.assertTrue(!ArchitectActionPayload.process(owner, toggle), "refused out of reach");
            owner.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5);
            helper.assertTrue(ArchitectActionPayload.process(owner, toggle) && table.layout().state(plot) == Layout.EMPTY, "second click unqueues");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            stranger.containerMenu = stranger.inventoryMenu;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void onlyTheOwnerCanUseTheTable(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(table.canUse(player), "an ownerless table can be used");
        table.claimIfFree(player);
        helper.assertTrue(table.canUse(player), "the owner can use the table");
        helper.assertTrue(table.ownerName().equals(player.getGameProfile().getName()), "owner name is stored");
        var other = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!other.getUUID().equals(player.getUUID())) helper.assertTrue(!table.canUse(other), "a stranger must not use the table");
        helper.succeed();
    }

    // ---------------------------------------------------------------- building

    /** Two joined plots build completely out of matter and FE: shared wall open, table kept, the bill paid. */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void joinedBuildingsBuildFully(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 1, false);
        BlockPos tablePos = table.getBlockPos();
        int east = Plots.index(1, 0);
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0) == null, "queue the table plot");
        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, east, 0) == null, "queue the east plot");
        int energyBefore = table.energy.getEnergyStored();
        Matter matterBefore = table.matter();
        helper.runAfterDelay(10, () -> helper.assertTrue(table.layout().state(Plots.CENTER) == Layout.QUEUED && !table.running(),
                "nothing is built before Build is pressed"));
        helper.runAfterDelay(11, () -> table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0));

        helper.succeedWhen(() -> {
            helper.assertTrue(table.running() || table.layout().state(east) == Layout.BUILT, "build should run");
            helper.assertTrue(!table.layout().hasWork(), "build should be finished, status " + table.status() + ", progress " + table.progressPermille());
            int checked = 0;
            for (int plot : new int[]{Plots.CENTER, east}) {
                BlockPos origin = Plots.origin(tablePos, plot);
                for (BlockOp op : Shell.generate(table.layout().shape(plot), plot == Plots.CENTER)) {
                    BlockState expected = op.piece().resolve(BuildStyle.TIMBERFRAME);
                    BlockState actual = helper.getLevel().getBlockState(origin.offset(op.x(), op.y(), op.z()));
                    helper.assertTrue(actual == expected, "plot " + plot + " at " + op + " expected " + expected + " but found " + actual);
                    checked++;
                }
            }
            helper.assertTrue(checked > 900, "should have checked two shells, checked " + checked);
            helper.assertTrue(helper.getLevel().getBlockState(tablePos).is(ArchitectRegistry.ARCHITECT_TABLE.get()), "table must survive");
            BlockPos shared = Plots.origin(tablePos, Plots.CENTER).offset(Plots.SIZE - 1, 2, 4);
            helper.assertTrue(helper.getLevel().getBlockState(shared).isAir() && helper.getLevel().getBlockState(shared.east()).isAir(), "the shared wall is open");
            helper.assertTrue(helper.getLevel().getBlockState(tablePos.offset(0, 1, 4)).isAir(), "door in front of the table");
            helper.assertTrue(table.energy.getEnergyStored() < energyBefore, "energy should have been spent");
            Matter after = table.matter();
            helper.assertTrue(after.rustic() < matterBefore.rustic(), "rustic matter should have been spent");
            helper.assertTrue(after.refined() == matterBefore.refined(), "Timberframe uses no refined matter");
            helper.assertTrue(!table.running() && table.status() == ArchitectTableBlockEntity.ST_IDLE, "table should be idle");
        });
    }

    /** Clear terrain breaks whatever is in the way: junk is voided, a chest and its contents go to the chest on the table. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void clearTerrainSavesContainersAndVoidsJunk(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 2, true);
        BlockPos origin = Plots.origin(table.getBlockPos(), Plots.CENTER);
        BlockPos stone1 = origin.offset(3, 1, 3);
        BlockPos stone2 = origin.offset(5, 2, 3);
        BlockPos chest = origin.offset(3, 1, 5);
        helper.getLevel().setBlockAndUpdate(stone1, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(stone2, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, chest, null).insertItem(0, new ItemStack(Items.DIAMOND, 5), false);
        BlockPos stash = table.getBlockPos().above();
        helper.getLevel().setBlockAndUpdate(stash, Blocks.CHEST.defaultBlockState());
        helper.assertTrue(table.clearTerrain(), "clear terrain should be on");
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0);
        helper.succeedWhen(() -> {
            helper.assertTrue(!table.layout().hasWork(), "build should be finished, status " + table.status());
            helper.assertTrue(helper.getLevel().getBlockState(stone1).isAir() && helper.getLevel().getBlockState(stone2).isAir(), "stone in the footprint should be gone");
            helper.assertTrue(!helper.getLevel().getBlockState(chest).is(Blocks.CHEST), "a chest in the way is broken");
            helper.assertTrue(helper.getLevel().getBlockState(stash).is(Blocks.CHEST), "the chest on the table stays");
            IItemHandler drops = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, stash, Direction.DOWN);
            int cobble = 0, diamonds = 0, chests = 0;
            for (int i = 0; i < drops.getSlots(); i++) {
                ItemStack s = drops.getStackInSlot(i);
                if (s.is(Items.COBBLESTONE)) cobble += s.getCount();
                if (s.is(Items.DIAMOND)) diamonds += s.getCount();
                if (s.is(Items.CHEST)) chests += s.getCount();
            }
            helper.assertTrue(cobble == 0, "cobblestone is junk and voided, found " + cobble);
            helper.assertTrue(diamonds == 5 && chests == 1, "the broken chest and its diamonds are saved, found " + diamonds + " diamonds, " + chests + " chests");
        });
    }

    /** Without clear terrain, solid blocks stay and only air is built into. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void withoutClearTerrainBlocksStay(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 3, false);
        BlockPos stone = Plots.origin(table.getBlockPos(), Plots.CENTER).offset(3, 1, 3);
        helper.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0);
        helper.succeedWhen(() -> {
            helper.assertTrue(!table.layout().hasWork(), "build should be finished");
            helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "stone must not be replaced");
        });
    }

    /** Missing matter pauses the build and names the grade. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void missingMatterPausesTheBuild(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 4, false);
        table.setMatter(Matter.ZERO);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0);
        helper.succeedWhen(() -> {
            helper.assertTrue(table.layout().state(Plots.CENTER) == Layout.QUEUED, "build must wait");
            helper.assertTrue(table.status() == ArchitectTableBlockEntity.ST_NO_RUSTIC, "status should say rustic matter is missing, was " + table.status());
        });
    }

    /**
     * Running out of matter mid-build stops it for good: the status holds "needs rustic" on every tick (it used to flicker
     * with "building"), the drone goes, and new matter does nothing until Build is pressed again.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void runningOutOfMatterStopsWithoutFlicker(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 7, false);
        // no speed card: one block every few ticks, the ticks in between used to report "building" again
        table.upgrades.setStackInSlot(0, ItemStack.EMPTY);
        table.setMatter(new Matter(12, 0, 0));
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0);
        int[] stoppedAt = {-1};
        int[] tick = {0};
        boolean[] droneSeen = {false};
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(table.getBlockPos()).inflate(32);
        helper.onEachTick(() -> {
            tick[0]++;
            if (!helper.getLevel().getEntitiesOfClass(com.arno.robotica.architect.entity.BuilderDrone.class, around).isEmpty()) {
                droneSeen[0] = true;
            }
            if (stoppedAt[0] < 0) {
                if (table.status() == ArchitectTableBlockEntity.ST_NO_RUSTIC) stoppedAt[0] = tick[0];
                return;
            }
            int since = tick[0] - stoppedAt[0];
            if (since <= 80) {
                helper.assertTrue(table.status() == ArchitectTableBlockEntity.ST_NO_RUSTIC,
                        "status must hold while matter is missing, tick " + since + " was " + table.status());
                helper.assertTrue(!table.running() && table.needs() == ArchitectTableBlockEntity.ST_NO_RUSTIC, "the build is stopped");
                helper.assertTrue(!table.isBuilding(), "never building while stopped");
            }
            if (since == 5) {
                helper.assertTrue(helper.getLevel().getEntitiesOfClass(com.arno.robotica.architect.entity.BuilderDrone.class, around).isEmpty(),
                        "the drone is gone while the table waits");
            }
            if (since == 40) table.setMatter(new Matter(5000, 5000, 5000));
            if (since == 81) table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0);
            if (since > 85 && table.isBuilding() && table.needs() == 0) {
                helper.assertTrue(droneSeen[0], "a drone flew while the table built");
                helper.succeed();
            }
        });
    }

    /** The table never builds outside the world border; a skipped block costs nothing and does not stall the build. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void buildSkipsBlocksOutsideTheWorldBorder(GameTestHelper helper) {
        var level = helper.getLevel();
        var border = level.getWorldBorder();
        BlockPos inside = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos outside = inside.offset(100, 0, 0);
        double oldSize = border.getSize();
        double oldX = border.getCenterX(), oldZ = border.getCenterZ();
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            border.setCenter(inside.getX() + 0.5, inside.getZ() + 0.5);
            border.setSize(20);
            helper.assertTrue(ArchitectTableBlockEntity.mayBuildAt(level, inside, player), "inside the border is fine");
            helper.assertTrue(!ArchitectTableBlockEntity.mayBuildAt(level, outside, player), "outside the border is refused");
        } finally {
            border.setSize(oldSize);
            border.setCenter(oldX, oldZ);
        }
        helper.succeed();
    }

    /** Height cards: a taller shell keeps 3 high doors, gets a second window band and a roof on top; old plans keep their signature. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heightCardsMakeTallerShells(GameTestHelper helper) {
        Shell.Shape low = Shell.lone(Plots.S);
        Shell.Shape tall = new Shell.Shape(0, 0, Plots.bit(Plots.S), Plots.MAX_HEIGHT);
        helper.assertTrue(low.signature() == new Shell.Shape(0, 0, Plots.bit(Plots.S), Plots.HEIGHT).signature(), "base height keeps old signatures");
        helper.assertTrue(Shell.Shape.heightOf(tall.signature()) == Plots.MAX_HEIGHT, "the signature remembers the height");
        helper.assertTrue(Shell.generate(tall, false).size() == Plots.SIZE * Plots.SIZE * Plots.MAX_HEIGHT, "a 12 high shell fills 12 layers");
        int top = Plots.MAX_HEIGHT - 1;
        helper.assertTrue(Shell.piece(tall, 4, top, 4).role() == Role.ROOF || Shell.piece(tall, 4, top, 4).role() == Role.LIGHT, "roof on the top layer");
        helper.assertTrue(Shell.piece(tall, 4, top - 1, 4) == Piece.AIR, "open inside below the roof");
        helper.assertTrue(Shell.piece(tall, 4, 3, 8) == Piece.AIR && Shell.piece(tall, 4, 4, 8).role() == Role.PILLAR, "doors stay 3 high with a lintel");
        helper.assertTrue(Shell.piece(tall, 4, 6, 0) == Piece.WINDOW && Shell.piece(tall, 4, 4, 0) == Piece.WALL, "a second window band on tall walls");

        ArchitectTableBlockEntity table = preparedTable(helper, 6, false);
        table.upgrades.setStackInSlot(1, new ItemStack(CoreItems.card(UpgradeKind.HEIGHT).get(), 6));
        table.serverTick(helper.getLevel(), table.getBlockPos(), table.getBlockState());
        helper.assertTrue(table.layout().height() == Plots.MAX_HEIGHT, "six cards make 12 high buildings, got " + table.layout().height());

        // a re-pass that is reverted half way still finishes, and remembers the tallest height for the trim
        Layout layout = new Layout();
        layout.queue(Plots.CENTER, BuildStyle.TIMBERFRAME);
        layout.markBuilt(Plots.CENTER, layout.signature(Plots.CENTER));
        layout.setHeight(8);
        helper.assertTrue(layout.needsWork(Plots.CENTER), "taller plan: the built plot needs a re-pass");
        layout.markChanging(Plots.CENTER, 8);
        layout.setHeight(Plots.HEIGHT);
        helper.assertTrue(layout.needsWork(Plots.CENTER), "cards taken out half way: the plot is still not done");
        helper.assertTrue(layout.builtHeight(Plots.CENTER) == 8, "the trim knows the walk reached 8 high");
        helper.succeed();
    }

    // ---------------------------------------------------------------- persistence

    /** The plan, settings and matter survive saving and picking the table up; elsewhere only unbuilt work moves along. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void buildStateSurvivesSavingAndPickingUp(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 5, true);
        int queued = Plots.index(0, -1);
        int built = Plots.index(-1, 0);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, queued, 0);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, built, 0);
        table.layout().markBuilt(built, table.layout().signature(built));
        int[] words = planWords(table);

        var provider = helper.getLevel().registryAccess();
        var tag = table.saveWithoutMetadata(provider);
        BlockPos other = high(5).offset(0, 3, 0);
        helper.setBlock(other, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity copy = (ArchitectTableBlockEntity) helper.getBlockEntity(other);
        copy.loadWithComponents(tag, provider);
        helper.assertTrue(java.util.Arrays.equals(planWords(copy), words), "the plan survives saving");
        helper.assertTrue(copy.matter().equals(table.matter()) && copy.clearTerrain(), "matter and settings survive saving");

        ItemStack stack = new ItemStack(ArchitectRegistry.ARCHITECT_TABLE_ITEM.get());
        stack.applyComponents(table.collectComponents());
        helper.assertTrue(stack.has(ArchitectRegistry.BUILD_STATE_COMPONENT.get()), "the item carries the build state");

        // same spot: everything is back
        helper.setBlock(high(5), Blocks.AIR);
        helper.setBlock(high(5), ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity same = (ArchitectTableBlockEntity) helper.getBlockEntity(high(5));
        same.applyComponentsFromItemStack(stack);
        helper.assertTrue(java.util.Arrays.equals(planWords(same), words), "the plan is restored on the same spot");
        helper.assertTrue(same.clearTerrain(), "settings restored");
        helper.assertTrue(same.energy.getEnergyStored() == table.energy.getEnergyStored() && same.energy.getEnergyStored() > 0, "the energy comes along");
        helper.assertTrue(same.upgrades.level(UpgradeKind.SPEED) == table.upgrades.level(UpgradeKind.SPEED) && same.upgrades.level(UpgradeKind.SPEED) > 0, "the upgrade cards come along");

        // another spot: built plots stay behind, queued ones move along
        BlockPos elsewhere = high(5).offset(20, 0, 0);
        helper.setBlock(elsewhere, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity moved = (ArchitectTableBlockEntity) helper.getBlockEntity(elsewhere);
        moved.applyComponentsFromItemStack(stack);
        helper.assertTrue(moved.layout().state(queued) == Layout.QUEUED, "unbuilt work moves with the table");
        helper.assertTrue(moved.layout().state(built) == Layout.EMPTY, "built plots stay where they stand");
        helper.succeed();
    }

    // ---------------------------------------------------------------- demolish

    /**
     * Demolish takes a built plot down to the floor: building blocks go back as matter, a chest's contents, the chest,
     * a door and silk-touched stone go to the stash on the table; the table, the stash, bedrock and a block outside the
     * world border stay; a queued plot leaves the plan without touching its ground, and the plan ends up empty.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void demolishClearsBuiltPlotsAndRefundsMatter(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 8, false);
        var level = helper.getLevel();
        BlockPos tablePos = table.getBlockPos();
        BlockPos origin = Plots.origin(tablePos, Plots.CENTER);
        int east = Plots.index(1, 0);
        Layout layout = table.layout();
        layout.queue(Plots.CENTER, BuildStyle.TIMBERFRAME);
        layout.markBuilt(Plots.CENTER, layout.signature(Plots.CENTER));
        layout.queue(east, BuildStyle.TIMBERFRAME);

        BlockPos wall = origin.offset(4, 2, 0), roof = origin.offset(4, 5, 4), stone = origin.offset(5, 2, 5);
        BlockPos chest = origin.offset(3, 1, 3), bedrock = origin.offset(2, 1, 2), door = origin.offset(6, 1, 6);
        BlockPos outside = origin.offset(0, 3, 4), eastGround = Plots.origin(tablePos, east).offset(4, 1, 4), stash = tablePos.above();
        level.setBlockAndUpdate(wall, ArchitectRegistry.styleBlock(BuildStyle.TIMBERFRAME, Role.WALL).get().defaultBlockState());
        level.setBlockAndUpdate(roof, ArchitectRegistry.styleBlock(BuildStyle.TIMBERFRAME, Role.ROOF).get().defaultBlockState());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
        level.setBlockAndUpdate(outside, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(eastGround, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(door, Blocks.OAK_DOOR.defaultBlockState());
        level.setBlockAndUpdate(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        level.getCapability(Capabilities.ItemHandler.BLOCK, chest, null).insertItem(0, new ItemStack(Items.DIAMOND, 5), false);
        level.setBlockAndUpdate(stash, Blocks.CHEST.defaultBlockState());
        table.setMatter(new Matter(100, 0, 0));
        int energyBefore = table.energy.getEnergyStored();

        helper.assertTrue(table.handleAction(null, ArchitectTableBlockEntity.ACTION_DEMOLISH, 1, 0) != null && !table.demolishing(),
                "demolish needs the confirm code");
        var border = level.getWorldBorder();
        double oldSize = border.getSize(), oldX = border.getCenterX(), oldZ = border.getCenterZ();
        try {
            // the border leaves out the plot's west edge column (local x 0) and nothing else of it
            border.setCenter(tablePos.getX() + 20.5, tablePos.getZ() + 0.5);
            border.setSize(47);
            table.handleAction(null, ArchitectTableBlockEntity.ACTION_DEMOLISH, ArchitectTableBlockEntity.DEMOLISH_CONFIRM, 0);
            helper.assertTrue(table.demolishing() && table.status() == ArchitectTableBlockEntity.ST_DEMOLISHING, "demolish started");
            helper.assertTrue(layout.state(east) == Layout.EMPTY, "queued plots leave the plan");
            BlockState tableState = level.getBlockState(tablePos);
            for (int i = 0; i < 2000 && table.demolishing(); i++) table.serverTick(level, tablePos, tableState);
        } finally {
            border.setSize(oldSize);
            border.setCenter(oldX, oldZ);
        }
        helper.assertTrue(!table.demolishing() && layout.isEmpty(), "demolish finished with an empty plan");
        helper.assertTrue(level.getBlockState(tablePos).is(ArchitectRegistry.ARCHITECT_TABLE.get()), "the table stays");
        helper.assertTrue(level.getBlockState(stash).is(Blocks.CHEST), "the stash on the table stays");
        helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK), "unbreakable blocks stay");
        helper.assertTrue(level.getBlockState(outside).is(Blocks.STONE), "blocks outside the world border stay");
        helper.assertTrue(level.getBlockState(eastGround).is(Blocks.STONE), "a queued plot's ground is not touched");
        for (int y = 0; y < Plots.HEIGHT; y++) for (int z = 0; z < Plots.SIZE; z++) for (int x = 0; x < Plots.SIZE; x++) {
            BlockPos p = origin.offset(x, y, z);
            if (p.equals(tablePos) || p.equals(stash) || p.equals(bedrock) || p.equals(outside)) continue;
            helper.assertTrue(level.getBlockState(p).isAir(), "everything else in the plot is gone, found " + level.getBlockState(p) + " at " + x + " " + y + " " + z);
        }
        IItemHandler drops = level.getCapability(Capabilities.ItemHandler.BLOCK, stash, Direction.DOWN);
        int diamonds = 0, chests = 0, stones = 0, doors = 0, walls = 0;
        for (int i = 0; i < drops.getSlots(); i++) {
            ItemStack s = drops.getStackInSlot(i);
            if (s.is(Items.DIAMOND)) diamonds += s.getCount();
            if (s.is(Items.CHEST)) chests += s.getCount();
            if (s.is(Items.STONE)) stones += s.getCount();
            if (s.is(Items.OAK_DOOR)) doors += s.getCount();
            if (s.is(ArchitectRegistry.styleItem(BuildStyle.TIMBERFRAME, Role.WALL).get())) walls += s.getCount();
        }
        helper.assertTrue(diamonds == 5 && chests == 1, "the chest and its diamonds are kept, found " + diamonds + " diamonds, " + chests + " chests");
        helper.assertTrue(stones == 1 && doors == 1, "other blocks drop themselves, found " + stones + " stone, " + doors + " doors");
        helper.assertTrue(walls == 0, "building blocks do not come back as items");
        int refund = (int) (2 * BuildStyle.TIMBERFRAME.cost.rustic() * com.arno.robotica.architect.ArchitectConfig.demolishRefund());
        helper.assertTrue(table.matter().rustic() == 100 + refund, "two building blocks refund " + refund + " rustic, matter is " + table.matter());
        helper.assertTrue(table.energy.getEnergyStored() < energyBefore, "demolish uses some energy");
        helper.succeed();
    }

    /** Demolish goes through the payload checks, locks the plan while it runs and Cancel stops it with the plan kept. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void demolishIsValidatedAndCancellable(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 9, false);
        BlockPos pos = table.getBlockPos();
        FakePlayer owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("5e1d2c3b-0000-4000-8000-00000000a003"), "architect_owner2"));
        FakePlayer stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("5e1d2c3b-0000-4000-8000-00000000a004"), "architect_stranger2"));
        table.setOwner(owner);
        Layout layout = table.layout();
        ArchitectActionPayload demolish = new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_DEMOLISH, ArchitectTableBlockEntity.DEMOLISH_CONFIRM, 0);
        try {
            owner.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5);
            owner.containerMenu = new ArchitectMenu(1, owner.getInventory(), table);
            helper.assertTrue(ArchitectActionPayload.process(owner, demolish) && !table.demolishing(), "nothing built: nothing to demolish");
            layout.queue(Plots.CENTER, BuildStyle.TIMBERFRAME);
            layout.markBuilt(Plots.CENTER, layout.signature(Plots.CENTER));

            stranger.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5);
            stranger.containerMenu = new ArchitectMenu(2, stranger.getInventory(), table);
            helper.assertTrue(!ArchitectActionPayload.process(stranger, demolish) && !table.demolishing(), "a stranger may not demolish");
            owner.containerMenu = owner.inventoryMenu;
            helper.assertTrue(!ArchitectActionPayload.process(owner, demolish) && !table.demolishing(), "refused without the menu open");
            owner.containerMenu = new ArchitectMenu(3, owner.getInventory(), table);
            owner.setPos(pos.getX() + 30, pos.getY(), pos.getZ());
            helper.assertTrue(!ArchitectActionPayload.process(owner, demolish) && !table.demolishing(), "refused out of reach");
            owner.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 2.5);
            helper.assertTrue(ArchitectActionPayload.process(owner, demolish) && table.demolishing(), "the owner may demolish");
            helper.assertTrue((table.flags() & 256) != 0, "the menu sees the demolish flag");
            com.arno.robotica.compat.MachineInfo info = new com.arno.robotica.compat.MachineInfo();
            table.collectInfo(helper.getLevel(), info);
            helper.assertTrue("working".equals(info.status), "Jade shows a demolishing table as working, was " + info.status);

            int east = Plots.index(1, 0);
            ArchitectActionPayload.process(owner, new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_TOGGLE, east, 0));
            helper.assertTrue(layout.state(east) == Layout.EMPTY, "the plan is locked while demolishing");
            ArchitectActionPayload.process(owner, new ArchitectActionPayload(pos, ArchitectTableBlockEntity.ACTION_CANCEL, 0, 0));
            helper.assertTrue(!table.demolishing() && layout.state(Plots.CENTER) == Layout.BUILT, "Cancel stops demolish, the plot stays planned");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            stranger.containerMenu = stranger.inventoryMenu;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static int[] planWords(ArchitectTableBlockEntity table) {
        int[] w = new int[(Plots.COUNT + 1) / 2];
        for (int i = 0; i < w.length; i++) w[i] = table.planWord(i);
        return w;
    }

    private static ArchitectTableBlockEntity preparedTable(GameTestHelper helper, int band, boolean clear) {
        helper.setBlock(high(band), ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity table = (ArchitectTableBlockEntity) helper.getBlockEntity(high(band));
        table.setMatter(new Matter(5000, 5000, 5000));
        table.energy.setEnergy(table.energy.getMaxEnergyStored());
        table.upgrades.setStackInSlot(0, new ItemStack(CoreItems.card(UpgradeKind.SPEED, 4).get()));
        if (clear) table.handleAction(null, ArchitectTableBlockEntity.ACTION_CLEAR, 1, 0);
        return table;
    }
}
