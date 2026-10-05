package com.arno.robotica.power.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.AccumulatorBlockEntity;
import com.arno.robotica.power.block.ChargerBlockEntity;
import com.arno.robotica.power.block.CombustionGeneratorBlockEntity;
import com.arno.robotica.power.block.ConduitBlock;
import com.arno.robotica.power.block.MetalPressBlockEntity;
import com.arno.robotica.power.block.PowerBlock;
import com.arno.robotica.power.block.WindingCrankBlockEntity;
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

/** Headless tests of the power module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class PowerGameTests {

    /** Generator (coal) -> copper conduit -> accumulator I. Proves networks build, tick and respect the face rules. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void generatorFeedsAccumulatorThroughConduit(GameTestHelper helper) {
        BlockPos generator = new BlockPos(0, 1, 1);
        BlockPos conduit = new BlockPos(1, 1, 1);
        BlockPos accumulator = new BlockPos(2, 1, 1);
        helper.setBlock(generator, PowerRegistry.COMBUSTION_GENERATOR.get().defaultBlockState());
        helper.setBlock(conduit, PowerRegistry.COPPER_CONDUIT.get().defaultBlockState());
        // Front faces east, away from the conduit, so the conduit side is an input face.
        helper.setBlock(accumulator, PowerRegistry.ACCUMULATOR_1.get().defaultBlockState().setValue(PowerBlock.FACING, Direction.EAST));
        ((CombustionGeneratorBlockEntity) helper.getBlockEntity(generator)).fuel.setStackInSlot(0, new ItemStack(Items.COAL));
        AccumulatorBlockEntity be = (AccumulatorBlockEntity) helper.getBlockEntity(accumulator);

        helper.succeedWhen(() -> {
            helper.assertTrue(be.energy.getEnergyStored() > 200, "Accumulator should receive energy, has " + be.energy.getEnergyStored());
            ConduitBlock.Conn west = helper.getBlockState(conduit).getValue(ConduitBlock.CONN.get(Direction.WEST));
            helper.assertTrue(west == ConduitBlock.Conn.BLOCK, "Conduit should render a connection to the generator");
        });
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
        helper.assertTrue(placed.energy.getMaxEnergyStored() == 4_000_000, "Tier II holds 4M FE");

        ItemStack item = new ItemStack(PowerRegistry.ACCUMULATOR_2_ITEM.get());
        ItemEnergy.set(item, 5_000);
        IEnergyStorage itemCap = item.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(itemCap != null && itemCap.getEnergyStored() == 5_000 && itemCap.receiveEnergy(100, false) == 0,
                "Accumulator item shows its energy and cannot be charged from outside");
        helper.succeed();
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
            helper.assertTrue(stored >= 1_200, "Cell should be charging at 400 FE/t, has " + stored);
            helper.assertTrue(stored % 400 == 0, "Charge rate is 400 FE/t, has " + stored);
        });
    }

    /** Furnace-like neighbours (block entity, no FE) are recorded at scan time and never cause rescans. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void nonEndpointNeighbourDoesNotRescan(GameTestHelper helper) {
        BlockPos conduit = new BlockPos(1, 1, 1);
        BlockPos furnace = new BlockPos(2, 1, 1);
        helper.setBlock(conduit, PowerRegistry.COPPER_CONDUIT.get().defaultBlockState());
        helper.setBlock(furnace, net.minecraft.world.level.block.Blocks.FURNACE);
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            BlockPos abs = helper.absolutePos(conduit);
            helper.assertTrue(com.arno.robotica.power.conduit.ConduitManager.networkSize(level, abs) == 1, "network should be built");
            helper.assertTrue(!com.arno.robotica.power.conduit.ConduitManager.endpointsDirty(level, abs), "freshly scanned");
            for (int i = 0; i < 5; i++) {
                com.arno.robotica.power.conduit.ConduitManager.neighborChanged(level, abs, helper.absolutePos(furnace),
                        net.minecraft.world.level.block.Blocks.FURNACE);
            }
            helper.assertTrue(!com.arno.robotica.power.conduit.ConduitManager.endpointsDirty(level, abs),
                    "repeat updates from a known non-endpoint must not trigger a rescan");
            // A block entity that was not there at scan time still triggers one.
            com.arno.robotica.power.conduit.ConduitManager.neighborChanged(level, abs, helper.absolutePos(new BlockPos(1, 2, 1)),
                    net.minecraft.world.level.block.Blocks.CHEST);
            helper.assertTrue(com.arno.robotica.power.conduit.ConduitManager.endpointsDirty(level, abs), "an unknown block entity marks the network dirty");
        });
    }
}
