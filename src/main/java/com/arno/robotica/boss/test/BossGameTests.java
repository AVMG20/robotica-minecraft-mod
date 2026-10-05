package com.arno.robotica.boss.test;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import com.arno.robotica.boss.block.ColossusAltarBlock;
import com.arno.robotica.boss.block.ColossusAltarBlockEntity;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import com.arno.robotica.boss.world.RustedFoundryPiece;
import com.arno.robotica.boss.world.RustedFoundryStructure;
import com.arno.robotica.core.item.CoreItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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
                e -> e instanceof ScrapColossus || e instanceof ScrapDrone || e instanceof ItemEntity);
        leftovers.forEach(Entity::discard);
    }

    /** A flare at the altar wakes one Colossus on top, sets the cooldown, and the altar refuses while it lives or cools down. */
    @GameTest(template = "boss_arena", batch = "bossAltar", timeoutTicks = 200)
    public static void altarFlareSpawnsBossAndSetsCooldown(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(CENTRE, BossRegistry.COLOSSUS_ALTAR.get());
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(CENTRE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ColossusAltarBlock.Result first = ColossusAltarBlock.awaken(level, altarPos, null);
        helper.assertTrue(first == ColossusAltarBlock.Result.SPAWNED, "the first flare wakes the Colossus, got " + first);
        ScrapColossus boss = helper.findClosestEntity(BossRegistry.SCRAP_COLOSSUS.get(), CENTRE.getX(), CENTRE.getY() + 1, CENTRE.getZ(), 3.0);
        helper.assertTrue(boss != null && altarPos.equals(boss.altarPos()), "the Colossus stands on its altar");
        helper.assertTrue(boss.getY() >= altarPos.getY() + 1.0, "the Colossus spawns on top of the altar");
        ColossusAltarBlockEntity altar = (ColossusAltarBlockEntity) level.getBlockEntity(altarPos);
        helper.assertTrue(altar != null && altar.cooldownLeft(level.getGameTime()) == BossConfig.altarCooldownTicks(),
                "the altar cools down for the configured time");
        helper.assertTrue(!level.getBlockState(altarPos).getValue(ColossusAltarBlock.READY), "the altar goes dark while it cools down");

        ColossusAltarBlock.Result second = ColossusAltarBlock.awaken(level, altarPos, player);
        helper.assertTrue(second == ColossusAltarBlock.Result.BOSS_ALIVE, "one Colossus per altar, got " + second);
        boss.discard();
        ColossusAltarBlock.Result third = ColossusAltarBlock.awaken(level, altarPos, player);
        helper.assertTrue(third == ColossusAltarBlock.Result.COOLDOWN, "the cooldown holds after the Colossus is gone, got " + third);
        altar.resetCooldown();
        ColossusAltarBlock.Result fourth = ColossusAltarBlock.awaken(level, altarPos, player);
        helper.assertTrue(fourth == ColossusAltarBlock.Result.SPAWNED, "a cooled down altar works again, got " + fourth);
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
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!boss.isPhaseTwo(), "a fresh Colossus starts in phase 1");
            boss.hurt(helper.getLevel().damageSources().magic(), boss.getMaxHealth() * 0.55F);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(boss.isPhaseTwo(), "below half health is phase 2");
            helper.assertTrue(boss.isOverheating(), "phase 2 starts with an overheat");
            int drones = boss.minions().size();
            helper.assertTrue(drones >= 1 && drones <= BossConfig.minionCap(), "phase 2 calls drones up to the cap, has " + drones);
            float before = boss.getHealth();
            boss.hurt(helper.getLevel().damageSources().magic(), 10.0F);
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
        helper.assertTrue(altars.get(0).state().getValue(ColossusAltarBlock.NATURAL), "the ruin's altar is marked natural");
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
}
