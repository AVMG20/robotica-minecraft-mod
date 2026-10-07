package com.arno.robotica.automation.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.AutomationConfig;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.entity.ExcavatorBlockEntity;
import com.arno.robotica.automation.entity.SurveyOrePool;
import com.arno.robotica.automation.entity.SurveyRigBlockEntity;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.UUID;

/** Excavator and Survey Rig Mk1-Mk4: stats per Mk and the in-place upgrade that keeps everything. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class WorkerMkGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private static void use(GameTestHelper helper, Player player, ItemStack stack) {
        BlockPos abs = helper.absolutePos(POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(POS).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static boolean has(Player player, net.minecraft.world.item.Item item) {
        return player.getInventory().contains(new ItemStack(item));
    }

    /** Right-click with the next Mk: block swapped, battery, cards, buffer, energy and owner kept, old block given back. */
    @GameTest(template = "empty")
    public static void excavatorUpgradesInPlace(GameTestHelper helper) {
        helper.setBlock(POS, AutomationContent.EXCAVATOR.get());
        ExcavatorBlockEntity old = helper.getBlockEntity(POS);
        UUID owner = UUID.randomUUID();
        old.setOwner(owner);
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        ItemEnergy.fill(cell);
        old.battery.setStackInSlot(0, cell);
        old.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 2));
        old.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.FORTUNE, 1));
        old.buffer.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 10));
        old.energy.setEnergy(5_000);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack mk3 = new ItemStack(AutomationContent.EXCAVATOR_MK3_ITEM.get());
        use(helper, player, mk3);
        helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.EXCAVATOR.get()) && mk3.getCount() == 1, "Mk3 can not skip Mk2");

        ItemStack mk2 = new ItemStack(AutomationContent.EXCAVATOR_MK2_ITEM.get());
        use(helper, player, mk2);
        helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.EXCAVATOR.get()) && mk2.getCount() == 1, "a stranger may not upgrade it");

        owner = player.getUUID();
        old.setOwner(owner);
        use(helper, player, mk2);
        helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.EXCAVATOR_MK2.get()), "the block is now Mk2");
        helper.assertTrue(mk2.isEmpty(), "the Mk2 item was used");
        helper.assertTrue(has(player, AutomationContent.EXCAVATOR_ITEM.get()), "the Mk1 comes back");
        ExcavatorBlockEntity fresh = helper.getBlockEntity(POS);
        helper.assertTrue(fresh != old && fresh.tier() == 2, "a fresh Mk2 block entity, tier " + fresh.tier());
        helper.assertTrue(fresh.upgradeSlotCount() == 3, "Mk2 has 3 card slots, has " + fresh.upgradeSlotCount());
        helper.assertTrue(fresh.upgrades.level(UpgradeKind.SPEED) == 2 && fresh.upgrades.level(UpgradeKind.FORTUNE) == 1, "cards kept");
        helper.assertTrue(fresh.buffer.getStackInSlot(0).is(Items.COBBLESTONE) && fresh.buffer.getStackInSlot(0).getCount() == 10, "buffer kept");
        helper.assertTrue(fresh.battery.getStackInSlot(0).is(CoreItems.COPPER_CELL.get()), "battery kept");
        helper.assertTrue(fresh.energy.getEnergyStored() == 5_000, "energy kept, is " + fresh.energy.getEnergyStored());
        helper.assertTrue(owner.equals(fresh.owner()), "owner kept");
        helper.assertTrue(fresh.energy.getMaxEnergyStored() == 2 * AutomationConfig.energyBuffer(), "Mk2 buffer doubles");
        helper.succeed();
    }

    /** Each Mk: one more card slot, higher caps, a bigger base hole and a shorter interval. */
    @GameTest(template = "empty")
    public static void excavatorMkStats(GameTestHelper helper) {
        List<Block> blocks = List.of(AutomationContent.EXCAVATOR.get(), AutomationContent.EXCAVATOR_MK2.get(),
                AutomationContent.EXCAVATOR_MK3.get(), AutomationContent.EXCAVATOR_MK4.get());
        int[] slots = {2, 3, 4, 5}, speedCap = {2, 4, 6, 8}, size = {8, 12, 16, 24}, interval = {60, 40, 30, 20};
        for (int i = 0; i < 4; i++) {
            helper.setBlock(POS, blocks.get(i));
            ExcavatorBlockEntity be = helper.getBlockEntity(POS);
            be.setSizeOverride(0);
            String mk = "Mk" + (i + 1) + ": ";
            helper.assertTrue(be.tier() == i + 1, mk + "tier");
            helper.assertTrue(be.upgradeSlotCount() == slots[i], mk + "slots " + be.upgradeSlotCount());
            helper.assertTrue(be.upgrades.cap(UpgradeKind.SPEED) == speedCap[i], mk + "speed cap " + be.upgrades.cap(UpgradeKind.SPEED));
            helper.assertTrue(be.upgrades.cap(UpgradeKind.RANGE) == i + 1, mk + "range cap");
            helper.assertTrue(be.areaSize() == size[i], mk + "size " + be.areaSize());
            helper.assertTrue(be.actionInterval() == interval[i], mk + "interval " + be.actionInterval());
            helper.assertTrue(be.energyPerBlock() == 40, mk + "FE per block stays 40, is " + be.energyPerBlock());
        }
        ExcavatorBlockEntity mk4 = helper.getBlockEntity(POS);
        mk4.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.RANGE, 4));
        mk4.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.SPEED, 8));
        mk4.setSizeOverride(0);
        helper.assertTrue(mk4.areaSize() == 64, "Mk4 with 4 range cards digs 64 wide, is " + mk4.areaSize());
        helper.assertTrue(mk4.actionInterval() == 1, "Mk4 with 8 speed cards: a block per tick, is " + mk4.actionInterval());
        helper.setBlock(POS, AutomationContent.EXCAVATOR.get());
        ExcavatorBlockEntity mk1 = helper.getBlockEntity(POS);
        mk1.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.RANGE, 4));
        mk1.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.SPEED, 8));
        mk1.setSizeOverride(0);
        helper.assertTrue(mk1.areaSize() == 18 && mk1.upgrades.level(UpgradeKind.SPEED) == 2, "Mk1 counts one range and two speed cards");
        helper.succeed();
    }

    /** Survey Rig Mk: faster at the same FE per ore, rare ores weigh more, Magma Core and cards survive the upgrade. */
    @GameTest(template = "empty")
    public static void surveyRigMkUpgradeAndRareOres(GameTestHelper helper) {
        helper.setBlock(POS, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity mk1 = helper.getBlockEntity(POS);
        helper.assertTrue(mk1.upgradeSlotCount() == 1 && mk1.upgrades.cap(UpgradeKind.SPEED) == 2, "Mk1: one slot, two speed cards");
        mk1.core.setStackInSlot(0, new ItemStack(CoreItems.MAGMA_CORE.get()));
        mk1.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 2));
        mk1.energy.setEnergy(123_456);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mk2 = new ItemStack(AutomationContent.SURVEY_RIG_MK2_ITEM.get());
        use(helper, player, mk2);
        helper.assertTrue(helper.getBlockState(POS).is(AutomationContent.SURVEY_RIG_MK2.get()), "the rig is now Mk2");
        SurveyRigBlockEntity rig = helper.getBlockEntity(POS);
        helper.assertTrue(rig.hasCore() && rig.upgrades.level(UpgradeKind.SPEED) == 2 && rig.energy.getEnergyStored() == 123_456,
                "core, cards and energy kept");
        helper.assertTrue(rig.upgradeSlotCount() == 2 && rig.upgrades.cap(UpgradeKind.SPEED) == 4, "Mk2: two slots, four speed cards");

        helper.setBlock(POS, AutomationContent.SURVEY_RIG_MK4.get());
        SurveyRigBlockEntity mk4 = helper.getBlockEntity(POS);
        helper.assertTrue(mk4.actionInterval() == 133 && mk4.energyPerTick() == 600, "Mk4: x3 speed at x3 FE/t, is "
                + mk4.actionInterval() + " ticks, " + mk4.energyPerTick() + " FE/t");
        helper.assertTrue(Math.abs(mk4.energyPerOre() - 80_000) < 1_000, "same FE per ore, is " + mk4.energyPerOre());

        SurveyOrePool pool = SurveyOrePool.get();
        SurveyOrePool.Kind diamond = pool.kind(Items.DIAMOND_ORE);
        SurveyOrePool.Kind iron = pool.kind(Items.IRON_ORE);
        int rare = AutomationConfig.surveyRareWeight();
        double base = pool.chance(diamond, false, 0, rare);
        double boosted = pool.chance(diamond, false, mk4.rareBonus(), rare);
        helper.assertTrue(boosted > 2.5 * base, "Mk4 makes diamonds about three times as often: " + base + " -> " + boosted);
        helper.assertTrue(pool.chance(iron, false, mk4.rareBonus(), rare) < pool.chance(iron, false, 0, rare), "common ores get rarer");
        helper.succeed();
    }

    /** A rig saved with more cards than its Mk has slots (before the Mk tiers) keeps the first and puts the rest out. */
    @GameTest(template = "empty")
    public static void extraCardsComeOutOnLoad(GameTestHelper helper) {
        helper.setBlock(POS, AutomationContent.SURVEY_RIG.get());
        SurveyRigBlockEntity rig = helper.getBlockEntity(POS);
        var provider = helper.getLevel().registryAccess();
        CompoundTag tag = rig.saveWithoutMetadata(provider);
        ItemStackHandler old = new ItemStackHandler(4);
        old.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 8));
        old.setStackInSlot(1, CoreItems.cards(UpgradeKind.EFFICIENCY, 4));
        old.setStackInSlot(3, CoreItems.cards(UpgradeKind.VOID, 1));
        tag.put("upgrades", old.serializeNBT(provider));
        rig.loadWithComponents(tag, provider);
        helper.assertTrue(rig.upgrades.getSlots() == 1, "still one slot");
        helper.assertTrue(rig.upgrades.getStackInSlot(0).getCount() == 8 && rig.upgrades.level(UpgradeKind.SPEED) == 2,
                "the first card stack stays, two of it count");
        helper.assertTrue(rig.hasPendingOutput(), "the other cards wait to come out");
        helper.succeed();
    }
}
