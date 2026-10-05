package com.arno.robotica.drones.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

/** Headless tests of the drones module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class DronesGameTests {

    /** Fills x 1..6, y 0..4, z -1..3 with stone: a block of rock east of the drone with the floor and ring walls around a 3x3 line. */
    private static void rock(GameTestHelper helper) {
        clear(helper);
        for (int x = 1; x <= 6; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -1; z <= 3; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
    }

    /** The test area is reused by later tests, so every test that builds leaves air behind. */
    private static void clear(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -1; z <= 8; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            }
        }
    }

    private static MiningDrone miner(GameTestHelper helper) {
        MiningDrone drone = helper.spawn(DronesRegistry.MINING_DRONE_ENTITY.get(), new BlockPos(0, 2, 1));
        drone.setTier(1);
        drone.setHealth(drone.getMaxHealth());
        drone.setOwnerUUID(UUID.randomUUID());
        drone.setEnergy(drone.getEnergyCapacity());
        drone.digTicksOverride = 1;
        return drone;
    }

    @GameTest(template = "empty", batch = "dronesTunnel", timeoutTicks = 600)
    public static void miningDroneCarvesTunnel(GameTestHelper helper) {
        rock(helper);
        MiningDrone drone = miner(helper);
        int before = drone.getEnergy();
        helper.runAfterDelay(2, () -> drone.startTunnel(Direction.EAST, 5, helper.absolutePos(new BlockPos(0, 1, 1))));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.lastStop() == MiningDrone.Stop.DONE, "tunnel should finish with DONE, is " + drone.lastStop());
            for (int x = 1; x <= 5; x++) {
                for (int y = 1; y <= 3; y++) {
                    for (int z = 0; z <= 2; z++) helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(x, y, z));
                }
            }
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, 2, 1));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 4, 1));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 3));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 0, 1));
            helper.assertTrue(drone.count(Items.COBBLESTONE) == 45, "5 slices of 9 stone give 45 cobblestone, has " + drone.count(Items.COBBLESTONE));
            helper.assertTrue(drone.getEnergy() == before - 45 * 60, "each block costs 60 FE, spent " + (before - drone.getEnergy()));
            clear(helper);
        });
    }

    @GameTest(template = "empty", batch = "dronesLava", timeoutTicks = 600)
    public static void lavaAheadStopsTunnel(GameTestHelper helper) {
        rock(helper);
        helper.setBlock(new BlockPos(4, 2, 1), Blocks.LAVA);
        MiningDrone drone = miner(helper);
        helper.runAfterDelay(2, () -> drone.startTunnel(Direction.EAST, 5, helper.absolutePos(new BlockPos(0, 1, 1))));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.lastStop() == MiningDrone.Stop.LAVA, "lava should stop the tunnel, stop is " + drone.lastStop());
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(4, 2, 1));
            helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(2, 2, 1));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 1));
            helper.assertTrue(drone.mode() != MiningDrone.Mode.TUNNEL, "the drone must leave tunnel mode");
            clear(helper);
        });
    }

    private static final Consumer<BlockEvent.BreakEvent>[] VETO = new Consumer[1];

    @GameTest(template = "empty", batch = "dronesProtect", timeoutTicks = 600)
    public static void protectedBlockStopsTunnel(GameTestHelper helper) {
        rock(helper);
        BlockPos guarded = helper.absolutePos(new BlockPos(2, 2, 1));
        Consumer<BlockEvent.BreakEvent> veto = event -> {
            if (event.getPos().equals(guarded)) event.setCanceled(true);
        };
        VETO[0] = veto;
        NeoForge.EVENT_BUS.addListener(veto);
        MiningDrone drone = miner(helper);
        helper.runAfterDelay(2, () -> drone.startTunnel(Direction.EAST, 5, helper.absolutePos(new BlockPos(0, 1, 1))));
        helper.succeedWhen(() -> {
            helper.assertTrue(drone.lastStop() == MiningDrone.Stop.BLOCKED, "a vetoed break should block the tunnel, stop is " + drone.lastStop());
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 2, 1));
            NeoForge.EVENT_BUS.unregister(veto);
            clear(helper);
        });
    }

    @GameTest(template = "empty")
    public static void pickUpKeepsEnergyAndSettings(GameTestHelper helper) {
        MiningDrone drone = DronesRegistry.MINING_DRONE_ENTITY.get().create(helper.getLevel());
        drone.setTier(1);
        drone.setEnergy(123_456);
        drone.setHeading(Direction.EAST);
        drone.cycleLength();
        drone.toggleVoid();
        drone.storage.setStackInSlot(0, new ItemStack(Items.IRON_ORE, 17));
        drone.torch.setStackInSlot(0, new ItemStack(Items.TORCH, 5));
        drone.battery.setStackInSlot(0, new ItemStack(CoreItems.COPPER_CELL.get()));
        drone.setCustomName(Component.literal("Digger"));
        ItemStack stack = drone.toItemStack(false);
        helper.assertTrue(stack.is(DronesRegistry.MINING_DRONE.get()), "item form of a Mk1 drone");
        helper.assertTrue(ItemEnergy.get(stack) == 123_456, "energy is kept in the energy component");
        helper.assertTrue(stack.has(CoreComponents.ENERGY.get()) && stack.has(DronesRegistry.DRONE_STATE.get()), "components present");

        MiningDrone again = DronesRegistry.MINING_DRONE_ENTITY.get().create(helper.getLevel());
        again.initFromStack(stack, null);
        helper.assertTrue(again.getEnergy() == 123_456, "energy restored, is " + again.getEnergy());
        helper.assertTrue(again.storage.getStackInSlot(0).is(Items.IRON_ORE) && again.storage.getStackInSlot(0).getCount() == 17, "inventory restored");
        helper.assertTrue(again.torch.getStackInSlot(0).getCount() == 5, "torches restored");
        helper.assertTrue(again.battery.getStackInSlot(0).is(CoreItems.COPPER_CELL.get()), "battery restored");
        helper.assertTrue(again.voidOn() && again.tunnelLength() == 128 && again.heading() == Direction.EAST, "settings restored");
        helper.assertTrue(again.hasCustomName() && again.getCustomName().getString().equals("Digger"), "name restored");

        ItemStack mk2 = new ItemStack(DronesRegistry.MINING_DRONE_MK2.get());
        MiningDrone big = DronesRegistry.MINING_DRONE_ENTITY.get().create(helper.getLevel());
        big.initFromStack(mk2, null);
        helper.assertTrue(big.tier() == 2 && big.storageSlots() == 27, "Mk2 has tier 2 and 27 slots");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sentryPickUpKeepsSettings(GameTestHelper helper) {
        SentryDrone drone = DronesRegistry.SENTRY_DRONE_ENTITY.get().create(helper.getLevel());
        drone.setTier(1);
        drone.setEnergy(77_777);
        drone.toggleStance();
        drone.cycleRadius();
        drone.upgrades.setStackInSlot(0, new ItemStack(CoreItems.card(UpgradeKind.SPEED, 1).get()));
        ItemStack stack = drone.toItemStack(false);
        helper.assertTrue(ItemEnergy.get(stack) == 77_777, "energy kept");
        SentryDrone again = DronesRegistry.SENTRY_DRONE_ENTITY.get().create(helper.getLevel());
        again.initFromStack(stack, null);
        helper.assertTrue(again.getEnergy() == 77_777 && !again.aggressive() && again.radiusIndex() == 1, "settings restored");
        helper.assertTrue(again.upgrades.level(UpgradeKind.SPEED) == 1, "upgrade card restored");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dronesSentry", timeoutTicks = 400)
    public static void sentryShootsHostilesOnly(GameTestHelper helper) {
        clear(helper);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
        SentryDrone sentry = helper.spawn(DronesRegistry.SENTRY_DRONE_ENTITY.get(), new BlockPos(1, 2, 1));
        sentry.setTier(1);
        sentry.setHealth(sentry.getMaxHealth());
        sentry.setOwnerUUID(UUID.randomUUID());
        sentry.setEnergy(sentry.getEnergyCapacity());
        sentry.setMode(SentryDrone.Mode.STAY);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 1, 1));
        husk.setNoAi(true);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 1));
        villager.setNoAi(true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(sentry.canTarget(husk), "a husk is a target");
        helper.assertTrue(!sentry.canTarget(villager), "never a villager");
        helper.assertTrue(!sentry.canTarget(player), "never a player");
        helper.assertTrue(!sentry.canTarget(sentry), "never a drone");
        SentryDrone other = DronesRegistry.SENTRY_DRONE_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(!sentry.canTarget(other), "never another drone");
        sentry.toggleStance();
        helper.assertTrue(!sentry.canTarget(husk), "defensive: a calm husk is left alone");
        sentry.toggleStance();
        float villagerHealth = villager.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(sentry.shots > 0, "the sentry must fire");
            helper.assertTrue(!husk.isAlive() || husk.getHealth() < husk.getMaxHealth(), "the husk must be hurt");
            helper.assertTrue(villager.getHealth() == villagerHealth, "the villager must stay unhurt");
            helper.assertTrue(sentry.getEnergy() < sentry.getEnergyCapacity(), "shots cost energy");
            clear(helper);
        });
    }

    @GameTest(template = "empty")
    public static void sentryOnlyFiresWithEnergy(GameTestHelper helper) {
        SentryDrone sentry = DronesRegistry.SENTRY_DRONE_ENTITY.get().create(helper.getLevel());
        sentry.setTier(1);
        sentry.setEnergy(0);
        helper.assertTrue(sentry.isEnergyLow(), "an empty drone is low");
        sentry.setEnergy(sentry.getEnergyCapacity());
        helper.assertTrue(!sentry.isEnergyLow(), "a full drone is not low");
        helper.assertTrue(sentry.consume(sentry.fePerShot()), "a shot is paid from the buffer");
        helper.assertTrue(sentry.getEnergy() == sentry.getEnergyCapacity() - sentry.fePerShot(), "exactly the shot cost is spent");
        helper.succeed();
    }
}
