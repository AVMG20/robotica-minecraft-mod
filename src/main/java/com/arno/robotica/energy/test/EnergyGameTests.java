package com.arno.robotica.energy.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.energy.EnergyConfig;
import com.arno.robotica.energy.EnergyDataMaps;
import com.arno.robotica.energy.EnergyRegistry;
import com.arno.robotica.energy.block.BankControllerBlockEntity;
import com.arno.robotica.energy.block.ColliderBlockEntity;
import com.arno.robotica.energy.block.ControllerBlock;
import com.arno.robotica.energy.block.CoreReactorBlockEntity;
import com.arno.robotica.energy.block.FramedPartBlock;
import com.arno.robotica.energy.block.PortBlock;
import com.arno.robotica.energy.block.PortBlockEntity;
import com.arno.robotica.energy.block.SpireBlockEntity;
import com.arno.robotica.energy.block.StructureControllerBlockEntity;
import com.arno.robotica.energy.block.StructureGlassBlock;
import com.arno.robotica.energy.net.ControllerActionPayload;
import com.arno.robotica.power.PowerRegistry;
import com.arno.robotica.power.block.ChargerBlockEntity;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

/**
 * Headless tests of the energy module ({@code scripts/docker-build.sh runGameTestServer}). They never use the industry
 * module's pellets: stand-ins are registered through the test hooks of {@link EnergyDataMaps} (a jigsaw as spire fuel,
 * a barrier as reactor fuel, a structure block as a reactor core, a structure void as collider fuel). Conductors and
 * modulators are vanilla blocks from the shipped data maps. Template "energy_arena" is an empty 9x9x9 box; the Tesla
 * Spire sticks out of its top.
 */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class EnergyGameTests {
    private static final String ARENA = "energy_arena";
    /** Spire test fuel: 100k FE per item before efficiency, one gunpowder of waste. */
    private static final EnergyDataMaps.SpireFuel SPIRE_FUEL = new EnergyDataMaps.SpireFuel(100_000, Optional.of(Items.GUNPOWDER));
    /** Reactor test fuel: 400 FE/t for 100 ticks, one gunpowder of waste. Test core: x2 power for 1,000 ticks. */
    private static final EnergyDataMaps.ReactorFuel REACTOR_FUEL = new EnergyDataMaps.ReactorFuel(400, 100, Optional.of(Items.GUNPOWDER));
    private static final EnergyDataMaps.ReactorCore CORE = new EnergyDataMaps.ReactorCore(2.0F, 1_000);

    static {
        EnergyDataMaps.registerTestSpireFuel(Items.JIGSAW, SPIRE_FUEL);
        EnergyDataMaps.registerTestFuel(Items.BARRIER, REACTOR_FUEL);
        EnergyDataMaps.registerTestCore(Items.STRUCTURE_BLOCK, CORE);
        EnergyDataMaps.registerTestColliderFuel(Items.STRUCTURE_VOID, new EnergyDataMaps.ColliderFuel(6_000));
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
    private static String key(StructureControllerBlockEntity be) {
        if (be.problem() == null) return null;
        return be.problem().message().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : null;
    }

    private static String why(StructureControllerBlockEntity be) {
        return be.problem() == null ? "-" : be.problem().message().getString();
    }

    @SuppressWarnings("unchecked")
    private static <T> T be(GameTestHelper helper, BlockPos rel) {
        return (T) helper.getBlockEntity(rel);
    }

    /**
     * 5x5x5 Core Reactor at {@code min}: two Blocks of Redstone and one Blue Ice inside (power x1.03, burn x1.05), a
     * Power Port in the south wall, an Access Port next to it, a window in the east wall.
     */
    private static CoreReactorBlockEntity reactorAt(GameTestHelper helper, BlockPos min) {
        BlockPos c = cuboid(helper, min, 5, 5, 5, EnergyRegistry.REACTOR_CASING.get(), EnergyRegistry.REACTOR_CONTROLLER.get());
        helper.setBlock(min.offset(1, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(min.offset(3, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(min.offset(1, 3, 3), Blocks.BLUE_ICE);
        helper.setBlock(min.offset(2, 2, 4), EnergyRegistry.REACTOR_POWER_PORT.get());
        helper.setBlock(min.offset(1, 2, 4), EnergyRegistry.REACTOR_ACCESS_PORT.get());
        helper.setBlock(min.offset(4, 2, 2), EnergyRegistry.REACTOR_GLASS.get());
        CoreReactorBlockEntity be = be(helper, c);
        be.scanNow();
        return be;
    }

    private static CoreReactorBlockEntity reactor(GameTestHelper helper) {
        return reactorAt(helper, new BlockPos(1, 1, 1));
    }

    private static int gunpowder(IItemHandler handler) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) n += handler.getStackInSlot(i).is(Items.GUNPOWDER) ? handler.getStackInSlot(i).getCount() : 0;
        return n;
    }

    // ---------------------------------------------------------------- structure detection

    /** A valid reactor forms; a stranger in the wall, a gap in the frame and a second controller are named with their position. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void structureNamesTheWrongBlock(GameTestHelper helper) {
        CoreReactorBlockEntity be = reactor(helper);
        helper.assertTrue(be.isFormed(), "5x5x5 reactor should form, problem: " + why(be));
        helper.assertTrue(be.box() != null && be.box().getXSpan() == 5 && be.box().getYSpan() == 5 && be.box().getZSpan() == 5, "box should be 5x5x5");
        helper.assertTrue(be.modulators().size() == 3, "three modulators, got " + be.modulators().size());

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

    /** Too shallow (front and back wall touch), a block in the core chamber, a stranger inside, and a port without a controller. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void structureTooSmallAndMissingController(GameTestHelper helper) {
        // 3 wide, 3 tall, but only 2 deep.
        BlockPos c = cuboid(helper, new BlockPos(1, 1, 1), 3, 3, 2, EnergyRegistry.BANK_CASING.get(), EnergyRegistry.BANK_CONTROLLER.get());
        BankControllerBlockEntity bank = be(helper, c);
        bank.scanNow();
        helper.assertFalse(bank.isFormed(), "a 3x3x2 bank is too shallow");
        helper.assertTrue("multiblock.robotica.too_shallow".equals(key(bank)), "problem should say it is not deep enough, got " + key(bank));

        // The middle of a Core Reactor must stay empty; inside, only modulators.
        BlockPos min = new BlockPos(1, 1, 4);
        CoreReactorBlockEntity reactor = reactorAt(helper, min);
        helper.assertTrue(reactor.isFormed(), "test reactor forms: " + why(reactor));
        BlockPos middle = min.offset(2, 2, 2);
        helper.setBlock(middle, Blocks.REDSTONE_BLOCK);
        reactor.scanNow();
        helper.assertTrue(!reactor.isFormed() && helper.absolutePos(middle).equals(reactor.problem().pos())
                && "multiblock.robotica.core_chamber".equals(key(reactor)), "a block in the core chamber must be named, got " + key(reactor));
        helper.setBlock(middle, Blocks.AIR);
        BlockPos stranger = min.offset(3, 3, 3);
        helper.setBlock(stranger, Blocks.DIRT);
        reactor.scanNow();
        helper.assertTrue(!reactor.isFormed() && "multiblock.robotica.core_inside".equals(key(reactor)), "dirt inside must be named, got " + key(reactor));
        helper.setBlock(stranger, Blocks.AIR);
        helper.setBlock(min.offset(2, 2, 4), EnergyRegistry.REACTOR_CASING.get());
        reactor.scanNow();
        helper.assertTrue(!reactor.isFormed() && "multiblock.robotica.reactor_no_power_port".equals(key(reactor)), "needs a Power Port, got " + key(reactor));

        // Missing controller: a bank shell without one never links its port.
        BlockPos bmin = new BlockPos(5, 1, 1);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) {
            helper.setBlock(bmin.offset(x, y, z), (x == 1 && y == 1 && z == 1) ? EnergyRegistry.CAPACITOR_COPPER.get().defaultBlockState()
                    : EnergyRegistry.BANK_CASING.get().defaultBlockState());
        }
        helper.setBlock(bmin.offset(1, 1, 0), EnergyRegistry.BANK_PORT.get());
        PortBlockEntity port = be(helper, bmin.offset(1, 1, 0));
        helper.assertTrue(port.controller() == null, "no controller: the port is not linked");
        IEnergyStorage cap = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(bmin.offset(1, 1, 0)), Direction.NORTH);
        helper.assertTrue(cap != null && !cap.canReceive() && cap.receiveEnergy(1000, false) == 0, "an unlinked port takes nothing");
        helper.succeed();
    }

    /**
     * A walk ends at the frame edge: a neighbour one air gap away and casing touching an edge do not break the
     * structure. A controller turned sideways still forms (and turns to face out).
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void structureEndsAtItsFrame(GameTestHelper helper) {
        CoreReactorBlockEntity be = reactor(helper);
        BlockPos min = new BlockPos(1, 1, 1);
        helper.setBlock(min.offset(5, 2, 0), EnergyRegistry.REACTOR_CASING.get());
        helper.setBlock(min.offset(7, 2, 0), EnergyRegistry.REACTOR_CASING.get());
        helper.setBlock(min.offset(2, 5, 0), EnergyRegistry.REACTOR_CASING.get());
        be.scanNow();
        helper.assertTrue(be.isFormed() && be.box().getXSpan() == 5 && be.box().getYSpan() == 5,
                "casing next to the frame does not stretch the reactor: " + (be.problem() == null ? "box " + be.box() : why(be)));

        BlockPos c = min.offset(2, 2, 0);
        helper.setBlock(c, helper.getBlockState(c).setValue(ControllerBlock.FACING, Direction.EAST));
        be = be(helper, c);
        be.scanNow();
        helper.assertTrue(be.isFormed(), "a sideways controller still forms");
        helper.assertTrue(helper.getBlockState(c).getValue(ControllerBlock.FACING) == Direction.NORTH, "and turns to face out");
        helper.succeed();
    }

    // ---------------------------------------------------------------- core reactor

    /**
     * Pellet 400 FE/t x core 2 x modulators 1.03 = 824 FE/t; pellet and core burn 1.05 ticks per tick. Waste comes out,
     * the Access Port takes fuel and cores, the core burns out and the reactor waits for the next one.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void coreReactorBurnsFuelAndCores(GameTestHelper helper) {
        CoreReactorBlockEntity be = reactor(helper);
        helper.assertTrue(be.isFormed(), "reactor forms: " + why(be));
        helper.assertTrue(Math.abs(be.powerMultiplier() - 1.03) < 1e-6 && Math.abs(be.burnMultiplier() - 1.05) < 1e-6,
                "2 redstone blocks and a blue ice: x1.03 power, x1.05 burn, got " + be.powerMultiplier() + " / " + be.burnMultiplier());
        be.fuel.setStackInSlot(0, new ItemStack(Items.BARRIER, 32));
        be.simulate(1);
        helper.assertTrue(be.state() == CoreReactorBlockEntity.State.NO_CORE && be.fePerTick() == 0, "no core, no power: " + be.state());
        be.cores.setStackInSlot(0, new ItemStack(Items.STRUCTURE_BLOCK));
        be.simulate(100);
        helper.assertTrue(be.state() == CoreReactorBlockEntity.State.RUNNING, "should run, is " + be.state());
        helper.assertTrue(be.fePerTick() == 824, "824 FE/t, got " + be.fePerTick());
        helper.assertTrue(Math.abs(be.coreWear() - 105) < 1e-6, "core wears 1.05 per tick, got " + be.coreWear());
        helper.assertTrue(gunpowder(be.waste) == 1, "105 ticks of burn = 1 pellet done, waste " + gunpowder(be.waste));
        var tag = new net.minecraft.nbt.CompoundTag();
        be.writeSync(tag, helper.getLevel().registryAccess());
        helper.assertTrue(tag.getInt("fe") == 824 && tag.getInt("powerPct") == 103 && Math.abs(tag.getFloat("integrity") - 0.895F) < 0.001F,
                "GUI sync of the reactor");

        // Access Port: fuel and cores go in, waste comes out, fuel can not be pulled.
        IItemHandler access = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(new BlockPos(2, 3, 5)), Direction.SOUTH);
        helper.assertTrue(access != null && access.getSlots() == 7, "access port shows 3 fuel + 3 waste + 1 core slots");
        helper.assertTrue(access.insertItem(1, new ItemStack(Items.BARRIER), false).isEmpty(), "fuel goes in");
        helper.assertTrue(!access.insertItem(1, new ItemStack(Items.DIRT), true).isEmpty(), "non-fuel is refused");
        helper.assertTrue(access.extractItem(0, 1, true).isEmpty(), "fuel can not be pulled out");
        helper.assertTrue(access.extractItem(3, 64, false).is(Items.GUNPOWDER), "waste can be pulled out");
        helper.assertTrue(access.insertItem(6, new ItemStack(Items.DIRT), true).getCount() == 1, "only cores in the core slot");

        // Power Port gives the buffer out through the standard FE capability.
        IEnergyStorage port = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(new BlockPos(3, 3, 5)), Direction.SOUTH);
        int stored = be.energy.getEnergyStored();
        helper.assertTrue(port != null && port.canExtract() && !port.canReceive(), "power port: out only");
        helper.assertTrue(stored > 0 && port.extractEnergy(1_000, false) == Math.min(1_000, stored), "power port gives FE");

        // 1,000 ticks of life at 1.05 per tick: gone after 953 ticks, then it waits for a core.
        be.simulate(860);
        helper.assertTrue(be.state() == CoreReactorBlockEntity.State.NO_CORE && be.activeCore().isEmpty(), "the core burned out: " + be.state());
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 1)).getValue(ControllerBlock.FORMED), "nothing breaks");
        helper.succeed();
    }

    /**
     * A formed reactor wears the formed look: corner caps, edge beams along their axis, wall panels, frameless glass. It
     * comes off when the structure breaks and when the controller is broken.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void formedLookComesAndGoes(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        CoreReactorBlockEntity be = reactorAt(helper, min);
        helper.assertTrue(be.isFormed(), "forms: " + why(be));
        assertFrame(helper, min, FramedPartBlock.Shape.CORNER);
        assertFrame(helper, min.offset(2, 0, 0), FramedPartBlock.Shape.X);
        assertFrame(helper, min.offset(0, 2, 0), FramedPartBlock.Shape.Y);
        assertFrame(helper, min.offset(0, 0, 2), FramedPartBlock.Shape.Z);
        assertFrame(helper, min.offset(2, 4, 2), FramedPartBlock.Shape.WALL);
        helper.assertTrue(helper.getBlockState(min.offset(4, 2, 2)).getValue(StructureGlassBlock.FORMED), "the window drops its frame");

        helper.setBlock(min.offset(2, 4, 2), Blocks.DIRT);
        be.scanNow();
        helper.assertFalse(be.isFormed(), "dirt in the roof breaks it");
        assertFrame(helper, min, FramedPartBlock.Shape.NONE);
        assertFrame(helper, min.offset(0, 2, 0), FramedPartBlock.Shape.NONE);
        helper.assertFalse(helper.getBlockState(min.offset(4, 2, 2)).getValue(StructureGlassBlock.FORMED), "the window gets its frame back");

        helper.setBlock(min.offset(2, 4, 2), EnergyRegistry.REACTOR_CASING.get());
        be.scanNow();
        assertFrame(helper, min, FramedPartBlock.Shape.CORNER);
        helper.destroyBlock(min.offset(2, 2, 0));
        assertFrame(helper, min, FramedPartBlock.Shape.NONE);
        assertFrame(helper, min.offset(2, 4, 2), FramedPartBlock.Shape.NONE);
        helper.succeed();
    }

    private static void assertFrame(GameTestHelper helper, BlockPos pos, FramedPartBlock.Shape shape) {
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.hasProperty(FramedPartBlock.FRAME) && state.getValue(FramedPartBlock.FRAME) == shape,
                "casing at " + pos + " should be " + shape + ", got " + state);
    }

    /** A loaded core is used up: breaking the controller gives it back only if it never burned. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void coreReactorUsesUpCores(GameTestHelper helper) {
        CoreReactorBlockEntity be = reactor(helper);
        be.cores.setStackInSlot(0, new ItemStack(Items.STRUCTURE_BLOCK));
        be.simulate(5);
        helper.assertTrue(be.cores.getStackInSlot(0).isEmpty() && be.activeCore().is(Items.STRUCTURE_BLOCK) && be.coreWear() == 0,
                "the core loads out of the slot and waits without fuel");
        helper.destroyBlock(new BlockPos(3, 3, 1));
        helper.assertTrue(countDropped(helper, Items.STRUCTURE_BLOCK) == 1, "an unburned core comes back");
        clearDrops(helper);

        CoreReactorBlockEntity again = reactor(helper);
        again.fuel.setStackInSlot(0, new ItemStack(Items.BARRIER, 4));
        again.cores.setStackInSlot(0, new ItemStack(Items.STRUCTURE_BLOCK));
        again.simulate(40);
        helper.assertTrue(again.coreWear() > 0, "the core burns with the fuel");
        helper.destroyBlock(new BlockPos(3, 3, 1));
        helper.assertTrue(countDropped(helper, Items.STRUCTURE_BLOCK) == 0, "a burning core is used up");
        helper.succeed();
    }

    private static int countDropped(GameTestHelper helper, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12))) {
            if (e.getItem().is(item)) n += e.getItem().getCount();
        }
        return n;
    }

    private static void clearDrops(GameTestHelper helper) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12)).forEach(e -> e.discard());
    }

    /** With room for only part of a tick's output the reactor burns only that share; full, it burns nothing. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void coreReactorBurnsOnlyWhatFits(GameTestHelper helper) {
        CoreReactorBlockEntity be = reactor(helper);
        be.fuel.setStackInSlot(0, new ItemStack(Items.BARRIER, 8));
        be.cores.setStackInSlot(0, new ItemStack(Items.STRUCTURE_BLOCK));
        be.simulate(20);
        int max = be.energy.getMaxEnergyStored();
        be.energy.setEnergy(max - 100);
        double before = be.burnLeft(), wear = be.coreWear();
        be.simulate(1);
        double used = before - be.burnLeft();
        helper.assertTrue(be.energy.getEnergyStored() >= max - 2, "fills the buffer to the brim, has " + (max - be.energy.getEnergyStored()) + " room");
        helper.assertTrue(used > 0 && used < 1.05 * 0.15, "burns only the share that fits: " + used);
        helper.assertTrue(Math.abs((be.coreWear() - wear) - used) < 1e-9, "the core wears as much as the pellet");
        helper.assertTrue(be.state() == CoreReactorBlockEntity.State.RUNNING, "still running");
        be.energy.setEnergy(max);
        before = be.burnLeft();
        be.simulate(1);
        helper.assertTrue(be.state() == CoreReactorBlockEntity.State.BUFFER_FULL && be.burnLeft() == before, "a full buffer burns nothing");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 1)).getValue(ControllerBlock.LIT), "the glow does not flicker off at the brim");
        helper.succeed();
    }

    /** A Tesla Coil sitting on a Power Port pulls the reactor's FE and sends it to a Charger. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void teslaCoilPullsFromPowerPort(GameTestHelper helper) {
        CoreReactorBlockEntity be = reactor(helper);
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

    // ---------------------------------------------------------------- tesla spire

    /** Spire Base at {@code base}, {@code copper} Blocks of Copper then {@code iron} Blocks of Iron above it, the crown on top. */
    private static SpireBlockEntity spire(GameTestHelper helper, BlockPos base, int copper, int iron) {
        helper.setBlock(base, EnergyRegistry.SPIRE_BASE.get().defaultBlockState().setValue(ControllerBlock.FACING, Direction.NORTH));
        for (int i = 1; i <= copper + iron; i++) helper.setBlock(base.above(i), i <= copper ? Blocks.COPPER_BLOCK : Blocks.IRON_BLOCK);
        helper.setBlock(base.above(copper + iron + 1), EnergyRegistry.SPIRE_CROWN.get());
        SpireBlockEntity be = be(helper, base);
        be.scanNow();
        be.setSkyForTest(true);
        return be;
    }

    /**
     * 4 copper (30, x1.0) + 4 iron (45, x1.25): 300 FE/t base at x1.15 efficiency. Fuel pays the output divided by the
     * efficiency, waste comes out, a strike adds a burst, no sky stops it, a neighbour 5.7 blocks away costs 45%.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void spireBurnsFuelAndCatchesLightning(GameTestHelper helper) {
        BlockPos basePos = new BlockPos(4, 1, 4);
        SpireBlockEntity be = spire(helper, basePos, 4, 4);
        helper.assertTrue(be.isFormed(), "spire forms: " + why(be));
        helper.assertTrue(be.conductors() == 8 && be.basePower() == 300 && Math.abs(be.efficiency() - 1.15) < 1e-6,
                "8 conductors, 300 FE/t, x1.15: " + be.conductors() + " / " + be.basePower() + " / " + be.efficiency());
        be.simulate(1);
        helper.assertTrue(be.state() == SpireBlockEntity.State.NO_FUEL && be.fePerTick() == 0, "no fuel: " + be.state());

        be.fuel.setStackInSlot(0, new ItemStack(Items.JIGSAW, 2));
        be.simulate(1);
        double expected = 300 * be.altitude() * be.weatherFactor() * be.interference() * com.arno.robotica.core.CoreConfig.generation();
        helper.assertTrue(be.state() == SpireBlockEntity.State.RUNNING && Math.abs(be.fePerTick() - expected) <= 1, "runs at " + expected + ", got " + be.fePerTick());
        helper.assertTrue(Math.abs((100_000 - be.fuelLeft()) - be.fePerTick() / 1.15) < 0.5, "fuel pays output / efficiency, left " + be.fuelLeft());
        int ticks = (int) Math.ceil(100_000 * 1.15 / expected) + 2;
        be.simulate(ticks);
        helper.assertTrue(gunpowder(be.waste) == 1, "one item burned leaves its waste, got " + gunpowder(be.waste));
        helper.assertTrue(Math.abs(be.energy.getEnergyStored() - 115_000 - expected) < expected * 3, "one item gives 100k x 1.15 FE, stored " + be.energy.getEnergyStored());

        int before = be.energy.getEnergyStored();
        be.strike(helper.getLevel(), helper.getLevel().getGameTime());
        long burst = Math.max(SpireBlockEntity.STRIKE_MIN, Math.round(expected * 20 * EnergyConfig.spireStrikeSeconds()));
        helper.assertTrue(be.energy.getEnergyStored() - before == burst, "a strike adds " + burst + " FE, got " + (be.energy.getEnergyStored() - before));
        helper.assertTrue(be.energyView(Direction.UP) == null && be.energyView(Direction.NORTH) != null, "FE out of every side but the column");
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(basePos), Direction.NORTH);
        helper.assertTrue(items != null && items.insertItem(0, new ItemStack(Items.JIGSAW), false).isEmpty() && !items.insertItem(0, new ItemStack(Items.DIRT), true).isEmpty(),
                "hoppers put fuel in, nothing else");
        helper.assertTrue(items.extractItem(2, 64, false).is(Items.GUNPOWDER), "and pull the waste out");

        be.setSkyForTest(false);
        be.simulate(1);
        helper.assertTrue(be.state() == SpireBlockEntity.State.NO_SKY && be.fePerTick() == 0, "no sky, no power: " + be.state());
        be.setSkyForTest(true);

        // A second spire 4 blocks west and 4 north: 5.66 blocks apart, each keeps 0.5 + 0.5 x 5.66 / 64 = 54.4%.
        // (Relative to before: spires of other tests may stand nearby.)
        double alone = be.interference();
        SpireBlockEntity other = spire(helper, new BlockPos(0, 1, 0), 8, 0);
        helper.assertTrue(other.isFormed(), "second spire forms: " + why(other));
        be.scanNow();
        double share = 0.5 + 0.5 * Math.sqrt(32) / EnergyConfig.spireSpacing();
        helper.assertTrue(Math.abs(be.interference() - alone * share) < 1e-6, "neighbour costs output: " + be.interference() + " vs " + alone * share);
        helper.destroyBlock(new BlockPos(0, 1, 0));
        be.scanNow();
        helper.assertTrue(Math.abs(be.interference() - alone) < 1e-9, "back to before when it is gone");
        helper.succeed();
    }

    /** Too short, a stranger in the column, no crown, and the crown lights while it runs. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void spireNamesTheWrongBlock(GameTestHelper helper) {
        BlockPos base = new BlockPos(4, 1, 4);
        SpireBlockEntity be = spire(helper, base, 3, 0);
        helper.assertTrue(!be.isFormed() && "multiblock.robotica.spire_too_short".equals(key(be)), "3 conductors are too few, got " + key(be));
        be = spire(helper, base, 8, 0);
        helper.assertTrue(be.isFormed(), "8 copper forms: " + why(be));
        helper.assertTrue(helper.getBlockState(base.above(9)).getValue(com.arno.robotica.energy.block.SpireCrownBlock.FORMED), "the formed crown grows its toroid");
        helper.setBlock(base.above(3), Blocks.DIRT);
        be.scanNow();
        helper.assertFalse(helper.getBlockState(base.above(9)).getValue(com.arno.robotica.energy.block.SpireCrownBlock.FORMED), "a broken spire's crown shrinks back");
        helper.assertTrue(!be.isFormed() && "multiblock.robotica.spire_column".equals(key(be)) && helper.absolutePos(base.above(3)).equals(be.problem().pos()),
                "dirt in the column is named, got " + key(be));
        helper.setBlock(base.above(3), Blocks.COPPER_BLOCK);
        helper.setBlock(base.above(9), Blocks.AIR);
        be.scanNow();
        helper.assertTrue(!be.isFormed() && "multiblock.robotica.spire_no_crown".equals(key(be)), "a missing crown is named, got " + key(be));
        helper.setBlock(base.above(9), EnergyRegistry.SPIRE_CROWN.get());
        be.scanNow();
        be.setSkyForTest(true);
        be.fuel.setStackInSlot(0, new ItemStack(Items.JIGSAW));
        be.simulate(1);
        helper.assertTrue(helper.getBlockState(base.above(9)).getValue(com.arno.robotica.energy.block.SpireCrownBlock.LIT), "the crown lights while it runs");
        helper.succeed();
    }

    // ---------------------------------------------------------------- ring collider

    /** A 7x7 square loop of Accelerator Segments with the controller in the middle of the north side. */
    private static BlockPos ring(GameTestHelper helper, BlockPos min) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) {
            if (x == 0 || z == 0 || x == 6 || z == 6) helper.setBlock(min.offset(x, 0, z), EnergyRegistry.ACCELERATOR_SEGMENT.get());
        }
        BlockPos c = min.offset(3, 0, 0);
        helper.setBlock(c, EnergyRegistry.COLLIDER_CONTROLLER.get().defaultBlockState().setValue(ControllerBlock.FACING, Direction.NORTH));
        return c;
    }

    /**
     * 24 blocks: 23 segments = 23,000 FE/t. Charges 12M FE at most 2M per tick, starts with fuel, ramps up over 200
     * ticks, makes Strange Matter progress, and collapses when switched off.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void colliderSpinsUpAndRuns(GameTestHelper helper) {
        BlockPos c = ring(helper, new BlockPos(1, 1, 1));
        ColliderBlockEntity be = be(helper, c);
        be.scanNow();
        helper.assertTrue(be.isFormed() && be.length() == 24, "24 block ring forms: " + why(be));
        helper.assertTrue(be.chargeNeeded() == 24L * EnergyConfig.colliderSpinupPerSegment() && be.fullPower() == 23_000, "12M charge, 23k FE/t");

        IEnergyStorage cap = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(c), Direction.NORTH);
        be.step();
        helper.assertTrue(be.state() == ColliderBlockEntity.State.CHARGING && cap != null && cap.canReceive(), "charging");
        helper.assertTrue(cap.receiveEnergy(Integer.MAX_VALUE, false) == EnergyConfig.colliderChargeRate(), "charging is limited per tick");
        helper.assertTrue(cap.receiveEnergy(Integer.MAX_VALUE, false) == 0, "shared within a tick");
        be.setCharge(be.chargeNeeded());
        be.step();
        helper.assertTrue(!be.beamOn() && be.state() == ColliderBlockEntity.State.NO_FUEL, "no fuel, no beam");
        be.fuel.setStackInSlot(0, new ItemStack(Items.STRUCTURE_VOID, 2));
        be.step();
        helper.assertTrue(be.beamOn() && be.charge() == 0, "starts and spends the charge");
        for (int i = 0; i < 220; i++) be.step();
        helper.assertTrue(be.fePerTick() == 23_000, "full output after warm-up, got " + be.fePerTick());
        helper.assertTrue(be.luminosity() > 23 * 200 / 2, "Strange Matter progress, got " + be.luminosity());
        helper.assertFalse(cap.canReceive(), "no charging while the beam runs");
        helper.assertTrue(cap.canExtract() && cap.extractEnergy(1_000, false) == 1_000, "FE comes out of the controller");
        var tag = new net.minecraft.nbt.CompoundTag();
        be.writeSync(tag, helper.getLevel().registryAccess());
        helper.assertTrue(tag.getBoolean("beamOn") && tag.getInt("fe") == 23_000 && tag.getInt("length") == 24, "GUI sync of the collider");
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(c), Direction.NORTH);
        helper.assertTrue(items != null && items.insertItem(0, new ItemStack(Items.STRUCTURE_VOID), false).isEmpty() && items.extractItem(0, 64, false).isEmpty(),
                "fuel goes in, automation can not pull it back out");
        be.setEnabled(false);
        be.step();
        helper.assertFalse(be.beamOn(), "switching off collapses the beam");
        helper.succeed();
    }

    /**
     * A full buffer pauses the beam (no fuel, no Strange Matter progress); a broken ring collapses the beam and the
     * client stops drawing it.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void colliderPausesWhenFullAndStopsWhenBroken(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos c = ring(helper, min);
        ColliderBlockEntity be = be(helper, c);
        be.scanNow();
        be.setCharge(be.chargeNeeded());
        be.fuel.setStackInSlot(0, new ItemStack(Items.STRUCTURE_VOID, 2));
        for (int i = 0; i < 210; i++) be.step();
        helper.assertTrue(be.beamOn() && be.fePerTick() == 23_000 && be.shownBeam() == 10, "runs at full beam");
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        long lum = be.luminosity();
        for (int i = 0; i < 50; i++) be.step();
        helper.assertTrue(be.state() == ColliderBlockEntity.State.BUFFER_FULL && be.fePerTick() == 0 && be.luminosity() == lum && be.beamOn(),
                "a full buffer pauses the beam, got " + be.state());
        be.energy.setEnergy(0);
        be.step();
        helper.assertTrue(be.state() == ColliderBlockEntity.State.RUNNING && be.luminosity() > lum, "runs again once power is taken");

        helper.setBlock(min.offset(6, 0, 3), Blocks.AIR);
        be.scanNow();
        helper.assertTrue(!be.isFormed() && !be.beamOn() && be.ring().isEmpty(), "a broken ring collapses the beam");
        be.serverTick(helper.getLevel(), be.getBlockPos(), be.getBlockState());
        helper.assertTrue(be.shownBeam() == 0 && be.shownCharge() == 0, "clients stop drawing the beam");
        helper.succeed();
    }

    /** Open loop, a branch and a stranger in the loop are named; a short loop is too short. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void colliderNamesTheWrongBlock(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        BlockPos c = ring(helper, min);
        ColliderBlockEntity be = be(helper, c);
        BlockPos gap = min.offset(6, 0, 3);
        helper.setBlock(gap, Blocks.AIR);
        be.scanNow();
        helper.assertTrue(!be.isFormed() && "multiblock.robotica.collider_open".equals(key(be)), "an open loop is named, got " + key(be));
        helper.setBlock(gap, Blocks.STONE);
        be.scanNow();
        helper.assertTrue(!be.isFormed() && "multiblock.robotica.collider_open".equals(key(be)), "stone does not join the loop, got " + key(be));
        helper.setBlock(gap, EnergyRegistry.RESONANT_SEGMENT.get());
        BlockPos branch = min.offset(3, 0, 6).south();
        helper.setBlock(branch, EnergyRegistry.ACCELERATOR_SEGMENT.get());
        be.scanNow();
        helper.assertTrue(!be.isFormed() && "multiblock.robotica.collider_branch".equals(key(be)), "a branch is named, got " + key(be));
        helper.setBlock(branch, Blocks.AIR);
        be.scanNow();
        helper.assertTrue(be.isFormed() && be.fullPower() == 22 * 1_000 + 2_500, "a Resonant Segment adds more, got " + be.fullPower());

        // a 3x3 loop: 8 blocks, too short
        BlockPos small = new BlockPos(1, 3, 1);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            if (x != 1 || z != 1) helper.setBlock(small.offset(x, 0, z), EnergyRegistry.ACCELERATOR_SEGMENT.get());
        }
        helper.setBlock(small.offset(1, 0, 0), EnergyRegistry.COLLIDER_CONTROLLER.get());
        ColliderBlockEntity tiny = be(helper, small.offset(1, 0, 0));
        tiny.scanNow();
        helper.assertTrue(!tiny.isFormed() && "multiblock.robotica.collider_too_short".equals(key(tiny)), "8 blocks are too short, got " + key(tiny));
        helper.succeed();
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

    /** The shipped data maps load with the contract's format. */
    @GameTest(template = ARENA)
    public static void dataMapsLoaded(GameTestHelper helper) {
        var copper = EnergyDataMaps.conductor(Blocks.COPPER_BLOCK.defaultBlockState());
        var netherite = EnergyDataMaps.conductor(Blocks.NETHERITE_BLOCK.defaultBlockState());
        helper.assertTrue(copper != null && copper.power() == 30 && copper.efficiency() == 1.0F, "copper 30 FE/t at x1.0");
        helper.assertTrue(netherite != null && netherite.power() == 500 && netherite.efficiency() == 3.0F, "netherite 500 FE/t at x3.0");
        helper.assertTrue(EnergyDataMaps.conductor(Blocks.STONE.defaultBlockState()) == null, "stone is no conductor");
        var redstone = EnergyDataMaps.modulator(Blocks.REDSTONE_BLOCK.defaultBlockState());
        var cryo = EnergyDataMaps.modulator(EnergyRegistry.CRYO_COOLANT.get().defaultBlockState());
        helper.assertTrue(redstone != null && Math.abs(redstone.power() - 0.03F) < 1e-6 && Math.abs(redstone.burn() - 0.06F) < 1e-6, "redstone block +3% / +6%");
        helper.assertTrue(cryo != null && cryo.power() < 0 && cryo.burn() < cryo.power(), "Cryo Coolant: less power, much slower burn");
        var servo = EnergyDataMaps.reactorCore(new ItemStack(CoreItems.SERVO_CORE.get()));
        helper.assertTrue(servo != null && servo.power() == 2.0F && servo.life() == 12_096_000, "Servo Core: x2 for 7 days");
        helper.assertTrue(EnergyDataMaps.reactorFuel(new ItemStack(Items.DIRT)) == null && EnergyDataMaps.spireFuel(new ItemStack(Items.DIRT)) == null, "dirt is no fuel");
        helper.succeed();
    }

    /**
     * Controller GUI actions (the collider's on/off switch) only work for the owner and the owner's team; a stranger
     * with the GUI open may watch but changes nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void controllerActionsNeedOwner(GameTestHelper helper) {
        BlockPos rel = new BlockPos(2, 2, 2);
        helper.setBlock(rel, EnergyRegistry.COLLIDER_CONTROLLER.get());
        ColliderBlockEntity collider = (ColliderBlockEntity) helper.getBlockEntity(rel);
        BlockPos pos = collider.getBlockPos();
        var owner = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5e1d2c3b-0000-4000-8000-00000000e001"), "reactor_owner"));
        var stranger = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("5e1d2c3b-0000-4000-8000-00000000e002"), "reactor_stranger"));
        collider.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("robotica_reactor_test");
        try {
            for (var p : java.util.List.of(owner, stranger)) {
                p.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
                p.containerMenu = new com.arno.robotica.energy.menu.ColliderMenu(1, p.getInventory(), collider);
            }
            var off = new ControllerActionPayload(pos, ControllerActionPayload.SET_ENABLED, 0);
            helper.assertTrue(ControllerActionPayload.process(owner, off) && !collider.enabled(), "the owner switches it off");
            var on = new ControllerActionPayload(pos, ControllerActionPayload.SET_ENABLED, 1);
            helper.assertTrue(!collider.canControl(stranger), "a stranger may not control it");
            helper.assertTrue(!ControllerActionPayload.process(stranger, on) && !collider.enabled(), "a stranger changes nothing");
            helper.assertTrue(stranger.containerMenu.stillValid(stranger), "a stranger may still watch");
            scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(stranger.getScoreboardName(), team);
            helper.assertTrue(ControllerActionPayload.process(stranger, on) && collider.enabled(), "a team mate may");
            scoreboard.removePlayerFromTeam(stranger.getScoreboardName(), team);
            collider.setOwner(null);
            helper.assertTrue(collider.canControl(stranger), "without an owner everyone may");
        } finally {
            scoreboard.removePlayerTeam(team);
            owner.containerMenu = owner.inventoryMenu;
            stranger.containerMenu = stranger.inventoryMenu;
        }
        helper.succeed();
    }
}
