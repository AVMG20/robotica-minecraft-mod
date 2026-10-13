package com.arno.robotica.drones.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.CoreComponents;
import com.arno.robotica.core.CoreConfig;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.drones.DronesConfig;
import com.arno.robotica.drones.DronesRegistry;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.CourierRoute;
import com.arno.robotica.gear.GearBlocks;
import com.arno.robotica.gear.GearConfig;
import com.arno.robotica.drones.entity.HaulerDrone;
import com.arno.robotica.drones.entity.BuddyBot;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.drones.menu.BuddyBotMenu;
import com.arno.robotica.drones.net.DroneCommandPayload;
import com.arno.robotica.gear.GearItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Headless tests of the drones module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class DronesGameTests {

    /** Test positions are shifted by 2 in z so the ring around the tunnel stays inside the arena structure. */
    private static BlockPos p(int x, int y, int z) {
        return new BlockPos(x, y, z + 2);
    }

    /** Fills x 1..6, y 0..4, z -1..3 with stone: a block of rock east of the drone with the floor and ring walls around a 3x3 line. */
    private static void rock(GameTestHelper helper) {
        clear(helper);
        for (int x = 1; x <= 6; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -1; z <= 3; z++) helper.setBlock(p(x, y, z), Blocks.STONE);
            }
        }
    }

    /** The test area is reused by later tests, so every test that builds leaves air behind. */
    private static void clear(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -1; z <= 8; z++) helper.setBlock(p(x, y, z), Blocks.AIR);
            }
        }
    }

    private static BuddyBot buddy(GameTestHelper helper) {
        BuddyBot bot = helper.spawn(DronesRegistry.BUDDY_BOT_ENTITY.get(), p(0, 2, 1));
        bot.setTier(1);
        bot.setHealth(bot.getMaxHealth());
        bot.setOwnerUUID(UUID.randomUUID());
        bot.setEnergy(bot.getEnergyCapacity());
        bot.setMode(BuddyBot.Mode.STAY);
        bot.digTicksOverride = 1;
        return bot;
    }

    /** Spark Lamps the tunnel put up in the test area (they cost FE too). */
    private static int lamps(GameTestHelper helper) {
        int n = 0;
        for (int x = -2; x <= 8; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = -2; z <= 8; z++) {
                    if (helper.getBlockState(p(x, y, z)).is(GearBlocks.SPARK_LAMP.get())) n++;
                }
            }
        }
        return n;
    }

    @GameTest(template = "drones_arena", batch = "dronesTunnel", timeoutTicks = 600)
    public static void buddyCarvesTunnel(GameTestHelper helper) {
        rock(helper);
        BuddyBot bot = buddy(helper);
        int before = bot.getEnergy();
        helper.runAfterDelay(2, () -> bot.startTunnel(Direction.EAST, 5, helper.absolutePos(p(0, 1, 1))));
        helper.succeedWhen(() -> {
            helper.assertTrue(bot.lastStop() == BuddyBot.Stop.DONE, "tunnel should finish with DONE, is " + bot.lastStop());
            for (int x = 1; x <= 5; x++) {
                for (int y = 1; y <= 3; y++) {
                    for (int z = 0; z <= 2; z++) helper.assertBlockNotPresent(Blocks.STONE, p(x, y, z));
                }
            }
            helper.assertBlockPresent(Blocks.STONE, p(6, 2, 1));
            helper.assertBlockPresent(Blocks.STONE, p(3, 4, 1));
            helper.assertBlockPresent(Blocks.STONE, p(3, 2, 3));
            helper.assertBlockPresent(Blocks.STONE, p(3, 0, 1));
            helper.assertTrue(bot.count(Items.COBBLESTONE) == 45, "5 slices of 9 stone give 45 cobblestone, has " + bot.count(Items.COBBLESTONE));
            int perBlock = CoreConfig.scaleEnergy(DronesConfig.buddyFePerBlock());
            int spent = before - bot.getEnergy() - lamps(helper) * GearConfig.lampCost();
            helper.assertTrue(spent == 45 * perBlock, "each block costs " + perBlock + " FE, spent " + spent);
            helper.assertTrue(!bot.hasTunnel() && !bot.isDigging(), "the bot leaves the tunnel job");
            clear(helper);
        });
    }

    /** Mk3 digs a 5x5 slice (floor at foot level, so 2 to each side and 3 above its line) and rolls drops with Fortune I. */
    @GameTest(template = "drones_arena", batch = "dronesTunnel5", timeoutTicks = 800)
    public static void buddyMk3CarvesFiveByFive(GameTestHelper helper) {
        fill(helper, 0, 8, -2, 8, Blocks.AIR);
        fill(helper, 1, 5, -2, 4, Blocks.STONE);
        BuddyBot bot = buddy(helper);
        var fortune = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
        bot.setTier(2);
        helper.assertTrue(bot.radius() == 1 && bot.tool().getEnchantmentLevel(fortune) == 0, "Mk2 stays 3x3 without Fortune");
        bot.setTier(4);
        helper.assertTrue(bot.radius() == 2 && bot.tool().getEnchantmentLevel(fortune) == 2, "Mk4 digs 5x5 with Fortune II");
        bot.setTier(3);
        helper.assertTrue(bot.radius() == 2 && bot.tool().getEnchantmentLevel(fortune) == 1, "Mk3 digs 5x5 with Fortune I");
        bot.setEnergy(bot.getEnergyCapacity());
        helper.runAfterDelay(2, () -> bot.startTunnel(Direction.EAST, 3, helper.absolutePos(p(0, 1, 1))));
        helper.succeedWhen(() -> {
            helper.assertTrue(bot.lastStop() == BuddyBot.Stop.DONE, "tunnel should finish with DONE, is " + bot.lastStop());
            for (int x = 1; x <= 3; x++) {
                for (int y = 1; y <= 5; y++) {
                    for (int z = -1; z <= 3; z++) helper.assertBlockNotPresent(Blocks.STONE, p(x, y, z));
                }
            }
            helper.assertBlockPresent(Blocks.STONE, p(4, 3, 1));
            helper.assertBlockPresent(Blocks.STONE, p(2, 6, 1));
            helper.assertBlockPresent(Blocks.STONE, p(2, 3, 4));
            helper.assertBlockPresent(Blocks.STONE, p(2, 3, -2));
            helper.assertBlockPresent(Blocks.STONE, p(2, 0, 1));
            helper.assertTrue(bot.count(Items.COBBLESTONE) == 75, "3 slices of 25 stone give 75 cobblestone, has " + bot.count(Items.COBBLESTONE));
            fill(helper, 0, 8, -2, 8, Blocks.AIR);
        });
    }

    /** Fills x {@code x0..x1}, y 0..6, z {@code z0..z1} (test coordinates). */
    private static void fill(GameTestHelper helper, int x0, int x1, int z0, int z1, net.minecraft.world.level.block.Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = z0; z <= z1; z++) helper.setBlock(p(x, y, z), block);
            }
        }
    }

    @GameTest(template = "drones_arena", batch = "dronesLava", timeoutTicks = 600)
    public static void lavaAheadStopsTunnel(GameTestHelper helper) {
        rock(helper);
        helper.setBlock(p(4, 2, 1), Blocks.LAVA);
        BuddyBot bot = buddy(helper);
        helper.runAfterDelay(2, () -> bot.startTunnel(Direction.EAST, 5, helper.absolutePos(p(0, 1, 1))));
        helper.succeedWhen(() -> {
            helper.assertTrue(bot.lastStop() == BuddyBot.Stop.LAVA, "lava should stop the tunnel, stop is " + bot.lastStop());
            helper.assertBlockPresent(Blocks.COBBLESTONE, p(4, 2, 1));
            helper.assertBlockNotPresent(Blocks.STONE, p(2, 2, 1));
            helper.assertBlockPresent(Blocks.STONE, p(3, 2, 1));
            helper.assertTrue(!bot.hasTunnel(), "the bot must leave the tunnel job");
            clear(helper);
        });
    }

    private static final Consumer<BlockEvent.BreakEvent>[] VETO = new Consumer[1];

    @GameTest(template = "drones_arena", batch = "dronesProtect", timeoutTicks = 600)
    public static void protectedBlockStopsTunnel(GameTestHelper helper) {
        rock(helper);
        BlockPos guarded = helper.absolutePos(p(2, 2, 1));
        Consumer<BlockEvent.BreakEvent> veto = event -> {
            if (event.getPos().equals(guarded)) event.setCanceled(true);
        };
        VETO[0] = veto;
        NeoForge.EVENT_BUS.addListener(veto);
        BuddyBot bot = buddy(helper);
        helper.runAfterDelay(2, () -> bot.startTunnel(Direction.EAST, 5, helper.absolutePos(p(0, 1, 1))));
        helper.succeedWhen(() -> {
            helper.assertTrue(bot.lastStop() == BuddyBot.Stop.BLOCKED, "a vetoed break should block the tunnel, stop is " + bot.lastStop());
            helper.assertBlockPresent(Blocks.STONE, p(2, 2, 1));
            NeoForge.EVENT_BUS.unregister(veto);
            clear(helper);
        });
    }

    @GameTest(template = "empty")
    public static void pickUpKeepsEnergyAndSettings(GameTestHelper helper) {
        BuddyBot bot = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        bot.setTier(1);
        bot.setEnergy(123_456);
        bot.setStance(BuddyBot.Stance.DEFENSE);
        bot.setPickup(false);
        bot.setLights(true);
        bot.setVoid(true);
        bot.storage.setStackInSlot(0, new ItemStack(Items.IRON_ORE, 17));
        bot.battery.setStackInSlot(0, new ItemStack(CoreItems.COPPER_CELL.get()));
        bot.setCustomName(Component.literal("Sparky"));
        ItemStack stack = bot.toItemStack(false);
        helper.assertTrue(stack.is(DronesRegistry.BUDDY_BOT.get()), "item form of a Mk1 bot");
        helper.assertTrue(ItemEnergy.get(stack) == 123_456, "energy is kept in the energy component");
        helper.assertTrue(stack.has(CoreComponents.ENERGY.get()) && stack.has(DronesRegistry.DRONE_STATE.get()), "components present");

        BuddyBot again = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        again.initFromStack(stack, null);
        helper.assertTrue(again.getEnergy() == 123_456, "energy restored, is " + again.getEnergy());
        helper.assertTrue(again.storage.getStackInSlot(0).is(Items.IRON_ORE) && again.storage.getStackInSlot(0).getCount() == 17, "cargo restored");
        helper.assertTrue(again.battery.getStackInSlot(0).is(CoreItems.COPPER_CELL.get()), "battery restored");
        helper.assertTrue(again.stance() == BuddyBot.Stance.DEFENSE && !again.pickupOn() && again.lightsOn() && again.voidOn(), "settings restored");
        helper.assertTrue(again.hasCustomName() && again.getCustomName().getString().equals("Sparky"), "name restored");
        helper.succeed();
    }

    /** A charged tool is never loaded as a battery: right-click opens the GUI and shift-click puts it in the cargo. */
    @GameTest(template = "empty")
    public static void toolsAreNotBatteries(GameTestHelper helper) {
        ItemStack drill = new ItemStack(GearItems.BORE_DRILL.get());
        ItemStack cell = new ItemStack(CoreItems.COPPER_CELL.get());
        helper.assertTrue(!DroneBase.isBattery(drill), "a Bore Drill is not a battery");
        helper.assertTrue(DroneBase.isBattery(cell), "a Copper Cell is a battery");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BuddyBot bot = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        bot.setTier(1);
        BuddyBotMenu menu = new BuddyBotMenu(1, player.getInventory(), bot);
        int hotbar = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            if (menu.slots.get(i).container == player.getInventory() && menu.slots.get(i).getContainerSlot() == 0) hotbar = i;
        }
        helper.assertTrue(hotbar >= 0, "hotbar slot found");
        player.getInventory().setItem(0, drill);
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(bot.battery.getStackInSlot(0).isEmpty(), "shift-click keeps the drill out of the battery slot");
        helper.assertTrue(bot.storage.getStackInSlot(0).is(GearItems.BORE_DRILL.get()), "the drill goes to the cargo");
        player.getInventory().setItem(0, cell);
        menu.quickMoveStack(player, hotbar);
        helper.assertTrue(bot.battery.getStackInSlot(0).is(CoreItems.COPPER_CELL.get()), "shift-click puts a cell in the battery slot");
        helper.succeed();
    }

    /** The backpack key picks the nearest own bot the player's client tracks, never another player's, and reaches past arm's reach. */
    @GameTest(template = "empty")
    public static void backpackKeyOpensOwnBot(GameTestHelper helper) {
        // A fake player stays out of the level's player list: a listed mock player far away makes other tests' mobs despawn.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.fromString("5e1d2c3b-0000-4000-8000-00000000d001"), "backpack_owner"));
        BlockPos at = helper.absolutePos(p(0, 1, 0));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        BuddyBot theirs = helper.spawn(DronesRegistry.BUDDY_BOT_ENTITY.get(), p(1, 2, 0));
        BuddyBot mine = helper.spawn(DronesRegistry.BUDDY_BOT_ENTITY.get(), p(3, 2, 0));
        for (BuddyBot b : List.of(theirs, mine)) {
            b.setTier(1);
            b.setMode(BuddyBot.Mode.STAY);
            b.setStance(BuddyBot.Stance.PASSIVE);
            b.setPickup(false);
        }
        theirs.setOwnerUUID(UUID.randomUUID());
        mine.setOwnerUUID(player.getUUID());
        theirs.startSeenByPlayer(player);
        mine.stopSeenByPlayer(player);
        BuddyBot untracked = DroneCommandPayload.backpackBot(player);
        mine.startSeenByPlayer(player);
        BuddyBot picked = DroneCommandPayload.backpackBot(player);
        BuddyBotMenu menu = new BuddyBotMenu(1, player.getInventory(), mine).remote(DronesConfig.commandDistance());
        boolean openWhileTracked = menu.stillValid(player);
        mine.stopSeenByPlayer(player);
        boolean openAfterTracking = menu.stillValid(player);
        theirs.discard();
        mine.discard();
        helper.assertTrue(untracked == null, "a bot the client does not track is not picked");
        helper.assertTrue(picked == mine, "the own bot is picked, not the nearer one of another player");
        helper.assertTrue(openWhileTracked, "the menu stays open while tracked");
        helper.assertTrue(!openAfterTracking, "the menu closes when the client stops tracking the bot");

        BuddyBot far = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        far.setTier(1);
        far.setOwnerUUID(player.getUUID());
        far.setPos(player.getX() + 12, player.getY(), player.getZ());
        far.startSeenByPlayer(player);
        helper.assertTrue(!new BuddyBotMenu(2, player.getInventory(), far).stillValid(player), "right-click reach is 8 blocks");
        helper.assertTrue(new BuddyBotMenu(3, player.getInventory(), far).remote(DronesConfig.commandDistance()).stillValid(player),
                "the backpack key reaches further");
        helper.succeed();
    }

    /** Every Mk adds cargo space; the slots past the tier take nothing. */
    @GameTest(template = "empty")
    public static void cargoGrowsWithTier(GameTestHelper helper) {
        int[] want = {18, 27, 36, 54};
        net.minecraft.world.item.Item[] items = {DronesRegistry.BUDDY_BOT.get(), DronesRegistry.BUDDY_BOT_MK2.get(),
                DronesRegistry.BUDDY_BOT_MK3.get(), DronesRegistry.BUDDY_BOT_MK4.get()};
        int lastBuffer = 0;
        for (int t = 1; t <= 4; t++) {
            BuddyBot bot = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
            bot.initFromStack(new ItemStack(items[t - 1]), null);
            helper.assertTrue(bot.tier() == t && bot.cargoSlots() == want[t - 1], "Mk" + t + " has " + want[t - 1] + " slots, has " + bot.cargoSlots());
            helper.assertTrue(bot.baseItem() == items[t - 1], "Mk" + t + " turns back into its own item");
            helper.assertTrue(bot.getEnergyCapacity() > lastBuffer, "Mk" + t + " holds more FE than the Mk before");
            lastBuffer = bot.getEnergyCapacity();
            if (t < 4) {
                ItemStack rest = bot.storage.insertItem(want[t - 1], new ItemStack(Items.DIRT), false);
                helper.assertTrue(rest.getCount() == 1, "Mk" + t + " refuses items past its cargo");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mk4KeepsTierOnLoad(GameTestHelper helper) {
        BuddyBot bot = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        bot.setTier(4);
        bot.setHealth(bot.getMaxHealth());
        bot.setEnergy(bot.getEnergyCapacity());
        CompoundTag saved = bot.saveWithoutId(new CompoundTag());

        BuddyBot again = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        again.load(saved);
        helper.assertTrue(again.tier() == 4, "Mk4 stays Mk4 after a reload, is Mk" + again.tier());
        helper.assertTrue(again.getEnergy() == DronesConfig.buddyBuffer(4), "Mk4 buffer kept, is " + again.getEnergy());
        helper.assertTrue(again.getMaxHealth() == DronesConfig.buddyHealth(4) && again.getHealth() == again.getMaxHealth(),
                "Mk4 health kept, is " + again.getHealth() + "/" + again.getMaxHealth());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void buddyRegensOnlyWhenCharged(GameTestHelper helper) {
        BuddyBot bot = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        bot.setTier(1);
        int cap = bot.getEnergyCapacity();
        int fe = CoreConfig.scaleEnergy(DroneBase.REGEN_FE_PER_HP);
        bot.setHealth(10.0F);

        bot.setEnergy(cap * DroneBase.REGEN_MIN_PERCENT / 100);
        helper.assertFalse(bot.regenStep(), "no regen at 80% charge");
        helper.assertTrue(bot.getHealth() == 10.0F && bot.getEnergy() == cap * DroneBase.REGEN_MIN_PERCENT / 100, "80% charge: nothing changes");

        bot.setEnergy(cap);
        helper.assertTrue(bot.regenStep(), "regen on a full charge");
        helper.assertTrue(bot.getHealth() == 11.0F, "1 HP per step, is " + bot.getHealth());
        helper.assertTrue(bot.getEnergy() == cap - fe, "regen costs " + fe + " FE, energy is " + bot.getEnergy());

        bot.setHealth(bot.getMaxHealth());
        bot.setEnergy(cap);
        helper.assertFalse(bot.regenStep(), "no regen at full health");
        helper.assertTrue(bot.getEnergy() == cap, "full health costs nothing");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void buddyRegensWhileDeployed(GameTestHelper helper) {
        BuddyBot bot = helper.spawn(DronesRegistry.BUDDY_BOT_ENTITY.get(), new BlockPos(1, 2, 1));
        bot.setTier(1);
        bot.setOwnerUUID(UUID.randomUUID());
        bot.setEnergy(bot.getEnergyCapacity());
        bot.setMode(BuddyBot.Mode.STAY);
        bot.setHealth(10.0F);
        helper.succeedWhen(() -> helper.assertTrue(bot.getHealth() > 10.0F, "deployed bot heals over time, is " + bot.getHealth()));
    }

    @GameTest(template = "drones_arena", batch = "dronesSentry", timeoutTicks = 400)
    public static void buddyShootsHostilesOnly(GameTestHelper helper) {
        clear(helper);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) helper.setBlock(p(x, 0, z), Blocks.STONE);
        }
        BuddyBot bot = helper.spawn(DronesRegistry.BUDDY_BOT_ENTITY.get(), p(1, 2, 1));
        bot.setTier(1);
        bot.setHealth(bot.getMaxHealth());
        bot.setOwnerUUID(UUID.randomUUID());
        bot.setEnergy(bot.getEnergyCapacity());
        bot.setMode(BuddyBot.Mode.STAY);
        Husk husk = helper.spawn(EntityType.HUSK, p(6, 1, 1));
        husk.setNoAi(true);
        Villager villager = helper.spawn(EntityType.VILLAGER, p(3, 1, 1));
        villager.setNoAi(true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(bot.canTarget(husk), "aggressive: a husk is a target");
        helper.assertTrue(!bot.canTarget(villager), "never a villager");
        helper.assertTrue(!bot.canTarget(player), "never a player");
        helper.assertTrue(!bot.canTarget(bot), "never itself");
        BuddyBot other = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(!bot.canTarget(other), "never another drone");
        bot.setStance(BuddyBot.Stance.DEFENSE);
        helper.assertTrue(!bot.canTarget(husk), "defense: a husk the owner is not fighting is left alone");
        bot.setStance(BuddyBot.Stance.PASSIVE);
        helper.assertTrue(!bot.canTarget(husk), "passive: never fires");
        bot.setStance(BuddyBot.Stance.AGGRESSIVE);
        float villagerHealth = villager.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(bot.shots > 0, "the bot must fire");
            helper.assertTrue(!husk.isAlive() || husk.getHealth() < husk.getMaxHealth(), "the husk must be hurt");
            helper.assertTrue(villager.getHealth() == villagerHealth, "the villager must stay unhurt");
            helper.assertTrue(bot.getEnergy() < bot.getEnergyCapacity(), "shots cost energy");
            clear(helper);
        });
    }

    @GameTest(template = "empty")
    public static void buddyOnlyFiresWithEnergy(GameTestHelper helper) {
        BuddyBot bot = DronesRegistry.BUDDY_BOT_ENTITY.get().create(helper.getLevel());
        bot.setTier(1);
        bot.setEnergy(0);
        helper.assertTrue(bot.isEnergyLow(), "an empty bot is low");
        bot.setEnergy(bot.getEnergyCapacity());
        helper.assertTrue(!bot.isEnergyLow(), "a full bot is not low");
        helper.assertTrue(bot.consume(bot.fePerShot()), "a shot is paid from the buffer");
        helper.assertTrue(bot.getEnergy() == bot.getEnergyCapacity() - bot.fePerShot(), "exactly the shot cost is spent");
        helper.succeed();
    }

    /** Drops lying around go into the cargo, only while pick-up is on; with void on, junk drops are deleted. */
    @GameTest(template = "drones_arena", batch = "dronesPickup", timeoutTicks = 200)
    public static void pickupToggleControlsSweeping(GameTestHelper helper) {
        clear(helper);
        BuddyBot bot = buddy(helper);
        bot.setPickup(false);
        BlockPos at = helper.absolutePos(p(1, 2, 1));
        ItemEntity drop = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.APPLE, 3));
        drop.setNoGravity(true);
        drop.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(drop);
        ItemEntity junk = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.DIRT, 5));
        junk.setNoGravity(true);
        junk.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(junk);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(drop.isAlive() && bot.count(Items.APPLE) == 0, "pick-up off: the drop stays");
            bot.setVoid(true);
            bot.setPickup(true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(bot.pickupOn() && bot.count(Items.APPLE) == 3, "pick-up on: the drop is in the cargo");
            helper.assertTrue(!drop.isAlive(), "the item entity is gone");
            helper.assertTrue(!junk.isAlive() && bot.count(Items.DIRT) == 0, "void on: the junk drop is deleted");
            clear(helper);
        });
    }

    /** Light-up mode puts a Spark Lamp where the bot hovers in the dark, for FE. */
    @GameTest(template = "drones_arena", batch = "dronesLights", timeoutTicks = 200)
    public static void lightsModeLightsTheDark(GameTestHelper helper) {
        clear(helper);
        // a closed stone box, 3x3x3 of air inside around p(2, 2, 2)
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                    helper.setBlock(p(x, y, z), wall ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        BuddyBot bot = helper.spawn(DronesRegistry.BUDDY_BOT_ENTITY.get(), p(2, 2, 2));
        bot.setTier(1);
        bot.setOwnerUUID(UUID.randomUUID());
        bot.setEnergy(bot.getEnergyCapacity());
        bot.setMode(BuddyBot.Mode.STAY);
        bot.setLights(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(lamps(helper) >= 1, "a lamp in the dark box");
            helper.assertTrue(bot.getEnergy() < bot.getEnergyCapacity(), "the lamp costs FE");
            clear(helper);
        });
    }

    // ---------------------------------------------------------------- courier

    private static int count(ChestBlockEntity chest, Item item) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) n += chest.getItem(i).getCount();
        }
        return n;
    }

    private static CourierDrone courier(GameTestHelper helper, BlockPos from, BlockPos to) {
        CourierDrone drone = helper.spawn(DronesRegistry.COURIER_DRONE_ENTITY.get(), p(3, 3, 1));
        drone.setTier(1);
        drone.setHealth(drone.getMaxHealth());
        drone.setOwnerUUID(UUID.randomUUID());
        drone.setEnergy(drone.getEnergyCapacity());
        String dim = helper.getLevel().dimension().location().toString();
        drone.addRoute(new CourierRoute(dim, helper.absolutePos(from), Direction.UP, helper.absolutePos(to), Direction.UP));
        return drone;
    }

    private static void fillSource(ChestBlockEntity chest) {
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        chest.setItem(1, new ItemStack(Items.IRON_INGOT, 10));
        chest.setItem(2, new ItemStack(Items.DIAMOND, 5));
    }

    @GameTest(template = "drones_arena", batch = "dronesCourier", timeoutTicks = 600)
    public static void courierWhitelistMovesOnlyListedItems(GameTestHelper helper) {
        clear(helper);
        BlockPos a = p(1, 1, 1);
        BlockPos b = p(6, 1, 1);
        helper.setBlock(a, Blocks.CHEST);
        helper.setBlock(b, Blocks.CHEST);
        fillSource(helper.getBlockEntity(a));
        CourierDrone drone = courier(helper, a, b);
        drone.filter.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        int before = drone.getEnergy();
        helper.succeedWhen(() -> {
            ChestBlockEntity target = helper.getBlockEntity(b);
            ChestBlockEntity source = helper.getBlockEntity(a);
            helper.assertTrue(count(target, Items.IRON_INGOT) == 10, "all 10 iron ingots arrive, got " + count(target, Items.IRON_INGOT));
            helper.assertTrue(count(target, Items.COBBLESTONE) == 0 && count(target, Items.DIAMOND) == 0, "nothing else is moved");
            helper.assertTrue(count(source, Items.COBBLESTONE) == 20 && count(source, Items.DIAMOND) == 5, "the rest stays in the source");
            helper.assertTrue(drone.getEnergy() < before, "a trip costs energy");
            clear(helper);
        });
    }

    @GameTest(template = "drones_arena", batch = "dronesCourier2", timeoutTicks = 800)
    public static void courierBlacklistSkipsListedItems(GameTestHelper helper) {
        clear(helper);
        BlockPos a = p(1, 1, 1);
        BlockPos b = p(6, 1, 1);
        helper.setBlock(a, Blocks.CHEST);
        helper.setBlock(b, Blocks.CHEST);
        fillSource(helper.getBlockEntity(a));
        CourierDrone drone = courier(helper, a, b);
        drone.toggleWhitelist();
        drone.filter.setStackInSlot(0, new ItemStack(Items.COBBLESTONE));
        helper.succeedWhen(() -> {
            ChestBlockEntity target = helper.getBlockEntity(b);
            helper.assertTrue(count(target, Items.IRON_INGOT) == 10 && count(target, Items.DIAMOND) == 5, "iron and diamonds are carried over");
            helper.assertTrue(count(target, Items.COBBLESTONE) == 0, "the blacklisted cobblestone stays");
            clear(helper);
        });
    }

    @GameTest(template = "empty")
    public static void courierFilterModes(GameTestHelper helper) {
        CourierDrone drone = DronesRegistry.COURIER_DRONE_ENTITY.get().create(helper.getLevel());
        ItemStack cobble = new ItemStack(Items.COBBLESTONE);
        ItemStack deepslate = new ItemStack(Items.COBBLED_DEEPSLATE);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        helper.assertTrue(drone.passes(cobble) && drone.passes(iron), "an empty filter lets everything through");
        drone.filter.setStackInSlot(0, cobble.copy());
        helper.assertTrue(drone.passes(cobble) && !drone.passes(iron) && !drone.passes(deepslate), "whitelist: exact items only");
        drone.toggleMatchTags();
        helper.assertTrue(drone.passes(deepslate) && !drone.passes(iron), "tag matching: cobbled deepslate shares a tag with cobblestone");
        drone.toggleWhitelist();
        helper.assertTrue(!drone.passes(cobble) && !drone.passes(deepslate) && drone.passes(iron), "blacklist: everything but the listed items");
        helper.succeed();
    }

    @GameTest(template = "drones_arena", batch = "dronesLink")
    public static void courierLinkingNeedsInventoriesAndCapsAtFour(GameTestHelper helper) {
        helper.setBlock(p(0, 1, 0), Blocks.CHEST);
        helper.setBlock(p(1, 1, 0), Blocks.CHEST);
        helper.setBlock(p(2, 1, 0), Blocks.STONE);
        ServerLevel level = helper.getLevel();
        List<CourierRoute> routes = new ArrayList<>();
        CompoundTag holder = new CompoundTag();
        BlockPos a = helper.absolutePos(p(0, 1, 0));
        BlockPos b = helper.absolutePos(p(1, 1, 0));
        CourierRoute.click(holder, routes, level, helper.absolutePos(p(2, 1, 0)), Direction.UP);
        helper.assertTrue(!holder.contains("Pending"), "a stone block is not an inventory");
        CourierRoute.click(holder, routes, level, a, Direction.UP);
        helper.assertTrue(holder.contains("Pending") && routes.isEmpty(), "first click sets the source");
        CourierRoute.click(holder, routes, level, a, Direction.UP);
        helper.assertTrue(routes.isEmpty(), "source and target must differ");
        CourierRoute.click(holder, routes, level, b, Direction.UP);
        helper.assertTrue(routes.size() == 1 && routes.get(0).source().equals(a) && routes.get(0).target().equals(b), "second click links the route");
        for (int i = 0; i < 6; i++) {
            CourierRoute.click(holder, routes, level, a, Direction.UP);
            CourierRoute.click(holder, routes, level, b, Direction.UP);
        }
        helper.assertTrue(routes.size() == 4, "at most 4 routes, has " + routes.size());
        CompoundTag saved = new CompoundTag();
        CourierRoute.saveList(saved, routes);
        helper.assertTrue(CourierRoute.loadList(saved).equals(routes), "routes survive a save and load");
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = "drones_arena", batch = "dronesCourier3", timeoutTicks = 300)
    public static void courierIgnoresRoutesOutOfReach(GameTestHelper helper) {
        clear(helper);
        BlockPos a = p(1, 1, 1);
        helper.setBlock(a, Blocks.CHEST);
        fillSource(helper.getBlockEntity(a));
        CourierDrone drone = helper.spawn(DronesRegistry.COURIER_DRONE_ENTITY.get(), p(3, 3, 1));
        drone.setTier(1);
        drone.setOwnerUUID(UUID.randomUUID());
        drone.setEnergy(drone.getEnergyCapacity());
        String dim = helper.getLevel().dimension().location().toString();
        BlockPos far = helper.absolutePos(a).offset(4000, 0, 4000);
        drone.addRoute(new CourierRoute(dim, helper.absolutePos(a), Direction.UP, far, Direction.UP));
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(drone.trips == 0, "an unloaded or too distant route is skipped");
            helper.assertTrue(count(helper.getBlockEntity(a), Items.IRON_INGOT) == 10, "nothing was taken");
            clear(helper);
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- Hauler Drone

    /** Clears the arena and lays a stone floor at y 0 (x, z 0..8). */
    private static void floor(GameTestHelper helper) {
        clear(helper);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) helper.setBlock(p(x, 0, z), Blocks.STONE);
        }
    }

    private static ItemStack haulerItem(int tier, int energy) {
        ItemStack stack = new ItemStack(tier >= 2 ? DronesRegistry.HAULER_DRONE_MK2.get() : DronesRegistry.HAULER_DRONE.get());
        ItemEnergy.set(stack, energy);
        return stack;
    }

    private static HaulerDrone hauler(GameTestHelper helper, int tier, int x, int y, int z) {
        HaulerDrone drone = helper.spawn(DronesRegistry.HAULER_DRONE_ENTITY.get(), p(x, y, z));
        drone.setTier(tier);
        drone.setHealth(drone.getMaxHealth());
        drone.setOwnerUUID(UUID.randomUUID());
        drone.setEnergy(drone.getEnergyCapacity());
        return drone;
    }

    private static String key(@org.jetbrains.annotations.Nullable Component c) {
        return c != null && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    /** Item on a villager: the drone deploys, the villager hangs under it without AI; an empty-hand click sets it down on the floor. */
    @GameTest(template = "drones_arena", batch = "dronesHauler1", timeoutTicks = 200)
    public static void haulerCarriesAndReleasesVillager(GameTestHelper helper) {
        floor(helper);
        Villager villager = helper.spawn(EntityType.VILLAGER, p(3, 1, 3));
        villager.setCustomName(Component.literal("Bob"));
        villager.setVillagerData(villager.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(2));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(p(3, 1, 6));
        player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        ItemStack stack = haulerItem(1, 50_000);
        helper.assertTrue(HaulerDrone.capture(player, stack, villager), "a Mk1 takes a villager");
        helper.assertTrue(villager.getVehicle() instanceof HaulerDrone, "the villager rides the drone");
        HaulerDrone drone = (HaulerDrone) villager.getVehicle();
        helper.assertTrue(villager.isNoAi(), "no AI while carried");
        helper.assertTrue(drone.getEnergy() == 50_000 && drone.tier() == 1, "energy and tier come from the item");
        helper.assertTrue(player.getUUID().equals(drone.getOwnerUUID()), "the player owns the drone");
        double floorY = helper.absolutePos(p(0, 1, 0)).getY();
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(villager.isPassenger(), "still carried");
            helper.assertTrue(villager.getY() < drone.getY() - villager.getBbHeight() + 0.01, "the villager hangs under the drone");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            drone.interactFrom(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(!villager.isPassenger() && !drone.isCarrying(), "released");
            helper.assertTrue(!villager.isNoAi(), "AI back after release");
            helper.assertTrue(villager.isAlive() && Math.abs(villager.getY() - floorY) < 0.01, "set down on the floor, y " + villager.getY());
            helper.assertTrue(villager.getCustomName() != null && villager.getCustomName().getString().equals("Bob"), "name kept");
            helper.assertTrue(villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.FARMER
                    && villager.getVillagerData().getLevel() == 2, "profession kept");
            helper.assertTrue(drone.isAlive(), "the drone stays");
            drone.discard();
            villager.discard();
            player.discard();
            clear(helper);
            helper.succeed();
        });
    }

    /** Mk1 refuses hostile and Iron Golem sized mobs, Mk2 takes them. */
    @GameTest(template = "drones_arena", batch = "dronesHauler2", timeoutTicks = 100)
    public static void haulerMk1RefusesZombieMk2TakesIt(GameTestHelper helper) {
        floor(helper);
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, p(3, 1, 3));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack mk1 = haulerItem(1, 50_000);
        helper.assertTrue(key(HaulerDrone.refusal(zombie, player, 1)).equals("message.robotica.hauler.hostile"), "Mk1: hostile mobs need a Mk2");
        helper.assertTrue(!HaulerDrone.capture(player, mk1, zombie) && !zombie.isPassenger() && mk1.getCount() == 1, "Mk1 refuses the zombie");
        var golem = EntityType.IRON_GOLEM.create(helper.getLevel());
        helper.assertTrue(key(HaulerDrone.refusal(golem, player, 1)).equals("message.robotica.hauler.too_big_mk1"), "an Iron Golem is too big for Mk1");
        helper.assertTrue(HaulerDrone.refusal(golem, player, 2) == null, "Mk2 takes an Iron Golem");
        helper.assertTrue(HaulerDrone.refusal(EntityType.RAVAGER.create(helper.getLevel()), player, 2) == null, "Mk2 takes a Ravager");
        helper.assertTrue(HaulerDrone.refusal(EntityType.HORSE.create(helper.getLevel()), player, 1) == null, "Mk1 takes a horse");
        helper.assertTrue(HaulerDrone.refusal(EntityType.GHAST.create(helper.getLevel()), player, 2) != null, "a Ghast is too big");
        ItemStack mk2 = haulerItem(2, 50_000);
        helper.assertTrue(HaulerDrone.capture(player, mk2, zombie), "Mk2 takes the zombie");
        helper.assertTrue(zombie.getVehicle() instanceof HaulerDrone h && h.tier() == 2 && zombie.isNoAi(), "carried by a Mk2");
        HaulerDrone drone = (HaulerDrone) zombie.getVehicle();
        helper.assertTrue(HaulerDrone.capture(player, haulerItem(2, 50_000), zombie) == false, "a carried mob is not taken twice");
        drone.releaseCargo();
        helper.assertTrue(!zombie.isNoAi(), "AI back");
        drone.discard();
        zombie.discard();
        player.discard();
        clear(helper);
        helper.succeed();
    }

    /** Players, bosses, blacklisted types, other players' pets and drones are never taken; your own pet is. */
    @GameTest(template = "empty")
    public static void haulerRefusesForbiddenMobs(GameTestHelper helper) {
        var level = helper.getLevel();
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var wolf = EntityType.WOLF.create(level);
        wolf.setTame(true, false);
        wolf.setOwnerUUID(UUID.randomUUID());
        helper.assertTrue(key(HaulerDrone.refusal(wolf, player, 2)).equals("message.robotica.hauler.cant"), "another player's wolf is refused");
        wolf.setOwnerUUID(player.getUUID());
        helper.assertTrue(HaulerDrone.refusal(wolf, player, 1) == null, "your own wolf is fine");
        helper.assertTrue(HaulerDrone.refusal(EntityType.WITHER.create(level), player, 2) != null, "never the Wither");
        helper.assertTrue(HaulerDrone.refusal(EntityType.WARDEN.create(level), player, 2) != null, "never the Warden");
        helper.assertTrue(HaulerDrone.refusal(com.arno.robotica.boss.BossRegistry.SCRAP_COLOSSUS.get().create(level), player, 2) != null, "never a Robotica boss");
        helper.assertTrue(EntityType.ELDER_GUARDIAN.is(HaulerDrone.BLACKLIST), "bosses are in robotica:hauler_blacklist");
        helper.assertTrue(HaulerDrone.refusal(helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL), player, 2) != null, "never a player");
        helper.assertTrue(HaulerDrone.refusal(DronesRegistry.BUDDY_BOT_ENTITY.get().create(level), player, 2) != null, "never a drone");
        var rancher = com.arno.robotica.automation.rancher.RancherContent.RANCHER_ENTITY.get().create(level);
        rancher.setOwnerUUID(UUID.randomUUID());
        helper.assertTrue(HaulerDrone.refusal(rancher, player, 2) != null, "never another player's Rancher");
        rancher.setOwnerUUID(player.getUUID());
        helper.assertTrue(HaulerDrone.refusal(rancher, player, 2) == null, "your own Rancher is fine");
        helper.assertTrue(rancher.fireImmune(), "a Rancher is fire immune");
        helper.succeed();
    }

    /** The carried mob is saved inside the drone (vanilla Passengers) and comes back riding it, still without AI. */
    @GameTest(template = "drones_arena", batch = "dronesHauler3", timeoutTicks = 100)
    public static void haulerCargoSurvivesSaveAndLoad(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawn(EntityType.VILLAGER, p(3, 1, 3));
        villager.setCustomName(Component.literal("Carried"));
        HaulerDrone drone = hauler(helper, 1, 3, 4, 3);
        helper.assertTrue(drone.grab(villager), "grabbed");
        CompoundTag tag = new CompoundTag();
        helper.assertTrue(drone.save(tag) && tag.contains("Passengers"), "the drone saves its passenger");
        helper.assertTrue(!villager.save(new CompoundTag()), "the passenger is not saved on its own");
        drone.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        villager.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(villager.isNoAi(), "unloading does not wake the mob");
        net.minecraft.world.entity.Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
        helper.assertTrue(loaded instanceof HaulerDrone, "the drone loads");
        helper.assertTrue(level.tryAddFreshEntityWithPassengers(loaded), "added back to the level");
        HaulerDrone again = (HaulerDrone) loaded;
        helper.assertTrue(again.cargo() instanceof Villager v && v.getCustomName() != null && v.getCustomName().getString().equals("Carried"),
                "the villager comes back riding the drone");
        Villager back = (Villager) again.cargo();
        helper.assertTrue(back.isNoAi() && back.getPersistentData().contains(HaulerDrone.PREV_NO_AI), "still without AI, old flag kept");
        again.releaseCargo();
        helper.assertTrue(!back.isNoAi() && !back.isPassenger(), "released after loading");
        again.discard();
        back.discard();
        clear(helper);
        helper.succeed();
    }

    /** A destroyed drone sets the mob down alive on the floor, AI on. */
    @GameTest(template = "drones_arena", batch = "dronesHauler4", timeoutTicks = 100)
    public static void haulerDeathSetsMobDown(GameTestHelper helper) {
        floor(helper);
        var cow = helper.spawn(EntityType.COW, p(3, 1, 3));
        HaulerDrone drone = hauler(helper, 1, 3, 4, 3);
        helper.assertTrue(drone.grab(cow) && cow.isNoAi(), "grabbed");
        double floorY = helper.absolutePos(p(0, 1, 0)).getY();
        helper.runAfterDelay(3, () -> {
            drone.kill();
            helper.assertTrue(!cow.isPassenger() && cow.isAlive() && !cow.isNoAi(), "the cow is set down alive with AI");
            helper.assertTrue(Math.abs(cow.getY() - floorY) < 0.01, "on the floor, y " + cow.getY());
            cow.discard();
            helper.killAllEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class);
            clear(helper);
            helper.succeed();
        });
    }

    /** No FE while empty, fePerTick while carrying; an empty buffer hovers over lava and sets the mob down once there is safe ground. */
    @GameTest(template = "drones_arena", batch = "dronesHauler5", timeoutTicks = 200)
    public static void haulerUsesEnergyOnlyWhileCarrying(GameTestHelper helper) {
        floor(helper);
        HaulerDrone drone = hauler(helper, 1, 3, 5, 3);
        drone.setEnergy(10_000);
        var cow = helper.spawn(EntityType.COW, p(3, 1, 3));
        int[] start = new int[1];
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(drone.getEnergy() == 10_000, "an empty drone uses no FE, has " + drone.getEnergy());
                    helper.assertTrue(drone.grab(cow), "grabbed");
                    start[0] = drone.getEnergy();
                })
                .thenExecuteAfter(20, () -> {
                    int spent = start[0] - drone.getEnergy();
                    helper.assertTrue(drone.ticksCarried > 0 && spent == drone.ticksCarried * drone.fePerTick(),
                            "carrying costs " + drone.fePerTick() + " FE/t, spent " + spent + " in " + drone.ticksCarried + " ticks");
                    helper.assertTrue(drone.fePerTick() == CoreConfig.scaleEnergy(DronesConfig.haulerFePerTick()), "Mk1 rate");
                    for (int x = 0; x <= 6; x++) {
                        for (int z = 0; z <= 6; z++) helper.setBlock(p(x, 1, z), Blocks.LAVA);
                    }
                    drone.setEnergy(0);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(cow.getVehicle() == drone && cow.isNoAi() && cow.isAlive(), "out of FE over lava: the cow stays carried");
                    for (int x = 0; x <= 8; x++) {
                        for (int z = 0; z <= 8; z++) helper.setBlock(p(x, 1, z), Blocks.AIR);
                    }
                })
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(!cow.isPassenger() && !cow.isNoAi() && cow.isAlive(), "out of FE over safe ground: the cow is set down");
                    drone.discard();
                    cow.discard();
                    clear(helper);
                })
                .thenSucceed();
    }
}
