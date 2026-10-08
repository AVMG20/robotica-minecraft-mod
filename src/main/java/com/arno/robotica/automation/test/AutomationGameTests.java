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
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

    /** A huge tree (90 logs) costs at most half the buffer, so a working Stumpy (never exactly full) still fells it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stumpyFellsHugeTrees(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                helper.setBlock(new BlockPos(dx, 0, 2 + dz), Blocks.DIRT);
                for (int y = 1; y <= 10; y++) helper.setBlock(new BlockPos(dx, y, 2 + dz), Blocks.OAK_LOG);
            }
        }
        helper.setBlock(new BlockPos(1, 11, 3), Blocks.OAK_LEAVES);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        // No battery: a little under full, like a buffer that drains while it works.
        stumpy.energy.setEnergy(stumpy.energy.getMaxEnergyStored() - 1_000);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(1, 5, 3)));
    }

    /** A one-log-wide trunk on dirt with a leaf on top. */
    private static void columnTree(GameTestHelper helper, int x, int z, int height) {
        helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
        for (int y = 1; y <= height; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(x, height + 1, z), Blocks.OAK_LEAVES);
    }

    private static int logItems(GameTestHelper helper) {
        int n = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(12))) {
            if (item.getItem().is(Items.OAK_LOG)) n += item.getItem().getCount();
        }
        return n;
    }

    private static int standingLogs(GameTestHelper helper, int x, int z, int height) {
        int n = 0;
        for (int y = 1; y <= height; y++) {
            if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LOG)) n++;
        }
        return n;
    }

    /** The tree comes down bottom-up over several ticks; the FE is paid once before the wave and the logs reach the chest. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stumpyFellsTreeInAWave(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        BlockPos chestPos = new BlockPos(2, 1, 0);
        int height = 6;
        columnTree(helper, 1, 2, height);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        int start = stumpy.energy.getMaxEnergyStored() - 1_000;
        stumpy.energy.setEnergy(start);
        int cost = height * CoreConfig.scaleEnergy(AutomationConfig.stumpyFePerLog());
        int[] waveEnergy = {-1};
        int[] waveTicks = {0};
        helper.onEachTick(() -> {
            if (!stumpy.waveActive()) return;
            int energy = stumpy.energy.getEnergyStored();
            if (waveEnergy[0] < 0) {
                waveEnergy[0] = energy;
                helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(1, height, 2));
                helper.assertTrue(start - energy >= cost, "The whole tree is paid up front: " + (start - energy) + " < " + cost);
            }
            helper.assertTrue(energy == waveEnergy[0], "No FE is paid during the wave");
            waveTicks[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(waveEnergy[0] >= 0, "Wave started");
            helper.assertFalse(stumpy.waveActive(), "Wave finished");
            helper.assertTrue(standingLogs(helper, 1, 2, height) == 0, "All logs felled");
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.OAK_LOG) == height, "Chest should hold " + height + " logs, has " + count(chest, Items.OAK_LOG));
            helper.assertTrue(waveTicks[0] >= 10, "The wave takes a while, took " + waveTicks[0] + " ticks");
        });
    }

    /** Breaking Stumpy mid-wave: the felled logs drop with its contents, the rest keeps standing. Nothing lost or duplicated. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stumpyRemovedMidWaveKeepsEveryLog(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        int height = 9;
        columnTree(helper, 1, 2, height);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity stumpy = helper.getBlockEntity(bot);
        stumpy.battery.setStackInSlot(0, chargedCell());
        boolean[] removed = {false};
        helper.onEachTick(() -> {
            if (!removed[0] && stumpy.waveActive() && standingLogs(helper, 1, 2, height) <= height - 3) {
                removed[0] = true;
                helper.setBlock(bot, Blocks.AIR);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(removed[0], "Removed mid-wave");
            int standing = standingLogs(helper, 1, 2, height);
            helper.assertTrue(standing > 0, "The rest of the tree stays");
            helper.assertTrue(standing + logItems(helper) == height,
                    "Logs standing (" + standing + ") plus dropped (" + logItems(helper) + ") must be " + height);
        });
    }

    /** An unloaded or reloaded Stumpy saves its wave and finishes it: every log ends up in the chest exactly once. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void stumpyWaveSurvivesReload(GameTestHelper helper) {
        BlockPos bot = new BlockPos(1, 1, 0);
        BlockPos chestPos = new BlockPos(2, 1, 0);
        int height = 9;
        columnTree(helper, 1, 2, height);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(bot, AutomationContent.STUMPY.get());
        StumpyBlockEntity first = helper.getBlockEntity(bot);
        first.battery.setStackInSlot(0, chargedCell());
        boolean[] reloaded = {false};
        helper.onEachTick(() -> {
            if (!reloaded[0] && first.waveActive() && standingLogs(helper, 1, 2, height) <= height - 3) {
                reloaded[0] = true;
                var registries = helper.getLevel().registryAccess();
                var saved = first.saveWithoutMetadata(registries);
                first.keepContents = true;
                helper.setBlock(bot, Blocks.AIR);
                helper.setBlock(bot, AutomationContent.STUMPY.get());
                StumpyBlockEntity second = helper.getBlockEntity(bot);
                second.loadCustomOnly(saved, registries);
                helper.assertTrue(second.waveActive(), "The wave is saved");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(reloaded[0], "Reloaded mid-wave");
            StumpyBlockEntity second = helper.getBlockEntity(bot);
            helper.assertFalse(second.waveActive(), "Wave finished");
            helper.assertTrue(standingLogs(helper, 1, 2, height) == 0, "All logs felled");
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.OAK_LOG) == height, "Chest should hold " + height + " logs, has " + count(chest, Items.OAK_LOG));
            helper.assertTrue(logItems(helper) == 0, "Nothing dropped");
        });
    }

    private static int bufferLogs(StumpyBlockEntity stumpy) {
        int n = 0;
        for (int i = 0; i < stumpy.buffer.getSlots(); i++) {
            if (stumpy.buffer.getStackInSlot(i).is(Items.OAK_LOG)) n += stumpy.buffer.getStackInSlot(i).getCount();
        }
        return n;
    }

    /** Two Stumpys reaching the same tree: one fells and pays for it, the other leaves its logs alone. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overlappingStumpysFellATreeOnce(GameTestHelper helper) {
        int height = 6;
        columnTree(helper, 1, 2, height);
        helper.setBlock(new BlockPos(1, 1, 0), AutomationContent.STUMPY.get());
        helper.setBlock(new BlockPos(1, 1, 4), AutomationContent.STUMPY.get());
        StumpyBlockEntity a = helper.getBlockEntity(new BlockPos(1, 1, 0));
        StumpyBlockEntity b = helper.getBlockEntity(new BlockPos(1, 1, 4));
        int start = a.energy.getMaxEnergyStored() - 1_000;
        a.energy.setEnergy(start);
        b.energy.setEnergy(start);
        int cost = height * CoreConfig.scaleEnergy(AutomationConfig.stumpyFePerLog());
        helper.onEachTick(() -> helper.assertFalse(a.waveActive() && b.waveActive(), "Only one Stumpy fells the tree"));
        helper.succeedWhen(() -> {
            helper.assertTrue(standingLogs(helper, 1, 2, height) == 0, "All logs felled");
            helper.assertFalse(a.waveActive() || b.waveActive(), "Wave finished");
            helper.assertTrue(bufferLogs(a) + bufferLogs(b) == height, "Logs felled once: " + (bufferLogs(a) + bufferLogs(b)));
            int paid = (start - a.energy.getEnergyStored() >= cost ? 1 : 0) + (start - b.energy.getEnergyStored() >= cost ? 1 : 0);
            helper.assertTrue(paid == 1, "Exactly one Stumpy pays for the tree, " + paid + " did");
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

    /** Sugar cane and cactus are cut above their bottom block, which stays to grow back. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutCutsCaneAndCactus(GameTestHelper helper) {
        // The template is 3x3: the cactus sits in the middle with nothing solid beside it, the chest on top of the bot.
        BlockPos bot = new BlockPos(0, 1, 0);
        BlockPos chestPos = new BlockPos(0, 2, 0);
        BlockPos cane = new BlockPos(2, 1, 2), cactus = new BlockPos(1, 1, 1);
        helper.setBlock(cane.below(), Blocks.SAND);
        helper.setBlock(cane.below().north(), Blocks.WATER);
        helper.setBlock(cactus.below(), Blocks.SAND);
        for (int i = 0; i < 3; i++) {
            helper.setBlock(cane.above(i), Blocks.SUGAR_CANE);
            helper.setBlock(cactus.above(i), Blocks.CACTUS);
        }
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(bot, AutomationContent.SPROUT.get());
        SproutBlockEntity sprout = helper.getBlockEntity(bot);
        sprout.battery.setStackInSlot(0, chargedCell());
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.SUGAR_CANE) >= 2, "sugar cane cut, got " + count(chest, Items.SUGAR_CANE));
            helper.assertTrue(count(chest, Items.CACTUS) >= 2, "cactus cut, got " + count(chest, Items.CACTUS));
            helper.assertBlockPresent(Blocks.SUGAR_CANE, cane);
            helper.assertBlockPresent(Blocks.CACTUS, cactus);
            helper.assertBlockNotPresent(Blocks.SUGAR_CANE, cane.above());
            helper.assertBlockNotPresent(Blocks.CACTUS, cactus.above());
        });
    }

    /** Puts a charged Sprout at (0,1,0) with a chest on top (room for plants in the rest of the 3x3 template). */
    private static SproutBlockEntity cornerSprout(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 2, 0), Blocks.CHEST);
        helper.setBlock(new BlockPos(0, 1, 0), AutomationContent.SPROUT.get());
        SproutBlockEntity sprout = helper.getBlockEntity(new BlockPos(0, 1, 0));
        sprout.battery.setStackInSlot(0, chargedCell());
        return sprout;
    }

    private static int chestCount(GameTestHelper helper, Item item) {
        return count(helper.getBlockEntity(new BlockPos(0, 2, 0)), item);
    }

    private static int bufferCount(SproutBlockEntity sprout, Item item) {
        int n = 0;
        for (int i = 0; i < sprout.buffer.getSlots(); i++) {
            if (sprout.buffer.getStackInSlot(i).is(item)) n += sprout.buffer.getStackInSlot(i).getCount();
        }
        return n;
    }

    /** Melons and pumpkins are broken off their attached stems; the ground beside a stem is not tilled or planted. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutPicksMelonsAndPumpkins(GameTestHelper helper) {
        BlockPos melonStem = new BlockPos(1, 1, 1), melon = new BlockPos(2, 1, 1);
        BlockPos pumpkinStem = new BlockPos(1, 1, 2), pumpkin = new BlockPos(0, 1, 2);
        helper.setBlock(melonStem.below(), Blocks.FARMLAND);
        helper.setBlock(pumpkinStem.below(), Blocks.FARMLAND);
        helper.setBlock(melon.below(), Blocks.DIRT);
        helper.setBlock(pumpkin.below(), Blocks.DIRT);
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.WATER);
        helper.setBlock(melon, Blocks.MELON);
        helper.setBlock(pumpkin, Blocks.PUMPKIN);
        helper.setBlock(melonStem, Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
        helper.setBlock(pumpkinStem, Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.WEST));
        SproutBlockEntity sprout = cornerSprout(helper);
        // Seeds and water nearby: without the stem rule Sprout would till the fruit spots and plant wheat there.
        sprout.buffer.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 4));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(chestCount(helper, Items.MELON_SLICE) >= 3, "melon picked, got " + chestCount(helper, Items.MELON_SLICE));
                    helper.assertTrue(chestCount(helper, Items.PUMPKIN) >= 1, "pumpkin picked, got " + chestCount(helper, Items.PUMPKIN));
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.DIRT, melon.below());
                    helper.assertBlockPresent(Blocks.DIRT, pumpkin.below());
                    helper.assertTrue(!helper.getBlockState(melonStem).isAir() && !helper.getBlockState(pumpkinStem).isAir(), "stems stay");
                })
                .thenSucceed();
    }

    /** Bamboo is cut above its bottom block like sugar cane. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutCutsBamboo(GameTestHelper helper) {
        BlockPos bamboo = new BlockPos(1, 1, 1);
        helper.setBlock(bamboo.below(), Blocks.DIRT);
        for (int i = 0; i < 5; i++) helper.setBlock(bamboo.above(i), Blocks.BAMBOO);
        cornerSprout(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(chestCount(helper, Items.BAMBOO) >= 4, "bamboo cut, got " + chestCount(helper, Items.BAMBOO));
            helper.assertBlockPresent(Blocks.BAMBOO, bamboo);
            helper.assertBlockNotPresent(Blocks.BAMBOO, bamboo.above(2));
        });
    }

    /** Kelp is cut above its bottom block and leaves water behind. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutCutsKelp(GameTestHelper helper) {
        BlockPos kelp = new BlockPos(1, 1, 1);
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 1; y <= 3; y++) {
                    if (x != 0 || z != 0) helper.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        helper.setBlock(kelp.below(), Blocks.SAND);
        helper.setBlock(kelp, Blocks.KELP_PLANT);
        helper.setBlock(kelp.above(), Blocks.KELP_PLANT);
        helper.setBlock(kelp.above(2), Blocks.KELP);
        cornerSprout(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(chestCount(helper, Items.KELP) >= 2, "kelp cut, got " + chestCount(helper, Items.KELP));
            BlockState bottom = helper.getBlockState(kelp);
            helper.assertTrue(bottom.is(Blocks.KELP) || bottom.is(Blocks.KELP_PLANT), "bottom kelp stays, is " + bottom);
            helper.assertBlockPresent(Blocks.WATER, kelp.above(2));
        });
    }

    /** Glow berries are picked off the vine, which stays; the berries go to the chest, not the seed buffer. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutPicksGlowBerries(GameTestHelper helper) {
        BlockPos tip = new BlockPos(1, 1, 1), vine = tip.above();
        helper.setBlock(vine.above(), Blocks.STONE);
        helper.setBlock(vine, Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVines.BERRIES, true));
        helper.setBlock(tip, Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, true));
        cornerSprout(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(chestCount(helper, Items.GLOW_BERRIES) == 2, "both berries picked, got " + chestCount(helper, Items.GLOW_BERRIES));
            helper.assertTrue(helper.getBlockState(vine).is(Blocks.CAVE_VINES_PLANT) && !helper.getBlockState(vine).getValue(CaveVines.BERRIES), "vine stays, picked");
            helper.assertTrue(helper.getBlockState(tip).is(Blocks.CAVE_VINES) && !helper.getBlockState(tip).getValue(CaveVines.BERRIES), "tip stays, picked");
        });
    }

    /** A grown pitcher plant is harvested (both halves) and replanted from a pitcher pod in the buffer. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutHarvestsPitcherPlant(GameTestHelper helper) {
        BlockPos crop = new BlockPos(1, 1, 1);
        BlockState grown = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, grown.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(crop.above(), grown.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        SproutBlockEntity sprout = cornerSprout(helper);
        sprout.buffer.setStackInSlot(0, new ItemStack(Items.PITCHER_POD, 1));
        helper.succeedWhen(() -> {
            helper.assertTrue(chestCount(helper, Items.PITCHER_PLANT) == 1, "pitcher plant harvested, got " + chestCount(helper, Items.PITCHER_PLANT));
            BlockState state = helper.getBlockState(crop);
            helper.assertTrue(state.is(Blocks.PITCHER_CROP) && state.getValue(PitcherCropBlock.AGE) < 3, "replanted from the pod, is " + state);
            helper.assertTrue(bufferCount(sprout, Items.PITCHER_POD) == 0, "pod used");
            helper.assertTrue(helper.getBlockState(crop.above()).isAir(), "top half removed");
        });
    }

    /** A grown torchflower on farmland is harvested and replanted from torchflower seeds in the buffer. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sproutHarvestsTorchflower(GameTestHelper helper) {
        BlockPos crop = new BlockPos(1, 1, 1);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.TORCHFLOWER);
        SproutBlockEntity sprout = cornerSprout(helper);
        sprout.buffer.setStackInSlot(0, new ItemStack(Items.TORCHFLOWER_SEEDS, 1));
        helper.succeedWhen(() -> {
            helper.assertTrue(chestCount(helper, Items.TORCHFLOWER) == 1, "torchflower harvested, got " + chestCount(helper, Items.TORCHFLOWER));
            helper.assertBlockPresent(Blocks.TORCHFLOWER_CROP, crop);
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

    /** Mk4 with four speed cards is already at the 1 tick floor: it must not pay for nothing. Mk1 takes two speed cards. */
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
        helper.assertTrue(mk1.upgrades.level(UpgradeKind.SPEED) == 2, "Mk1 counts two speed cards, got " + mk1.upgrades.level(UpgradeKind.SPEED));
        helper.assertTrue(mk1.effectiveSpeedMultiplier() == 3, "Mk1 with two cards is a real x3, got " + mk1.effectiveSpeedMultiplier());
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
