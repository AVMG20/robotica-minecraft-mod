package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SurveyLedgers;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity.RigState;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Survey Rig tests. Each test keeps its ledger under its own fake chunk key and scans only its own structure, so
 * tests that share a real chunk never see each other's ores or ledgers.
 */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class SurveyRigGameTests {
    private static final BlockPos RIG = new BlockPos(1, 2, 1);

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

    /** Places a rig over the structure floor, scanning only the two layers below it, its ledger under {@code key}. */
    private static SurveyRigBlockEntity rig(GameTestHelper helper, BlockPos at, ChunkPos key, boolean strip) {
        SurveyLedgers.get(helper.getLevel()).forget(key);
        return place(helper, at, key, strip);
    }

    private static SurveyRigBlockEntity place(GameTestHelper helper, BlockPos at, ChunkPos key, boolean strip) {
        helper.setBlock(at, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity rig = helper.getBlockEntity(at);
        rig.setLedgerChunk(key);
        rig.setStripOverride(strip);
        rig.setScanBounds(BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(2, 1, 2))));
        return rig;
    }

    /** 1 when the ore at {@code rel} lies in the rig's real chunk (the only chunk it scans), else 0. */
    private static int inChunk(GameTestHelper helper, BlockPos rel) {
        return new ChunkPos(helper.absolutePos(RIG)).equals(new ChunkPos(helper.absolutePos(rel))) ? 1 : 0;
    }

    /** The scan builds the ledger from placed ores; mining it fills the chest with ore drops and leaves the world alone. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void surveyRigMinesLedgerWithoutDigging(GameTestHelper helper) {
        BlockPos iron = new BlockPos(1, 1, 1);
        BlockPos diamond = new BlockPos(1, 0, 1);
        BlockPos coalA = new BlockPos(0, 1, 0);
        BlockPos coalB = new BlockPos(2, 0, 2);
        helper.setBlock(iron, Blocks.IRON_ORE);
        helper.setBlock(diamond, Blocks.DEEPSLATE_DIAMOND_ORE);
        helper.setBlock(coalA, Blocks.COAL_ORE);
        helper.setBlock(coalB, Blocks.COAL_ORE);
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        BlockPos chestPos = new BlockPos(2, 2, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        ChunkPos key = new ChunkPos(-29_000, 101);
        SurveyRigBlockEntity rig = rig(helper, RIG, key, false);
        rig.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 8));
        rig.battery.setStackInSlot(0, chargedCell());
        int coal = inChunk(helper, coalA) + inChunk(helper, coalB);
        int total = 2 + coal;
        // Watch the ledger while it is full: right after the scan, before the first ore is taken.
        boolean[] sawLedger = {false};
        helper.onEachTick(() -> {
            SurveyLedgers.Entry e = SurveyLedgers.get(helper.getLevel()).get(key);
            if (!sawLedger[0] && e != null && e.phase() == SurveyLedgers.Phase.MINING && e.left() == total) {
                helper.assertTrue(e.total() == total, "ledger total " + e.total() + ", expected " + total);
                helper.assertTrue(e.count(Blocks.IRON_ORE) == 1, "one iron ore in the ledger");
                helper.assertTrue(e.count(Blocks.DEEPSLATE_DIAMOND_ORE) == 1, "one diamond ore in the ledger");
                helper.assertTrue(e.count(Blocks.COAL_ORE) == coal, "coal in the ledger " + e.count(Blocks.COAL_ORE) + ", expected " + coal);
                helper.assertTrue(e.count(Blocks.STONE) == 0, "stone is no ore");
                sawLedger[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawLedger[0], "the scan should build the full ledger first");
            helper.assertTrue(rig.rigState() == RigState.FINISHED, "rig should finish its ledger, is " + rig.rigState());
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(count(chest, Items.RAW_IRON) == 1, "one raw iron, got " + count(chest, Items.RAW_IRON));
            helper.assertTrue(count(chest, Items.DIAMOND) == 1, "one diamond, got " + count(chest, Items.DIAMOND));
            helper.assertTrue(count(chest, Items.COAL) == coal, "coal " + count(chest, Items.COAL) + ", expected " + coal);
            // Purely virtual mode: nothing in the world changed.
            helper.assertBlockPresent(Blocks.IRON_ORE, iron);
            helper.assertBlockPresent(Blocks.DEEPSLATE_DIAMOND_ORE, diamond);
            helper.assertBlockPresent(Blocks.COAL_ORE, coalA);
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(0, 0, 0));
            helper.assertTrue(SurveyLedgers.get(helper.getLevel()).isSurveyed(key), "the chunk is surveyed once mined out");
        });
    }

    /** With stripOresFromWorld the counted ores turn into their host rock; an ore that is gone comes off the ledger. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void surveyRigStripsCountedOres(GameTestHelper helper) {
        BlockPos iron = new BlockPos(1, 1, 1);
        BlockPos diamond = new BlockPos(1, 0, 1);
        helper.setBlock(iron, Blocks.IRON_ORE);
        helper.setBlock(diamond, Blocks.DEEPSLATE_DIAMOND_ORE);
        ChunkPos key = new ChunkPos(-29_000, 102);
        SurveyRigBlockEntity rig = rig(helper, RIG, key, true);
        rig.battery.setStackInSlot(0, chargedCell());
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.STONE, iron);
            helper.assertBlockPresent(Blocks.DEEPSLATE, diamond);
            SurveyLedgers.Entry e = SurveyLedgers.get(helper.getLevel()).get(key);
            helper.assertTrue(e != null && e.total() == 2 && e.stripQueued() == 0, "both ores counted and stripped");
            helper.assertTrue(rig.ledgerCount(helper.getLevel(), Blocks.IRON_ORE) + rig.ledgerCount(helper.getLevel(), Blocks.DEEPSLATE_DIAMOND_ORE)
                    + countBuffer(rig) >= 2, "stripped ores stay in the ledger");
        });
    }

    private static int countBuffer(SurveyRigBlockEntity rig) {
        int n = 0;
        for (ItemStack s : rig.bufferContents()) n += s.getCount();
        return n;
    }

    /** A surveyed chunk refuses a new rig; a second rig in a chunk another rig works waits as "busy". */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void surveyRigRefusesSurveyedAndTakenChunks(GameTestHelper helper) {
        ChunkPos surveyed = new ChunkPos(-29_000, 103);
        ChunkPos shared = new ChunkPos(-29_000, 104);
        SurveyRigBlockEntity refused = rig(helper, new BlockPos(0, 2, 0), surveyed, false);
        SurveyLedgers.get(helper.getLevel()).markSurveyed(surveyed);
        refused.battery.setStackInSlot(0, chargedCell());

        helper.setBlock(new BlockPos(1, 0, 1), Blocks.GOLD_ORE);
        SurveyRigBlockEntity first = rig(helper, new BlockPos(1, 2, 1), shared, false);
        first.battery.setStackInSlot(0, chargedCell());
        helper.runAfterDelay(5, () -> {
            SurveyRigBlockEntity second = place(helper, new BlockPos(2, 2, 2), shared, false);
            second.battery.setStackInSlot(0, chargedCell());
        });
        helper.runAfterDelay(40, () -> {
            SurveyRigBlockEntity second = helper.getBlockEntity(new BlockPos(2, 2, 2));
            helper.assertTrue(refused.rigState() == RigState.SURVEYED, "surveyed chunk must refuse, is " + refused.rigState());
            helper.assertTrue(refused.status() == AreaWorkerBlockEntity.Status.IDLE, "a refused rig idles");
            helper.assertTrue(countBuffer(refused) == 0, "a refused rig mines nothing");
            helper.assertTrue(first.rigState() == RigState.MINING, "the first rig keeps its chunk, is " + first.rigState());
            helper.assertTrue(second.rigState() == RigState.BUSY, "the second rig waits, is " + second.rigState());
            helper.assertBlockPresent(Blocks.GOLD_ORE, new BlockPos(1, 0, 1));
            helper.succeed();
        });
    }

    /** Base: one ore per 100 ticks for 2,000 FE. Speed caps at 8 cards (x20) and costs steeply; efficiency takes some back. */
    @GameTest(template = "empty")
    public static void surveyRigEnergyAndSpeedMath(GameTestHelper helper) {
        helper.setBlock(RIG, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity rig = helper.getBlockEntity(RIG);
        helper.assertTrue(rig.actionInterval() == 100, "base interval 100, is " + rig.actionInterval());
        helper.assertTrue(rig.energyPerOre() == 2_000, "base 2,000 FE per ore, is " + rig.energyPerOre());
        helper.assertTrue(rig.upgrades.cap(UpgradeKind.SPEED) == 8, "speed cap 8");
        helper.assertTrue(rig.upgrades.cap(UpgradeKind.RANGE) == 0, "no range cards: the area is the chunk");
        rig.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 1));
        helper.assertTrue(rig.energyPerOre() == 3_500, "1 card x1.75, is " + rig.energyPerOre());
        rig.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 8));
        helper.assertTrue(rig.actionInterval() == 5, "8 cards: x20, is " + rig.actionInterval());
        helper.assertTrue(rig.energyPerOre() == 42_000, "8 cards x21, is " + rig.energyPerOre());
        rig.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.EFFICIENCY, 4));
        helper.assertTrue(rig.energyPerOre() == 16_800, "8 speed + 4 efficiency x8.4, is " + rig.energyPerOre());
        helper.assertTrue(rig.energy.getMaxEnergyStored() >= 42_000, "the buffer holds at least one top speed ore");
        helper.assertTrue(rig.areaSize() == 16 && Math.floorMod(rig.areaMinX(), 16) == 0 && Math.floorMod(rig.areaMinZ(), 16) == 0,
                "the work area is the rig's chunk");
        helper.succeed();
    }
}
