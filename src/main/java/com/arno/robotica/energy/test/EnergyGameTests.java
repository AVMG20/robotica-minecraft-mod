package com.arno.robotica.energy.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.BankControllerBlockEntity;
import com.arno.robotica.energy.block.ControllerBlock;
import com.arno.robotica.energy.block.FusionControllerBlockEntity;
import com.arno.robotica.energy.block.PortBlock;
import com.arno.robotica.energy.block.PortBlockEntity;
import com.arno.robotica.energy.block.ReactorControllerBlockEntity;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.ChargerBlockEntity;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

/**
 * Headless tests of the energy module ({@code scripts/docker-build.sh runGameTestServer}). They never use the industry
 * module's pellets: a barrier stands in as reactor fuel and a structure void as fusion fuel (registered through the
 * test hooks of {@link EnergyDataMaps}). Template "energy_arena" is an empty 9x9x9 box.
 */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class EnergyGameTests {
    private static final String ARENA = "energy_arena";
    /** Test fuel: 400 heat per tick for 100 ticks per unit, one gunpowder of waste. */
    private static final EnergyDataMaps.ReactorFuel TEST_FUEL = new EnergyDataMaps.ReactorFuel(400, 100, Optional.of(Items.GUNPOWDER));

    static {
        EnergyDataMaps.registerTestFuel(Items.BARRIER, TEST_FUEL);
        EnergyDataMaps.registerTestFusionFuel(Items.STRUCTURE_VOID, new EnergyDataMaps.FusionFuel(200_000, 6_000));
    }

    // ---------------------------------------------------------------- builders

    /**
     * Builds a hollow cuboid of {@code casing} from {@code min} (relative), w x h x d, with the controller in the middle
     * of the north wall (facing north, out of the box). Returns the controller position (relative).
     */
    private static BlockPos cuboid(GameTestHelper helper, BlockPos min, int w, int h, int d, Block casing, Block controller) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0 || x == w - 1 || y == h - 1 || z == d - 1;
                    helper.setBlock(min.offset(x, y, z), shell ? casing.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos c = min.offset(w / 2, h / 2, 0);
        helper.setBlock(c, controller.defaultBlockState().setValue(ControllerBlock.FACING, Direction.NORTH));
        return c;
    }

    /** Translation key of the controller's structure problem, or null. */
    private static String key(com.arno.robotica.energy.block.StructureControllerBlockEntity be) {
        if (be.problem() == null) return null;
        return be.problem().message().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T be(GameTestHelper helper, BlockPos rel) {
        return (T) helper.getBlockEntity(rel);
    }

    /** 5x5x5 reactor at (1,1,1): rod column in the middle, {@code coolant} around it (or air), a power port in the south wall. */
    private static ReactorControllerBlockEntity reactor(GameTestHelper helper, BlockState coolant) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos c = cuboid(helper, min, 5, 5, 5, EnergyRegistry.REACTOR_CASING.get(), EnergyRegistry.REACTOR_CONTROLLER.get());
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    boolean rod = x == 2 && z == 2;
                    helper.setBlock(min.offset(x, y, z), rod ? EnergyRegistry.REACTOR_FUEL_ROD.get().defaultBlockState() : coolant);
                }
            }
        }
        helper.setBlock(min.offset(2, 2, 4), EnergyRegistry.REACTOR_POWER_PORT.get());
        helper.setBlock(min.offset(1, 2, 4), EnergyRegistry.REACTOR_ACCESS_PORT.get());
        helper.setBlock(min.offset(4, 2, 2), EnergyRegistry.REACTOR_GLASS.get());
        ReactorControllerBlockEntity be = be(helper, c);
        be.scanNow();
        return be;
    }

    // ---------------------------------------------------------------- structure detection

    /** A valid reactor forms; a stranger in the wall, a gap in the frame and a second controller are named with their position. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void structureNamesTheWrongBlock(GameTestHelper helper) {
        ReactorControllerBlockEntity be = reactor(helper, Blocks.PACKED_ICE.defaultBlockState());
        helper.assertTrue(be.isFormed(), "5x5x5 reactor should form, problem: " + (be.problem() == null ? "-" : be.problem().message().getString()));
        helper.assertTrue(be.box() != null && be.box().getXSpan() == 5 && be.box().getYSpan() == 5 && be.box().getZSpan() == 5, "box should be 5x5x5");
        helper.assertTrue(be.rods() == 3, "one column of 3 rods, got " + be.rods());
        helper.assertTrue(Math.abs(be.averageCoolant() - 8.0) < 0.01, "4 packed ice (2) per rod = 8, got " + be.averageCoolant());

        // Wrong block in a wall (not in the controller's row or column): named exactly.
        BlockPos dirt = new BlockPos(1, 1, 1).offset(4, 1, 1);
        helper.setBlock(dirt, Blocks.DIRT);
        be.scanNow();
        helper.assertFalse(be.isFormed(), "dirt in the wall must unform it");
        helper.assertTrue(helper.absolutePos(dirt).equals(be.problem().pos()), "problem should point at the dirt, got " + be.problem().pos());
        helper.assertTrue("multiblock.robotica.wall_wrong".equals(key(be)), "wrong block in the wall, got " + key(be));
        // The GUI sync carries the problem as JSON: it must survive the round trip.
        var tag = new net.minecraft.nbt.CompoundTag();
        be.writeSync(tag, helper.getLevel().registryAccess());
        var back = net.minecraft.network.chat.Component.Serializer.fromJson(tag.getString("problem"), helper.getLevel().registryAccess());
        helper.assertTrue(back != null && back.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey().equals("multiblock.robotica.wall_wrong") && tag.getLong("problemPos") == helper.absolutePos(dirt).asLong(), "GUI sync of the problem");
        helper.setBlock(dirt, EnergyRegistry.REACTOR_CASING.get());

        // Glass on an edge: frame must be casing.
        BlockPos edge = new BlockPos(1, 1, 1).offset(0, 2, 0);
        helper.setBlock(edge, EnergyRegistry.REACTOR_GLASS.get());
        be.scanNow();
        helper.assertTrue(!be.isFormed() && helper.absolutePos(edge).equals(be.problem().pos()), "glass on the frame edge must be named");
        helper.setBlock(edge, EnergyRegistry.REACTOR_CASING.get());

        // A gap in the controller's row is found by the walk itself.
        BlockPos gap = new BlockPos(1, 1, 1).offset(1, 2, 0);
        helper.setBlock(gap, Blocks.AIR);
        be.scanNow();
        helper.assertTrue(!be.isFormed() && helper.absolutePos(gap).equals(be.problem().pos()), "the gap next to the controller must be named, got " + be.problem().pos());
        helper.setBlock(gap, EnergyRegistry.REACTOR_CASING.get());

        // A second controller.
        BlockPos second = new BlockPos(1, 1, 1).offset(2, 4, 2);
        helper.setBlock(second, EnergyRegistry.REACTOR_CONTROLLER.get());
        be.scanNow();
        helper.assertTrue(!be.isFormed() && helper.absolutePos(second).equals(be.problem().pos()), "the second controller must be named");
        helper.setBlock(second, EnergyRegistry.REACTOR_CASING.get());

        be.scanNow();
        helper.assertTrue(be.isFormed(), "fixed again: formed");
        helper.succeed();
    }

    /** Too shallow (front and back wall touch), an incomplete rod column, and a port without a controller. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void structureTooSmallAndMissingController(GameTestHelper helper) {
        // 3 wide, 3 tall, but only 2 deep.
        BlockPos c = cuboid(helper, new BlockPos(1, 1, 1), 3, 3, 2, EnergyRegistry.BANK_CASING.get(), EnergyRegistry.BANK_CONTROLLER.get());
        BankControllerBlockEntity bank = be(helper, c);
        bank.scanNow();
        helper.assertFalse(bank.isFormed(), "a 3x3x2 bank is too shallow");
        helper.assertTrue("multiblock.robotica.too_shallow".equals(key(bank)), "problem should say it is not deep enough, got " + key(bank));

        // A rod column that does not reach the roof.
        ReactorControllerBlockEntity reactor = reactorAt(helper, new BlockPos(1, 1, 4));
        helper.assertTrue(reactor.isFormed(), "test reactor forms");
        BlockPos top = new BlockPos(1, 1, 4).offset(2, 3, 2);
        helper.setBlock(top, Blocks.AIR);
        reactor.scanNow();
        helper.assertTrue(!reactor.isFormed() && helper.absolutePos(top).equals(reactor.problem().pos()), "the missing rod must be named");

        // Missing controller: a bank shell without one never links its port.
        BlockPos min = new BlockPos(5, 1, 1);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) {
            helper.setBlock(min.offset(x, y, z), (x == 1 && y == 1 && z == 1) ? EnergyRegistry.CAPACITOR_COPPER.get().defaultBlockState()
                    : EnergyRegistry.BANK_CASING.get().defaultBlockState());
        }
        helper.setBlock(min.offset(1, 1, 0), EnergyRegistry.BANK_PORT.get());
        PortBlockEntity port = be(helper, min.offset(1, 1, 0));
        helper.assertTrue(port.controller() == null, "no controller: the port is not linked");
        IEnergyStorage cap = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(min.offset(1, 1, 0)), Direction.NORTH);
        helper.assertTrue(cap != null && !cap.canReceive() && cap.receiveEnergy(1000, false) == 0, "an unlinked port takes nothing");
        helper.succeed();
    }

    /** 5x5x5 reactor with one full column of 3 rods and packed ice, at another spot. */
    private static ReactorControllerBlockEntity reactorAt(GameTestHelper helper, BlockPos min) {
        BlockPos c = cuboid(helper, min, 5, 5, 5, EnergyRegistry.REACTOR_CASING.get(), EnergyRegistry.REACTOR_CONTROLLER.get());
        for (int y = 1; y <= 3; y++) helper.setBlock(min.offset(2, y, 2), EnergyRegistry.REACTOR_FUEL_ROD.get());
        helper.setBlock(min.offset(2, 2, 4), EnergyRegistry.REACTOR_POWER_PORT.get());
        ReactorControllerBlockEntity be = be(helper, c);
        be.scanNow();
        return be;
    }

    // ---------------------------------------------------------------- reactor

    /** Packed ice: heat 400 x 3^0.75 = 912, cooling 4,787, efficiency 1.4: about 1,280 FE/t, waste comes out, rods throttle. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void reactorMakesPowerAndWaste(GameTestHelper helper) {
        ReactorControllerBlockEntity be = reactor(helper, Blocks.PACKED_ICE.defaultBlockState());
        helper.assertTrue(be.isFormed(), "reactor forms");
        be.fuel.setStackInSlot(0, new ItemStack(Items.BARRIER, 8));
        be.simulate(100);
        helper.assertTrue(be.state() == ReactorControllerBlockEntity.State.RUNNING, "should run, is " + be.state());
        double expectedHeat = 400 * Math.pow(3, 0.75);
        helper.assertTrue(Math.abs(be.heat() - expectedHeat) < 1, "heat " + be.heat() + " expected " + expectedHeat);
        helper.assertTrue(be.fePerTick() > 1_180 && be.fePerTick() < 1_380, "about 1,280 FE/t, got " + be.fePerTick());
        helper.assertTrue(be.temperature() < 1000, "well cooled reactor stays under the safe temperature, got " + be.temperature());
        int waste = 0;
        for (int i = 0; i < be.waste.getSlots(); i++) waste += be.waste.getStackInSlot(i).is(Items.GUNPOWDER) ? be.waste.getStackInSlot(i).getCount() : 0;
        helper.assertTrue(waste >= 2, "100 ticks burn 100 * 2.28 / 100 = 2.3 units, waste " + waste);

        // Access Port: fuel goes in, waste comes out, fuel can not be pulled.
        IItemHandler access = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(new BlockPos(2, 3, 5)), Direction.SOUTH);
        helper.assertTrue(access != null && access.getSlots() == 6, "access port shows 3 fuel + 3 waste slots");
        helper.assertTrue(access.insertItem(1, new ItemStack(Items.BARRIER), false).isEmpty(), "fuel goes in");
        helper.assertTrue(!access.insertItem(1, new ItemStack(Items.DIRT), true).isEmpty(), "non-fuel is refused");
        helper.assertTrue(access.extractItem(0, 1, true).isEmpty(), "fuel can not be pulled out");
        helper.assertTrue(access.extractItem(3, 64, false).is(Items.GUNPOWDER), "waste can be pulled out");

        int full = be.fePerTick();
        be.setRodInsertion(50);
        be.simulate(1);
        helper.assertTrue(Math.abs(be.heat() - expectedHeat / 2) < 1, "50% rods halve the heat");
        helper.assertTrue(Math.abs(be.fePerTick() - full / 2) <= 2, "and the output");

        // Power Port gives the buffer out through the standard FE capability.
        IEnergyStorage port = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(new BlockPos(3, 3, 5)), Direction.SOUTH);
        int stored = be.energy.getEnergyStored();
        helper.assertTrue(port != null && port.canExtract() && !port.canReceive(), "power port: out only");
        helper.assertTrue(stored > 0 && port.extractEnergy(1_000, false) == Math.min(1_000, stored), "power port gives FE");
        helper.succeed();
    }

    /** No coolant: heat far above cooling, the temperature climbs, output throttles, then a SCRAM. Nothing breaks. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void reactorScramsWithoutCooling(GameTestHelper helper) {
        ReactorControllerBlockEntity be = reactor(helper, Blocks.AIR.defaultBlockState());
        helper.assertTrue(be.isFormed(), "reactor with air around the rods still forms");
        be.fuel.setStackInSlot(0, new ItemStack(Items.BARRIER, 64));
        boolean throttled = false;
        int wasteBefore = 0;
        for (int i = 0; i < 600 && !be.isScrammed(); i++) {
            wasteBefore = gunpowder(be);
            be.simulate(1);
            if (be.temperature() > 1000 && be.throttle() < 1.0) throttled = true;
        }
        helper.assertTrue(throttled, "output should throttle above the safe temperature");
        helper.assertTrue(be.isScrammed(), "load 4x cooling must SCRAM, temperature " + be.temperature());
        helper.assertTrue(be.burnLeft() == 0 && gunpowder(be) > wasteBefore, "a SCRAM turns the burning pellet into waste");
        be.simulate(1);
        helper.assertTrue(be.heat() == 0 && be.fePerTick() == 0, "a SCRAMed reactor makes no heat");
        helper.assertFalse(be.resetScram(), "no reset while hot");
        be.simulate(400);
        helper.assertTrue(be.temperature() < 200, "cools down after the SCRAM, " + be.temperature());
        // No explosion, no block damage: every shell block is still there.
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) {
            helper.assertTrue(!helper.getBlockState(new BlockPos(1 + x, 1 + y, 1)).isAir(), "north wall intact");
        }
        helper.assertTrue(be.isFormed(), "still formed");
        helper.assertTrue(be.resetScram(), "reset once cool");
        helper.assertFalse(be.isScrammed(), "running again");
        helper.succeed();
    }

    private static int gunpowder(ReactorControllerBlockEntity be) {
        int n = 0;
        for (int i = 0; i < be.waste.getSlots(); i++) n += be.waste.getStackInSlot(i).is(Items.GUNPOWDER) ? be.waste.getStackInSlot(i).getCount() : 0;
        return n;
    }

    /** With room for only part of a tick's output the reactor burns only that share of fuel; full, it burns none. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void reactorBurnsOnlyWhatFits(GameTestHelper helper) {
        ReactorControllerBlockEntity be = reactor(helper, Blocks.PACKED_ICE.defaultBlockState());
        be.fuel.setStackInSlot(0, new ItemStack(Items.BARRIER, 8));
        be.simulate(20);
        double perTick = Math.pow(3, 0.75);
        int max = be.energy.getMaxEnergyStored();
        be.energy.setEnergy(max - 100);
        double before = be.burnLeft();
        be.simulate(1);
        double used = before - be.burnLeft();
        helper.assertTrue(be.energy.getEnergyStored() >= max - 2, "fills the buffer to the brim, has " + (max - be.energy.getEnergyStored()) + " room");
        helper.assertTrue(used > 0 && used < perTick * 0.15, "burns only the share that fits: " + used + " of " + perTick);
        helper.assertTrue(be.state() == ReactorControllerBlockEntity.State.RUNNING, "still running");
        be.energy.setEnergy(max);
        before = be.burnLeft();
        be.simulate(1);
        helper.assertTrue(be.state() == ReactorControllerBlockEntity.State.BUFFER_FULL && be.burnLeft() == before, "a full buffer burns nothing");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 1)).getValue(ControllerBlock.LIT), "the glow does not flicker off at the brim");
        helper.succeed();
    }

    /**
     * A walk ends at the frame edge: a neighbour one air gap away and casing touching an edge do not break the
     * structure. A controller turned sideways still forms (and turns to face out). Reactors start at 5x5x5.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void structureEndsAtItsFrame(GameTestHelper helper) {
        ReactorControllerBlockEntity be = reactor(helper, Blocks.PACKED_ICE.defaultBlockState());
        BlockPos min = new BlockPos(1, 1, 1);
        helper.setBlock(min.offset(5, 2, 0), EnergyRegistry.REACTOR_CASING.get());
        helper.setBlock(min.offset(7, 2, 0), EnergyRegistry.REACTOR_CASING.get());
        helper.setBlock(min.offset(2, 5, 0), EnergyRegistry.REACTOR_CASING.get());
        be.scanNow();
        helper.assertTrue(be.isFormed() && be.box().getXSpan() == 5 && be.box().getYSpan() == 5,
                "casing next to the frame does not stretch the reactor: " + (be.problem() == null ? "box " + be.box() : be.problem().message().getString()));

        BlockPos c = min.offset(2, 2, 0);
        helper.setBlock(c, helper.getBlockState(c).setValue(ControllerBlock.FACING, Direction.EAST));
        be = be(helper, c);
        be.scanNow();
        helper.assertTrue(be.isFormed(), "a sideways controller still forms");
        helper.assertTrue(helper.getBlockState(c).getValue(ControllerBlock.FACING) == Direction.NORTH, "and turns to face out");
        helper.assertTrue(ReactorControllerBlockEntity.SPEC.minWidth().getAsInt() == 5, "reactors start at 5x5x5");
        helper.succeed();
    }

    /** A Tesla Coil sitting on a Power Port pulls the reactor's FE and sends it to a Charger. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void teslaCoilPullsFromPowerPort(GameTestHelper helper) {
        ReactorControllerBlockEntity be = reactor(helper, Blocks.PACKED_ICE.defaultBlockState());
        helper.setBlock(new BlockPos(3, 5, 3), EnergyRegistry.REACTOR_POWER_PORT.get());
        be.scanNow();
        be.energy.setEnergy(1_000_000);
        helper.setBlock(new BlockPos(3, 6, 3), PowerRegistry.TESLA_COIL_1.get().defaultBlockState().setValue(TeslaCoilBlock.FACING, Direction.UP));
        TeslaCoilBlockEntity coil = be(helper, new BlockPos(3, 6, 3));
        BlockPos chargerPos = new BlockPos(7, 1, 7);
        helper.setBlock(chargerPos, PowerRegistry.CHARGER.get());
        ChargerBlockEntity charger = be(helper, chargerPos);
        helper.assertTrue(TeslaNetwork.toggle(coil, helper.absolutePos(chargerPos), Direction.UP) == TeslaNetwork.LinkResult.LINKED, "link coil to charger");
        helper.succeedWhen(() -> {
            helper.assertTrue(coil.isRoot(), "a coil on a Power Port is a source");
            helper.assertTrue(charger.energy.getEnergyStored() > 2_000, "charger fills from the reactor, has " + charger.energy.getEnergyStored());
        });
    }

    // ---------------------------------------------------------------- capacitor bank

    /** 3x4x3 bank: one Copper Capacitor and one Basic Transfer Coil; I/O is clamped to the coil rate per tick. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void bankCapacityAndIoClamp(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos c = cuboid(helper, min, 3, 4, 3, EnergyRegistry.BANK_CASING.get(), EnergyRegistry.BANK_CONTROLLER.get());
        helper.setBlock(min.offset(1, 1, 1), EnergyRegistry.CAPACITOR_COPPER.get());
        helper.setBlock(min.offset(1, 2, 1), EnergyRegistry.TRANSFER_COIL_BASIC.get());
        BlockPos inPos = min.offset(1, 1, 2), outPos = min.offset(1, 2, 2);
        helper.setBlock(inPos, EnergyRegistry.BANK_PORT.get());
        helper.setBlock(outPos, EnergyRegistry.BANK_PORT.get().defaultBlockState().setValue(PortBlock.OUTPUT, true));
        BankControllerBlockEntity bank = be(helper, c);
        bank.scanNow();
        helper.assertTrue(bank.isFormed(), "bank forms: " + (bank.problem() == null ? "" : bank.problem().message().getString()));
        long cap = EnergyConfig.capacitor(0);
        int rate = EnergyConfig.transferCoil(0);
        helper.assertTrue(cap == 4_000_000L && rate == 16_000, "defaults: 4M FE, 16k FE/t");
        helper.assertTrue(bank.capacity() == cap && bank.rate() == rate, "one capacitor, one coil; got " + bank.capacity() + " / " + bank.rate());

        IEnergyStorage in = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(inPos), Direction.SOUTH);
        IEnergyStorage out = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(outPos), Direction.SOUTH);
        helper.assertTrue(in != null && in.canReceive() && !in.canExtract(), "input port");
        helper.assertTrue(out != null && out.canExtract() && !out.canReceive(), "output port");
        helper.assertTrue(in.receiveEnergy(Integer.MAX_VALUE, true) == rate, "input clamps to the coil rate");
        helper.assertTrue(in.receiveEnergy(10_000, false) == 10_000, "takes 10k");
        helper.assertTrue(in.receiveEnergy(10_000, false) == rate - 10_000, "only the rest of the rate this tick (rate shared by all ports)");
        helper.assertTrue(out.extractEnergy(Integer.MAX_VALUE, false) == rate, "output clamps to the coil rate");
        helper.assertTrue(out.extractEnergy(1, false) == 0, "output budget spent for this tick");
        helper.assertTrue(bank.energy() == 0, "16k in, 16k out");
        var tag = new net.minecraft.nbt.CompoundTag();
        bank.writeSync(tag, helper.getLevel().registryAccess());
        helper.assertTrue(tag.getLongArray("history").length == BankControllerBlockEntity.HISTORY && tag.getLong("capacity") == cap, "GUI sync of the bank");

        // A port saves its link: reloaded next to a running controller it works right away.
        PortBlockEntity inPort = be(helper, inPos);
        var saved = inPort.saveWithoutMetadata(helper.getLevel().registryAccess());
        PortBlockEntity reloaded = new PortBlockEntity(helper.absolutePos(inPos), helper.getBlockState(inPos));
        reloaded.setLevel(helper.getLevel());
        reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(reloaded.controller() == bank, "the reloaded port knows its controller");
        helper.succeed();
    }

    /** 5x5x5 bank of Ender Capacitors: far beyond an int; the ports clamp what they report. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void bankStoresLongBeyondInt(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos c = cuboid(helper, min, 5, 5, 5, EnergyRegistry.BANK_CASING.get(), EnergyRegistry.BANK_CONTROLLER.get());
        for (int x = 1; x <= 3; x++) for (int y = 1; y <= 3; y++) for (int z = 1; z <= 3; z++) {
            helper.setBlock(min.offset(x, y, z), (x == 2 && y == 2 && z == 2 ? EnergyRegistry.TRANSFER_COIL_ELITE : EnergyRegistry.CAPACITOR_ENDER).get());
        }
        BlockPos portPos = min.offset(2, 2, 4);
        helper.setBlock(portPos, EnergyRegistry.BANK_PORT.get());
        BankControllerBlockEntity bank = be(helper, c);
        bank.scanNow();
        helper.assertTrue(bank.isFormed(), "bank forms");
        long capacity = 26L * 512_000_000L;
        helper.assertTrue(bank.capacity() == capacity, "26 Ender Capacitors = 13.3G FE, got " + bank.capacity());
        long big = 10_000_000_000L;
        bank.setEnergy(big);
        IEnergyStorage port = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(portPos), Direction.SOUTH);
        helper.assertTrue(port.getMaxEnergyStored() == Integer.MAX_VALUE, "ports clamp the capacity to int");
        long expected = (long) ((double) big * Integer.MAX_VALUE / capacity);
        helper.assertTrue(Math.abs(port.getEnergyStored() - expected) <= 1, "stored scales with the capacity: " + port.getEnergyStored() + " vs " + expected);
        bank.setEnergy(capacity - 1);
        helper.assertTrue(port.getEnergyStored() < port.getMaxEnergyStored(), "a bank that is not full never reports itself full");
        bank.setEnergy(big);
        helper.assertTrue(port.receiveEnergy(4_000_000, false) == 4_000_000, "elite coil: 4M FE/t in");
        helper.assertTrue(bank.energy() == big + 4_000_000L, "long arithmetic, got " + bank.energy());
        // The energy travels with the controller item.
        var components = bank.collectComponents();
        helper.assertTrue(components.getOrDefault(EnergyRegistry.BANK_ENERGY.get(), 0L) == big + 4_000_000L, "controller item keeps the long");

        // Rebuilt smaller: the energy above the new capacity is lost.
        for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) helper.setBlock(min.offset(x, 1, z), Blocks.AIR);
        bank.scanNow();
        helper.assertTrue(bank.isFormed() && bank.capacity() == 17L * 512_000_000L, "17 capacitors left, got " + bank.capacity());
        helper.assertTrue(bank.energy() == bank.capacity(), "energy capped to the smaller bank, got " + bank.energy());
        helper.succeed();
    }

    // ---------------------------------------------------------------- data maps

    /** The shipped coolant data map loads with the contract's format. */
    @GameTest(template = ARENA)
    public static void coolantDataMapLoaded(GameTestHelper helper) {
        helper.assertTrue(EnergyDataMaps.cooling(Blocks.WATER.defaultBlockState()) == 1.0F, "water 1");
        helper.assertTrue(EnergyDataMaps.cooling(Blocks.ICE.defaultBlockState()) == 1.5F, "ice 1.5");
        helper.assertTrue(EnergyDataMaps.cooling(Blocks.PACKED_ICE.defaultBlockState()) == 2.0F, "packed ice 2");
        helper.assertTrue(EnergyDataMaps.cooling(Blocks.BLUE_ICE.defaultBlockState()) == 3.0F, "blue ice 3");
        helper.assertTrue(EnergyDataMaps.cooling(EnergyRegistry.CRYO_COOLANT.get().defaultBlockState()) == 4.0F, "cryo coolant 4");
        helper.assertTrue(EnergyDataMaps.cooling(Blocks.STONE.defaultBlockState()) == 0.0F && !EnergyDataMaps.isCoolant(Blocks.STONE.defaultBlockState()), "stone is no coolant");
        helper.assertTrue(EnergyDataMaps.reactorFuel(new ItemStack(Items.DIRT)) == null, "dirt is no fuel");
        helper.succeed();
    }

    // ---------------------------------------------------------------- fusion

    /** 7x3x7 fusion reactor: charge 100M FE through a Power Port, ignite with fuel, ramp up to 200k FE/t. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void fusionIgnitesAndBurns(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos c = cuboid(helper, min, 7, 3, 7, EnergyRegistry.FUSION_CASING.get(), EnergyRegistry.FUSION_CONTROLLER.get());
        for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) {
            boolean ring = x == 1 || x == 5 || z == 1 || z == 5;
            if (ring) helper.setBlock(min.offset(x, 1, z), EnergyRegistry.FUSION_COIL.get());
        }
        BlockPos portPos = min.offset(3, 1, 6);
        helper.setBlock(portPos, EnergyRegistry.REACTOR_POWER_PORT.get());
        BlockPos accessPos = min.offset(2, 1, 6);
        helper.setBlock(accessPos, EnergyRegistry.REACTOR_ACCESS_PORT.get());
        helper.setBlock(min.offset(3, 2, 3), EnergyRegistry.REACTOR_GLASS.get());
        FusionControllerBlockEntity fusion = be(helper, c);
        fusion.scanNow();
        helper.assertTrue(fusion.isFormed(), "fusion forms: " + (fusion.problem() == null ? "" : fusion.problem().message().getString()));

        // A coil missing from the ring is named.
        BlockPos coil = min.offset(5, 1, 3);
        helper.setBlock(coil, Blocks.AIR);
        fusion.scanNow();
        helper.assertTrue(!fusion.isFormed() && helper.absolutePos(coil).equals(fusion.problem().pos()), "missing fusion coil named");
        helper.setBlock(coil, EnergyRegistry.FUSION_COIL.get());
        fusion.scanNow();
        helper.assertTrue(fusion.isFormed(), "formed again");

        IEnergyStorage port = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(portPos), Direction.SOUTH);
        helper.assertTrue(port != null && port.canReceive(), "the power port takes the ignition charge");
        fusion.step();
        helper.assertTrue(fusion.state() == FusionControllerBlockEntity.State.CHARGING, "charging");
        helper.assertTrue(port.receiveEnergy(Integer.MAX_VALUE, false) == 1_000_000, "charging is limited to 1M FE/t per tick");
        helper.assertTrue(port.receiveEnergy(Integer.MAX_VALUE, false) == 0, "shared by all ports within a tick");
        fusion.setCharge(EnergyConfig.fusionIgnitionEnergy());
        fusion.step();
        helper.assertFalse(fusion.ignited(), "no fuel, no ignition");
        helper.assertTrue(fusion.state() == FusionControllerBlockEntity.State.NO_FUEL, "waits for fuel");
        fusion.fuel.setStackInSlot(0, new ItemStack(Items.STRUCTURE_VOID, 2));
        fusion.step();
        helper.assertTrue(fusion.ignited() && fusion.charge() == 0, "ignites and spends the charge");
        for (int i = 0; i < 250; i++) fusion.step();
        helper.assertTrue(fusion.fePerTick() == 200_000, "full output after warm-up, got " + fusion.fePerTick());
        helper.assertFalse(port.canReceive(), "no charging while burning");
        var tag = new net.minecraft.nbt.CompoundTag();
        fusion.writeSync(tag, helper.getLevel().registryAccess());
        helper.assertTrue(tag.getBoolean("ignited") && tag.getInt("fe") == 200_000, "GUI sync of the fusion reactor");
        fusion.setEnabled(false);
        fusion.step();
        helper.assertFalse(fusion.ignited(), "switching off collapses the plasma");

        // Access Port: fuel goes in, automation can not pull it back out.
        IItemHandler access = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(accessPos), Direction.SOUTH);
        helper.assertTrue(access != null && access.insertItem(0, new ItemStack(Items.STRUCTURE_VOID), false).isEmpty(), "fuel goes in");
        helper.assertTrue(!fusion.fuel.getStackInSlot(0).isEmpty() && access.extractItem(0, 64, false).isEmpty(), "fuel can not be pulled out");
        helper.succeed();
    }

}
