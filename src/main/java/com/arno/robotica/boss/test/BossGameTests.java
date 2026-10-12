package com.arno.robotica.boss.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import com.arno.robotica.boss.block.BossAltarBlock;
import com.arno.robotica.boss.block.BossAltarBlockEntity;
import com.arno.robotica.boss.BossLoot;
import com.arno.robotica.boss.entity.ForgeTyrant;
import com.arno.robotica.boss.entity.MagmaGlob;
import com.arno.robotica.boss.entity.NullPylon;
import com.arno.robotica.boss.entity.NullSentinel;
import com.arno.robotica.boss.entity.ScrapChunk;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import com.arno.robotica.boss.world.CinderForgePiece;
import com.arno.robotica.boss.world.CinderForgeStructure;
import com.arno.robotica.boss.world.NullRelayPiece;
import com.arno.robotica.boss.world.NullRelayStructure;
import com.arno.robotica.boss.world.RustedFoundryPiece;
import com.arno.robotica.boss.world.RustedFoundryStructure;
import com.arno.robotica.core.item.CoreItems;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Headless tests of the boss module: {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Robotica.MODID)
@PrefixGameTestTemplate(false)
public class BossGameTests {
    private static final BlockPos CENTRE = new BlockPos(4, 1, 4);

    /** Stone floor under the 9x9 arena so the Colossus stands still instead of falling out of the test area. */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        }
    }

    private static ScrapColossus colossus(GameTestHelper helper) {
        floor(helper);
        ScrapColossus boss = helper.spawn(BossRegistry.SCRAP_COLOSSUS.get(), CENTRE);
        boss.setHealth(boss.getMaxHealth());
        return boss;
    }

    /** Removes the boss, its drones and loose items, so later tests in the same spot start clean. */
    private static void cleanup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
        List<Entity> leftovers = level.getEntitiesOfClass(Entity.class, area,
                e -> e instanceof ScrapColossus || e instanceof ScrapDrone || e instanceof ItemEntity || e instanceof Husk
                        || e instanceof ScrapChunk || e instanceof ForgeTyrant || e instanceof MagmaGlob || e instanceof NullSentinel
                        || e instanceof NullPylon);
        leftovers.forEach(Entity::discard);
    }

    private static ForgeTyrant tyrant(GameTestHelper helper) {
        floor(helper);
        ForgeTyrant boss = helper.spawn(BossRegistry.FORGE_TYRANT.get(), CENTRE);
        boss.setHealth(boss.getMaxHealth());
        boss.setAttackCooldown(100_000);
        return boss;
    }

    /** A server player in the level that counts as survival (the vanilla mock player is always creative). */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /** A husk that stands still and never burns: a target or attacker for tests. */
    private static Husk dummy(GameTestHelper helper, BlockPos pos) {
        Husk husk = helper.spawn(EntityType.HUSK, pos);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        return husk;
    }

    /** A flare at the altar wakes one Colossus on top, sets the cooldown, and the altar refuses while it lives or cools down. */
    @GameTest(template = "boss_arena", batch = "bossAltar", timeoutTicks = 200)
    public static void altarFlareSpawnsBossAndSetsCooldown(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(CENTRE, BossRegistry.COLOSSUS_ALTAR.get());
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(CENTRE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        BossAltarBlock.Result first = BossAltarBlock.awaken(level, altarPos, null);
        helper.assertTrue(first == BossAltarBlock.Result.SPAWNED, "the first flare wakes the Colossus, got " + first);
        ScrapColossus boss = helper.findClosestEntity(BossRegistry.SCRAP_COLOSSUS.get(), CENTRE.getX(), CENTRE.getY() + 1, CENTRE.getZ(), 3.0);
        helper.assertTrue(boss != null && altarPos.equals(boss.altarPos()), "the Colossus stands on its altar");
        helper.assertTrue(boss.getY() >= altarPos.getY() + 1.0, "the Colossus spawns on top of the altar");
        BossAltarBlockEntity altar = (BossAltarBlockEntity) level.getBlockEntity(altarPos);
        helper.assertTrue(altar != null && altar.cooldownLeft(level.getGameTime()) == BossConfig.altarCooldownTicks(),
                "the altar cools down for the configured time");
        helper.assertTrue(!level.getBlockState(altarPos).getValue(BossAltarBlock.READY), "the altar goes dark while it cools down");

        BossAltarBlock.Result second = BossAltarBlock.awaken(level, altarPos, player);
        helper.assertTrue(second == BossAltarBlock.Result.BOSS_ALIVE, "one Colossus per altar, got " + second);
        boss.discard();
        BossAltarBlock.Result third = BossAltarBlock.awaken(level, altarPos, player);
        helper.assertTrue(third == BossAltarBlock.Result.COOLDOWN, "the cooldown holds after the Colossus is gone, got " + third);
        altar.resetCooldown();
        BossAltarBlock.Result fourth = BossAltarBlock.awaken(level, altarPos, player);
        helper.assertTrue(fourth == BossAltarBlock.Result.SPAWNED, "a cooled down altar works again, got " + fourth);
        cleanup(helper);
        helper.succeed();
    }

    /** The Colossus always drops a Servo Core, also when no player landed the kill. */
    @GameTest(template = "boss_arena", batch = "bossLoot", timeoutTicks = 200)
    public static void colossusDropsServoCore(GameTestHelper helper) {
        ScrapColossus boss = colossus(helper);
        helper.runAfterDelay(2, boss::kill);
        helper.succeedWhen(() -> {
            helper.assertItemEntityPresent(CoreItems.SERVO_CORE.get(), CENTRE.above(), 6.0);
            cleanup(helper);
        });
    }

    /** Below half health it enters phase 2 once: drones come and it overheats, taking double damage while the core is open. */
    @GameTest(template = "boss_arena", batch = "bossPhase", timeoutTicks = 200)
    public static void phaseTwoAtHalfHealth(GameTestHelper helper) {
        ScrapColossus boss = colossus(helper);
        // Only attacks with a living attacker hurt it; indirect magic also ignores armour, so the numbers stay exact.
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!boss.isPhaseTwo(), "a fresh Colossus starts in phase 1");
            boss.hurt(helper.getLevel().damageSources().indirectMagic(attacker, attacker), boss.getMaxHealth() * 0.55F);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(boss.isPhaseTwo(), "below half health is phase 2");
            helper.assertTrue(boss.isOverheating(), "phase 2 starts with an overheat");
            int drones = boss.minions().size();
            helper.assertTrue(drones >= 1 && drones <= BossConfig.minionCap(), "phase 2 calls drones up to the cap, has " + drones);
            float before = boss.getHealth();
            boss.hurt(helper.getLevel().damageSources().indirectMagic(attacker, attacker), 10.0F);
            float taken = before - boss.getHealth();
            helper.assertTrue(Math.abs(taken - 20.0F) < 0.01F, "an exposed core takes double damage, took " + taken);
            cleanup(helper);
            helper.succeed();
        });
    }

    /** The Rusted Foundry template loads, is 25x12x25 with a natural altar in the centre, and its structure is registered. */
    @GameTest(template = "empty")
    public static void foundryTemplateHasAltar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getStructureManager().get(RustedFoundryPiece.TEMPLATE);
        helper.assertTrue(template.isPresent(), "the rusted_foundry template loads");
        helper.assertTrue(template.get().getSize().equals(new Vec3i(25, 12, 25)), "the hall is 25x12x25, is " + template.get().getSize());
        var altars = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), BossRegistry.COLOSSUS_ALTAR.get());
        helper.assertTrue(altars.size() == 1, "exactly one altar, found " + altars.size());
        helper.assertTrue(altars.get(0).pos().equals(new BlockPos(RustedFoundryStructure.HALF, 1, RustedFoundryStructure.HALF)),
                "the altar sits in the centre, at " + altars.get(0).pos());
        helper.assertTrue(altars.get(0).state().getValue(BossAltarBlock.NATURAL), "the ruin's altar is marked natural");
        var chests = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
        helper.assertTrue(chests.size() == 1 && chests.get(0).nbt() != null
                && "robotica:chests/rusted_foundry".equals(chests.get(0).nbt().getString("LootTable")), "one loot chest with the foundry loot");
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure foundry = structures.get(Robotica.id("rusted_foundry"));
        helper.assertTrue(foundry instanceof RustedFoundryStructure, "robotica:rusted_foundry is registered with its own type");
        helper.succeed();
    }

    /** Calling drones again and again never puts more than the cap in the air. */
    @GameTest(template = "boss_arena", batch = "bossMinions", timeoutTicks = 200)
    public static void minionCapHolds(GameTestHelper helper) {
        ScrapColossus boss = colossus(helper);
        helper.runAfterDelay(2, () -> {
            for (int i = 0; i < 4; i++) boss.summonMinions();
            int cap = BossConfig.minionCap();
            helper.assertTrue(boss.minions().size() == cap, "drones stop at the cap of " + cap + ", has " + boss.minions().size());
            helper.assertTrue(boss.summonMinions() == 0, "a full squad calls no more drones");
            cleanup(helper);
            helper.succeed();
        });
    }

    /**
     * Boss in a hole: buried in stone, it takes no suffocation damage, and no other damage without a living attacker
     * (cactus, drowning, poison, unowned TNT). Attacks still hurt. A trap can never farm Servo Cores.
     */
    @GameTest(template = "boss_arena", batch = "bossTrap", timeoutTicks = 200)
    public static void colossusIgnoresTrapDamage(GameTestHelper helper) {
        ScrapColossus boss = colossus(helper);
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = helper.getLevel().damageSources();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!boss.hurt(damage.cactus(), 5.0F), "cactus does nothing");
            helper.assertTrue(!boss.hurt(damage.drown(), 5.0F), "drowning does nothing");
            helper.assertTrue(!boss.hurt(damage.magic(), 5.0F), "poison and other unowned magic does nothing");
            helper.assertTrue(!boss.hurt(damage.explosion(null, null), 5.0F), "unowned TNT does nothing");
            helper.assertTrue(!boss.hurt(damage.inWall(), 5.0F), "suffocation does nothing");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(), "still at full health");
            // bury it
            for (int x = 3; x <= 5; x++) {
                for (int y = 1; y <= 4; y++) {
                    for (int z = 3; z <= 5; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(boss.isAlive() && boss.isInWall(), "the Colossus is stuck in the stone");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(), "buried for 3 s and not a scratch, has " + boss.getHealth());
            helper.assertTrue(boss.hurt(damage.mobAttack(attacker), 5.0F), "a real attack still hurts");
            cleanup(helper);
            helper.succeed();
        });
    }

    /**
     * No safe spot at 3-6 blocks: a target it cannot reach in melee gets scrap thrown at it after a few seconds, also when
     * it stands closer than the normal 6 block throwing range.
     */
    @GameTest(template = "boss_arena", batch = "bossReach", timeoutTicks = 200)
    public static void colossusThrowsAtUnreachableTargets(GameTestHelper helper) {
        ScrapColossus boss = colossus(helper);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        Husk target = dummy(helper, new BlockPos(0, 1, 0));
        double dist = Math.sqrt(boss.distanceToSqr(target));
        helper.assertTrue(dist > 3.0 && dist < 6.0, "the target stands in the old blind spot, " + dist);
        helper.runAfterDelay(2, () -> {
            boss.setTarget(target);
            helper.assertTrue(!boss.canThrowAt(target), "no scrap right away at close range");
        });
        helper.runAfterDelay(2 + ScrapColossus.OUT_OF_REACH_TICKS + 10, () -> {
            helper.assertTrue(boss.getTarget() == target, "still on the same target");
            helper.assertTrue(boss.canThrowAt(target), "an unreachable close target gets scrap");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** A cooling altar cannot be mined (breaking and replacing it would skip the cooldown); a ready one can. */
    @GameTest(template = "boss_arena", batch = "bossAltarMine", timeoutTicks = 100)
    public static void coolingAltarCannotBeMined(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(CENTRE, BossRegistry.COLOSSUS_ALTAR.get());
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(CENTRE);
        BossAltarBlock.Result result = BossAltarBlock.awaken(level, altarPos, null);
        helper.assertTrue(result == BossAltarBlock.Result.SPAWNED, "woke the Colossus, got " + result);
        cleanup(helper);

        ServerPlayer miner = helper.makeMockServerPlayerInLevel();
        miner.setGameMode(GameType.SURVIVAL);
        miner.moveTo(altarPos.getX() + 0.5, altarPos.getY() + 1.0, altarPos.getZ() + 2.5);
        var state = level.getBlockState(altarPos);
        helper.assertTrue(state.getDestroyProgress(miner, level, altarPos) == 0.0F, "no mining progress while it cools down");
        miner.gameMode.destroyBlock(altarPos);
        helper.assertTrue(level.getBlockState(altarPos).is(BossRegistry.COLOSSUS_ALTAR.get()), "a forced break is refused too");

        ((BossAltarBlockEntity) level.getBlockEntity(altarPos)).resetCooldown();
        level.setBlock(altarPos, level.getBlockState(altarPos).setValue(BossAltarBlock.READY, true), 3);
        helper.assertTrue(level.getBlockState(altarPos).getDestroyProgress(miner, level, altarPos) > 0.0F, "a ready altar can be mined");
        miner.gameMode.destroyBlock(altarPos);
        helper.assertTrue(level.getBlockState(altarPos).isAir(), "and broken");
        cleanup(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ Forge Tyrant

    /** An Ignition Charge at a Forge Altar wakes one Forge Tyrant on top; the Signal Flare does not. */
    @GameTest(template = "boss_arena", batch = "tyrantAltar", timeoutTicks = 200)
    public static void forgeAltarSpawnsTyrant(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(CENTRE, BossRegistry.FORGE_ALTAR.get());
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(CENTRE);
        BossAltarBlock block = (BossAltarBlock) BossRegistry.FORGE_ALTAR.get();
        helper.assertTrue(new ItemStack(BossRegistry.IGNITION_CHARGE.get()).is(block.kind().summon().get()), "the Ignition Charge wakes it");
        helper.assertTrue(!new ItemStack(BossRegistry.SIGNAL_FLARE.get()).is(block.kind().summon().get()), "a Signal Flare does not");
        BossAltarBlock.Result first = BossAltarBlock.awaken(level, altarPos, null);
        helper.assertTrue(first == BossAltarBlock.Result.SPAWNED, "the charge wakes the Tyrant, got " + first);
        ForgeTyrant boss = helper.findClosestEntity(BossRegistry.FORGE_TYRANT.get(), CENTRE.getX(), CENTRE.getY() + 1, CENTRE.getZ(), 3.0);
        helper.assertTrue(boss != null && altarPos.equals(boss.altarPos()), "the Tyrant stands on its altar");
        helper.assertTrue(Math.abs(boss.getMaxHealth() - BossConfig.tyrantHealth()) < 0.01, "health comes from the config, has " + boss.getMaxHealth());
        helper.assertTrue(BossAltarBlock.awaken(level, altarPos, null) == BossAltarBlock.Result.BOSS_ALIVE, "one Tyrant per altar");
        cleanup(helper);
        helper.succeed();
    }

    /** Lava, fire, magma floors, suffocation and unowned damage do nothing; a living attacker's hit does. */
    @GameTest(template = "boss_arena", batch = "tyrantTrap", timeoutTicks = 100)
    public static void tyrantOnlyTakesDamageFromLivingAttackers(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = helper.getLevel().damageSources();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!boss.hurt(damage.lava(), 5.0F), "lava does nothing");
            helper.assertTrue(!boss.hurt(damage.inFire(), 5.0F), "fire does nothing");
            helper.assertTrue(!boss.hurt(damage.hotFloor(), 5.0F), "magma blocks do nothing");
            helper.assertTrue(!boss.hurt(damage.inWall(), 5.0F), "suffocation does nothing");
            helper.assertTrue(!boss.hurt(damage.cactus(), 5.0F), "cactus does nothing");
            helper.assertTrue(!boss.hurt(damage.magic(), 5.0F), "unowned magic does nothing");
            helper.assertTrue(!boss.hurt(damage.explosion(null, null), 5.0F), "unowned TNT does nothing");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(), "still at full health");
            helper.assertTrue(boss.hurt(damage.mobAttack(attacker), 5.0F), "a real attack hurts");
            helper.assertTrue(boss.getHealth() < boss.getMaxHealth(), "and takes health");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** A player kill drops the Magma Core at the killer, locked to them. */
    @GameTest(template = "boss_arena", batch = "tyrantLoot", timeoutTicks = 100)
    public static void tyrantDropsMagmaCoreToKiller(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        ServerPlayer killer = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        killer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.runAfterDelay(2, () -> {
            boss.setHealth(1.0F);
            boss.hurt(helper.getLevel().damageSources().playerAttack(killer), 100.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(boss.isDeadOrDying(), "the Tyrant is dead");
            List<ItemEntity> cores = helper.getLevel().getEntitiesOfClass(ItemEntity.class, killer.getBoundingBox().inflate(3.0),
                    e -> e.getItem().is(CoreItems.MAGMA_CORE.get()));
            if (cores.isEmpty() && killer.getInventory().contains(new ItemStack(CoreItems.MAGMA_CORE.get()))) {
                cleanup(helper);
                return;                                            // already picked up by the killer
            }
            helper.assertTrue(cores.size() == 1, "one Magma Core at the killer, found " + cores.size());
            helper.assertTrue(killer.getUUID().equals(cores.get(0).getTarget()), "the core is locked to the killer");
            cleanup(helper);
        });
    }

    /** While it vents it takes the configured extra damage; below half health it marks a ring of eruptions. */
    @GameTest(template = "boss_arena", batch = "tyrantVent", timeoutTicks = 100)
    public static void tyrantVentsAndEntersPhaseTwo(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = helper.getLevel().damageSources();
        helper.runAfterDelay(2, () -> {
            boss.startVent();
            helper.assertTrue(boss.isVenting(), "it vents");
            float before = boss.getHealth();
            boss.hurt(damage.indirectMagic(attacker, attacker), 10.0F);
            float taken = before - boss.getHealth();
            float expected = 10.0F * BossConfig.tyrantVentMultiplier();
            helper.assertTrue(Math.abs(taken - expected) < 0.01F, "venting takes " + expected + ", took " + taken);
            helper.assertTrue(!boss.isPhaseTwo(), "still phase 1");
            boss.hurt(damage.indirectMagic(attacker, attacker), boss.getMaxHealth() * 0.3F);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(boss.isPhaseTwo(), "below half health is phase 2");
            helper.assertTrue(boss.pendingEruptions() == 8, "phase 2 marks 8 eruptions around it, has " + boss.pendingEruptions());
            cleanup(helper);
            helper.succeed();
        });
    }

    /**
     * No safe spot: a target it cannot reach in melee gets mortar and eruptions after a few seconds, also at close range,
     * and an eruption under a target hurts it.
     */
    @GameTest(template = "boss_arena", batch = "tyrantReach", timeoutTicks = 200)
    public static void tyrantEruptsUnderUnreachableTargets(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        Husk target = dummy(helper, new BlockPos(0, 1, 0));
        helper.runAfterDelay(2, () -> {
            boss.setTarget(target);
            helper.assertTrue(!boss.attackOptions(target).contains(ForgeTyrant.Action.MORTAR_WINDUP), "no mortar at close range right away");
        });
        helper.runAfterDelay(2 + ForgeTyrant.OUT_OF_REACH_TICKS + 10, () -> {
            helper.assertTrue(boss.getTarget() == target, "still on the same target");
            var options = boss.attackOptions(target);
            helper.assertTrue(options.contains(ForgeTyrant.Action.MORTAR_WINDUP) && options.contains(ForgeTyrant.Action.ERUPT_WINDUP),
                    "an unreachable close target gets mortar and eruptions, options " + options);
            helper.assertTrue(boss.erupt(target) >= 3, "eruptions mark the target and around it");
        });
        helper.runAfterDelay(2 + ForgeTyrant.OUT_OF_REACH_TICKS + 10 + ForgeTyrant.ERUPT_DELAY + 5, () -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the eruption hurt the target");
            helper.assertTrue(boss.pendingEruptions() == 0, "every marked spot erupted");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** The Cinder Forge template loads with a natural Forge Altar in the centre and a loot chest; its structure is registered. */
    @GameTest(template = "empty")
    public static void cinderForgeTemplateHasAltar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getStructureManager().get(CinderForgePiece.TEMPLATE);
        helper.assertTrue(template.isPresent(), "the cinder_forge template loads");
        int size = CinderForgeStructure.SIZE;
        helper.assertTrue(template.get().getSize().getX() == size && template.get().getSize().getZ() == size,
                "the hall is " + size + " wide, is " + template.get().getSize());
        var altars = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), BossRegistry.FORGE_ALTAR.get());
        helper.assertTrue(altars.size() == 1, "exactly one altar, found " + altars.size());
        helper.assertTrue(altars.get(0).pos().equals(new BlockPos(CinderForgeStructure.HALF, 1, CinderForgeStructure.HALF)),
                "the altar sits in the centre, at " + altars.get(0).pos());
        helper.assertTrue(altars.get(0).state().getValue(BossAltarBlock.NATURAL), "the ruin's altar is marked natural");
        var chests = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
        helper.assertTrue(chests.size() == 1 && chests.get(0).nbt() != null
                && "robotica:chests/cinder_forge".equals(chests.get(0).nbt().getString("LootTable")), "one loot chest with the forge loot");
        Structure forge = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Robotica.id("cinder_forge"));
        helper.assertTrue(forge instanceof CinderForgeStructure, "robotica:cinder_forge is registered with its own type");
        helper.assertTrue(level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).containsKey(Robotica.id("cinder_forge")),
                "robotica:cinder_forge has a structure set");
        helper.succeed();
    }

    /**
     * Loot locked to the killer is released after the lock time or as soon as the killer is offline, so it never sits
     * there for nobody until it despawns.
     */
    @GameTest(template = "boss_arena", batch = "bossLootLock", timeoutTicks = 100)
    public static void lockedLootIsReleased(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer killer = helper.makeMockServerPlayerInLevel();
        long now = level.getGameTime();
        BlockPos at = helper.absolutePos(CENTRE.above());
        ItemEntity mine = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(CoreItems.SERVO_CORE.get()));
        ItemEntity offline = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(CoreItems.SERVO_CORE.get()));
        ItemEntity expired = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(CoreItems.SERVO_CORE.get()));
        BossLoot.lock(mine, killer.getUUID(), now);
        BossLoot.lock(offline, UUID.randomUUID(), now);
        BossLoot.lock(expired, killer.getUUID(), now - BossConfig.lootLockTicks());
        for (ItemEntity item : List.of(mine, offline, expired)) {
            item.setNeverPickUp();
            level.addFreshEntity(item);
        }
        BossLoot.releaseDue(level.getServer());
        helper.assertTrue(killer.getUUID().equals(mine.getTarget()), "the online killer keeps the lock");
        helper.assertTrue(offline.getTarget() == null, "a logged out killer's loot is free");
        helper.assertTrue(expired.getTarget() == null, "after the lock time the loot is free");
        cleanup(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ 0.5 audit fixes

    /**
     * No bow cheese: a target out past its leash gets eruptions (and mortar in sight) out to the follow range once it has
     * been out of reach a while, and hits from attackers further than the configured distance do nothing.
     */
    @GameTest(template = "boss_arena", batch = "tyrantFarReach", timeoutTicks = 200)
    public static void tyrantReachesFarTargetsAndIgnoresSnipers(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        Husk far = dummy(helper, CENTRE.offset(0, 0, 35));
        Husk near = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = helper.getLevel().damageSources();
        helper.runAfterDelay(2, () -> {
            double d = boss.distanceTo(far);
            helper.assertTrue(d > ForgeTyrant.MORTAR_RANGE && d > BossConfig.maxAttackerDistance()
                    && d < boss.getAttributeValue(Attributes.FOLLOW_RANGE), "the sniper stands past the normal ranges, " + d);
            boss.setTarget(far);
            helper.assertTrue(boss.attackOptions(far).isEmpty(), "nothing reaches that far right away");
            helper.assertTrue(!boss.hurt(damage.mobAttack(far), 5.0F), "a hit from 35 blocks does nothing");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(), "still at full health");
        });
        helper.runAfterDelay(2 + ForgeTyrant.OUT_OF_REACH_TICKS + 10, () -> {
            helper.assertTrue(boss.getTarget() == far, "still on the sniper");
            var options = boss.attackOptions(far);
            helper.assertTrue(options.contains(ForgeTyrant.Action.ERUPT_WINDUP), "eruptions reach the sniper, options " + options);
            if (boss.hasLineOfSight(far)) {
                helper.assertTrue(options.contains(ForgeTyrant.Action.MORTAR_WINDUP), "and the mortar does in sight, options " + options);
            }
            helper.assertTrue(boss.hurt(damage.mobAttack(near), 5.0F), "a hit from close by hurts");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** The Colossus throws scrap at an unreachable target out past its normal 28 block range, up to the follow range. */
    @GameTest(template = "boss_arena", batch = "colossusFarReach", timeoutTicks = 200)
    public static void colossusReachesFarTargets(GameTestHelper helper) {
        ScrapColossus boss = colossus(helper);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        Husk far = dummy(helper, CENTRE.offset(0, 0, 34));
        helper.runAfterDelay(2, () -> {
            boss.setTarget(far);
            helper.assertTrue(!boss.canThrowAt(far), "no scrap past 28 blocks right away");
        });
        helper.runAfterDelay(2 + ScrapColossus.OUT_OF_REACH_TICKS + 10, () -> {
            helper.assertTrue(boss.getTarget() == far, "still on the same target");
            helper.assertTrue(boss.canThrowAt(far), "an unreachable far target gets scrap");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** The Tyrant paths over a strip of lava and takes lava as a place to walk to, like a strider. */
    @GameTest(template = "boss_arena", batch = "tyrantLava", timeoutTicks = 300)
    public static void tyrantPathsOverLava(GameTestHelper helper) {
        floor(helper);
        for (int x = 1; x <= 7; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 0, z), Blocks.LAVA);
            }
        }
        ForgeTyrant boss = helper.spawn(BossRegistry.FORGE_TYRANT.get(), new BlockPos(4, 1, 1));
        boss.setAttackCooldown(100_000);
        BlockPos dest = helper.absolutePos(new BlockPos(4, 1, 7));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(boss.getNavigation().isStableDestination(helper.absolutePos(new BlockPos(4, 0, 4))),
                    "lava counts as a place to walk to");
            var path = boss.getNavigation().createPath(dest, 0);
            helper.assertTrue(path != null && path.canReach(), "a path leads over the lava, got " + (path == null ? null
                    : path.getNodeCount() + " nodes to " + path.getTarget() + " from " + boss.blockPosition() + ", dest " + dest));
        });
        helper.onEachTick(() -> {
            if (boss.isAlive() && !dest.equals(boss.getNavigation().getTargetPos())) {
                boss.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 1.0);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(boss.getZ() > helper.absolutePos(new BlockPos(4, 1, 6)).getZ() + 0.4, "the Tyrant crossed the lava");
            for (int x = 1; x <= 7; x++) {
                for (int z = 3; z <= 5; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
            cleanup(helper);
        });
    }

    /**
     * Its attacks hit only players, their pets and what it fights: an eruption under its target spares a mob standing
     * next to it.
     */
    @GameTest(template = "boss_arena", batch = "tyrantBystander", timeoutTicks = 100)
    public static void tyrantSparesBystanders(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        Husk target = dummy(helper, new BlockPos(0, 1, 0));
        Husk bystander = dummy(helper, new BlockPos(1, 1, 0));
        helper.runAfterDelay(2, () -> {
            boss.setTarget(target);
            boss.erupt(target);
        });
        helper.runAfterDelay(2 + ForgeTyrant.ERUPT_DELAY + 5, () -> {
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the eruption hurt its target");
            helper.assertTrue(bystander.getHealth() == bystander.getMaxHealth(), "the mob next to it was spared");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** No other mob steals its attention from a player in range: it keeps on the player after a mob hits it. */
    @GameTest(template = "boss_arena", batch = "tyrantAggro", timeoutTicks = 100)
    public static void tyrantKeepsPlayerAggro(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        Husk mob = dummy(helper, new BlockPos(0, 1, 0));
        ServerPlayer player = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(8, 1, 8));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.runAfterDelay(2, () -> {
            boss.setTarget(player);
            boss.hurt(helper.getLevel().damageSources().mobAttack(mob), 1.0F);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(boss.getTarget() != mob, "a mob's hit does not pull it off the player");
            helper.getLevel().getServer().getPlayerList().remove(player);
            cleanup(helper);
            helper.succeed();
        });
    }

    /** With no player nearby for the configured time it heals. */
    @GameTest(template = "boss_arena", batch = "tyrantRegen", timeoutTicks = 100)
    public static void tyrantHealsWhenLeftAlone(GameTestHelper helper) {
        ForgeTyrant boss = tyrant(helper);
        boss.setHealth(100.0F);
        boss.setAloneTicks(BossConfig.regenDelayTicks());
        helper.runAfterDelay(45, () -> {
            boolean playerNear = helper.getLevel().getNearestPlayer(boss.getX(), boss.getY(), boss.getZ(), BossConfig.regenRadius(),
                    EntitySelector.NO_CREATIVE_OR_SPECTATOR) != null;
            if (!playerNear) helper.assertTrue(boss.getHealth() > 100.0F, "it heals when left alone, has " + boss.getHealth());
            cleanup(helper);
            helper.succeed();
        });
    }

    /** An altar refuses while a boss of its kind is alive close by, also one it did not wake (a moved altar). */
    @GameTest(template = "boss_arena", batch = "tyrantOneBoss", timeoutTicks = 100)
    public static void altarRefusesWithBossNearby(GameTestHelper helper) {
        floor(helper);
        ForgeTyrant other = helper.spawn(BossRegistry.FORGE_TYRANT.get(), new BlockPos(1, 1, 1));
        other.setAttackCooldown(100_000);
        helper.setBlock(new BlockPos(6, 1, 6), BossRegistry.FORGE_ALTAR.get());
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.runAfterDelay(2, () -> {
            BossAltarBlock.Result first = BossAltarBlock.awaken(level, altarPos, null);
            helper.assertTrue(first == BossAltarBlock.Result.BOSS_ALIVE, "a Tyrant nearby blocks the altar, got " + first);
            other.discard();
            BossAltarBlock.Result second = BossAltarBlock.awaken(level, altarPos, null);
            helper.assertTrue(second == BossAltarBlock.Result.SPAWNED, "with it gone the altar works, got " + second);
            cleanup(helper);
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ Null Sentinel

    private static NullSentinel sentinel(GameTestHelper helper) {
        floor(helper);
        NullSentinel boss = helper.spawn(BossRegistry.NULL_SENTINEL.get(), CENTRE);
        boss.setHealth(boss.getMaxHealth());
        boss.setAttackCooldown(100_000);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
        return boss;
    }

    /** A Null Key at a Null Altar wakes one Null Sentinel on top; it cannot be hurt until it has woken up. */
    @GameTest(template = "boss_arena", batch = "sentinelAltar", timeoutTicks = 200)
    public static void nullAltarWakesSentinel(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(CENTRE, BossRegistry.NULL_ALTAR.get());
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(CENTRE);
        BossAltarBlock block = (BossAltarBlock) BossRegistry.NULL_ALTAR.get();
        helper.assertTrue(new ItemStack(BossRegistry.NULL_KEY.get()).is(block.kind().summon().get()), "the Null Key wakes it");
        helper.assertTrue(!new ItemStack(BossRegistry.IGNITION_CHARGE.get()).is(block.kind().summon().get()), "an Ignition Charge does not");
        BossAltarBlock.Result first = BossAltarBlock.awaken(level, altarPos, null);
        helper.assertTrue(first == BossAltarBlock.Result.SPAWNED, "the key wakes the Sentinel, got " + first);
        NullSentinel boss = helper.findClosestEntity(BossRegistry.NULL_SENTINEL.get(), CENTRE.getX(), CENTRE.getY() + 1, CENTRE.getZ(), 3.0);
        helper.assertTrue(boss != null && altarPos.equals(boss.altarPos()), "the Sentinel stands on its altar");
        helper.assertTrue(Math.abs(boss.getMaxHealth() - BossConfig.sentinelHealth()) < 0.01, "health comes from the config, has " + boss.getMaxHealth());
        helper.assertTrue(boss.action() == NullSentinel.Action.INTRO, "it wakes up first");
        helper.assertTrue(BossAltarBlock.awaken(level, altarPos, null) == BossAltarBlock.Result.BOSS_ALIVE, "one Sentinel per altar");
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = level.damageSources();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!boss.hurt(damage.mobAttack(attacker), 10.0F), "it cannot be hurt while it wakes");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(), "still at full health");
        });
        helper.runAfterDelay(NullSentinel.INTRO_TICKS + 5, () -> {
            helper.assertTrue(boss.action() != NullSentinel.Action.INTRO, "awake after the intro");
            helper.assertTrue(boss.hurt(damage.mobAttack(attacker), 10.0F), "now a real attack hurts");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** Lava, fire, suffocation and unowned damage do nothing; a living attacker's hit does. */
    @GameTest(template = "boss_arena", batch = "sentinelTrap", timeoutTicks = 100)
    public static void sentinelOnlyTakesDamageFromLivingAttackers(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = helper.getLevel().damageSources();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!boss.hurt(damage.lava(), 5.0F), "lava does nothing");
            helper.assertTrue(!boss.hurt(damage.inWall(), 5.0F), "suffocation does nothing");
            helper.assertTrue(!boss.hurt(damage.magic(), 5.0F), "unowned magic does nothing");
            helper.assertTrue(!boss.hurt(damage.explosion(null, null), 5.0F), "unowned TNT does nothing");
            helper.assertTrue(boss.getHealth() == boss.getMaxHealth(), "still at full health");
            helper.assertTrue(boss.hurt(damage.mobAttack(attacker), 5.0F), "a real attack hurts");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** A player kill drops the Antigrav Core at the killer, locked to them. */
    @GameTest(template = "boss_arena", batch = "sentinelLoot", timeoutTicks = 200)
    public static void sentinelDropsAntigravCoreToKiller(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        ServerPlayer killer = helper.makeMockServerPlayerInLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        killer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.runAfterDelay(2, () -> {
            boss.setHealth(1.0F);
            boss.hurt(helper.getLevel().damageSources().playerAttack(killer), 100.0F);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(boss.isDeadOrDying(), "the Sentinel is dead");
            List<ItemEntity> cores = helper.getLevel().getEntitiesOfClass(ItemEntity.class, killer.getBoundingBox().inflate(3.0),
                    e -> e.getItem().is(CoreItems.ANTIGRAV_CORE.get()));
            if (cores.isEmpty() && killer.getInventory().contains(new ItemStack(CoreItems.ANTIGRAV_CORE.get()))) {
                cleanup(helper);
                return;                                            // already picked up by the killer
            }
            helper.assertTrue(cores.size() == 1, "one Antigrav Core at the killer, found " + cores.size());
            helper.assertTrue(killer.getUUID().equals(cores.get(0).getTarget()), "the core is locked to the killer");
            cleanup(helper);
        });
    }

    /** Its death is a slow collapse: it stays a few seconds, then it is gone. */
    @GameTest(template = "boss_arena", batch = "sentinelDeath", timeoutTicks = 200)
    public static void sentinelCollapsesThenVanishes(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        helper.runAfterDelay(2, boss::kill);
        helper.runAfterDelay(2 + NullSentinel.DEATH_TICKS / 2, () -> {
            helper.assertTrue(!boss.isRemoved() && boss.dyingTicks() > 0, "it is still collapsing");
            helper.assertItemEntityPresent(CoreItems.ANTIGRAV_CORE.get(), CENTRE.above(), 6.0);
        });
        helper.runAfterDelay(2 + NullSentinel.DEATH_TICKS + 5, () -> {
            helper.assertTrue(boss.isRemoved(), "gone after the collapse");
            cleanup(helper);
            helper.succeed();
        });
    }

    /**
     * At 60 % it shifts to phase 2 (untouchable while it does) and raises pylons that cut the damage it takes to a quarter;
     * with the pylons broken it takes full damage again; at 30 % it shifts to phase 3.
     */
    @GameTest(template = "boss_arena", batch = "sentinelPhase", timeoutTicks = 300)
    public static void sentinelPhasesAndPylons(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = helper.getLevel().damageSources();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(boss.phase() == 1, "a fresh Sentinel starts in phase 1");
            boss.hurt(damage.indirectMagic(attacker, attacker), boss.getMaxHealth() * 0.45F);
        });
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(boss.phase() == 2 && boss.action() == NullSentinel.Action.SHIFT, "below 60 % it shifts to phase 2");
            helper.assertTrue(!boss.hurt(damage.indirectMagic(attacker, attacker), 10.0F), "untouchable while it shifts");
        });
        int afterShift = 4 + NullSentinel.SHIFT_TICKS + 4;
        helper.runAfterDelay(afterShift, () -> {
            int pylons = boss.livingPylons().size();
            helper.assertTrue(pylons >= 3 && pylons <= 5, "phase 2 raises 3 to 5 pylons, has " + pylons);
            helper.assertTrue(boss.shieldUp(), "the pylons shield it");
            helper.assertTrue(!boss.action().shielded(), "the shift is over");
            float before = boss.getHealth();
            boss.hurt(damage.indirectMagic(attacker, attacker), 40.0F);
            float taken = before - boss.getHealth();
            float expected = 40.0F * NullSentinel.SHIELD_MULTIPLIER;
            helper.assertTrue(Math.abs(taken - expected) < 0.01F, "shielded it takes " + expected + ", took " + taken);
            for (NullPylon p : boss.livingPylons()) p.hurt(damage.indirectMagic(attacker, attacker), 100.0F);
        });
        helper.runAfterDelay(afterShift + 15, () -> {
            helper.assertTrue(boss.livingPylons().isEmpty(), "the pylons are broken");
            helper.assertTrue(!boss.shieldUp(), "the shield is down");
            float before = boss.getHealth();
            boss.hurt(damage.indirectMagic(attacker, attacker), 40.0F);
            float taken = before - boss.getHealth();
            helper.assertTrue(Math.abs(taken - 40.0F) < 0.01F, "unshielded it takes full damage, took " + taken);
        });
        helper.runAfterDelay(afterShift + 30, () -> boss.hurt(damage.indirectMagic(attacker, attacker), boss.getHealth() - boss.getMaxHealth() * 0.25F));
        helper.runAfterDelay(afterShift + 35, () -> {
            helper.assertTrue(boss.phase() == 3 && boss.action() == NullSentinel.Action.SHIFT, "below 30 % it shifts to phase 3");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** While it recharges after its attacks, its open core takes double damage. */
    @GameTest(template = "boss_arena", batch = "sentinelRecharge", timeoutTicks = 100)
    public static void sentinelRechargeTakesDoubleDamage(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        Husk attacker = dummy(helper, new BlockPos(0, 1, 0));
        DamageSources damage = helper.getLevel().damageSources();
        helper.runAfterDelay(2, () -> {
            boss.startRecharge();
            helper.assertTrue(boss.isRecharging(), "it recharges");
            float before = boss.getHealth();
            boss.hurt(damage.indirectMagic(attacker, attacker), 10.0F);
            float taken = before - boss.getHealth();
            float expected = 10.0F * NullSentinel.RECHARGE_MULTIPLIER;
            helper.assertTrue(Math.abs(taken - expected) < 0.01F, "recharging takes " + expected + ", took " + taken);
        });
        helper.runAfterDelay(4 + NullSentinel.RECHARGE_TICKS, () -> {
            helper.assertTrue(!boss.isRecharging(), "the recharge ends");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** The beam hurts its target in line and spares a bystander standing in the same beam. */
    @GameTest(template = "boss_arena", batch = "sentinelBeam", timeoutTicks = 100)
    public static void sentinelBeamHitsTargetSparesBystanders(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        Husk target = dummy(helper, new BlockPos(4, 1, 8));
        Husk bystander = dummy(helper, new BlockPos(4, 1, 7));
        helper.runAfterDelay(2, () -> {
            boss.setTarget(target);
            var aim = target.getBoundingBox().getCenter().subtract(boss.lensPos()).normalize();
            boss.pose(NullSentinel.Action.BEAM, aim);
            aim = target.getBoundingBox().getCenter().subtract(boss.lensPos()).normalize();
            boss.pose(NullSentinel.Action.BEAM, aim);
            int hit = boss.beamTick(target);
            helper.assertTrue(hit == 1, "the beam hits the target only, hit " + hit);
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the target is hurt");
            helper.assertTrue(bystander.getHealth() == bystander.getMaxHealth(), "the bystander is not");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** The gravity well drags its target in and the burst hurts and throws up whoever is inside the ring. */
    @GameTest(template = "boss_arena", batch = "sentinelWell", timeoutTicks = 100)
    public static void sentinelWellPullsAndBursts(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        Husk target = dummy(helper, new BlockPos(1, 1, 4));
        helper.runAfterDelay(2, () -> {
            boss.setTarget(target);
            boss.pose(NullSentinel.Action.WELL, boss.aim());
            Vec3 before = target.getDeltaMovement();
            helper.assertTrue(boss.wellTick() == 1, "the well pulls the target");
            Vec3 toBoss = boss.position().subtract(target.position());
            helper.assertTrue(target.getDeltaMovement().subtract(before).dot(toBoss) > 0.0, "towards the Sentinel");
            helper.assertTrue(boss.burst() == 1, "the burst hits the target inside the ring");
            helper.assertTrue(target.getHealth() < target.getMaxHealth(), "the target is hurt");
            helper.assertTrue(target.getDeltaMovement().y > 0.5, "and thrown up, dy " + target.getDeltaMovement().y);
            cleanup(helper);
            helper.succeed();
        });
    }

    /** Gravity rings throw whoever stands in them after their delay; rifts hurt and slow whoever stands in them once open. */
    @GameTest(template = "boss_arena", batch = "sentinelSpots", timeoutTicks = 200)
    public static void sentinelRingsAndRifts(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        Husk ringed = dummy(helper, new BlockPos(0, 1, 0));
        Husk rifted = dummy(helper, new BlockPos(8, 1, 8));
        helper.runAfterDelay(2, () -> {
            boss.setTarget(ringed);
            helper.assertTrue(boss.markLaunches(ringed) >= 1, "a ring under the target");
            boss.markRift(rifted.position());
            helper.assertTrue(boss.pendingSpots(NullSentinel.SPOT_LAUNCH) >= 1 && boss.pendingSpots(NullSentinel.SPOT_RIFT) == 1, "rings and a rift are marked");
            helper.assertTrue(ringed.getHealth() == ringed.getMaxHealth(), "nothing hurts before the delay");
        });
        helper.runAfterDelay(2 + NullSentinel.LAUNCH_DELAY + 5, () -> {
            helper.assertTrue(ringed.getHealth() < ringed.getMaxHealth(), "the ring hurt the target");
            helper.assertTrue(boss.pendingSpots(NullSentinel.SPOT_LAUNCH) == 0, "every ring went off");
        });
        helper.runAfterDelay(2 + NullSentinel.RIFT_ARM + 15, () -> {
            boss.setTarget(rifted);
        });
        helper.runAfterDelay(2 + NullSentinel.RIFT_ARM + 30, () -> {
            helper.assertTrue(rifted.getHealth() < rifted.getMaxHealth(), "the open rift hurt its target");
            helper.assertTrue(rifted.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed it");
            cleanup(helper);
            helper.succeed();
        });
    }

    /** A Sentinel that falls far below its altar (off the island) is put back on it. */
    @GameTest(template = "boss_arena", batch = "sentinelVoid", timeoutTicks = 100)
    public static void sentinelReturnsToItsAltar(GameTestHelper helper) {
        NullSentinel boss = sentinel(helper);
        BlockPos altar = helper.absolutePos(CENTRE);
        helper.runAfterDelay(2, () -> {
            boss.setAltarPos(altar);
            boss.teleportTo(boss.getX(), altar.getY() - 20, boss.getZ());
        });
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(boss.getY() > altar.getY() - 2, "back on its altar, at y " + boss.getY());
            cleanup(helper);
            helper.succeed();
        });
    }

    /** The Null Relay template loads with a natural Null Altar in the centre and a loot chest; its structure is registered. */
    @GameTest(template = "empty")
    public static void nullRelayTemplateHasAltar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var template = level.getStructureManager().get(NullRelayPiece.TEMPLATE);
        helper.assertTrue(template.isPresent(), "the null_relay template loads");
        int size = NullRelayStructure.SIZE;
        helper.assertTrue(template.get().getSize().getX() == size && template.get().getSize().getZ() == size,
                "the relay is " + size + " wide, is " + template.get().getSize());
        var altars = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), BossRegistry.NULL_ALTAR.get());
        helper.assertTrue(altars.size() == 1, "exactly one altar, found " + altars.size());
        helper.assertTrue(altars.get(0).pos().equals(new BlockPos(NullRelayStructure.HALF, 1, NullRelayStructure.HALF)),
                "the altar sits in the centre, at " + altars.get(0).pos());
        helper.assertTrue(altars.get(0).state().getValue(BossAltarBlock.NATURAL), "the ruin's altar is marked natural");
        var chests = template.get().filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
        helper.assertTrue(chests.size() == 1 && chests.get(0).nbt() != null
                && "robotica:chests/null_relay".equals(chests.get(0).nbt().getString("LootTable")), "one loot chest with the relay loot");
        Structure relay = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Robotica.id("null_relay"));
        helper.assertTrue(relay instanceof NullRelayStructure, "robotica:null_relay is registered with its own type");
        helper.assertTrue(level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).containsKey(Robotica.id("null_relay")),
                "robotica:null_relay has a structure set");
        helper.succeed();
    }
}
