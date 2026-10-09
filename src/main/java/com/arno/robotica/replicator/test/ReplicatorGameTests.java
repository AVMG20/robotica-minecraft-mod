package com.arno.robotica.replicator.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.core.upgrade.UpgradeKind;
import com.arno.robotica.replicator.ReplicatorRegistry;
import com.arno.robotica.replicator.block.ReplicatorControllerBlock;
import com.arno.robotica.replicator.block.ReplicatorControllerBlockEntity;
import com.arno.robotica.replicator.item.EssenceVialItem;
import com.arno.robotica.replicator.logic.Essence;
import com.arno.robotica.replicator.logic.Harvest;
import com.arno.robotica.replicator.logic.ReplicatorStructure;
import com.arno.robotica.replicator.menu.ReplicatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** Headless tests of the replicator module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class ReplicatorGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 1, 0);
    private static final BlockPos CENTER = new BlockPos(1, 1, 1);
    private static final BlockPos GLASS = new BlockPos(0, 0, 0);

    /** Fills the whole 3x3x3 with frames, the controller in the middle of the north face, optionally one glass. */
    private static void buildShell(GameTestHelper helper, boolean withGlass) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(CENTER)) continue;
                    if (pos.equals(CONTROLLER)) {
                        helper.setBlock(pos, ReplicatorRegistry.REPLICATOR_CONTROLLER.get().defaultBlockState()
                                .setValue(ReplicatorControllerBlock.FACING, Direction.NORTH));
                    } else if (withGlass && pos.equals(GLASS)) {
                        helper.setBlock(pos, ReplicatorRegistry.REPLICATOR_GLASS.get());
                    } else {
                        helper.setBlock(pos, ReplicatorRegistry.REPLICATOR_FRAME.get());
                    }
                }
            }
        }
    }

    private static ReplicatorStructure.Status check(GameTestHelper helper) {
        return ReplicatorStructure.validate(helper.getLevel(), helper.absolutePos(CONTROLLER), Direction.NORTH);
    }

    // ---- vial ----

    /** Binding, completion, wrong mobs, bosses and the data components, on bare item stacks. */
    @GameTest(template = "empty")
    public static void vialBindsAndCompletes(GameTestHelper helper) {
        ItemStack vial = new ItemStack(ReplicatorRegistry.ESSENCE_VIAL.get());
        helper.assertTrue(vial.getMaxStackSize() == 16, "an empty vial stacks to 16");
        helper.assertTrue(!Essence.isBound(vial) && Essence.samples(vial) == 0, "a new vial is empty");

        helper.assertTrue(Essence.sample(vial, EntityType.ZOMBIE) == Essence.Result.BOUND, "first sample binds");
        helper.assertTrue(Essence.type(vial) == EntityType.ZOMBIE && Essence.samples(vial) == 1, "bound to zombie with 1 sample");
        helper.assertTrue(vial.getMaxStackSize() == 1, "a bound vial no longer stacks");
        helper.assertTrue(!vial.getItem().isFoil(vial), "no glint before it is complete");

        helper.assertTrue(Essence.sample(vial, EntityType.SKELETON) == Essence.Result.WRONG_TYPE, "another mob is refused");
        helper.assertTrue(Essence.samples(vial) == 1, "a refused sample changes nothing");

        for (int i = 2; i < Essence.SAMPLES_REQUIRED; i++) {
            helper.assertTrue(Essence.sample(vial, EntityType.ZOMBIE) == Essence.Result.SAMPLED, "sample " + i);
        }
        helper.assertTrue(!Essence.isComplete(vial) && !Essence.isUsable(vial), "7 samples are not enough");
        helper.assertTrue(Essence.sample(vial, EntityType.ZOMBIE) == Essence.Result.COMPLETED, "the 8th sample completes it");
        helper.assertTrue(Essence.isComplete(vial) && Essence.isUsable(vial), "complete vial is usable");
        helper.assertTrue(Essence.sample(vial, EntityType.ZOMBIE) == Essence.Result.ALREADY_COMPLETE, "no 9th sample");
        helper.assertTrue(Essence.samples(vial) == Essence.SAMPLES_REQUIRED, "stays at 8");
        helper.assertTrue(vial.getItem().isFoil(vial), "complete vial glints");
        helper.assertTrue(vial.getHoverName().getContents() instanceof TranslatableContents tc && tc.getKey().equals("item.robotica.essence_vial.bound"),
                "a bound vial is named after the mob");

        for (EntityType<?> boss : List.of(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.WARDEN, EntityType.ELDER_GUARDIAN)) {
            ItemStack fresh = new ItemStack(ReplicatorRegistry.ESSENCE_VIAL.get());
            helper.assertTrue(Essence.sample(fresh, boss) == Essence.Result.REFUSED, "boss must be refused: " + boss);
            helper.assertTrue(!Essence.isBound(fresh), "a refused boss leaves the vial empty");
        }
        helper.assertTrue(!Essence.isHostile(helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 1)), "cows are not hostile");
        helper.assertTrue(Essence.isHostile(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1)), "zombies are hostile");
        helper.assertTrue(Essence.isOutputBlacklisted(new ItemStack(Items.NETHER_STAR)), "nether stars never come out");
        helper.assertTrue(!Essence.isOutputBlacklisted(new ItemStack(Items.ROTTEN_FLESH)), "rotten flesh is fine");
        helper.succeed();
    }

    /** Right-click on a zombie: binds one vial out of a stack of 3, 2 damage, cooldown stops a second sample. */
    @GameTest(template = "empty")
    public static void vialInteractionSplitsStackAndHasCooldown(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        float health = zombie.getHealth();
        ItemStack stack = new ItemStack(ReplicatorRegistry.ESSENCE_VIAL.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        InteractionResult first = stack.getItem().interactLivingEntity(stack, player, zombie, InteractionHand.MAIN_HAND);
        helper.assertTrue(first.consumesAction(), "sampling a zombie should work");
        helper.assertTrue(stack.getCount() == 2 && !Essence.isBound(stack), "two empty vials stay in the stack");
        ItemStack bound = null;
        for (ItemStack s : player.getInventory().items) {
            if (Essence.isBound(s)) bound = s;
        }
        helper.assertTrue(bound != null && Essence.samples(bound) == 1 && Essence.type(bound) == EntityType.ZOMBIE,
                "a bound vial with 1 sample went to the inventory");
        helper.assertTrue(zombie.getHealth() <= health - 0.9F * EssenceVialItem.SAMPLE_DAMAGE, "the zombie takes 2 damage (less its armour)");

        InteractionResult second = bound.getItem().interactLivingEntity(bound, player, zombie, InteractionHand.MAIN_HAND);
        helper.assertTrue(second.consumesAction() && Essence.samples(bound) == 1, "the cooldown blocks a second sample");
        helper.succeed();
    }

    // ---- structure ----

    @GameTest(template = "empty")
    public static void structureValidation(GameTestHelper helper) {
        buildShell(helper, false);
        helper.assertTrue(check(helper) == ReplicatorStructure.Status.NO_GLASS, "no glass yet, got " + check(helper));

        helper.setBlock(GLASS, ReplicatorRegistry.REPLICATOR_GLASS.get());
        helper.assertTrue(check(helper) == ReplicatorStructure.Status.FORMED, "should be formed, got " + check(helper));

        helper.setBlock(CENTER, Blocks.STONE);
        helper.assertTrue(check(helper) == ReplicatorStructure.Status.CENTER_BLOCKED, "centre must be empty, got " + check(helper));
        helper.setBlock(CENTER, Blocks.AIR);

        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        helper.assertTrue(check(helper) == ReplicatorStructure.Status.INCOMPLETE, "a hole is incomplete, got " + check(helper));
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.assertTrue(check(helper) == ReplicatorStructure.Status.INCOMPLETE, "a wrong block is incomplete, got " + check(helper));
        helper.setBlock(new BlockPos(2, 2, 2), ReplicatorRegistry.REPLICATOR_FRAME.get());
        helper.assertTrue(check(helper) == ReplicatorStructure.Status.FORMED, "repaired, got " + check(helper));
        helper.succeed();
    }

    /** The controller notices the structure on its own and shows it in the blockstate. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void controllerShowsFormedState(GameTestHelper helper) {
        buildShell(helper, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getBlockState(CONTROLLER).getValue(ReplicatorControllerBlock.FORMED), "blockstate should say formed");
            ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
            helper.assertTrue(be.isFormed(), "block entity should be formed");
        });
    }

    /** The glow outlasts a short stall (no flicker on a weak supply) and goes out a second after the work stops. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void glowOutlastsShortStall(GameTestHelper helper) {
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.ZOMBIE));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(helper.getBlockState(CONTROLLER).getValue(ReplicatorControllerBlock.LIT), "a working replicator glows");
            be.energy.setEnergy(0);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(helper.getBlockState(CONTROLLER).getValue(ReplicatorControllerBlock.LIT), "a short stall keeps the glow");
                helper.runAfterDelay(40, () -> {
                    helper.assertFalse(helper.getBlockState(CONTROLLER).getValue(ReplicatorControllerBlock.LIT), "a long stall turns it off");
                    helper.succeed();
                });
            });
        });
    }

    // ---- harvest ----

    /** Rolls a zombie's loot as a player kill. Rotten flesh must show up; boss items never do. */
    @GameTest(template = "empty")
    public static void harvestRollGivesRottenFlesh(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(CENTER);
        int flesh = 0;
        int xp = 0;
        for (int i = 0; i < 40; i++) {
            Harvest.Result result = Harvest.roll(level, EntityType.ZOMBIE, origin, null, 0);
            helper.assertTrue(result != null, "zombie loot must roll");
            for (ItemStack drop : result.drops()) {
                if (drop.is(Items.ROTTEN_FLESH)) flesh += drop.getCount();
                helper.assertTrue(!Essence.isOutputBlacklisted(drop), "blacklisted item came out: " + drop);
            }
            xp += result.xp();
        }
        helper.assertTrue(flesh > 0, "40 zombie rolls should drop rotten flesh");
        helper.assertTrue(xp > 0, "a player kill gives experience");

        int plain = 0;
        int looted = 0;
        for (int i = 0; i < 200; i++) {
            for (ItemStack drop : Harvest.roll(level, EntityType.ZOMBIE, origin, null, 0).drops()) if (drop.is(Items.ROTTEN_FLESH)) plain += drop.getCount();
            for (ItemStack drop : Harvest.roll(level, EntityType.ZOMBIE, origin, null, 3).drops()) if (drop.is(Items.ROTTEN_FLESH)) looted += drop.getCount();
        }
        helper.assertTrue(looted > plain, "looting III should beat no looting: " + looted + " vs " + plain);
        helper.succeed();
    }

    /** A whole machine: shell, vial, power, speed card, boost and looting: rotten flesh arrives in the output. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void replicatorHarvestsZombieIntoOutput(GameTestHelper helper) {
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.ZOMBIE));
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 3));
        be.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.FORTUNE, 3));
        be.boost.setStackInSlot(0, new ItemStack(CoreItems.PLASMA_ACTUATOR.get()));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        helper.assertTrue(be.speedMultiplier() == 8, "3 speed cards (x4) and the boost (x2) make x8, got " + be.speedMultiplier());
        helper.assertTrue(be.lootingLevel() == 3, "3 fortune cards are looting III");
        int energyBefore = be.energy.getEnergyStored();
        // At x20 the machine burns 7,000 FE/t, so a full 1M buffer only lasts two cycles. A zombie roll with
        // Looting III still drops no flesh 1 time in 18, so the test used to depend on luck: it stalled with an empty
        // buffer after two unlucky rolls. A generator keeps it running here (refills below half, so it never reads full).
        int[] spent = keepPowered(helper, be);

        helper.succeedWhen(() -> {
            helper.assertTrue(be.cycles() >= 1, "a cycle should have finished");
            helper.assertTrue(spent[0] > 0 || be.energy.getEnergyStored() < energyBefore, "cycles cost energy");
            IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CONTROLLER), null);
            helper.assertTrue(items != null, "output capability must exist");
            int flesh = 0;
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stack = items.getStackInSlot(i);
                if (stack.is(Items.ROTTEN_FLESH)) flesh += stack.getCount();
                helper.assertTrue(items.insertItem(i, new ItemStack(Items.DIRT), true).getCount() == 1, "the output accepts no insertions");
            }
            helper.assertTrue(flesh > 0, "rotten flesh should be in the output");
            ItemStack taken = items.extractItem(findSlot(items), 1, true);
            helper.assertTrue(!taken.isEmpty(), "output can be extracted");
        });
    }

    /**
     * Acts as a generator: tops the buffer up by half whenever it drops below half, so it never reads full again.
     * Returns a counter of the FE added.
     */
    private static int[] keepPowered(GameTestHelper helper, ReplicatorControllerBlockEntity be) {
        int[] added = new int[1];
        helper.onEachTick(() -> {
            int half = be.energy.getMaxEnergyStored() / 2;
            if (be.energy.getEnergyStored() < half) {
                added[0] += half;
                be.energy.setEnergy(be.energy.getEnergyStored() + half);
            }
        });
        return added;
    }

    private static int findSlot(IItemHandler items) {
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) return i;
        }
        return 0;
    }

    /** A full output pauses the machine and keeps the drops of the finished cycle instead of losing them. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullOutputPausesWithoutLosingDrops(GameTestHelper helper) {
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.ZOMBIE));
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 3));
        be.upgrades.setStackInSlot(1, CoreItems.cards(UpgradeKind.FORTUNE, 3));
        be.boost.setStackInSlot(0, new ItemStack(CoreItems.PLASMA_ACTUATOR.get()));
        for (int i = 0; i < be.output.getSlots(); i++) be.output.setStackInSlot(i, new ItemStack(Items.STONE, 64));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        keepPowered(helper, be); // two cycles per buffer could both roll no drops (same luck as the harvest test)

        helper.succeedWhen(() -> {
            helper.assertTrue(be.pendingCount() > 0, "drops of a finished cycle wait in the pending list");
            helper.assertTrue(be.pause() == ReplicatorControllerBlockEntity.Pause.OUTPUT_FULL, "the machine pauses, pause is " + be.pause());
        });
    }

    /** Age 3 mobs (blaze) wait for a Magma Core in the catalyst slot, Age 4 mobs (shulker) for an Antigrav Core. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void catalystGatesLadderMobs(GameTestHelper helper) {
        helper.assertTrue(Essence.requiredTier(EntityType.ZOMBIE) == 0, "zombies need no catalyst");
        helper.assertTrue(Essence.requiredTier(EntityType.BLAZE) == 3 && Essence.requiredTier(EntityType.GHAST) == 3
                && Essence.requiredTier(EntityType.WITHER_SKELETON) == 3 && Essence.requiredTier(EntityType.ENDERMAN) == 3, "tier 3 mobs");
        helper.assertTrue(Essence.requiredTier(EntityType.SHULKER) == 4, "shulkers are tier 4");
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.BLAZE));
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 3));
        be.boost.setStackInSlot(0, new ItemStack(CoreItems.PLASMA_ACTUATOR.get()));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        helper.assertTrue(be.catalyst.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "only cores fit the catalyst slot");

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(be.cycles() == 0, "a blaze must not be copied without a Magma Core");
            helper.assertTrue(be.pause() == ReplicatorControllerBlockEntity.Pause.NEEDS_MAGMA_CORE, "pause is " + be.pause());
            be.catalyst.setStackInSlot(0, new ItemStack(CoreItems.MAGMA_CORE.get()));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(be.cycles() >= 1, "with the core installed a cycle finishes");
            helper.assertTrue(!be.catalyst.getStackInSlot(0).isEmpty(), "the core is not consumed");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shulkerNeedsAntigravCore(GameTestHelper helper) {
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.SHULKER));
        be.catalyst.setStackInSlot(0, new ItemStack(CoreItems.MAGMA_CORE.get()));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        helper.succeedWhen(() -> {
            helper.assertTrue(be.catalystTier() == 3, "magma core is tier 3");
            helper.assertTrue(be.pause() == ReplicatorControllerBlockEntity.Pause.NEEDS_ANTIGRAV_CORE, "pause is " + be.pause());
        });
    }

    /** The cycle progress survives a save and load (chunk reload): activeType is rebuilt from the vial. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void progressSurvivesReload(GameTestHelper helper) {
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.ZOMBIE));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        int[] before = new int[1];
        helper.runAfterDelay(30, () -> {
            before[0] = be.progress();
            helper.assertTrue(before[0] > 10, "the machine should be working, progress " + before[0]);
            net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata(helper.getLevel().registryAccess());
            be.loadWithComponents(tag, helper.getLevel().registryAccess());
        });
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(be.progress() >= before[0], "progress must not reset after a reload, was " + before[0] + " now " + be.progress());
            helper.succeed();
        });
    }

    // ---- spawn ----

    /** Spawn mode puts the mob next to the controller; at the cap of 8 it waits. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void spawnModeSpawnsAndRespectsCap(GameTestHelper helper) {
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        be.setMode(ReplicatorControllerBlockEntity.Mode.SPAWN);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.SPIDER));
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 3));
        be.boost.setStackInSlot(0, new ItemStack(CoreItems.PLASMA_ACTUATOR.get()));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());
        ServerLevel level = helper.getLevel();
        AABB area = new AABB(helper.absolutePos(CENTER)).inflate(10);

        helper.succeedWhen(() -> {
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                helper.assertTrue(be.pause() == ReplicatorControllerBlockEntity.Pause.PEACEFUL, "peaceful stops hostile spawning");
                return;
            }
            List<Mob> spiders = level.getEntitiesOfClass(Mob.class, area, m -> m.getType() == EntityType.SPIDER);
            helper.assertTrue(!spiders.isEmpty(), "a spider should have been spawned");
            spiders.forEach(Mob::discard);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void spawnModeStopsAtEightNearby(GameTestHelper helper) {
        buildShell(helper, true);
        ReplicatorControllerBlockEntity be = (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
        ServerLevel level = helper.getLevel();
        AABB area = new AABB(helper.absolutePos(CENTER)).inflate(8);
        for (int i = 0; i < 8; i++) {
            Mob spider = helper.spawnWithNoFreeWill(EntityType.SPIDER, 1, 1, 1);
            spider.setPersistenceRequired();
        }
        int initial = level.getEntitiesOfClass(Mob.class, area, m -> m.getType() == EntityType.SPIDER).size();
        be.setMode(ReplicatorControllerBlockEntity.Mode.SPAWN);
        be.vial.setStackInSlot(0, Essence.completeVial(EntityType.SPIDER));
        be.upgrades.setStackInSlot(0, CoreItems.cards(UpgradeKind.SPEED, 3));
        be.boost.setStackInSlot(0, new ItemStack(CoreItems.PLASMA_ACTUATOR.get()));
        be.energy.setEnergy(be.energy.getMaxEnergyStored());

        helper.runAfterDelay(150, () -> {
            int count = level.getEntitiesOfClass(Mob.class, area, m -> m.getType() == EntityType.SPIDER).size();
            helper.assertTrue(count >= 8 && count <= initial, "still " + initial + " spiders, found " + count);
            helper.assertTrue(be.pause() == ReplicatorControllerBlockEntity.Pause.SPAWN_CAP || be.pause() == ReplicatorControllerBlockEntity.Pause.PEACEFUL,
                    "the machine waits, pause is " + be.pause());
            level.getEntitiesOfClass(Mob.class, area, m -> m.getType() == EntityType.SPIDER).forEach(Mob::discard);
            helper.succeed();
        });
    }

    // ---- experience, output and sides ----

    private static ReplicatorControllerBlockEntity placeController(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ReplicatorRegistry.REPLICATOR_CONTROLLER.get().defaultBlockState()
                .setValue(ReplicatorControllerBlock.FACING, Direction.NORTH));
        return (ReplicatorControllerBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    private static void setStoredXp(GameTestHelper helper, ReplicatorControllerBlockEntity be, int xp) {
        var registries = helper.getLevel().registryAccess();
        net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata(registries);
        tag.putInt("xp", xp);
        be.loadWithComponents(tag, registries);
    }

    /** The GUI claims exactly enough experience to reach the target level, or all of it, only from a player in reach. */
    @GameTest(template = "empty")
    public static void xpClaimToTargetLevel(GameTestHelper helper) {
        helper.assertTrue(ReplicatorControllerBlockEntity.xpForLevel(0) == 7 && ReplicatorControllerBlockEntity.xpForLevel(15) == 37
                && ReplicatorControllerBlockEntity.xpForLevel(30) == 112, "vanilla points per level");
        ReplicatorControllerBlockEntity be = placeController(helper);
        setStoredXp(helper, be, 5_000);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(CONTROLLER).south()));
        ReplicatorMenu menu = new ReplicatorMenu(1, player.getInventory(), be);

        helper.assertTrue(ReplicatorControllerBlockEntity.pointsToReach(player, 30) == 1_395, "0 -> 30 is 1,395 points");
        helper.assertTrue(menu.clickMenuButton(player, ReplicatorMenu.claimToLevel(30)), "claim to 30");
        helper.assertTrue(player.experienceLevel == 30, "player reached level 30, is " + player.experienceLevel);
        helper.assertTrue(be.xpStored() == 5_000 - 1_395, "1,395 points taken, left " + be.xpStored());

        menu.clickMenuButton(player, ReplicatorMenu.claimToLevel(30));
        helper.assertTrue(be.xpStored() == 5_000 - 1_395, "already level 30: nothing taken");

        player.giveExperiencePoints(50);
        long need = ReplicatorControllerBlockEntity.pointsToReach(player, 31);
        helper.assertTrue(need == 112 - 50, "half a level done: 62 points to 31, got " + need);
        menu.clickMenuButton(player, ReplicatorMenu.claimToLevel(31));
        helper.assertTrue(player.experienceLevel == 31 && be.xpStored() == 5_000 - 1_395 - 62, "exactly level 31, left " + be.xpStored());

        player.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(CONTROLLER)).add(0, 0, 20));
        helper.assertTrue(!menu.clickMenuButton(player, ReplicatorMenu.CLAIM_ALL), "out of reach: refused");
        helper.assertTrue(be.xpStored() == 5_000 - 1_395 - 62, "nothing taken out of reach");

        player.moveTo(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(CONTROLLER).south()));
        int before = player.totalExperience;
        helper.assertTrue(menu.clickMenuButton(player, ReplicatorMenu.CLAIM_ALL), "claim all");
        helper.assertTrue(be.xpStored() == 0 && player.totalExperience == before + 5_000 - 1_395 - 62, "all experience claimed");
        helper.succeed();
    }

    /** The output survives a save and load, also a save with a different slot count. */
    @GameTest(template = "empty")
    public static void outputSurvivesSaveLoad(GameTestHelper helper) {
        ReplicatorControllerBlockEntity be = placeController(helper);
        var registries = helper.getLevel().registryAccess();
        be.output.setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 12));
        be.output.setStackInSlot(17, new ItemStack(Items.BONE, 5));
        net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata(registries);
        be.output.setStackInSlot(0, ItemStack.EMPTY);
        be.output.setStackInSlot(17, ItemStack.EMPTY);
        be.loadWithComponents(tag, registries);
        helper.assertTrue(be.output.getStackInSlot(0).is(Items.ROTTEN_FLESH) && be.output.getStackInSlot(0).getCount() == 12, "slot 0 kept");
        helper.assertTrue(be.output.getStackInSlot(17).is(Items.BONE) && be.output.getStackInSlot(17).getCount() == 5, "slot 17 kept");

        tag.getCompound("output").putInt("Size", 20);
        be.loadWithComponents(tag, registries);
        helper.assertTrue(be.output.getSlots() == ReplicatorControllerBlockEntity.OUTPUT_SLOTS, "still 18 slots");
        helper.assertTrue(be.output.getStackInSlot(0).getCount() == 12 && be.output.getStackInSlot(17).getCount() == 5, "items kept with another saved size");
        helper.succeed();
    }

    /** Every face is output only by default; auto-eject pushes the output into a chest. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sidesAutoEjectOutput(GameTestHelper helper) {
        ReplicatorControllerBlockEntity be = placeController(helper);
        for (com.arno.robotica.core.side.RelativeSide side : com.arno.robotica.core.side.RelativeSide.values()) {
            helper.assertTrue(be.sides.mode(side) == com.arno.robotica.core.side.SideMode.OUTPUT, side + " is output");
        }
        IItemHandler top = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CONTROLLER), Direction.UP);
        helper.assertTrue(top != null && top.insertItem(0, new ItemStack(Items.DIRT), true).getCount() == 1, "no insertion on a face");
        helper.setBlock(CONTROLLER.above(), Blocks.CHEST);
        be.output.setStackInSlot(3, new ItemStack(Items.ROTTEN_FLESH, 8));
        be.sides.setAutoEject(true);
        helper.succeedWhen(() -> {
            IItemHandler chest = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CONTROLLER.above()), Direction.DOWN);
            helper.assertTrue(chest != null && chest.getStackInSlot(0).is(Items.ROTTEN_FLESH), "the chest got the output");
            helper.assertTrue(be.output.getStackInSlot(3).isEmpty(), "the output slot is empty");
        });
    }
}
