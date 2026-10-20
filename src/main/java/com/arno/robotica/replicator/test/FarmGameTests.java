package com.arno.robotica.replicator.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.energy.block.ControllerBlock;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.farm.FarmControllerBlockEntity;
import com.arno.robotica.replicator.farm.FarmCycle;
import com.arno.robotica.replicator.farm.FarmKind;
import com.arno.robotica.replicator.logic.Essence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Headless tests of the mob farms: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class FarmGameTests {
    private static final String ARENA = "energy_arena";
    private static final BlockPos MIN = new BlockPos(1, 1, 1);
    private static final BlockPos CONTROLLER = MIN.offset(2, 2, 0);
    private static final BlockPos RAM_WEST = MIN.offset(0, 2, 2), RAM_EAST = MIN.offset(4, 2, 2), RAM_SOUTH = MIN.offset(2, 2, 4);
    private static final BlockPos MATRIX = MIN.offset(2, 4, 2);
    private static final BlockPos PORT = MIN.offset(0, 2, 1);

    /**
     * A 5x5x5 farm at {@link #MIN}: casing shell, the controller in the middle of the north wall, rams in the middle of the
     * other three side walls, the matrix in the middle of the roof, a Farm Port in the west wall and a window.
     */
    private static FarmControllerBlockEntity farm(GameTestHelper helper, Block controller, Block matrix) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0 || x == 4 || y == 4 || z == 4;
                    helper.setBlock(MIN.offset(x, y, z), shell ? ReplicatorRegistry.FARM_CASING.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        helper.setBlock(CONTROLLER, controller.defaultBlockState().setValue(ControllerBlock.FACING, Direction.NORTH));
        for (BlockPos ram : new BlockPos[]{RAM_WEST, RAM_EAST, RAM_SOUTH}) helper.setBlock(ram, ReplicatorRegistry.IMPACT_RAM.get());
        helper.setBlock(MATRIX, matrix);
        helper.setBlock(PORT, ReplicatorRegistry.FARM_PORT.get());
        helper.setBlock(MIN.offset(4, 1, 2), ReplicatorRegistry.FARM_GLASS.get());
        FarmControllerBlockEntity be = (FarmControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.scanNow();
        return be;
    }

    private static FarmControllerBlockEntity golemFarm(GameTestHelper helper) {
        return farm(helper, ReplicatorRegistry.IRON_GOLEM_FARMER.get(), ReplicatorRegistry.GOLEM_MATRIX.get());
    }

    private static String key(FarmControllerBlockEntity be) {
        if (be.problem() == null) return null;
        return be.problem().message().getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
    }

    private static String why(FarmControllerBlockEntity be) {
        return be.problem() == null ? "-" : be.problem().message().getString();
    }

    private static void fillGolemVials(FarmControllerBlockEntity be) {
        for (int i = 0; i < 3; i++) be.vials.setStackInSlot(i, Essence.completeVial(EntityType.VILLAGER));
        be.vials.setStackInSlot(3, Essence.completeVial(EntityType.HUSK));
    }

    /** Runs the farm for {@code ticks} server ticks with a full buffer. */
    private static void run(GameTestHelper helper, FarmControllerBlockEntity be, int ticks) {
        BlockPos pos = helper.absolutePos(CONTROLLER);
        for (int i = 0; i < ticks; i++) {
            be.energy.setEnergy(be.energy.getMaxEnergyStored());
            be.serverTick(helper.getLevel(), pos, be.getBlockState());
        }
    }

    private static int count(IItemHandler handler, Item item) {
        int n = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) n += handler.getStackInSlot(i).getCount();
        }
        return n;
    }

    // ---------------------------------------------------------------- structure

    /** The farm forms with its rams in order; a wrong matrix, a missing ram, a block inside and a missing port are named. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void farmStructureRules(GameTestHelper helper) {
        FarmControllerBlockEntity be = golemFarm(helper);
        helper.assertTrue(be.isFormed(), "golem farm should form, problem: " + why(be));
        helper.assertTrue(be.kind() == FarmKind.GOLEM, "an Iron Golem Farmer is a golem farm");
        helper.assertTrue(be.rams().size() == 3 && be.center() != null && be.center().equals(helper.absolutePos(MIN.offset(2, 2, 2))),
                "three rams and the chamber's middle");

        helper.setBlock(MATRIX, ReplicatorRegistry.WITHER_MATRIX.get());
        be.scanNow();
        helper.assertTrue(!be.isFormed() && "multiblock.robotica.farm_matrix_wrong".equals(key(be)), "wither matrix on a golem farm, got " + key(be));
        helper.setBlock(MATRIX, ReplicatorRegistry.FARM_CASING.get());
        be.scanNow();
        helper.assertTrue("multiblock.robotica.farm_no_matrix".equals(key(be)), "no matrix, got " + key(be));
        helper.setBlock(MATRIX, ReplicatorRegistry.GOLEM_MATRIX.get());

        helper.setBlock(RAM_EAST, ReplicatorRegistry.FARM_CASING.get());
        be.scanNow();
        helper.assertTrue("multiblock.robotica.farm_rams".equals(key(be)), "two rams, got " + key(be));
        helper.setBlock(MIN.offset(4, 3, 2), ReplicatorRegistry.IMPACT_RAM.get());
        be.scanNow();
        helper.assertTrue("multiblock.robotica.farm_ram_spot".equals(key(be)), "a ram off the middle, got " + key(be));
        helper.setBlock(MIN.offset(4, 3, 2), ReplicatorRegistry.FARM_CASING.get());
        helper.setBlock(RAM_EAST, ReplicatorRegistry.IMPACT_RAM.get());

        helper.setBlock(MIN.offset(1, 1, 1), Blocks.DIRT);
        be.scanNow();
        helper.assertTrue("multiblock.robotica.farm_inside".equals(key(be)), "dirt in the chamber, got " + key(be));
        helper.setBlock(MIN.offset(1, 1, 1), Blocks.AIR);

        helper.setBlock(PORT, ReplicatorRegistry.FARM_CASING.get());
        be.scanNow();
        helper.assertTrue("multiblock.robotica.farm_no_port".equals(key(be)), "no port, got " + key(be));
        helper.setBlock(PORT, ReplicatorRegistry.FARM_PORT.get());

        be.scanNow();
        helper.assertTrue(be.isFormed(), "fixed again: formed, problem: " + why(be));
        helper.succeed();
    }

    // ---------------------------------------------------------------- vials

    /** Each slot takes only a complete vial of its mob; the Replicator refuses Villager vials. */
    @GameTest(template = "empty")
    public static void vialSlotsFollowTheirRules(GameTestHelper helper) {
        ItemStackHandler golem = FarmControllerBlockEntity.newVialHandler(FarmKind.GOLEM, () -> {});
        ItemStackHandler wither = FarmControllerBlockEntity.newVialHandler(FarmKind.WITHER, () -> {});
        ItemStack villager = Essence.completeVial(EntityType.VILLAGER);
        ItemStack zombie = Essence.completeVial(EntityType.ZOMBIE);
        ItemStack drowned = Essence.completeVial(EntityType.DROWNED);
        ItemStack skeleton = Essence.completeVial(EntityType.WITHER_SKELETON);
        helper.assertTrue(golem.getSlots() == 4 && wither.getSlots() == 3, "4 golem vial slots, 3 wither");
        helper.assertTrue(golem.isItemValid(0, villager) && golem.isItemValid(2, villager) && !golem.isItemValid(3, villager), "villagers in slots 0-2");
        helper.assertTrue(golem.isItemValid(3, zombie) && golem.isItemValid(3, drowned) && !golem.isItemValid(0, zombie), "any zombie in slot 3");
        helper.assertTrue(wither.isItemValid(0, skeleton) && !wither.isItemValid(0, zombie), "wither skeletons only");
        ItemStack partial = new ItemStack(ReplicatorRegistry.ESSENCE_VIAL.get());
        Essence.sample(partial, EntityType.VILLAGER);
        helper.assertFalse(golem.isItemValid(0, partial), "an unfinished vial is refused");
        helper.assertFalse(Essence.isUsable(villager), "a Villager vial is no Replicator vial");
        helper.assertFalse(ReplicatorControllerBlockEntity.newVialHandler(() -> {}).isItemValid(0, villager), "the Replicator refuses it");
        helper.succeed();
    }

    // ---------------------------------------------------------------- cycles

    /** Without its vials the farm waits and uses no power; with them one cycle gives the golem's iron, taken out through the port. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void golemFarmMakesIron(GameTestHelper helper) {
        FarmControllerBlockEntity be = golemFarm(helper);
        helper.assertTrue(be.isFormed(), "formed, problem: " + why(be));
        be.energy.setEnergy(1_000);
        be.serverTick(helper.getLevel(), helper.absolutePos(CONTROLLER), be.getBlockState());
        helper.assertTrue(be.state() == FarmControllerBlockEntity.State.NO_VIALS && be.energy.getEnergyStored() == 1_000, "no vials: waits, no power used");

        fillGolemVials(be);
        run(helper, be, be.cycleTicks());
        helper.assertTrue(be.cycles() == 1, "one golem after one cycle, got " + be.cycles());
        int iron = count(be.output, Items.IRON_INGOT);
        helper.assertTrue(iron >= 3 && iron <= 5, "an iron golem drops 3-5 iron, got " + iron);

        IItemHandler port = be.portItems();
        helper.assertTrue(port != null && port.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "the port inserts nothing");
        int slot = -1;
        for (int i = 0; i < port.getSlots(); i++) if (port.getStackInSlot(i).is(Items.IRON_INGOT)) slot = i;
        helper.assertTrue(slot >= 0 && port.extractItem(slot, 64, false).is(Items.IRON_INGOT), "the port gives the iron");
        helper.succeed();
    }

    /** One cycle of the Wither Farmer gives a Nether Star; speed cards and the boost shorten it. */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void witherFarmMakesStars(GameTestHelper helper) {
        FarmControllerBlockEntity be = farm(helper, ReplicatorRegistry.WITHER_FARMER.get(), ReplicatorRegistry.WITHER_MATRIX.get());
        helper.assertTrue(be.isFormed() && be.kind() == FarmKind.WITHER, "wither farm formed, problem: " + why(be));
        int base = be.cycleTicks();
        be.upgrades.setStackInSlot(0, com.arno.robotica.core.item.CoreItems.cards(com.arno.robotica.core.upgrade.UpgradeKind.SPEED, 3));
        be.boost.setStackInSlot(0, new ItemStack(com.arno.robotica.core.item.CoreItems.PLASMA_ACTUATOR.get()));
        helper.assertTrue(be.cycleTicks() < base, "cards and boost shorten the cycle");
        for (int i = 0; i < 3; i++) be.vials.setStackInSlot(i, Essence.completeVial(EntityType.WITHER_SKELETON));
        run(helper, be, be.cycleTicks());
        helper.assertTrue(be.cycles() == 1 && count(be.output, Items.NETHER_STAR) == 1, "one Wither, one star, got " + count(be.output, Items.NETHER_STAR));
        helper.succeed();
    }

    /** The three strikes land in order inside the cycle, with room for the mob to form and to fall, at any length. */
    @GameTest(template = "empty")
    public static void strikesFitTheCycle(GameTestHelper helper) {
        for (int needed : new int[]{1, 4, 20, 75, 600, 6000}) {
            int last = -1;
            for (int i = 0; i < 3; i++) {
                int s = FarmCycle.strike(i, needed);
                helper.assertTrue(s >= last && s <= needed, "strike " + i + " of " + needed + " at " + s);
                last = s;
            }
            if (needed >= 20) {
                helper.assertTrue(FarmCycle.strike(0, needed) > FarmCycle.formTicks(needed) && FarmCycle.strike(2, needed) < needed, "phases of " + needed);
            }
        }
        helper.succeed();
    }
}
