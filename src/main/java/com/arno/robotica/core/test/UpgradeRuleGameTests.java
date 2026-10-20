package com.arno.robotica.core.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.ArchitectRegistry;
import com.arno.robotica.architect.block.ArchitectTableBlockEntity;
import com.arno.robotica.automation.AutomationContent;
import com.arno.robotica.automation.RobotArea;
import com.arno.robotica.automation.farmbot.FarmBot;
import com.arno.robotica.automation.farmbot.FarmBotContent;
import com.arno.robotica.automation.rancher.Rancher;
import com.arno.robotica.automation.rancher.RancherContent;
import com.arno.robotica.automation.entity.AreaWorkerBlockEntity;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.core.upgrade.UpgradeRules;
import com.arno.robotica.core.upgrade.Upgrades;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.industry.IndustryRegistry;
import com.arno.robotica.industry.block.ProcessingBlockEntity;
import com.arno.robotica.industry.recipe.Machine;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import com.arno.robotica.power.block.MetalPressBlockEntity;
import com.arno.robotica.power.block.WirelessChargerBlockEntity;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.block.GrinderBlockEntity;
import com.arno.robotica.processing.block.ProcessingMachineBlockEntity;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import static com.arno.robotica.core.upgrade.UpgradeKind.*;

/** The one card rule: Mk machines (slots Mk + 1 up to one per kind, caps per Mk), fixed machines, refusals, backdrops and the shared energy math. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class UpgradeRuleGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private static Upgrades cards(GameTestHelper helper, BlockState state) {
        helper.setBlock(POS, state);
        BlockEntity be = helper.getBlockEntity(POS);
        if (be instanceof ProcessingBlockEntity m) return m.upgrades;
        if (be instanceof ProcessingMachineBlockEntity m) return m.upgrades;
        if (be instanceof AreaWorkerBlockEntity m) return m.upgrades;
        if (be instanceof MetalPressBlockEntity m) return m.upgrades;
        if (be instanceof CombustionGeneratorBlockEntity m) return m.upgrades;
        if (be instanceof WirelessChargerBlockEntity m) return m.upgrades;
        if (be instanceof ReplicatorControllerBlockEntity m) return m.upgrades;
        if (be instanceof ArchitectTableBlockEntity m) return m.upgrades;
        throw new IllegalStateException("no cards in " + state);
    }

    private static int expectedCap(int mk, UpgradeKind kind) {
        return switch (kind) {
            case SPEED -> 2 * mk;
            case EFFICIENCY, RANGE, GROWTH -> mk;
            case FORTUNE -> Math.min(mk, 3);
            default -> 1;
        };
    }

    private static void checkMk(GameTestHelper helper, String name, IntFunction<BlockState> block, Set<UpgradeKind> kinds) {
        for (int mk = 1; mk <= 4; mk++) {
            String at = name + " Mk" + mk + ": ";
            Upgrades up = cards(helper, block.apply(mk));
            int slots = Math.min(mk + 1, UpgradeRules.kindSlots(kinds));
            helper.assertTrue(up.activeSlots() == slots, at + "slots " + up.activeSlots() + ", want " + slots);
            helper.assertTrue(up.acceptedKinds().equals(kinds), at + "kinds " + up.acceptedKinds());
            for (UpgradeKind kind : UpgradeKind.values()) {
                int want = kinds.contains(kind) ? expectedCap(mk, kind) : 0;
                helper.assertTrue(up.cap(kind) == want, at + kind + " cap " + up.cap(kind) + ", want " + want);
            }
            ItemStack rest = up.insertItem(0, CoreItems.cards(SPEED, 8), false);
            helper.assertTrue(up.level(SPEED) == 2 * mk && rest.getCount() == 8 - 2 * mk, at + "speed beyond the cap is refused");
            if (slots < up.getSlots()) {
                helper.assertTrue(up.insertItem(slots, CoreItems.cards(EFFICIENCY, 1), true).getCount() == 1, at + "the slot after the last is closed");
            }
            helper.assertTrue(up.insertItem(1, CoreItems.cards(SPEED, 1), true).getCount() == 1, at + "one slot per kind");
            helper.assertTrue(up.insertItem(1, CoreItems.cards(HEIGHT, 1), true).getCount() == 1, at + "a kind it does not take is refused");
            up.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    /**
     * Every machine with a Mk: card slots Mk + 1 (at most one per kind it takes), speed 2 x Mk, efficiency, range and
     * growth Mk, fortune min(Mk, 3).
     */
    @GameTest(template = "empty")
    public static void mkRule(GameTestHelper helper) {
        checkMk(helper, "Alloy Smelter", mk -> IndustryRegistry.machineBlock(Machine.ALLOY_SMELTER, mk).get().defaultBlockState(),
                EnumSet.of(SPEED, EFFICIENCY));
        checkMk(helper, "Assembler", mk -> IndustryRegistry.machineBlock(Machine.ASSEMBLER, mk).get().defaultBlockState(), EnumSet.of(SPEED, EFFICIENCY));
        checkMk(helper, "Centrifuge", mk -> IndustryRegistry.machineBlock(Machine.CENTRIFUGE, mk).get().defaultBlockState(),
                EnumSet.of(SPEED, EFFICIENCY, VOID));
        Block[] presses = {PowerRegistry.METAL_PRESS.get(), PowerRegistry.METAL_PRESS_MK2.get(), PowerRegistry.METAL_PRESS_MK3.get(),
                PowerRegistry.METAL_PRESS_MK4.get()};
        checkMk(helper, "Metal Press", mk -> presses[mk - 1].defaultBlockState(), EnumSet.of(SPEED, EFFICIENCY));
        Block[] chargers = {PowerRegistry.WIRELESS_CHARGER.get(), PowerRegistry.WIRELESS_CHARGER_MK2.get(), PowerRegistry.WIRELESS_CHARGER_MK3.get(),
                PowerRegistry.WIRELESS_CHARGER_MK4.get()};
        checkMk(helper, "Wireless Charger", mk -> chargers[mk - 1].defaultBlockState(), EnumSet.of(SPEED, RANGE));
        checkMk(helper, "Grinder", mk -> ProcessingRegistry.GRINDERS.get(mk - 1).get().defaultBlockState(), EnumSet.of(SPEED, EFFICIENCY, FORTUNE, VOID));
        checkMk(helper, "Electric Furnace", mk -> ProcessingRegistry.FURNACES.get(mk - 1).get().defaultBlockState(),
                EnumSet.of(SPEED, EFFICIENCY, RANGE));
        Block[] excavators = {AutomationContent.EXCAVATOR.get(), AutomationContent.EXCAVATOR_MK2.get(), AutomationContent.EXCAVATOR_MK3.get(),
                AutomationContent.EXCAVATOR_MK4.get()};
        checkMk(helper, "Excavator", mk -> excavators[mk - 1].defaultBlockState(), EnumSet.of(SPEED, RANGE, EFFICIENCY, FORTUNE, SILK, VOID));
        Block[] rigs = {AutomationContent.SURVEY_RIG.get(), AutomationContent.SURVEY_RIG_MK2.get(), AutomationContent.SURVEY_RIG_MK3.get(),
                AutomationContent.SURVEY_RIG_MK4.get()};
        checkMk(helper, "Survey Rig", mk -> rigs[mk - 1].defaultBlockState(), EnumSet.of(SPEED, EFFICIENCY, FORTUNE, SILK, VOID));
        helper.succeed();
    }

    /** Walking robots follow the Mk rule, except range: 4 range cards on every Mk. */
    @GameTest(template = "empty")
    public static void robotsFollowTheMkRuleWithFreeRange(GameTestHelper helper) {
        FarmBot sprout = helper.spawn(FarmBotContent.SPROUT_ENTITY.get(), POS);
        FarmBot stumpy = helper.spawn(FarmBotContent.STUMPY_ENTITY.get(), POS);
        Rancher rancher = helper.spawn(RancherContent.RANCHER_ENTITY.get(), POS);
        for (int mk = 1; mk <= 4; mk++) {
            sprout.setTier(mk);
            stumpy.setTier(mk);
            checkRobot(helper, "Sprout Mk" + mk + ": ", sprout.upgrades, mk, FarmBot.KINDS);
            checkRobot(helper, "Stumpy Mk" + mk + ": ", stumpy.upgrades, mk, FarmBot.KINDS);
        }
        for (int mk = 1; mk <= 2; mk++) {
            rancher.setTier(mk);
            checkRobot(helper, "Rancher Mk" + mk + ": ", rancher.upgrades, mk, Rancher.KINDS);
        }
        sprout.discard();
        stumpy.discard();
        rancher.discard();
        helper.succeed();
    }

    private static void checkRobot(GameTestHelper helper, String at, Upgrades up, int mk, Set<UpgradeKind> kinds) {
        helper.assertTrue(up.activeSlots() == Math.min(mk + 1, UpgradeRules.kindSlots(kinds)), at + "slots " + up.activeSlots());
        helper.assertTrue(up.acceptedKinds().equals(kinds), at + "kinds " + up.acceptedKinds());
        for (UpgradeKind kind : UpgradeKind.values()) {
            int want = !kinds.contains(kind) ? 0 : kind == RANGE ? RobotArea.RANGE_CARDS : expectedCap(mk, kind);
            helper.assertTrue(up.cap(kind) == want, at + kind + " cap " + up.cap(kind) + ", want " + want);
        }
    }

    /** Raising a robot's Mk opens its slots and caps without anything else. */
    @GameTest(template = "empty")
    public static void mkRuleFollowsTheRobotTier(GameTestHelper helper) {
        FarmBot sprout = helper.spawn(FarmBotContent.SPROUT_ENTITY.get(), POS);
        Upgrades up = sprout.upgrades;
        up.setStackInSlot(0, CoreItems.cards(SPEED, 8));
        helper.assertTrue(up.level(SPEED) == 2 && up.activeSlots() == 2, "Mk1: 2 of 8 speed cards count, 2 slots");
        sprout.setTier(4);
        helper.assertTrue(up.level(SPEED) == 8 && up.activeSlots() == 4, "Mk4: all 8 count, 4 slots (one per card kind)");
        up.setStackInSlot(0, ItemStack.EMPTY);
        sprout.discard();
        helper.succeed();
    }

    private static void checkFixed(GameTestHelper helper, UpgradeRules.Fixed machine, Upgrades up) {
        int open = Math.min(machine.slots, UpgradeRules.kindSlots(machine.kinds()));
        helper.assertTrue(up.getSlots() == machine.slots && up.activeSlots() == open, machine + " slots");
        helper.assertTrue(up.acceptedKinds().equals(machine.kinds()), machine + " kinds " + up.acceptedKinds());
        for (UpgradeKind kind : UpgradeKind.values()) {
            helper.assertTrue(up.cap(kind) == machine.cap(kind), machine + " " + kind + " cap " + up.cap(kind));
        }
    }

    /** Machines without a Mk use the fixed table, and the table holds the documented numbers. */
    @GameTest(template = "empty")
    public static void fixedMachines(GameTestHelper helper) {
        UpgradeRules.Fixed gen = UpgradeRules.Fixed.COMBUSTION_GENERATOR;
        helper.assertTrue(gen.slots == 2 && gen.cap(SPEED) == 4 && gen.cap(EFFICIENCY) == 4, "Combustion Generator: speed 4, efficiency 4");
        UpgradeRules.Fixed rep = UpgradeRules.Fixed.REPLICATOR;
        helper.assertTrue(rep.slots == 3 && rep.cap(SPEED) == 3 && rep.cap(FORTUNE) == 3 && rep.cap(EFFICIENCY) == 4, "Replicator: speed 3, fortune 3, efficiency 4");
        UpgradeRules.Fixed table = UpgradeRules.Fixed.ARCHITECT_TABLE;
        helper.assertTrue(table.slots == 2 && table.cap(SPEED) == 8 && table.cap(HEIGHT) == 6, "Architect Table: speed 8, height 6");

        checkFixed(helper, gen, cards(helper, PowerRegistry.COMBUSTION_GENERATOR.get().defaultBlockState()));
        checkFixed(helper, rep, cards(helper, ReplicatorRegistry.REPLICATOR_CONTROLLER.get().defaultBlockState()));
        checkFixed(helper, table, cards(helper, ArchitectRegistry.ARCHITECT_TABLE.get().defaultBlockState()));
        checkFixed(helper, UpgradeRules.Fixed.COURIER_DRONE, DronesRegistry.COURIER_DRONE_ENTITY.get().create(helper.getLevel()).upgrades);

        Upgrades up = cards(helper, PowerRegistry.COMBUSTION_GENERATOR.get().defaultBlockState());
        ItemStack rest = up.insertItem(0, CoreItems.cards(SPEED, 8), false);
        helper.assertTrue(up.level(SPEED) == 4 && rest.getCount() == 4, "the generator refuses speed cards beyond 4");
        helper.assertTrue(up.insertItem(1, CoreItems.cards(FORTUNE, 1), true).getCount() == 1, "the generator refuses fortune");
        up.setStackInSlot(0, ItemStack.EMPTY);
        helper.succeed();
    }

    /** Empty card slots show the cards that still fit: one kind per slot, the last slot every kind left over. */
    @GameTest(template = "empty")
    public static void backdropsShowTheCardsThatFit(GameTestHelper helper) {
        Upgrades up = cards(helper, AutomationContent.SURVEY_RIG_MK2.get().defaultBlockState());
        helper.assertTrue(up.fitting(0).equals(List.of(SPEED)) && up.fitting(1).equals(List.of(EFFICIENCY))
                && up.fitting(2).equals(List.of(FORTUNE, SILK, VOID)), "empty: speed, efficiency, then the rest " + up.fitting(2));
        helper.assertTrue(up.fitting(3).isEmpty(), "a closed slot shows nothing");
        up.setStackInSlot(1, CoreItems.cards(SPEED, 1));
        helper.assertTrue(up.fitting(1).isEmpty() && up.fitting(0).equals(List.of(EFFICIENCY)), "installed kinds move out of the backdrops");
        up.setStackInSlot(0, CoreItems.cards(FORTUNE, 1));
        helper.assertTrue(up.fitting(2).equals(List.of(EFFICIENCY, VOID)), "fortune rules out silk " + up.fitting(2));
        up.setStackInSlot(0, ItemStack.EMPTY);
        up.setStackInSlot(1, ItemStack.EMPTY);
        helper.succeed();
    }

    /** Cards saved in a slot that no longer opens move to a free open slot when the machine loads. */
    @GameTest(template = "empty")
    public static void cardsInClosedSlotsMoveOnLoad(GameTestHelper helper) {
        Upgrades up = cards(helper, ProcessingRegistry.ELECTRIC_FURNACE_MK4.get().defaultBlockState());
        ItemStackHandler saved = new ItemStackHandler(UpgradeRules.MAX_SLOTS);
        saved.setStackInSlot(0, CoreItems.cards(SPEED, 4));
        saved.setStackInSlot(4, CoreItems.cards(RANGE, 2));
        up.deserializeNBT(helper.getLevel().registryAccess(), saved.serializeNBT(helper.getLevel().registryAccess()));
        helper.assertTrue(up.activeSlots() == 3, "Mk4 furnace: 3 slots");
        helper.assertTrue(up.getStackInSlot(1).getCount() == 2 && up.getStackInSlot(4).isEmpty() && up.level(RANGE) == 2,
                "the range cards moved into slot 2 and count");
        helper.succeed();
    }

    /** One speed table and one energy formula, used by every machine. */
    @GameTest(template = "empty")
    public static void energyMathShared(GameTestHelper helper) {
        helper.assertTrue(Upgrades.energyMultiplier(0, 0) == 1.0, "no cards: x1");
        helper.assertTrue(Math.abs(Upgrades.energyMultiplier(1, 0) - 1.3) < 1e-9, "1 speed card: +30% per action");
        helper.assertTrue(Math.abs(Upgrades.energyMultiplier(0, 1) - 0.85) < 1e-9, "1 efficiency card: -15%");
        helper.assertTrue(Upgrades.energyMultiplier(0, 4) == 0.4 && Upgrades.steepEnergyMultiplier(0, 4) == 0.4, "4 efficiency cards: the 40% floor");
        helper.assertTrue(Math.abs(Upgrades.steepEnergyMultiplier(8, 0) - 21.0) < 1e-9, "quarries: 8 speed cards x21 per action");
        helper.assertTrue(Upgrades.speedMultiplier(1) == 2 && Upgrades.speedMultiplier(4) == 6 && Upgrades.speedMultiplier(8) == 20, "speed steps");

        for (int s = 0; s <= 4; s++) {
            for (int e = 0; e <= 4; e++) {
                double perCard = com.arno.robotica.power.PowerConfig.generatorEfficiencyPerCard();
                helper.assertTrue(CombustionGeneratorBlockEntity.fuelPerTick(s, e) == Upgrades.speedMultiplier(s) * Upgrades.energyMultiplier(s, e, perCard),
                        "generator fuel follows the machine math (own efficiency step) at " + s + "/" + e);
            }
        }

        GrinderBlockEntity grinder = (GrinderBlockEntity) helper.getBlockEntity(place(helper, ProcessingRegistry.GRINDER_MK1.get().defaultBlockState()));
        grinder.upgrades.setStackInSlot(0, CoreItems.cards(SPEED, 2));
        grinder.upgrades.setStackInSlot(1, CoreItems.cards(EFFICIENCY, 1));
        int want = (int) Math.ceil(100 * grinder.speedFactor() * Upgrades.energyMultiplier(2, 1));
        helper.assertTrue(grinder.powerFor(100) == want, "Grinder FE/t " + grinder.powerFor(100) + ", want " + want);
        grinder.upgrades.setStackInSlot(0, ItemStack.EMPTY);
        grinder.upgrades.setStackInSlot(1, ItemStack.EMPTY);
        helper.succeed();
    }

    private static BlockPos place(GameTestHelper helper, BlockState state) {
        helper.setBlock(POS, state);
        return POS;
    }
}
