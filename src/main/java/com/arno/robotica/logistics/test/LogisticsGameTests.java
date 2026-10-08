package com.arno.robotica.logistics.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.side.RelativeSide;
import com.arno.robotica.core.side.SideMode;
import com.arno.robotica.logistics.LogisticsContent;
import com.arno.robotica.logistics.pipe.ItemPipeBlock;
import com.arno.robotica.logistics.pipe.ItemPipeBlockEntity;
import com.arno.robotica.logistics.pipe.PipeConnection;
import com.arno.robotica.logistics.pipe.PipeMode;
import com.arno.robotica.logistics.pipe.PipeOrder;
import com.arno.robotica.logistics.pipe.PipePriority;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.GrinderBlockEntity;
import com.arno.robotica.processing.block.ProcessingMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Item pipes: chest to chest along a pipe line, Off links, filters, Closest First, and arms that follow a machine's side config. */
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
}
