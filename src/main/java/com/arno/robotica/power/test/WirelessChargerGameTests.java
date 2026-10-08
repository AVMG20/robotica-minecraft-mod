package com.arno.robotica.power.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.WirelessChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Wireless Charger: charge order, range and owner rules, FE accounting. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class WirelessChargerGameTests {
    private static final BlockPos CHARGER = new BlockPos(1, 1, 1);

    private static WirelessChargerBlockEntity charger(GameTestHelper helper, ServerPlayer owner) {
        helper.setBlock(CHARGER, PowerRegistry.WIRELESS_CHARGER.get());
        WirelessChargerBlockEntity be = helper.getBlockEntity(CHARGER);
        be.setOwner(owner);
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        return be;
    }

    private static ServerPlayer player(GameTestHelper helper, double dx) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(CHARGER)).add(dx, 0, 0);
        player.moveTo(at.x, at.y, at.z);
        return player;
    }

    private static ItemStack emptyCell() {
        return new ItemStack(CoreItems.COPPER_CELL.get());
    }

    /** Worn armor first, then the held items, then the inventory; the buffer pays every FE plus the 10% loss. */
    @GameTest(template = "empty")
    public static void wirelessChargerChargesArmorFirst(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 2);
        WirelessChargerBlockEntity be = charger(helper, owner);
        ItemStack armor = emptyCell();
        ItemStack hand = emptyCell();
        ItemStack pocket = emptyCell();
        owner.getInventory().armor.set(2, armor);
        owner.setItemInHand(InteractionHand.MAIN_HAND, hand);
        owner.getInventory().setItem(20, pocket);
        int before = be.energy.getEnergyStored();

        long got = be.chargePlayer(owner, 1_000);
        helper.assertTrue(got == 1_000, "the whole budget goes out, got " + got);
        helper.assertTrue(ItemEnergy.get(armor) == 1_000 && ItemEnergy.get(hand) == 0 && ItemEnergy.get(pocket) == 0,
                "armor first: " + ItemEnergy.get(armor) + " / " + ItemEnergy.get(hand) + " / " + ItemEnergy.get(pocket));

        // a cell takes at most 2,000 FE per call: armor, hand, then the inventory get their share in that order
        got = be.chargePlayer(owner, 5_000);
        helper.assertTrue(got == 5_000, "5,000 FE delivered, got " + got);
        helper.assertTrue(ItemEnergy.get(armor) == 3_000 && ItemEnergy.get(hand) == 2_000 && ItemEnergy.get(pocket) == 1_000,
                "then hand, then inventory: " + ItemEnergy.get(armor) + " / " + ItemEnergy.get(hand) + " / " + ItemEnergy.get(pocket));

        int loss = PowerConfig.wirelessLoss();
        long drawn = before - be.energy.getEnergyStored();
        helper.assertTrue(drawn == WirelessChargerBlockEntity.costOf(1_000, loss) + WirelessChargerBlockEntity.costOf(5_000, loss),
                "the buffer pays delivered FE plus " + loss + "%: drew " + drawn);
        helper.assertTrue(drawn == 6_600, "6,000 FE delivered cost 6,600 at 10% loss, drew " + drawn);

        // a nearly empty buffer only delivers what it can pay for, loss included
        for (int stored = 1; stored <= 40; stored++) {
            be.energy.setEnergy(stored);
            long small = be.chargePlayer(owner, 1_000);
            long paid = stored - be.energy.getEnergyStored();
            helper.assertTrue(paid == WirelessChargerBlockEntity.costOf(small, loss) && paid <= stored,
                    stored + " FE stored: delivered " + small + " for " + paid);
        }

        // an empty buffer charges nothing
        be.energy.setEnergy(0);
        helper.assertTrue(be.chargePlayer(owner, 1_000) == 0, "no energy, no charge");
        helper.succeed();
    }

    /** Only players in range are listed; strangers are refused unless the config allows everyone; range cards reach further. */
    @GameTest(template = "empty")
    public static void wirelessChargerRangeAndOwnerRules(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 3);
        ServerPlayer stranger = player(helper, -2);
        WirelessChargerBlockEntity be = charger(helper, owner);
        int range = be.range();
        helper.assertTrue(range == PowerConfig.wirelessRange(), "base range " + range);

        be.scanNow();
        helper.assertTrue(be.stateOf(owner) >= 0 && be.stateOf(owner) != WirelessChargerBlockEntity.DENIED, "the owner is charged");
        helper.assertTrue(be.stateOf(stranger) == WirelessChargerBlockEntity.DENIED, "a stranger is refused");
        helper.assertTrue(be.mayCharge(owner) && !be.mayCharge(stranger), "owner rule");

        WirelessChargerBlockEntity.anyoneOverride = true;
        try {
            be.scanNow();
            helper.assertTrue(be.stateOf(stranger) >= 0 && be.stateOf(stranger) != WirelessChargerBlockEntity.DENIED,
                    "with wirelessChargeAnyone everybody is charged");
        } finally {
            WirelessChargerBlockEntity.anyoneOverride = null;
        }

        owner.moveTo(owner.getX() + range, owner.getY(), owner.getZ());
        be.scanNow();
        helper.assertTrue(be.stateOf(owner) == -1, "out of range: not listed");

        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.RANGE, 2));
        helper.assertTrue(be.range() == range + 2 * PowerConfig.wirelessRangePerCard(), "range cards add range, is " + be.range());
        be.scanNow();
        helper.assertTrue(be.stateOf(owner) >= 0, "in range again with range cards");

        be.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.SPEED, 8));
        helper.assertTrue(be.upgrades.level(UpgradeKind.SPEED) == 4, "speed capped");
        helper.assertTrue(be.ratePerPlayer() == PowerConfig.wirelessRate() * 6, "4 speed cards: x6 rate, is " + be.ratePerPlayer());
        helper.succeed();
    }

    /** Ticking on its own it feeds a nearby owner at its rate, lights up, and the buffer pays exactly the delivered FE plus loss. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wirelessChargerFeedsOwnerOverTime(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 2);
        WirelessChargerBlockEntity be = charger(helper, owner);
        ItemStack hand = emptyCell();
        owner.setItemInHand(InteractionHand.MAIN_HAND, hand);
        int start = be.energy.getEnergyStored();
        helper.runAfterDelay(45, () -> {
            long delivered = be.totalDelivered();
            helper.assertTrue(delivered > 0 && ItemEnergy.get(hand) == delivered, "the held cell got the FE: " + ItemEnergy.get(hand) + " of " + delivered);
            helper.assertTrue(delivered <= 45L * be.ratePerPlayer(), "never more than the rate per tick");
            long drawn = start - be.energy.getEnergyStored();
            helper.assertTrue(drawn == WirelessChargerBlockEntity.costOf(delivered, PowerConfig.wirelessLoss()), "drew " + drawn + " for " + delivered);
            helper.assertTrue(be.isCharging() && helper.getBlockState(CHARGER).getValue(com.arno.robotica.power.block.WirelessChargerBlock.LIT),
                    "it shows that it charges");
            helper.assertTrue(be.fePerTick() == be.ratePerPlayer(), "FE/t readout " + be.fePerTick());
            helper.succeed();
        });
    }

    /**
     * A player with nothing to charge is skipped until the next scan, and an empty buffer skips everyone: a cell picked
     * up later still gets charged after the next scan, and nothing is drawn while there is nothing to charge.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wirelessChargerSkipsFullPlayers(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 2);
        WirelessChargerBlockEntity be = charger(helper, owner);
        int start = be.energy.getEnergyStored();
        ItemStack cell = emptyCell();
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(be.energy.getEnergyStored() == start && be.totalDelivered() == 0, "nothing to charge, nothing drawn");
            owner.setItemInHand(InteractionHand.MAIN_HAND, cell);
        });
        helper.runAfterDelay(15 + WirelessChargerBlockEntity.SCAN_INTERVAL + 3, () -> {
            helper.assertTrue(ItemEnergy.get(cell) > 0, "the new cell is charged after the next scan");
            be.energy.setEnergy(0);
            long delivered = be.totalDelivered();
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(be.totalDelivered() == delivered, "an empty buffer charges nothing");
                helper.succeed();
            });
        });
    }
}
