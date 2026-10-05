package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.block.FarmBotBlock;
import com.arno.robotica.automation.entity.ExcavatorBlockEntity;
import com.arno.robotica.automation.entity.SproutBlockEntity;
import com.arno.robotica.automation.entity.StumpyBlockEntity;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.automation.entity.FarmBotBlockEntity;
import java.util.function.Consumer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless tests of the automation module: ./gradlew runGameTestServer */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class AutomationGameTests {

    private static ItemStack chargedCell() {
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        ItemEnergy.fill(cell);
        return cell;
    }

    private static int count(ChestBlockEntity chest, Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        }
        return n;
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stumpyFellsTreeAndReplants(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        BlockPos chestPos = new BlockPos(2, 1, 0);
        helper.setBlock(new BlockPos(1, 0, 2), Blocks.DIRT);
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(1, y, 2), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(1, 4, 2), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(0, 3, 2), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(1, 3, 3), Blocks.OAK_LEAVES);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        stumpy.battery.setStackInSlot(0, chargedCell());
        stumpy.buffer.setStackInSlot(0, new ItemStack(Items.OAK_SAPLING, 2));
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.OAK_LOG) == 3, "Chest should hold the 3 felled logs, has " + count(chest, Items.OAK_LOG));
            helper.assertBlockPresent(Blocks.OAK_SAPLING, new BlockPos(1, 1, 2));
            helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(1, 4, 2));
            helper.assertTrue(stumpy.energy.getEnergyStored() < stumpy.energy.getMaxEnergyStored(), "Felling must cost energy");
        });
    }

    /** Every log costs FE: with too little in the buffer and no battery Stumpy leaves the tree standing. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stumpyPaysPerLog(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        helper.setBlock(new BlockPos(1, 0, 2), Blocks.DIRT);
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(1, y, 2), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(1, 4, 2), Blocks.OAK_LEAVES);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        // Enough for the work ticks of a few attempts, not for 3 logs.
        stumpy.energy.setEnergy(3 * AutomationConfig.stumpyFePerLog() - 1);
        helper.runAfterDelay(150, () -> {
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, 1, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stumpyIgnoresLogsWithoutLeaves(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        for (int y = 1; y <= 2; y++) helper.setBlock(new BlockPos(1, y, 2), Blocks.OAK_LOG);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        stumpy.battery.setStackInSlot(0, chargedCell());
        helper.runAfterDelay(150, () -> {
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, 1, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutHarvestsAndReplants(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        BlockPos chestPos = new BlockPos(2, 1, 0);
        BlockPos crop = new BlockPos(1, 1, 2);
        helper.setBlock(new BlockPos(1, 0, 2), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(bot, AutomationContent.SPROUT.get());
        SproutBlockEntity sprout = helper.getBlockEntity(bot);
        sprout.battery.setStackInSlot(0, chargedCell());
        sprout.buffer.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 1));
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.WHEAT) >= 1, "Chest should hold the harvested wheat");
            BlockState state = helper.getBlockState(crop);
            helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 0, "Wheat should be replanted at age 0, is " + state);
        });
    }

    /** Every harvest costs FE: a buffer that covers the work ticks but not the harvest leaves the crop standing. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutPaysPerHarvest(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        BlockPos crop = new BlockPos(1, 1, 2);
        helper.setBlock(new BlockPos(1, 0, 2), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        helper.setBlock(bot, AutomationContent.SPROUT.get());
        SproutBlockEntity sprout = helper.getBlockEntity(bot);
        // Exactly one action's work ticks plus one FE short of the harvest (without the harvest cost this would reap the wheat).
        int workTicks = sprout.actionInterval() * CoreConfig.scaleEnergy(AutomationConfig.sproutFe());
        sprout.energy.setEnergy(workTicks + CoreConfig.scaleEnergy(AutomationConfig.sproutFePerHarvest()) - 1);
        helper.runAfterDelay(150, () -> {
            BlockState state = helper.getBlockState(crop);
            helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 7, "too little FE for the harvest: the crop waits, is " + state);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void excavatorMinesIntoChest(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 2, 1);
        BlockPos chestPos = new BlockPos(2, 2, 1);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(machine, AutomationContent.EXCAVATOR.get());
        ExcavatorBlockEntity excavator = helper.getBlockEntity(machine);
        excavator.setSizeOverride(3);
        excavator.battery.setStackInSlot(0, chargedCell());
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.COBBLESTONE) >= 1, "Chest should hold cobblestone from the mined stone");
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(1, 1, 1));
        });
    }

    @GameTest(template = "empty")
    public static void kitsApplyInOrder(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 1);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(bot);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        ItemStack mk3 = new ItemStack(AutomationContent.FARM_KIT_MK3.get());
        helper.getBlockState(bot).useItemOn(mk3, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(helper.getBlockState(bot).getValue(FarmBotBlock.TIER) == 1, "Mk3 kit must not apply to a Mk1 bot");
        ItemStack mk2 = new ItemStack(AutomationContent.FARM_KIT_MK2.get());
        helper.getBlockState(bot).useItemOn(mk2, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(helper.getBlockState(bot).getValue(FarmBotBlock.TIER) == 2, "Mk2 kit should upgrade to Mk2");
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        helper.assertTrue(stumpy.areaSize() == 11, "Stumpy Mk2 area is 11x11 (one less per side than Sprout), is " + stumpy.areaSize());
        helper.getBlockState(bot).useItemOn(mk3, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(helper.getBlockState(bot).getValue(FarmBotBlock.TIER) == 3, "Mk3 kit should upgrade a Mk2 bot");
        helper.succeed();
    }

    /** Fluids and blocks the BreakEvent vetoes must be left alone, and the cursor must still move on. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void excavatorHonoursBreakEventVeto(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 2, 1);
        BlockPos stone = new BlockPos(1, 1, 1);
        BlockPos lava = new BlockPos(0, 1, 1);
        BlockPos freeStone = new BlockPos(2, 1, 1);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(freeStone, Blocks.STONE);
        helper.setBlock(lava, Blocks.LAVA);
        helper.setBlock(machine, AutomationContent.EXCAVATOR.get());
        BlockPos absStone = helper.absolutePos(stone);
        BlockPos absLava = helper.absolutePos(lava);
        Consumer<BlockEvent.BreakEvent> veto = event -> {
            if (event.getPos().equals(absStone) || event.getPos().equals(absLava)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        ExcavatorBlockEntity excavator = helper.getBlockEntity(machine);
        excavator.setSizeOverride(3);
        excavator.battery.setStackInSlot(0, chargedCell());
        helper.runAfterDelay(200, () -> {
            NeoForge.EVENT_BUS.unregister(veto);
            helper.assertBlockNotPresent(Blocks.STONE, freeStone);
            helper.assertBlockPresent(Blocks.STONE, stone);
            helper.assertBlockPresent(Blocks.LAVA, lava);
            helper.succeed();
        });
    }

    /** Mk4 with four speed cards is already at the 1 tick floor: it must not pay for nothing. Mk1 takes one speed card. */
    @GameTest(template = "empty")
    public static void flooredSpeedDoesNotRaiseDrain(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 1);
        helper.setBlock(bot, AutomationContent.STUMPY.get().defaultBlockState().setValue(FarmBotBlock.TIER, 4));
        FarmBotBlockEntity be = helper.getBlockEntity(bot);
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 4));
        helper.assertTrue(be.actionInterval() == 1, "Mk4 interval is floored at 1, is " + be.actionInterval());
        helper.assertTrue(be.effectiveSpeedMultiplier() == 1, "floored interval gains nothing, multiplier " + be.effectiveSpeedMultiplier());
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        FarmBotBlockEntity mk1 = helper.getBlockEntity(bot);
        mk1.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 4));
        helper.assertTrue(mk1.upgrades.level(UpgradeKind.SPEED) == 1, "Mk1 counts one speed card, got " + mk1.upgrades.level(UpgradeKind.SPEED));
        helper.assertTrue(mk1.effectiveSpeedMultiplier() == 2, "Mk1 with one card is a real x2, got " + mk1.effectiveSpeedMultiplier());
        helper.succeed();
    }

    /** The vacuum leaves a player's thrown items alone and obeys the pickup event veto. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void vacuumSkipsPlayerItemsAndHonoursVeto(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 1);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        stumpy.battery.setStackInSlot(0, chargedCell());
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        ItemEntity thrown = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.OAK_SAPLING, 3));
        thrown.setThrower(player);
        ItemEntity vetoed = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.APPLE, 2));
        ItemEntity natural = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.STICK, 4));
        level.addFreshEntity(thrown);
        level.addFreshEntity(vetoed);
        level.addFreshEntity(natural);
        Consumer<ItemEntityPickupEvent.Pre> veto = event -> {
            if (event.getItemEntity() == vetoed) event.setCanPickup(TriState.FALSE);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        helper.succeedWhen(() -> {
            int sticks = 0;
            for (int i = 0; i < stumpy.buffer.getSlots(); i++) {
                if (stumpy.buffer.getStackInSlot(i).is(Items.STICK)) sticks += stumpy.buffer.getStackInSlot(i).getCount();
            }
            helper.assertTrue(sticks == 4, "natural sticks should be collected, got " + sticks);
            NeoForge.EVENT_BUS.unregister(veto);
            helper.assertTrue(thrown.isAlive(), "an item thrown by a player must stay");
            helper.assertTrue(vetoed.isAlive(), "a vetoed pickup must stay");
        });
    }
}
