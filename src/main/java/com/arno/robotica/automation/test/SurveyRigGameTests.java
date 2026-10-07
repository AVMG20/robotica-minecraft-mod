package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.automation.entity.SurveyOrePool;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity.RigState;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Survey Rig tests: the weighted ore pool, the work loop and the steep energy curve. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class SurveyRigGameTests {
    private static final BlockPos RIG = new BlockPos(1, 2, 1);

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    /** Every c:ores item is in the pool, variants share a kind, rare ores weigh less, core ores need a Magma Core. */
    @GameTest(template = "empty")
    public static void surveyOrePoolIsWeightedByRarity(GameTestHelper helper) {
        SurveyOrePool pool = SurveyOrePool.get();
        SurveyOrePool.Kind iron = pool.kind(Items.IRON_ORE);
        SurveyOrePool.Kind coal = pool.kind(Items.COAL_ORE);
        SurveyOrePool.Kind gold = pool.kind(Items.GOLD_ORE);
        SurveyOrePool.Kind diamond = pool.kind(Items.DIAMOND_ORE);
        SurveyOrePool.Kind debris = pool.kind(Items.ANCIENT_DEBRIS);
        SurveyOrePool.Kind thorium = pool.kind(item("robotica:thorium_ore"));
        helper.assertTrue(iron != null && coal != null && gold != null && diamond != null && debris != null, "vanilla ores are in the pool");
        helper.assertTrue(thorium != null, "Robotica's own ores are in the pool");
        helper.assertTrue(pool.kind(Items.DEEPSLATE_IRON_ORE) == iron, "stone and deepslate iron are one kind");
        helper.assertTrue(pool.kind(Items.NETHER_GOLD_ORE) == gold, "nether gold shares the gold kind");
        helper.assertTrue(coal.weight() >= iron.weight() && iron.weight() > gold.weight() && gold.weight() > diamond.weight()
                && diamond.weight() > debris.weight(), "coal >= iron > gold > diamond > debris");
        helper.assertTrue(debris.needsCore() && !diamond.needsCore(), "ancient debris needs the Magma Core");
        helper.assertTrue(pool.chance(debris, false) == 0.0 && pool.chance(debris, true) > 0.0, "no debris without a core");
        helper.assertTrue(pool.chance(diamond, false) < 0.05, "diamonds are rare: " + pool.chance(diamond, false));
        for (Item ore : BuiltInRegistries.ITEM.getTag(Tags.Items.ORES).map(t -> t.stream().map(h -> h.value()).toList()).orElse(List.of())) {
            helper.assertTrue(pool.kind(ore) != null, BuiltInRegistries.ITEM.getKey(ore) + " is in the pool");
        }

        // an unlisted kind gets the default weight, an item entry wins over its tag, 0 removes it
        SurveyOrePool custom = SurveyOrePool.build(List.of("#c:ores/iron=50", "minecraft:emerald_ore=0"), 7, List.of());
        helper.assertTrue(custom.kind(Items.COPPER_ORE).weight() == 7, "unlisted kinds use the default weight");
        helper.assertTrue(custom.kind(Items.IRON_ORE).weight() == 50, "listed kinds use their weight");
        helper.assertTrue(custom.kind(Items.EMERALD_ORE) == null, "weight 0 removes an ore");
        helper.assertTrue(!custom.kind(Items.ANCIENT_DEBRIS).needsCore(), "core ores come from the config");
        helper.succeed();
    }

    /** Powered, the rig makes ore drops into the chest next to it; without power it waits and makes nothing. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void surveyRigMakesRandomOresWithPower(GameTestHelper helper) {
        BlockPos chestPos = new BlockPos(2, 2, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(RIG, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity rig = helper.getBlockEntity(RIG);
        rig.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 4));
        rig.energy.setEnergy(rig.energy.getMaxEnergyStored());

        BlockPos idlePos = new BlockPos(0, 2, 3);
        helper.setBlock(idlePos, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity idle = helper.getBlockEntity(idlePos);

        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(chestPos);
            helper.assertTrue(!chest.isEmpty(), "the chest should receive ore drops");
            helper.assertTrue(rig.lastOre() != null && new ItemStack(rig.lastOre()).is(Tags.Items.ORES), "the last ore comes from c:ores");
            helper.assertTrue(rig.rigState() == RigState.MINING, "a powered rig mines, is " + rig.rigState());
            helper.assertTrue(rig.energy.getEnergyStored() < rig.energy.getMaxEnergyStored(), "work costs energy");
            helper.assertTrue(idle.status() == AreaWorkerBlockEntity.Status.NO_ENERGY && idle.bufferContents().isEmpty(),
                    "an unpowered rig makes nothing");
        });
    }

    /**
     * Mk1: one ore per 400 ticks at 200 FE/t. Speed cards cut the time but the FE per tick climbs steeply. Mk4 (x3 speed
     * at x3 FE/t) takes all 8 speed cards.
     */
    @GameTest(template = "empty")
    public static void surveyRigEnergyAndSpeedMath(GameTestHelper helper) {
        helper.setBlock(RIG, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity rig = helper.getBlockEntity(RIG);
        helper.assertTrue(rig.actionInterval() == 400, "base interval 400, is " + rig.actionInterval());
        helper.assertTrue(rig.energyPerTick() == 200, "base 200 FE/t, is " + rig.energyPerTick());
        helper.assertTrue(rig.energyPerOre() == 80_000, "base 80,000 FE per ore, is " + rig.energyPerOre());
        helper.assertTrue(rig.upgrades.cap(UpgradeKind.SPEED) == 2, "Mk1 speed cap 2");
        helper.assertTrue(rig.upgrades.cap(UpgradeKind.RANGE) == 0, "no range cards");
        rig.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 1));
        helper.assertTrue(rig.actionInterval() == 200 && rig.energyPerTick() == 700, "1 card: x2 speed, x3.5 FE/t, is " + rig.energyPerTick());

        helper.setBlock(RIG, AutomationContent.SURVEY_RIG_MK4.get());
        SurveyRigBlockEntity mk4 = helper.getBlockEntity(RIG);
        helper.assertTrue(mk4.upgrades.cap(UpgradeKind.SPEED) == 8, "Mk4 speed cap 8");
        mk4.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 8));
        helper.assertTrue(mk4.actionInterval() == 6, "Mk4 + 8 cards: 133 / 20 ticks, is " + mk4.actionInterval());
        helper.assertTrue(mk4.energyPerTick() == 252_000, "Mk4 + 8 cards: 600 x 420 FE/t, is " + mk4.energyPerTick());
        mk4.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.EFFICIENCY, 4));
        helper.assertTrue(mk4.energyPerTick() == 100_800, "8 speed + 4 efficiency, is " + mk4.energyPerTick());
        helper.assertTrue(mk4.energy.getMaxEnergyStored() >= 252_000 * 6, "the buffer holds at least one top speed ore");
        helper.succeed();
    }

    /** A rig saved by the old chunk-ledger version loads: its stale tags are ignored. */
    @GameTest(template = "empty")
    public static void surveyRigLoadsOldSaves(GameTestHelper helper) {
        helper.setBlock(RIG, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity rig = helper.getBlockEntity(RIG);
        CompoundTag old = rig.saveWithoutMetadata(helper.getLevel().registryAccess());
        old.putInt("rigState", 5);
        old.putBoolean("finishedHere", true);
        old.putInt("progress", 37);
        rig.loadWithComponents(old, helper.getLevel().registryAccess());
        helper.assertTrue(rig.rigState() == RigState.WAITING, "old states reset, is " + rig.rigState());
        helper.assertTrue(rig.progressTicks() == 37 && rig.lastOre() == null, "progress kept, no last ore");
        helper.succeed();
    }
}
