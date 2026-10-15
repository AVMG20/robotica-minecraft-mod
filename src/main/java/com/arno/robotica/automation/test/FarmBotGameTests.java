package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.RobotArea;
import com.arno.robotica.automation.farmbot.FarmBot;
import com.arno.robotica.automation.farmbot.FarmBotContent;
import com.arno.robotica.automation.farmbot.FarmBotItem;
import com.arno.robotica.automation.farmbot.Sprout;
import com.arno.robotica.automation.farmbot.Stumpy;
import com.arno.robotica.automation.rancher.Rancher;
import com.arno.robotica.automation.rancher.RancherContent;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Stumpy and Sprout game tests in the Rancher pen (9x9 inside, the Mk1 area around its centre; floor at test y 1, so
 * everything stands at y 2): ./gradlew runGameTestServer
 */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class FarmBotGameTests {
    private static final String PEN = "rancher_pen";
    private static final BlockPos HOME = new BlockPos(5, 2, 6);
    private static final BlockPos CHEST = new BlockPos(5, 2, 8);

    /** A charged robot at home with an owner. */
    private static <T extends FarmBot> T bot(GameTestHelper helper, EntityType<T> type, BlockPos at) {
        T bot = helper.spawn(type, at);
        bot.setHome(helper.absolutePos(at));
        bot.setOwnerUUID(UUID.randomUUID());
        bot.energy.setEnergy(bot.energy.getMaxEnergyStored());
        return bot;
    }

    private static Sprout sprout(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        return bot(helper, FarmBotContent.SPROUT_ENTITY.get(), HOME);
    }

    private static Stumpy stumpy(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        return bot(helper, FarmBotContent.STUMPY_ENTITY.get(), HOME);
    }

    private static int chest(GameTestHelper helper, Item item) {
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        }
        return n;
    }

    private static int count(IItemHandler handler, Item item) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) n += handler.getStackInSlot(i).getCount();
        }
        return n;
    }

    /** A one-log-wide trunk on dirt (replacing the floor) with a leaf on top. */
    private static void columnTree(GameTestHelper helper, int x, int z, int height) {
        helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
        for (int y = 2; y < 2 + height; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(x, 2 + height, z), Blocks.OAK_LEAVES);
    }

    private static int standingLogs(GameTestHelper helper, int x, int z, int height) {
        int n = 0;
        for (int y = 2; y < 2 + height; y++) {
            if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.OAK_LOG)) n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- Sprout

    /** Where the robot is and what it does, in absolute coordinates, for failure messages. */
    private static String where(GameTestHelper helper, FarmBot bot) {
        BlockPos x = helper.absolutePos(new BlockPos(1, 0, 0)).subtract(helper.absolutePos(BlockPos.ZERO));
        return " (" + bot.getType().getDescriptionId() + " " + bot.status() + (bot.isAlive() ? "" : " dead") + " at " + bot.blockPosition()
                + ", home " + bot.home() + ", test home " + helper.absolutePos(HOME) + ", +x is " + x + ", energy " + bot.energy.getEnergyStored() + ")";
    }

    /** The side of {@code from} that {@code to} is on, in the world (the test may be rotated). */
    private static Direction side(GameTestHelper helper, BlockPos from, BlockPos to) {
        BlockPos d = helper.absolutePos(to).subtract(helper.absolutePos(from));
        return Direction.getNearest(d.getX(), d.getY(), d.getZ());
    }

    /** Below the floor is open: sand, gravel and water there need a block under them. */
    private static void floorBlock(GameTestHelper helper, BlockPos at, Block block) {
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, block);
    }

    /**
     * Nine ripe wheat in a 3x3 square: the nearest is at its edge, yet one stop works the whole square, the 3x3 with
     * the most work around it.
     */
    @GameTest(template = PEN, timeoutTicks = 300)
    public static void sproutHarvestsA3x3PerStop(GameTestHelper helper) {
        for (int x = 4; x <= 6; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            }
        }
        Sprout sprout = sprout(helper);
        sprout.pouch.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 9));
        int[] firstHarvest = {-1};
        helper.onEachTick(() -> {
            if (firstHarvest[0] < 0 && chest(helper, Items.WHEAT) > 0) firstHarvest[0] = chest(helper, Items.WHEAT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firstHarvest[0] == 9, "one stop should harvest all 9 wheat, the first brought " + firstHarvest[0]);
            helper.assertTrue(chest(helper, Items.WHEAT) == 9, "Chest should hold 9 wheat, has " + chest(helper, Items.WHEAT));
            for (int x = 4; x <= 6; x++) {
                for (int z = 2; z <= 4; z++) {
                    BlockState state = helper.getBlockState(new BlockPos(x, 2, z));
                    helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) < 7, "Replanted at " + x + "," + z + ": " + state);
                }
            }
        });
    }

    /** Sugar cane and cactus are cut above their bottom block, which stays to grow back. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void sproutCutsCaneAndCactus(GameTestHelper helper) {
        BlockPos cane = new BlockPos(3, 2, 3), cactus = new BlockPos(7, 2, 3);
        floorBlock(helper, cane.below(), Blocks.SAND);
        floorBlock(helper, cane.below().north(), Blocks.WATER);
        floorBlock(helper, cactus.below(), Blocks.SAND);
        for (int i = 0; i < 3; i++) {
            helper.setBlock(cane.above(i), Blocks.SUGAR_CANE);
            helper.setBlock(cactus.above(i), Blocks.CACTUS);
        }
        Sprout sprout = sprout(helper);
        helper.succeedWhen(() -> {
            String state = where(helper, sprout) + " cane " + helper.getBlockState(cane.above()) + " cactus " + helper.getBlockState(cactus.above());
            helper.assertTrue(chest(helper, Items.SUGAR_CANE) >= 2, "sugar cane cut, got " + chest(helper, Items.SUGAR_CANE) + state);
            helper.assertTrue(chest(helper, Items.CACTUS) >= 2, "cactus cut, got " + chest(helper, Items.CACTUS) + state);
            helper.assertBlockPresent(Blocks.SUGAR_CANE, cane);
            helper.assertBlockPresent(Blocks.CACTUS, cactus);
            helper.assertBlockNotPresent(Blocks.SUGAR_CANE, cane.above());
            helper.assertBlockNotPresent(Blocks.CACTUS, cactus.above());
        });
    }

    /** Melons and pumpkins are broken off their attached stems; the ground beside a stem is not tilled or planted. */
    @GameTest(template = PEN, timeoutTicks = 500)
    public static void sproutPicksMelonsAndPumpkins(GameTestHelper helper) {
        BlockPos melonStem = new BlockPos(4, 2, 3), melon = new BlockPos(5, 2, 3);
        BlockPos pumpkinStem = new BlockPos(4, 2, 2), pumpkin = new BlockPos(3, 2, 2);
        helper.setBlock(melonStem.below(), Blocks.FARMLAND);
        helper.setBlock(pumpkinStem.below(), Blocks.FARMLAND);
        helper.setBlock(melon.below(), Blocks.DIRT);
        helper.setBlock(pumpkin.below(), Blocks.DIRT);
        floorBlock(helper, new BlockPos(5, 1, 2), Blocks.WATER);
        helper.setBlock(melon, Blocks.MELON);
        helper.setBlock(pumpkin, Blocks.PUMPKIN);
        helper.setBlock(melonStem, Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, side(helper, melonStem, melon)));
        helper.setBlock(pumpkinStem, Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, side(helper, pumpkinStem, pumpkin)));
        Sprout sprout = sprout(helper);
        // Seeds and water nearby: without the stem rule Sprout would till the fruit spots and plant wheat there.
        sprout.pouch.setStackInSlot(0, new ItemStack(Items.WHEAT_SEEDS, 4));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(chest(helper, Items.MELON_SLICE) >= 3, "melon picked, got " + chest(helper, Items.MELON_SLICE)
                            + where(helper, sprout) + " stem " + helper.getBlockState(melonStem) + " at " + helper.absolutePos(melonStem));
                    helper.assertTrue(chest(helper, Items.PUMPKIN) >= 1, "pumpkin picked, got " + chest(helper, Items.PUMPKIN));
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.DIRT, melon.below());
                    helper.assertBlockPresent(Blocks.DIRT, pumpkin.below());
                    helper.assertTrue(!helper.getBlockState(melonStem).isAir() && !helper.getBlockState(pumpkinStem).isAir(), "stems stay");
                })
                .thenSucceed();
    }

    /** Every harvest costs FE: a buffer that covers the work ticks but not the harvest leaves the crop standing. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void sproutPaysPerHarvest(GameTestHelper helper) {
        BlockPos crop = new BlockPos(5, 2, 5);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        Sprout sprout = sprout(helper);
        // The crop is in reach from home: one action's work ticks plus one FE short of the harvest.
        int workTicks = sprout.actionInterval() * CoreConfig.scaleEnergy(AutomationConfig.sproutFe());
        sprout.energy.setEnergy(workTicks + CoreConfig.scaleEnergy(AutomationConfig.sproutFePerHarvest()) - 1);
        helper.runAfterDelay(150, () -> {
            BlockState state = helper.getBlockState(crop);
            helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 7, "too little FE for the harvest: the crop waits, is " + state);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- Stumpy

    @GameTest(template = PEN, timeoutTicks = 400)
    public static void stumpyFellsTreeAndReplants(GameTestHelper helper) {
        columnTree(helper, 5, 3, 3);
        helper.setBlock(new BlockPos(4, 4, 3), Blocks.OAK_LEAVES);
        helper.setBlock(new BlockPos(6, 4, 3), Blocks.OAK_LEAVES);
        Stumpy stumpy = stumpy(helper);
        stumpy.pouch.setStackInSlot(0, new ItemStack(Items.OAK_SAPLING, 2));
        int start = stumpy.energy.getEnergyStored();
        helper.succeedWhen(() -> {
            helper.assertTrue(chest(helper, Items.OAK_LOG) == 3, "Chest should hold the 3 felled logs, has " + chest(helper, Items.OAK_LOG));
            helper.assertBlockPresent(Blocks.OAK_SAPLING, new BlockPos(5, 2, 3));
            helper.assertBlockNotPresent(Blocks.OAK_LEAVES, new BlockPos(5, 5, 3));
            helper.assertTrue(stumpy.energy.getEnergyStored() < start, "Felling must cost energy");
        });
    }

    /** A tree spot it saw (a sapling) that someone cleared gets a new sapling; plain dirt next to it stays empty. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void stumpyReplantsClearedTreeSpots(GameTestHelper helper) {
        BlockPos spot = new BlockPos(5, 2, 3), bare = new BlockPos(3, 2, 3);
        helper.setBlock(spot.below(), Blocks.DIRT);
        helper.setBlock(bare.below(), Blocks.DIRT);
        helper.setBlock(spot, Blocks.BIRCH_SAPLING);
        Stumpy stumpy = stumpy(helper);
        helper.runAfterDelay(40, () -> {
            helper.setBlock(spot, Blocks.AIR);
            stumpy.pouch.setStackInSlot(0, new ItemStack(Items.OAK_SAPLING, 4));
            stumpy.pouch.setStackInSlot(1, new ItemStack(Items.BIRCH_SAPLING, 4));
        });
        helper.runAfterDelay(41, () -> helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.BIRCH_SAPLING, spot);
            helper.assertBlockPresent(Blocks.AIR, bare);
            helper.assertTrue(count(stumpy.pouch, Items.BIRCH_SAPLING) == 3, "the birch sapling came from the pouch");
        }));
    }

    /** A huge tree (90 logs) costs at most half the buffer, so a working Stumpy (never exactly full) still fells it. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void stumpyFellsHugeTrees(GameTestHelper helper) {
        for (int dx = 4; dx <= 6; dx++) {
            for (int dz = 2; dz <= 4; dz++) {
                helper.setBlock(new BlockPos(dx, 1, dz), Blocks.DIRT);
                for (int y = 2; y <= 11; y++) helper.setBlock(new BlockPos(dx, y, dz), Blocks.OAK_LOG);
            }
        }
        helper.setBlock(new BlockPos(5, 12, 3), Blocks.OAK_LEAVES);
        Stumpy stumpy = stumpy(helper);
        stumpy.energy.setEnergy(stumpy.energy.getMaxEnergyStored() - 1_000);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(5, 6, 3)));
    }

    /** The tree comes down bottom-up over several ticks; the FE is paid once before the wave and the logs reach the chest. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void stumpyFellsTreeInAWave(GameTestHelper helper) {
        int height = 6;
        columnTree(helper, 5, 3, height);
        Stumpy stumpy = stumpy(helper);
        int perTree = height * CoreConfig.scaleEnergy(AutomationConfig.stumpyFePerLog());
        int[] before = {-1};
        int[] waveEnergy = {-1};
        int[] waveTicks = {0};
        helper.onEachTick(() -> {
            if (!stumpy.waveActive()) {
                if (waveEnergy[0] < 0) before[0] = stumpy.energy.getEnergyStored();
                return;
            }
            int energy = stumpy.energy.getEnergyStored();
            if (waveEnergy[0] < 0) {
                waveEnergy[0] = energy;
                helper.assertTrue(before[0] - energy >= perTree, "The whole tree is paid up front: " + (before[0] - energy) + " < " + perTree);
            }
            helper.assertTrue(energy == waveEnergy[0], "No FE is paid during the wave");
            waveTicks[0]++;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(waveEnergy[0] >= 0, "Wave started");
            helper.assertFalse(stumpy.waveActive(), "Wave finished");
            helper.assertTrue(standingLogs(helper, 5, 3, height) == 0, "All logs felled");
            helper.assertTrue(chest(helper, Items.OAK_LOG) == height, "Chest should hold " + height + " logs, has " + chest(helper, Items.OAK_LOG));
            helper.assertTrue(waveTicks[0] >= 10, "The wave takes a while, took " + waveTicks[0] + " ticks");
        });
    }

    /** Saved and loaded mid-wave (chunk unload, server restart): the new robot finishes the tree, every log reaches the chest once. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void stumpyWaveSurvivesReload(GameTestHelper helper) {
        int height = 9;
        columnTree(helper, 5, 3, height);
        Stumpy first = stumpy(helper);
        Stumpy[] second = {null};
        helper.onEachTick(() -> {
            if (second[0] == null && first.waveActive() && standingLogs(helper, 5, 3, height) <= height - 3) {
                CompoundTag saved = new CompoundTag();
                first.saveWithoutId(saved);
                first.discard();
                Stumpy copy = FarmBotContent.STUMPY_ENTITY.get().create(helper.getLevel());
                copy.load(saved);
                copy.setUUID(UUID.randomUUID());
                helper.getLevel().addFreshEntity(copy);
                helper.assertTrue(copy.waveActive(), "The wave is saved");
                second[0] = copy;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0] != null, "Reloaded mid-wave");
            helper.assertFalse(second[0].waveActive(), "Wave finished");
            helper.assertTrue(standingLogs(helper, 5, 3, height) == 0, "All logs felled");
            helper.assertTrue(chest(helper, Items.OAK_LOG) == height, "Chest should hold " + height + " logs, has " + chest(helper, Items.OAK_LOG));
        });
    }

    /** Two Stumpys reaching the same tree: one fells and pays for it, the other leaves its logs alone. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void overlappingStumpysFellATreeOnce(GameTestHelper helper) {
        int height = 6;
        columnTree(helper, 5, 3, height);
        Stumpy a = bot(helper, FarmBotContent.STUMPY_ENTITY.get(), new BlockPos(3, 2, 5));
        Stumpy b = bot(helper, FarmBotContent.STUMPY_ENTITY.get(), new BlockPos(7, 2, 5));
        int start = a.energy.getMaxEnergyStored() - 1_000;
        a.energy.setEnergy(start);
        b.energy.setEnergy(start);
        helper.onEachTick(() -> helper.assertFalse(a.waveActive() && b.waveActive(), "Only one Stumpy fells the tree"));
        helper.succeedWhen(() -> {
            helper.assertTrue(standingLogs(helper, 5, 3, height) == 0, "All logs felled");
            helper.assertFalse(a.waveActive() || b.waveActive(), "Wave finished");
            int logs = count(a.pouch, Items.OAK_LOG) + count(b.pouch, Items.OAK_LOG);
            helper.assertTrue(logs == height, "Logs felled once: " + logs);
        });
    }

    /** Every log costs FE: with too little in the buffer and no battery Stumpy leaves the tree standing. */
    @GameTest(template = PEN, timeoutTicks = 400)
    public static void stumpyPaysPerLog(GameTestHelper helper) {
        columnTree(helper, 5, 4, 3);
        Stumpy stumpy = stumpy(helper);
        stumpy.energy.setEnergy(3 * AutomationConfig.stumpyFePerLog() - 1);
        helper.runAfterDelay(150, () -> {
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(5, 2, 4));
            helper.succeed();
        });
    }

    @GameTest(template = PEN, timeoutTicks = 400)
    public static void stumpyIgnoresLogsWithoutLeaves(GameTestHelper helper) {
        for (int y = 2; y <= 3; y++) helper.setBlock(new BlockPos(5, y, 4), Blocks.OAK_LOG);
        stumpy(helper);
        helper.runAfterDelay(150, () -> {
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(5, 2, 4));
            helper.succeed();
        });
    }

    /** The vacuum leaves a player's thrown items alone and obeys the pickup event veto. */
    @GameTest(template = PEN, timeoutTicks = 300)
    public static void vacuumSkipsPlayerItemsAndHonoursVeto(GameTestHelper helper) {
        Stumpy stumpy = bot(helper, FarmBotContent.STUMPY_ENTITY.get(), HOME);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
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
            helper.assertTrue(count(stumpy.pouch, Items.STICK) == 4, "natural sticks should be collected, got " + count(stumpy.pouch, Items.STICK));
            NeoForge.EVENT_BUS.unregister(veto);
            helper.assertTrue(thrown.isAlive(), "an item thrown by a player must stay");
            helper.assertTrue(vetoed.isAlive(), "a vetoed pickup must stay");
        });
    }

    // ---------------------------------------------------------------- shared: unstuck, Mk, cards, item form

    /** Ending up inside a block (a sapling grew around it, a piston pushed it): it teleports to a free spot at home. */
    @GameTest(template = PEN, timeoutTicks = 200)
    public static void robotInsideABlockTeleportsHome(GameTestHelper helper) {
        Sprout sprout = sprout(helper);
        BlockPos trap = new BlockPos(2, 2, 2);
        helper.runAfterDelay(5, () -> {
            Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(trap));
            sprout.teleportTo(at.x, at.y, at.z);
            helper.setBlock(trap, Blocks.STONE);
            helper.setBlock(trap.above(), Blocks.STONE);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sprout.tickCount > 10, "trapped first");
            Vec3 home = Vec3.atBottomCenterOf(helper.absolutePos(HOME));
            helper.assertTrue(sprout.position().distanceTo(home) < 3.0, "back home, at " + sprout.position());
            helper.assertFalse(sprout.isInWall(), "not inside a block");
        });
    }

    /** Mk4 with four speed cards is already at the 1 tick floor: it must not pay for nothing. Mk1 takes two speed cards. */
    @GameTest(template = PEN)
    public static void flooredSpeedDoesNotRaiseDrain(GameTestHelper helper) {
        Stumpy mk4 = bot(helper, FarmBotContent.STUMPY_ENTITY.get(), HOME);
        mk4.setTier(4);
        mk4.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 4));
        helper.assertTrue(mk4.actionInterval() == 1, "Mk4 interval is floored at 1, is " + mk4.actionInterval());
        helper.assertTrue(mk4.effectiveSpeedMultiplier() == 1, "floored interval gains nothing, multiplier " + mk4.effectiveSpeedMultiplier());
        Stumpy mk1 = bot(helper, FarmBotContent.STUMPY_ENTITY.get(), new BlockPos(3, 2, 6));
        mk1.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 4));
        helper.assertTrue(mk1.upgrades.level(UpgradeKind.SPEED) == 2, "Mk1 counts two speed cards, got " + mk1.upgrades.level(UpgradeKind.SPEED));
        helper.assertTrue(mk1.effectiveSpeedMultiplier() == 3, "Mk1 with two cards is a real x3, got " + mk1.effectiveSpeedMultiplier());
        helper.succeed();
    }

    /** The area comes from range cards on every Mk: a Mk4 without cards stays 9x9, a Mk1 with 4 cards works 33x33; Stumpy starts at 7x7. */
    @GameTest(template = PEN)
    public static void rangeCardsSetTheAreaOnEveryMk(GameTestHelper helper) {
        Sprout sprout = sprout(helper);
        int base = RobotArea.size(0);
        helper.assertTrue(sprout.areaSize() == base, "Mk1 without cards: " + sprout.areaSize());
        sprout.setTier(4);
        helper.assertTrue(sprout.areaSize() == base, "Mk4 without cards keeps the base area: " + sprout.areaSize());
        sprout.setTier(1);
        sprout.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.RANGE, 4));
        helper.assertTrue(sprout.upgrades.level(UpgradeKind.RANGE) == 4, "Mk1 counts 4 range cards");
        helper.assertTrue(sprout.areaSize() == RobotArea.size(4), "Mk1 with 4 range cards: " + sprout.areaSize());
        Rancher rancher = helper.spawn(RancherContent.RANCHER_ENTITY.get(), new BlockPos(3, 2, 3));
        int radius = rancher.radius();
        rancher.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.RANGE, 1));
        helper.assertTrue(rancher.radius() == radius + RobotArea.RADIUS_PER_CARD, "a range card grows the Rancher's area: " + rancher.radius());
        rancher.discard();
        sprout.discard();
        Stumpy stumpy = stumpy(helper);
        helper.assertTrue(stumpy.areaSize() == RobotArea.size(AutomationConfig.stumpyRadius(), 0) && stumpy.areaSize() < base,
                "Stumpy starts smaller: " + stumpy.areaSize());
        stumpy.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.RANGE, 4));
        helper.assertTrue(stumpy.areaSize() == RobotArea.size(AutomationConfig.stumpyRadius(), 4), "Stumpy with 4 range cards: " + stumpy.areaSize());
        helper.succeed();
    }

    /** Picked up and placed again: Mk (the item), energy, cards, pouch and name come along. */
    @GameTest(template = PEN)
    public static void pickUpKeepsEverything(GameTestHelper helper) {
        Sprout sprout = sprout(helper);
        sprout.setTier(3);
        sprout.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 2));
        sprout.pouch.setStackInSlot(4, new ItemStack(Items.CARROT, 7));
        sprout.energy.setEnergy(12_345);
        ItemStack stack = sprout.toItemStack(false);
        helper.assertTrue(stack.is(FarmBotContent.SPROUT_ITEMS.get(2).get()), "a Mk3 turns into the Mk3 item, is " + stack);
        helper.assertTrue(ItemEnergy.get(stack) == 12_345, "energy in the item");
        sprout.discard();
        Sprout placed = FarmBotContent.SPROUT_ENTITY.get().create(helper.getLevel());
        placed.initFromStack(stack, null);
        helper.assertTrue(placed.tier() == 3, "Mk from the item, is " + placed.tier());
        helper.assertTrue(placed.upgrades.level(UpgradeKind.SPEED) == 2, "cards kept");
        helper.assertTrue(placed.pouch.getStackInSlot(4).is(Items.CARROT) && placed.pouch.getStackInSlot(4).getCount() == 7, "pouch kept");
        helper.assertTrue(placed.energy.getEnergyStored() == 12_345, "energy kept, is " + placed.energy.getEnergyStored());
        placed.discard();
        helper.succeed();
    }

    /** Right-click with the next Mk: the robot becomes that Mk and the old Mk comes back. Lower Mks and used items are refused. */
    @GameTest(template = PEN)
    public static void upgradeInPlaceSwapsTheMk(GameTestHelper helper) {
        Stumpy stumpy = stumpy(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        FarmBotItem mk3 = FarmBotContent.STUMPY_ITEMS.get(2).get();
        ItemStack held = new ItemStack(mk3);
        helper.assertTrue(stumpy.upgradeInPlace(player, held, mk3), "Mk1 to Mk3");
        helper.assertTrue(stumpy.tier() == 3 && held.isEmpty(), "Mk3 now, item used");
        helper.assertTrue(player.getInventory().contains(new ItemStack(FarmBotContent.STUMPY_ITEM.get())), "the Mk1 comes back");
        FarmBotItem mk2 = FarmBotContent.STUMPY_ITEMS.get(1).get();
        ItemStack lower = new ItemStack(mk2);
        helper.assertFalse(stumpy.upgradeInPlace(player, lower, mk2), "a lower Mk is refused");
        FarmBotItem mk4 = FarmBotContent.STUMPY_ITEMS.get(3).get();
        ItemStack used = new ItemStack(mk4);
        used.set(FarmBotContent.STATE.get(), new CompoundTag());
        helper.assertFalse(stumpy.upgradeInPlace(player, used, mk4), "a robot that was placed before is refused");
        helper.assertTrue(stumpy.tier() == 3 && !lower.isEmpty() && !used.isEmpty(), "nothing changed");
        helper.succeed();
    }
}
