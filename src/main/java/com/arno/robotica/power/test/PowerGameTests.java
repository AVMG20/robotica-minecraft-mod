package com.arno.robotica.power.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.PowerConfig;
import com.arno.robotica.power.block.AccumulatorBlockEntity;
import com.arno.robotica.power.block.ChargerBlockEntity;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import com.arno.robotica.power.block.MetalPressBlockEntity;
import com.arno.robotica.power.block.PowerBlock;
import com.arno.robotica.power.block.WindingCrankBlockEntity;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaNetwork;
import com.arno.robotica.power.tesla.TeslaTier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import com.arno.robotica.power.block.AccumulatorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;

/** Headless tests of the power module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class PowerGameTests {

    /**
     * Solar Mk1-Mk4: each tier makes more than the one before, has a buffer and an FE port, and its recipe consumes
     * the tier below (Mk3 and Mk4 are the Age 3 and Age 4 panels).
     */
    @GameTest(template = "empty")
    public static void solarTiersClimb(GameTestHelper helper) {
        var tiers = java.util.List.of(PowerRegistry.SOLAR_PANEL_MK1, PowerRegistry.SOLAR_PANEL_MK2, PowerRegistry.SOLAR_PANEL_MK3, PowerRegistry.SOLAR_PANEL_MK4);
        int lastOutput = 0;
        for (int i = 0; i < tiers.size(); i++) {
            var block = tiers.get(i).get();
            int output = block.tier().output();
            helper.assertTrue(output > lastOutput, "Mk" + (i + 1) + " makes more than the tier below: " + output);
            lastOutput = output;
            BlockPos pos = new BlockPos(i % 3, 1, i / 3);
            helper.setBlock(pos, block.defaultBlockState());
            com.arno.robotica.power.block.SolarPanelBlockEntity be = (com.arno.robotica.power.block.SolarPanelBlockEntity) helper.getBlockEntity(pos);
            helper.assertTrue(be.capacity() == block.tier().buffer, "Mk" + (i + 1) + " buffer");
            IEnergyStorage cap = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(pos), Direction.DOWN);
            helper.assertTrue(cap != null && cap.canExtract() && !cap.canReceive(), "Mk" + (i + 1) + " gives FE out and takes none");
            if (i == 0) continue;
            var recipe = helper.getLevel().getRecipeManager().byKey(Robotica.id("solar_panel_mk" + (i + 1)));
            helper.assertTrue(recipe.isPresent(), "recipe for Mk" + (i + 1));
            ItemStack below = new ItemStack(tiers.get(i - 1).get());
            helper.assertTrue(recipe.get().value().getIngredients().stream().anyMatch(ing -> ing.test(below)),
                    "Mk" + (i + 1) + " consumes the Mk" + i);
        }
        helper.succeed();
    }

    /** The metal press turns an iron ingot into an iron plate, and a speed card makes it much faster. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void metalPressMakesIronPlate(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(PowerRegistry.PRESSING_TYPE.get(),
                new SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), helper.getLevel());
        helper.assertTrue(recipe.isPresent(), "robotica:pressing recipe for iron ingots is missing");
        helper.assertTrue(recipe.get().value().result().is(CoreItems.IRON_PLATE.get()), "iron ingot should press into an iron plate");

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.METAL_PRESS.get().defaultBlockState());
        MetalPressBlockEntity press = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        press.items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        press.energy.setEnergy(MetalPressBlockEntity.ENERGY_CAPACITY);

        helper.succeedWhen(() -> {
            ItemStack out = press.items.getStackInSlot(1);
            helper.assertTrue(out.is(CoreItems.IRON_PLATE.get()) && out.getCount() == 1, "Press should output one iron plate");
            helper.assertTrue(press.items.getStackInSlot(0).isEmpty(), "Ingot should be consumed");
        });
    }

    /** Generic tag rule: a c:ingots/<m> item presses into the c:plates/<m> item; explicit recipes still win. */
    @GameTest(template = "empty")
    public static void metalPressTagRule(GameTestHelper helper) {
        var level = helper.getLevel();
        var rule = com.arno.robotica.power.recipe.PressingLogic.tagRecipe(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(rule != null && rule.result().is(CoreItems.GOLD_PLATE.get()) && rule.result().getCount() == 1,
                "gold ingot -> gold plate by the c: tags, got " + (rule == null ? null : rule.result()));
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.tagRecipe(new ItemStack(Items.NETHERITE_INGOT)) == null,
                "no c:plates/netherite: no rule");
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.find(level, new ItemStack(Items.DIRT)) == null, "dirt does not press");
        var explicit = level.getRecipeManager().getRecipeFor(PowerRegistry.PRESSING_TYPE.get(),
                new SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), level);
        helper.assertTrue(explicit.isPresent() && com.arno.robotica.power.recipe.PressingLogic.find(level, new ItemStack(Items.IRON_INGOT))
                == explicit.get().value(), "the robotica:pressing recipe beats the tag rule");
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.tagRules(s -> false).stream()
                .anyMatch(h -> h.value().ingredient().test(new ItemStack(Items.COPPER_INGOT))), "JEI rules list copper");
        helper.assertTrue(com.arno.robotica.power.recipe.PressingLogic.tagRules(s -> true).isEmpty(), "covered inputs are left out");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void metalPressSpeedCardIsFaster(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.METAL_PRESS.get().defaultBlockState());
        MetalPressBlockEntity press = (MetalPressBlockEntity) helper.getBlockEntity(pos);
        press.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 4));
        press.items.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 2));
        press.energy.setEnergy(MetalPressBlockEntity.ENERGY_CAPACITY);

        // Four speed cards are x6: 17 ticks per plate, so both ingots are done inside 60 ticks (base speed would need 200).
        helper.succeedWhen(() -> {
            ItemStack out = press.items.getStackInSlot(1);
            helper.assertTrue(out.is(CoreItems.COPPER_PLATE.get()) && out.getCount() == 2, "Two copper plates expected");
        });
    }

    @GameTest(template = "empty")
    public static void windingCrankWindsMainspring(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.WINDING_CRANK.get().defaultBlockState());
        WindingCrankBlockEntity crank = (WindingCrankBlockEntity) helper.getBlockEntity(pos);
        BlockPos abs = helper.absolutePos(pos);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, abs, null);
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, null);
        helper.assertTrue(energy != null && items != null, "Crank must expose FE and item capabilities");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "Empty crank accepts no energy");

        ItemStack spring = new ItemStack(CoreItems.MAINSPRING.get());
        helper.assertTrue(items.insertItem(0, spring, false).isEmpty(), "Automation may insert a Mainspring");
        helper.assertTrue(helper.getBlockState(pos).getValue(com.arno.robotica.power.block.WindingCrankBlock.SPRING), "Block state should show the spring");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 200, "A source winds at 200 FE/t");
        helper.assertTrue(energy.receiveEnergy(1000, false) == 0, "Only 200 FE per tick in total");
        helper.assertTrue(crank.wind(2000) == 2000, "Hand winding adds 2000 FE");
        helper.assertTrue(ItemEnergy.get(crank.spring.getStackInSlot(0)) == 2200, "Spring should hold 2200 FE");
        helper.assertTrue(items.extractItem(0, 1, true).isEmpty(), "Automation cannot extract a half-wound spring");

        ItemEnergy.fill(crank.spring.getStackInSlot(0));
        helper.assertTrue(!items.extractItem(0, 1, false).isEmpty(), "Automation can extract a full spring");
        // Other FE items fit (an FE source charges them) but only a Mainspring winds by hand.
        helper.assertTrue(items.insertItem(0, new ItemStack(CoreItems.COPPER_CELL.get()), false).isEmpty(), "A cell fits in the crank");
        helper.assertTrue(crank.wind(2000) == 0, "Hand winding only works on a Mainspring");
        helper.assertTrue(!WindingCrankBlockEntity.isWindable(new ItemStack(PowerRegistry.ACCUMULATOR_1_ITEM.get())),
                "An item that takes no FE does not fit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void accumulatorKeepsEnergyInItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.ACCUMULATOR_2.get().defaultBlockState());
        AccumulatorBlockEntity be = (AccumulatorBlockEntity) helper.getBlockEntity(pos);
        be.energy.setEnergy(123_456);
        DataComponentMap components = be.collectComponents();
        helper.assertTrue(components.getOrDefault(CoreComponents.ENERGY.get(), 0) == 123_456, "Energy must be written to the item components");

        BlockPos other = new BlockPos(2, 1, 1);
        helper.setBlock(other, PowerRegistry.ACCUMULATOR_2.get().defaultBlockState());
        AccumulatorBlockEntity placed = (AccumulatorBlockEntity) helper.getBlockEntity(other);
        placed.applyComponents(components, DataComponentPatch.EMPTY);
        helper.assertTrue(placed.energy.getEnergyStored() == 123_456, "Placed accumulator must restore the energy");
        helper.assertTrue(placed.energy.getMaxEnergyStored() == PowerConfig.accumulatorCapacity(2)
                && PowerConfig.accumulatorCapacity(2) == 16_000_000, "Tier II holds 16M FE (config)");

        ItemStack item = new ItemStack(PowerRegistry.ACCUMULATOR_2_ITEM.get());
        ItemEnergy.set(item, 5_000);
        IEnergyStorage itemCap = item.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(itemCap != null && itemCap.getEnergyStored() == 5_000 && itemCap.receiveEnergy(100, false) == 0,
                "Accumulator item shows its energy and cannot be charged from outside");
        helper.succeed();
    }

    /** The front gauge (block state CHARGE) follows the stored energy, and a placed item shows its charge at once. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void accumulatorGaugeShowsCharge(GameTestHelper helper) {
        helper.assertTrue(AccumulatorBlock.chargeLevel(0, 1_000_000) == 0, "empty: no cell");
        helper.assertTrue(AccumulatorBlock.chargeLevel(1, 1_000_000) == 1, "any charge: one cell");
        helper.assertTrue(AccumulatorBlock.chargeLevel(1_000_000, 1_000_000) == 5, "full: five cells");

        BlockPos pos = new BlockPos(1, 1, 1);
        AccumulatorBlockEntity be = accumulator(helper, pos, Direction.NORTH, 600_000);

        ItemStack item = new ItemStack(PowerRegistry.ACCUMULATOR_1_ITEM.get());
        ItemEnergy.set(item, 1_000_000);
        BlockPos other = helper.absolutePos(new BlockPos(1, 1, 2));
        var hit = new BlockHitResult(Vec3.atCenterOf(other), Direction.UP, other, false);
        var context = new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, item, hit);
        helper.assertTrue(((BlockItem) item.getItem()).place(context).consumesAction(), "accumulator item placed");
        helper.assertTrue(helper.getLevel().getBlockState(other).getValue(AccumulatorBlock.CHARGE) == 5,
                "a full item is placed with a full gauge");
        helper.assertTrue(helper.getLevel().getBlockEntity(other) instanceof AccumulatorBlockEntity placed
                && placed.energy.getEnergyStored() == 1_000_000, "the placed block keeps the energy");

        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(pos).getValue(AccumulatorBlock.CHARGE) == 3,
                "60% shows three cells, got " + helper.getBlockState(pos).getValue(AccumulatorBlock.CHARGE)
                        + " at " + be.energy.getEnergyStored() + " FE"));
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void chargerChargesCell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(!ChargerBlockEntity.canCharge(new ItemStack(CoreItems.MAINSPRING.get())), "Mainspring only winds at the crank");
        charger.slot.setStackInSlot(0, new ItemStack(CoreItems.COPPER_CELL.get()));
        charger.energy.setEnergy(ChargerBlockEntity.ENERGY_CAPACITY);

        helper.succeedWhen(() -> {
            int stored = ItemEnergy.get(charger.slot.getStackInSlot(0));
            int rate = Math.min(com.arno.robotica.power.PowerConfig.chargerRate(), ChargerBlockEntity.MAX_RECEIVE);
            helper.assertTrue(stored >= 3 * rate, "Cell should be charging at " + rate + " FE/t, has " + stored);
            helper.assertTrue(stored % rate == 0, "Charge rate is " + rate + " FE/t, has " + stored);
        });
    }

    // ---- Tesla Coils ----

    private static TeslaCoilBlockEntity coil(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.Block block) {
        helper.setBlock(pos, block.defaultBlockState().setValue(TeslaCoilBlock.FACING, Direction.UP));
        return (TeslaCoilBlockEntity) helper.getBlockEntity(pos);
    }

    private static AccumulatorBlockEntity accumulator(GameTestHelper helper, BlockPos pos, Direction front, int energy) {
        helper.setBlock(pos, PowerRegistry.ACCUMULATOR_1.get().defaultBlockState().setValue(PowerBlock.FACING, front));
        AccumulatorBlockEntity be = (AccumulatorBlockEntity) helper.getBlockEntity(pos);
        be.energy.setEnergy(energy);
        return be;
    }

    /** Accumulator -> coil on top of it -> Charger: the coil pulls from the block it sits on and feeds its link. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void teslaStorageFeedsMachine(GameTestHelper helper) {
        accumulator(helper, new BlockPos(0, 1, 0), Direction.NORTH, 500_000);
        TeslaCoilBlockEntity coil = coil(helper, new BlockPos(0, 2, 0), PowerRegistry.TESLA_COIL_1.get());
        BlockPos chargerPos = new BlockPos(2, 1, 2);
        helper.setBlock(chargerPos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(chargerPos);
        helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(chargerPos), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "link to the charger");
        helper.succeedWhen(() -> {
            helper.assertTrue(coil.isRoot(), "a coil on an Accumulator is a source");
            helper.assertTrue(charger.energy.getEnergyStored() > 2_000, "Charger should fill, has " + charger.energy.getEnergyStored());
        });
    }

    /** Accumulator -> coil A -> coil B -> Accumulator: the hop costs 5%, and A and B cannot link both ways. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void teslaHopLosesFivePercent(GameTestHelper helper) {
        AccumulatorBlockEntity source = accumulator(helper, new BlockPos(0, 1, 0), Direction.NORTH, 500_000);
        TeslaCoilBlockEntity a = coil(helper, new BlockPos(0, 2, 0), PowerRegistry.TESLA_COIL_1.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.IRON_BLOCK);
        TeslaCoilBlockEntity b = coil(helper, new BlockPos(2, 2, 2), PowerRegistry.TESLA_COIL_1.get());
        AccumulatorBlockEntity target = accumulator(helper, new BlockPos(2, 1, 0), Direction.NORTH, 0);
        helper.assertTrue(TeslaNetwork.toggle(a, b.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "A -> B");
        helper.assertTrue(TeslaNetwork.toggle(b, a.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.REVERSE, "B -> A must be refused");
        helper.assertTrue(TeslaNetwork.toggle(b, target.getBlockPos(), Direction.EAST) == TeslaNetwork.LinkResult.LINKED, "B -> accumulator");
        helper.succeedWhen(() -> {
            long received = target.energy.getEnergyStored();
            long drained = 500_000 - source.energy.getEnergyStored();
            helper.assertTrue(received >= 20_000, "target should fill, has " + received);
            helper.assertTrue(!b.isRoot(), "B sits on iron: a relay");
            helper.assertTrue(Math.abs(received * 100 - drained * 95) <= drained, "expected 95% of " + drained + ", got " + received);
        });
    }

    /** Tier I takes 4 links; a fifth is refused until one is unlinked. Tiers hold 4/8/12/16/32. */
    @GameTest(template = "empty")
    public static void teslaLinkLimitPerTier(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.IRON_BLOCK);
        TeslaCoilBlockEntity coil = coil(helper, new BlockPos(1, 1, 1), PowerRegistry.TESLA_COIL_1.get());
        BlockPos[] targets = {new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(0, 1, 2), new BlockPos(2, 1, 2), new BlockPos(0, 1, 1)};
        for (BlockPos t : targets) helper.setBlock(t, PowerRegistry.CHARGER.get().defaultBlockState());
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(targets[i]), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "link " + i);
        }
        helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(targets[4]), Direction.UP) == TeslaNetwork.LinkResult.FULL, "fifth link is refused");
        helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(targets[0]), Direction.UP) == TeslaNetwork.LinkResult.UNLINKED, "unlink frees a slot");
        helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(targets[4]), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "now it fits");
        helper.assertTrue(coil.linkCount() == 4, "four links");
        int[] expected = {4, 8, 12, 16, 32};
        for (TeslaTier tier : TeslaTier.values()) helper.assertTrue(tier.maxLinks == expected[tier.ordinal()], tier + " links");
        helper.succeed();
    }

    /** The clicked face is where the power goes in: an Accumulator's front only gives energy out. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void teslaFaceSpecificInsertion(GameTestHelper helper) {
        accumulator(helper, new BlockPos(0, 1, 0), Direction.NORTH, 500_000);
        TeslaCoilBlockEntity coil = coil(helper, new BlockPos(0, 2, 0), PowerRegistry.TESLA_COIL_1.get());
        AccumulatorBlockEntity front = accumulator(helper, new BlockPos(2, 1, 0), Direction.WEST, 0);
        AccumulatorBlockEntity side = accumulator(helper, new BlockPos(2, 1, 2), Direction.WEST, 0);
        helper.assertTrue(TeslaNetwork.toggle(coil, front.getBlockPos(), Direction.WEST) == TeslaNetwork.LinkResult.LINKED, "link front face");
        helper.assertTrue(TeslaNetwork.toggle(coil, side.getBlockPos(), Direction.EAST) == TeslaNetwork.LinkResult.LINKED, "link side face");
        helper.succeedWhen(() -> {
            helper.assertTrue(side.energy.getEnergyStored() > 5_000, "side face takes energy, has " + side.energy.getEnergyStored());
            helper.assertTrue(front.energy.getEnergyStored() == 0, "front face is output only, has " + front.energy.getEnergyStored());
        });
    }

    /** Range is checked per tier: Tier I reaches 12 blocks, Tier V 36. */
    @GameTest(template = "empty")
    public static void teslaRangeLimit(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.IRON_BLOCK);
        TeslaCoilBlockEntity small = coil(helper, new BlockPos(1, 1, 1), PowerRegistry.TESLA_COIL_1.get());
        BlockPos at = small.getBlockPos();
        helper.assertTrue(TeslaNetwork.toggle(small, at.above(13), Direction.UP) == TeslaNetwork.LinkResult.OUT_OF_RANGE, "13 blocks is out of range");
        helper.assertTrue(TeslaNetwork.toggle(small, at.above(12), Direction.UP) == TeslaNetwork.LinkResult.NO_ENERGY, "12 blocks is in range (air there; straight up, away from other tests)");
        helper.assertTrue(TeslaNetwork.toggle(small, at.below(), Direction.UP) == TeslaNetwork.LinkResult.OWN_SUPPORT, "not the block it sits on");
        helper.setBlock(new BlockPos(1, 1, 1), PowerRegistry.TESLA_COIL_5.get().defaultBlockState().setValue(TeslaCoilBlock.FACING, Direction.UP));
        TeslaCoilBlockEntity big = (TeslaCoilBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
        helper.assertTrue(TeslaNetwork.toggle(big, at.above(20), Direction.UP) == TeslaNetwork.LinkResult.NO_ENERGY, "Tier V reaches 20 blocks");
        helper.assertTrue(TeslaNetwork.toggle(big, at.above(37), Direction.UP) == TeslaNetwork.LinkResult.OUT_OF_RANGE, "but not 37");
        helper.succeed();
    }

    /** Clicking a linked target on the same face unlinks it (and energy stops); another face moves the link. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void teslaUnlinking(GameTestHelper helper) {
        accumulator(helper, new BlockPos(0, 1, 0), Direction.NORTH, 500_000);
        TeslaCoilBlockEntity coil = coil(helper, new BlockPos(0, 2, 0), PowerRegistry.TESLA_COIL_1.get());
        BlockPos chargerPos = new BlockPos(2, 1, 2);
        helper.setBlock(chargerPos, PowerRegistry.CHARGER.get().defaultBlockState());
        ChargerBlockEntity charger = (ChargerBlockEntity) helper.getBlockEntity(chargerPos);
        BlockPos abs = helper.absolutePos(chargerPos);
        helper.assertTrue(TeslaNetwork.toggle(coil, abs, Direction.UP) == TeslaNetwork.LinkResult.LINKED, "link");
        helper.assertTrue(TeslaNetwork.toggle(coil, abs, Direction.NORTH) == TeslaNetwork.LinkResult.FACE_CHANGED, "another face moves the link");
        helper.assertTrue(coil.linkCount() == 1 && coil.links().get(0).face() == Direction.NORTH, "one link, on the north face");
        helper.assertTrue(TeslaNetwork.toggle(coil, abs, Direction.NORTH) == TeslaNetwork.LinkResult.UNLINKED, "same face unlinks");
        helper.assertTrue(coil.linkCount() == 0, "no links left");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(charger.energy.getEnergyStored() == 0, "an unlinked charger gets nothing");
            helper.succeed();
        });
    }

    /**
     * Loops create no energy: coil A on an Accumulator feeds relay B, B feeds relay C, C feeds A again (a cycle) and the
     * very Accumulator A sits on (through a relay, since a direct link is refused). Energy only ever shrinks by the hop
     * losses, a machine on the loop still gets its share, and nothing recurses forever.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void teslaLoopsCreateNoEnergy(GameTestHelper helper) {
        int start = 500_000;
        AccumulatorBlockEntity source = accumulator(helper, new BlockPos(0, 1, 0), Direction.NORTH, start);
        TeslaCoilBlockEntity a = coil(helper, new BlockPos(0, 2, 0), PowerRegistry.TESLA_COIL_1.get());
        helper.setBlock(new BlockPos(2, 1, 0), Blocks.IRON_BLOCK);
        TeslaCoilBlockEntity b = coil(helper, new BlockPos(2, 2, 0), PowerRegistry.TESLA_COIL_1.get());
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.IRON_BLOCK);
        TeslaCoilBlockEntity c = coil(helper, new BlockPos(2, 2, 2), PowerRegistry.TESLA_COIL_1.get());
        AccumulatorBlockEntity sink = accumulator(helper, new BlockPos(0, 1, 2), Direction.NORTH, 0);
        helper.assertTrue(TeslaNetwork.toggle(a, source.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.OWN_SUPPORT, "no direct self link");
        helper.assertTrue(TeslaNetwork.toggle(a, b.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "A -> B");
        helper.assertTrue(TeslaNetwork.toggle(b, c.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "B -> C");
        helper.assertTrue(TeslaNetwork.toggle(c, a.getBlockPos(), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "C -> A closes a cycle");
        helper.assertTrue(TeslaNetwork.toggle(c, source.getBlockPos(), Direction.SOUTH) == TeslaNetwork.LinkResult.LINKED, "C -> A's own Accumulator");
        helper.assertTrue(TeslaNetwork.toggle(b, sink.getBlockPos(), Direction.EAST) == TeslaNetwork.LinkResult.LINKED, "B -> a machine");
        helper.runAfterDelay(60, () -> {
            long total = (long) source.energy.getEnergyStored() + sink.energy.getEnergyStored();
            helper.assertTrue(sink.energy.getEnergyStored() > 0, "the machine on the loop gets energy");
            helper.assertTrue(total < start, "the loop only loses energy: " + total + " of " + start);
            helper.assertTrue(a.isRoot() && !b.isRoot() && !c.isRoot(), "only A sends");
            helper.succeed();
        });
    }

    /**
     * A generator's push rate is one budget for all six faces, split evenly over the receivers, with or without the
     * neighbour cache; what one receiver does not take goes to the others.
     */
    @GameTest(template = "empty")
    public static void pushToNeighborsSharesOneBudget(GameTestHelper helper) {
        BlockPos center = new BlockPos(1, 2, 1);
        java.util.List<AccumulatorBlockEntity> receivers = new java.util.ArrayList<>();
        for (Direction dir : Direction.values()) {
            BlockPos at = center.relative(dir);
            // the front (output face, horizontal) points away, so the face touching the centre takes FE in
            Direction front = dir.getAxis().isHorizontal() ? dir : Direction.NORTH;
            helper.setBlock(at, PowerRegistry.ACCUMULATOR_1.get().defaultBlockState().setValue(PowerBlock.FACING, front));
            receivers.add((AccumulatorBlockEntity) helper.getBlockEntity(at));
        }
        var source = new com.arno.robotica.core.energy.MachineEnergyStorage(1_000_000, 0, 1_000_000, () -> {});
        source.setEnergy(1_000_000);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(center);
        int sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 600);
        helper.assertTrue(sent == 600, "600 FE in total, not per face: sent " + sent);
        helper.assertTrue(source.getEnergyStored() == 1_000_000 - 600, "the source paid what was sent");
        for (AccumulatorBlockEntity r : receivers) helper.assertTrue(r.stored() == 100, "an even share each, got " + r.stored());

        var cache = new com.arno.robotica.core.energy.EnergyNeighbors();
        sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 6_000, cache);
        int total = receivers.stream().mapToInt(AccumulatorBlockEntity::stored).sum();
        helper.assertTrue(sent == 6_000 && total == 6_600, "cached lookups share the budget too: sent " + sent + ", stored " + total);

        // a full receiver leaves its share to the others
        receivers.get(0).energy.setEnergy(receivers.get(0).capacity());
        sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 500, cache);
        helper.assertTrue(sent == 500, "the rest take the full budget: sent " + sent);

        // never more than the source holds
        source.setEnergy(50);
        sent = com.arno.robotica.core.energy.EnergyUtil.pushToNeighbors(level, abs, source, 600, cache);
        helper.assertTrue(sent == 50 && source.getEnergyStored() == 0, "only what the source holds: sent " + sent);
        helper.succeed();
    }
}
