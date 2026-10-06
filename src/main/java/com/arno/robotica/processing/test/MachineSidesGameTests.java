package com.arno.robotica.processing.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.side.RelativeSide;
import com.arno.robotica.core.side.SideMode;
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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Machine side configuration (core {@code SideConfig}) on a Grinder: capabilities per face and auto-transfer. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class MachineSidesGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private static GrinderBlockEntity grinder(GameTestHelper helper) {
        helper.setBlock(POS, ProcessingRegistry.GRINDER_MK1.get().defaultBlockState().setValue(ProcessingMachineBlock.FACING, Direction.NORTH));
        return (GrinderBlockEntity) helper.getBlockEntity(POS);
    }

    private static IItemHandler cap(GameTestHelper helper, Direction side) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POS), side);
    }

    /** Defaults keep the old rules (bottom only extracts); None removes the capability, Input and Output limit it. */
    @GameTest(template = "empty")
    public static void sideModesGateCapability(GameTestHelper helper) {
        GrinderBlockEntity be = grinder(helper);
        be.items.setStackInSlot(GrinderBlockEntity.OUT_FIRST, new ItemStack(Items.GRAVEL, 4));
        ItemStack ore = new ItemStack(Items.IRON_ORE);

        IItemHandler bottom = cap(helper, Direction.DOWN);
        helper.assertTrue(bottom != null && bottom.insertItem(GrinderBlockEntity.INPUT, ore, true).getCount() == 1, "bottom does not take input");
        helper.assertTrue(!bottom.extractItem(GrinderBlockEntity.OUT_FIRST, 1, true).isEmpty(), "bottom gives output");
        IItemHandler top = cap(helper, Direction.UP);
        helper.assertTrue(top != null && top.insertItem(GrinderBlockEntity.INPUT, ore, true).isEmpty(), "top takes input by default");

        be.sides.set(RelativeSide.FRONT, SideMode.NONE);
        helper.assertTrue(cap(helper, Direction.NORTH) == null, "None: no capability on the front");

        be.sides.set(RelativeSide.LEFT, SideMode.INPUT);   // facing north, the left face points east
        IItemHandler left = cap(helper, Direction.EAST);
        helper.assertTrue(left != null && left.insertItem(GrinderBlockEntity.INPUT, ore, true).isEmpty(), "Input takes input");
        helper.assertTrue(left.extractItem(GrinderBlockEntity.OUT_FIRST, 1, true).isEmpty(), "Input gives nothing out");

        be.sides.set(RelativeSide.RIGHT, SideMode.OUTPUT);
        IItemHandler right = cap(helper, Direction.WEST);
        helper.assertTrue(right != null && right.insertItem(GrinderBlockEntity.INPUT, ore, true).getCount() == 1, "Output takes nothing in");
        helper.assertTrue(!right.extractItem(GrinderBlockEntity.OUT_FIRST, 1, true).isEmpty(), "Output gives output");
        helper.assertTrue(right.extractItem(GrinderBlockEntity.INPUT, 1, true).isEmpty(), "never the input");

        helper.assertTrue(be.automation(null) != null, "a null side keeps the plain rules");
        helper.succeed();
    }

    /** Auto-eject pushes outputs into the chest below; auto-input pulls ore from the chest on top. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void autoEjectAndInput(GameTestHelper helper) {
        BlockPos below = POS.below(), above = POS.above();
        helper.setBlock(below, Blocks.CHEST);
        helper.setBlock(above, Blocks.CHEST);
        GrinderBlockEntity be = grinder(helper);
        be.items.setStackInSlot(GrinderBlockEntity.OUT_FIRST, new ItemStack(Items.GRAVEL, 5));
        ChestBlockEntity source = (ChestBlockEntity) helper.getBlockEntity(above);
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        be.sides.set(RelativeSide.TOP, SideMode.INPUT);   // else the top chest would get outputs too
        be.sides.setAutoEject(true);
        be.sides.setAutoInput(true);
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(below);
            helper.assertTrue(chest.getItem(0).is(Items.GRAVEL) && chest.getItem(0).getCount() == 5, "gravel ejected into the chest below");
            helper.assertTrue(be.items.getStackInSlot(GrinderBlockEntity.OUT_FIRST).isEmpty(), "output slot empty");
            helper.assertTrue(source.getItem(0).isEmpty(), "cobblestone pulled from the chest on top");
        });
    }
}
