package com.arno.robotica.logistics.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.side.RelativeSide;
import com.arno.robotica.core.side.SideMode;
import com.arno.robotica.logistics.LogisticsContent;
import com.arno.robotica.logistics.pipe.ItemPipeBlock;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import com.arno.robotica.logistics.pipe.PipeConnection;
import com.arno.robotica.logistics.pipe.PipeMode;
import com.arno.robotica.logistics.pipe.PipeNetwork;
import com.arno.robotica.logistics.pipe.PipeOrder;
import com.arno.robotica.logistics.pipe.PipePriority;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.GrinderBlockEntity;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Item pipes: chest to chest along a pipe line, Off links, filters, Closest First, arms that follow a machine's side config, and the glow route. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class LogisticsGameTests {
    private static final BlockPos SOURCE = new BlockPos(0, 1, 0), TARGET = new BlockPos(2, 1, 2);
    private static final BlockPos FIRST = new BlockPos(0, 1, 1), MIDDLE = new BlockPos(1, 1, 1), LAST = new BlockPos(2, 1, 1);

    /** Chest -> three pipes -> chest; the first pipe pulls from the source chest, the last one inserts. */
    private static ChestBlockEntity line(GameTestHelper helper) {
        helper.setBlock(SOURCE, Blocks.CHEST);
        helper.setBlock(TARGET, Blocks.CHEST);
        ChestBlockEntity source = (ChestBlockEntity) helper.getBlockEntity(SOURCE);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        source.setItem(5, new ItemStack(Items.IRON_INGOT, 3));
        for (BlockPos pos : new BlockPos[]{FIRST, MIDDLE, LAST}) helper.setBlock(pos, LogisticsContent.ITEM_PIPE.get());
        pipe(helper, FIRST).setMode(Direction.NORTH, PipeMode.EXTRACT);
        return source;
    }

    /** A barrel next to the first pipe: the Insert link nearest to the Extract link. */
    private static final BlockPos NEAR = new BlockPos(0, 1, 2);

    private static int count(BarrelBlockEntity barrel, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < barrel.getContainerSize(); i++) if (barrel.getItem(i).is(item)) n += barrel.getItem(i).getCount();
        return n;
    }

    private static ItemPipeBlockEntity pipe(GameTestHelper helper, BlockPos pos) {
        return (ItemPipeBlockEntity) helper.getBlockEntity(pos);
    }

    private static int count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        return n;
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chestToChestThroughPipes(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        helper.succeedWhen(() -> {
            helper.assertBlockProperty(FIRST, ItemPipeBlock.prop(Direction.NORTH), PipeConnection.EXTRACT);
            helper.assertBlockProperty(MIDDLE, ItemPipeBlock.prop(Direction.WEST), PipeConnection.PIPE);
            helper.assertBlockProperty(LAST, ItemPipeBlock.prop(Direction.SOUTH), PipeConnection.INSERT);
            ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(TARGET);
            helper.assertTrue(count(target, Items.COBBLESTONE) == 20 && count(target, Items.IRON_INGOT) == 3,
                    "everything arrived, got " + count(target, Items.COBBLESTONE) + " cobblestone");
            helper.assertTrue(source.isEmpty(), "source chest empty");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void offLinkMovesNothing(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        pipe(helper, LAST).setMode(Direction.SOUTH, PipeMode.DISABLED);
        helper.runAfterDelay(80, () -> {
            helper.assertBlockProperty(LAST, ItemPipeBlock.prop(Direction.SOUTH), PipeConnection.NONE);
            helper.assertTrue(((ChestBlockEntity) helper.getBlockEntity(TARGET)).isEmpty(), "nothing went into the Off link");
            helper.assertTrue(count(source, Items.COBBLESTONE) == 20, "nothing left the source");
            helper.succeed();
        });
    }

    /** A pipe on a Grinder's top inserts there; setting that face to None removes the arm, the capability is gone. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void armFollowsMachineSideConfig(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 1, 1), above = new BlockPos(1, 2, 1);
        helper.setBlock(machine, ProcessingRegistry.GRINDER_MK1.get().defaultBlockState().setValue(ProcessingMachineBlock.FACING, Direction.NORTH));
        helper.setBlock(above, LogisticsContent.ITEM_PIPE.get());
        GrinderBlockEntity grinder = (GrinderBlockEntity) helper.getBlockEntity(machine);
        helper.runAfterDelay(3, () -> {
            helper.assertBlockProperty(above, ItemPipeBlock.prop(Direction.DOWN), PipeConnection.INSERT);
            grinder.sides.set(RelativeSide.TOP, SideMode.NONE);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(grinder.sides.mode(RelativeSide.TOP) == SideMode.NONE, "top not switched off yet");
            helper.assertBlockProperty(above, ItemPipeBlock.prop(Direction.DOWN), PipeConnection.NONE);
        });
    }

    /** Extract blacklist keeps cobblestone in the source; an Insert whitelist sends iron only to the near barrel. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void filtersPickWhatMovesAndWhere(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        source.setItem(9, new ItemStack(Items.GOLD_INGOT, 4));
        helper.setBlock(NEAR, Blocks.BARREL);
        ItemPipeBlockEntity first = pipe(helper, FIRST);
        first.filter(Direction.NORTH).setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        first.filter(Direction.SOUTH).setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        first.setWhitelist(Direction.SOUTH, true);
        helper.succeedWhen(() -> {
            ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(TARGET);
            BarrelBlockEntity near = (BarrelBlockEntity) helper.getBlockEntity(NEAR);
            helper.assertTrue(count(near, Items.IRON_INGOT) == 3 && count(near, Items.GOLD_INGOT) == 0, "only iron in the whitelisted barrel");
            helper.assertTrue(count(target, Items.GOLD_INGOT) == 4 && count(target, Items.IRON_INGOT) == 0, "gold went to the open chest");
            helper.assertTrue(count(source, Items.COBBLESTONE) == 20 && count(target, Items.COBBLESTONE) == 0, "blacklisted cobblestone stayed");
        });
    }

    /** Closest First fills the barrel next to the Extract pipe; the far chest gets nothing while the barrel has room. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void closestFirstFillsNearestLink(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        helper.setBlock(NEAR, Blocks.BARREL);
        pipe(helper, FIRST).setOrder(Direction.NORTH, PipeOrder.CLOSEST_FIRST);
        helper.succeedWhen(() -> {
            BarrelBlockEntity near = (BarrelBlockEntity) helper.getBlockEntity(NEAR);
            helper.assertTrue(source.isEmpty(), "source chest empty");
            helper.assertTrue(count(near, Items.COBBLESTONE) == 20 && count(near, Items.IRON_INGOT) == 3, "everything in the near barrel");
            helper.assertTrue(((ChestBlockEntity) helper.getBlockEntity(TARGET)).isEmpty(), "nothing went to the far chest");
        });
    }

    /** Round robin with the same layout spreads items over both links. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void roundRobinSpreadsItems(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        helper.setBlock(NEAR, Blocks.BARREL);
        helper.succeedWhen(() -> {
            BarrelBlockEntity near = (BarrelBlockEntity) helper.getBlockEntity(NEAR);
            ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(TARGET);
            helper.assertTrue(source.isEmpty(), "source chest empty");
            helper.assertTrue(count(near, Items.COBBLESTONE) > 0 && count(target, Items.COBBLESTONE) > 0, "both links got cobblestone");
        });
    }

    /**
     * The far chest at Highest takes everything although round robin would spread items over both links. The items go in
     * once every arm is linked, so no pull runs before the chest arm exists.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void higherPriorityFillsFirst(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        ItemStack[] held = {source.removeItemNoUpdate(0), source.removeItemNoUpdate(5)};
        helper.setBlock(NEAR, Blocks.BARREL);
        pipe(helper, LAST).setPriority(Direction.SOUTH, PipePriority.HIGHEST);
        helper.runAfterDelay(3, () -> {
            source.setItem(0, held[0]);
            source.setItem(5, held[1]);
        });
        helper.succeedWhen(() -> {
            ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(TARGET);
            helper.assertTrue(source.isEmpty(), "source chest empty");
            helper.assertTrue(count(target, Items.COBBLESTONE) == 20 && count(target, Items.IRON_INGOT) == 3, "everything in the Highest chest");
            helper.assertTrue(((BarrelBlockEntity) helper.getBlockEntity(NEAR)).isEmpty(), "nothing went to the High barrel");
        });
    }

    /** The start slot of the next pull, for a 300 slot source (window of 128 checks) and a small chest. */
    @GameTest(template = "empty")
    public static void pipeScanWindowAdvances(GameTestHelper helper) {
        int w = ItemPipeBlockEntity.SCAN_SLOTS;
        helper.assertTrue(ItemPipeBlockEntity.nextStart(0, 300, w, true, true, true) == 0, "a full pull keeps its start");
        helper.assertTrue(ItemPipeBlockEntity.nextStart(0, 300, w, false, true, true) == w, "a trickling item does not hold the window");
        helper.assertTrue(ItemPipeBlockEntity.nextStart(w, 300, w, false, false, false) == 2 * w, "an empty window moves on");
        helper.assertTrue(ItemPipeBlockEntity.nextStart(2 * w, 300, w, false, false, false) == 0, "an empty lap starts over at slot 0");
        helper.assertTrue(ItemPipeBlockEntity.nextStart(2 * w, 300, w, false, false, true) == (3 * w) % 300, "a window with items wraps");
        helper.assertTrue(ItemPipeBlockEntity.nextStart(5, 27, 27, false, false, false) == 5, "an empty chest keeps its start");
        helper.assertTrue(ItemPipeBlockEntity.nextStart(5, 27, 27, false, false, true) == 6, "a stuck item moves the start by one");
        helper.assertTrue(ItemPipeBlockEntity.nextStart(5, 27, 27, false, true, true) == 5, "a partial pull on a chest keeps its start");
        helper.succeed();
    }

    /**
     * A Low link only gets what the High one refuses: the barrel has one free slot, the iron overflows to the chest.
     * Relies on the pull starting at slot 0 (cobblestone before iron): pulls on the still empty source must not move the
     * start slot, or the iron could take the free slot first.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void lowerPriorityTakesOverflow(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        helper.setBlock(NEAR, Blocks.BARREL);
        BarrelBlockEntity near = (BarrelBlockEntity) helper.getBlockEntity(NEAR);
        for (int i = 0; i < near.getContainerSize() - 1; i++) near.setItem(i, new ItemStack(Items.DIRT, 64));
        ItemStack[] held = {source.removeItemNoUpdate(0), source.removeItemNoUpdate(5)};
        pipe(helper, LAST).setPriority(Direction.SOUTH, PipePriority.LOW);
        helper.runAfterDelay(3, () -> {
            source.setItem(0, held[0]);
            source.setItem(5, held[1]);
        });
        helper.succeedWhen(() -> {
            ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(TARGET);
            helper.assertTrue(source.isEmpty(), "source chest empty");
            helper.assertTrue(count(near, Items.COBBLESTONE) == 20, "cobblestone filled the High barrel");
            helper.assertTrue(count(target, Items.IRON_INGOT) == 3 && count(target, Items.COBBLESTONE) == 0, "only the overflow in the Low chest");
        });
    }

    /**
     * The route the glow lights is the shortest one along the pipe links: two steps along the line, not over the detour
     * above it. Straight pipes (arms on two opposite faces only) have no node.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void glowRouteIsShortestPipePath(GameTestHelper helper) {
        line(helper);
        BlockPos top = new BlockPos(1, 3, 1);
        for (BlockPos p : new BlockPos[]{new BlockPos(0, 2, 1), new BlockPos(0, 3, 1), top, new BlockPos(2, 3, 1), new BlockPos(2, 2, 1)}) {
            helper.setBlock(p, LogisticsContent.ITEM_PIPE_MK2.get());
        }
        helper.runAfterDelay(5, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos from = helper.absolutePos(FIRST), to = helper.absolutePos(LAST);
            PipeNetwork.Route route = pipe(helper, FIRST).network(level).route(level, from, to);
            helper.assertTrue(route != null && route.steps().length == 2, "a two step route");
            BlockPos at = from;
            for (byte step : route.steps()) at = at.relative(Direction.from3DDataValue(step));
            helper.assertTrue(at.equals(to), "the route ends at the last pipe");
            helper.assertTrue(ItemPipeBlock.isStraight(helper.getBlockState(MIDDLE)) && ItemPipeBlock.isStraight(helper.getBlockState(top)),
                    "straight pipes have no node");
            helper.assertFalse(ItemPipeBlock.isStraight(helper.getBlockState(FIRST)), "a T has a node");
            helper.succeed();
        });
    }

    /**
     * A cut link splits the line: nothing reaches the target, the arm is gone on both pipes, a pipe placed again where
     * the cut one was keeps the cut, and joining the link lets the items through.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cutLinkSplitsNetwork(GameTestHelper helper) {
        ChestBlockEntity source = line(helper);
        ItemStack[] held = {source.removeItemNoUpdate(0), source.removeItemNoUpdate(5)};
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(ItemPipeBlock.toggleLink(level, helper.absolutePos(MIDDLE), Direction.EAST), "the link is cut");
            helper.assertBlockProperty(MIDDLE, ItemPipeBlock.prop(Direction.EAST), PipeConnection.NONE);
            helper.assertBlockProperty(LAST, ItemPipeBlock.prop(Direction.WEST), PipeConnection.NONE);
            helper.assertTrue(pipe(helper, FIRST).network(level).size() == 2, "the line split, got " + pipe(helper, FIRST).network(level).size());
            // break the last pipe and place it again: the middle one keeps the cut
            helper.setBlock(LAST, Blocks.AIR);
            helper.setBlock(LAST, LogisticsContent.ITEM_PIPE.get());
            source.setItem(0, held[0]);
            source.setItem(5, held[1]);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertBlockProperty(LAST, ItemPipeBlock.prop(Direction.WEST), PipeConnection.NONE);
            helper.assertTrue(pipe(helper, LAST).isCut(Direction.WEST), "the new pipe took over the cut");
            helper.assertTrue(((ChestBlockEntity) helper.getBlockEntity(TARGET)).isEmpty(), "nothing crossed the cut");
            helper.assertFalse(ItemPipeBlock.toggleLink(helper.getLevel(), helper.absolutePos(LAST), Direction.WEST), "the link is joined");
        });
        helper.runAfterDelay(65, () -> helper.succeedWhen(() -> {
            ChestBlockEntity target = (ChestBlockEntity) helper.getBlockEntity(TARGET);
            helper.assertBlockProperty(MIDDLE, ItemPipeBlock.prop(Direction.EAST), PipeConnection.PIPE);
            helper.assertTrue(count(target, Items.COBBLESTONE) == 20 && count(target, Items.IRON_INGOT) == 3, "everything arrived once joined");
        }));
    }
}
