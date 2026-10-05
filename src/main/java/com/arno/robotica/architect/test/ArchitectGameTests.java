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
                        Shell.Shape shape = new Shell.Shape(sides, corners, doors, corners % 2 == 1);
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
                Shell.Shape shape = new Shell.Shape(sides, 15, doors, false);
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
                // Corner posts only where neither side touching the corner is shared.
                for (int corner = 0; corner < 4; corner++) {
                    int x = (corner & 1) == 0 ? 0 : last, z = (corner & 2) == 0 ? 0 : last;
                    int[] cs = Plots.cornerSides(corner);
                    boolean outer = (sides & Plots.bit(cs[0])) == 0 && (sides & Plots.bit(cs[1])) == 0;
                    helper.assertTrue(at.get(key(x, 2, z)).equals(Piece.POST) == outer, "corner " + corner + " post mismatch in " + shape);
                }
            }
        }
        // A 2x2 block of plots has an open middle; the inner corner of an L is closed with wall.
        Shell.Shape quad = new Shell.Shape(Plots.bit(Plots.E) | Plots.bit(Plots.S), 1 << Plots.SE, Plots.bit(Plots.N), false);
        Map<Long, Piece> q = index(Shell.generate(quad, false));
        helper.assertTrue(q.get(key(last, 2, last)).isAir() && q.get(key(last, 0, last)).equals(Piece.FLOOR), "middle of a 2x2 hall is open");
        Shell.Shape ell = new Shell.Shape(Plots.bit(Plots.E) | Plots.bit(Plots.S), 0, Plots.bit(Plots.N), false);
        Map<Long, Piece> l = index(Shell.generate(ell, false));
        helper.assertTrue(l.get(key(last, 2, last)).equals(Piece.WALL), "inner corner of an L is wall");
        // Seam corner: the outside wall runs on as plain wall, its trim continues along the wall.
        helper.assertTrue(l.get(key(last, 2, 0)).equals(Piece.WALL) && l.get(key(last, 0, 0)).equals(Piece.pillar(Direction.Axis.X)), "seam corner is wall with trim");
        // Door frame: posts at 2 and 6, a lintel above.
        Map<Long, Piece> lone = index(Shell.generate(Shell.lone(Plots.S), false));
        helper.assertTrue(lone.get(key(2, 1, last)).equals(Piece.POST) && lone.get(key(6, 3, last)).equals(Piece.POST), "door posts");
        helper.assertTrue(lone.get(key(4, 4, last)).equals(Piece.pillar(Direction.Axis.X)), "door lintel");
        helper.assertTrue(lone.get(key(4, 2, 0)).equals(Piece.WINDOW) && lone.get(key(4, 1, 0)).equals(Piece.WALL), "the other sides keep their windows");
        // Lights: 5 on even plots, 4 on odd ones, 3 apart across the seam between them.
        long even = lone.values().stream().filter(Piece.LIGHT::equals).count();
        long odd = Shell.generate(new Shell.Shape(0, 0, 1, true), false).stream().filter(op -> op.piece().equals(Piece.LIGHT)).count();
        helper.assertTrue(even == 5 && odd == 4, "light lattice " + even + "/" + odd);
        helper.assertTrue(Shell.light(new Shell.Shape(0, 0, 0, false), 7, 7) && Shell.light(new Shell.Shape(0, 0, 0, true), 1, 4), "lattice continues across plots");
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
        helper.assertTrue(layout.needsWork(Plots.CENTER) && !layout.needsWork(west), "the centre opens toward the new plot, the west plot only touches diagonally");
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

    /** Clear terrain removes plain blocks in the footprint (and pays rustic matter) but never block entities. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void clearTerrainRemovesBlocksButKeepsContainers(GameTestHelper helper) {
        ArchitectTableBlockEntity table = preparedTable(helper, 2, true);
        BlockPos origin = Plots.origin(table.getBlockPos(), Plots.CENTER);
        BlockPos stone1 = origin.offset(3, 1, 3);
        BlockPos stone2 = origin.offset(5, 2, 3);
        BlockPos chest = origin.offset(3, 1, 5);
        helper.getLevel().setBlockAndUpdate(stone1, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(stone2, Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        helper.assertTrue(table.clearTerrain(), "clear terrain should be on");
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_TOGGLE, Plots.CENTER, 0);
        table.handleAction(null, ArchitectTableBlockEntity.ACTION_BUILD, 0, 0);
        helper.succeedWhen(() -> {
            helper.assertTrue(!table.layout().hasWork(), "build should be finished, status " + table.status());
            helper.assertTrue(helper.getLevel().getBlockState(stone1).isAir() && helper.getLevel().getBlockState(stone2).isAir(), "stone in the footprint should be gone");
            helper.assertTrue(helper.getLevel().getBlockState(chest).is(Blocks.CHEST), "containers must stay");
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
        helper.assertTrue(same.energy.getEnergyStored() == 0, "a new table starts without energy");

        // another spot: built plots stay behind, queued ones move along
        BlockPos elsewhere = high(5).offset(20, 0, 0);
        helper.setBlock(elsewhere, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState());
        ArchitectTableBlockEntity moved = (ArchitectTableBlockEntity) helper.getBlockEntity(elsewhere);
        moved.applyComponentsFromItemStack(stack);
        helper.assertTrue(moved.layout().state(queued) == Layout.QUEUED, "unbuilt work moves with the table");
        helper.assertTrue(moved.layout().state(built) == Layout.EMPTY, "built plots stay where they stand");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static int[] planWords(ArchitectTableBlockEntity table) {
        int[] w = new int[9];
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
