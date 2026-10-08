package com.arno.robotica.codex.client.dev.trailer;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.BossRegistry;
import com.arno.robotica.boss.entity.ForgeTyrant;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.gear.GearComponents;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.tool.AreaShape;
import com.arno.robotica.gear.tool.BreakQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Trailer scenes with the bosses: the Scrap Colossus in a Rusted Foundry, the Forge Tyrant in a blackstone arena. Dev-only. */
final class TrailerScenesBosses {
    private TrailerScenesBosses() {}

    static List<TrailerScene> scenes() {
        return List.of(colossus(), tyrant(), drillMining());
    }

    // ------------------------------------------------------------------ helpers

    /** An iron golem with a lot of health, so the bosses have something to fight for the whole clip. */
    private static IronGolem golem(ServerLevel level, double x, double y, double z, boolean ai) {
        IronGolem g = EntityType.IRON_GOLEM.spawn(level, BlockPos.containing(x, y, z), MobSpawnType.COMMAND);
        g.moveTo(x, y, z, 0, 0);
        g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4000);
        g.setHealth(4000);
        g.setPersistenceRequired();
        g.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.5);
        g.setNoAi(!ai);
        return g;
    }

    private static <T extends Mob> T first(ServerLevel level, Class<T> type, BlockPos site) {
        List<T> found = level.getEntitiesOfClass(type, new AABB(site).inflate(40));
        return found.isEmpty() ? null : found.get(0);
    }

    private static void setInt(Object o, String field, int value) {
        try {
            var f = o.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.setInt(o, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Vec3 at(BlockPos site, double x, double y, double z) {
        return new Vec3(site.getX() + 0.5 + x, site.getY() + y, site.getZ() + 0.5 + z);
    }

    // ------------------------------------------------------------------ Scrap Colossus

    /**
     * The Colossus in a Rusted Foundry at sunset. Standing idle during the warm-up; when recording starts it throws scrap at
     * a golem across the hall (0-1.5 s), turns on the golem next to it and slams the ground (2.2-3.5 s), then enters phase
     * two (4 s): a roar, Scrap Drones and steam from the open furnace core.
     */
    private static TrailerScene colossus() {
        final int duration = 150;
        TrailerScene.Builder b = TrailerScene.builder("boss_colossus")
                .site(44, 44)
                .time(12600)
                .difficulty(Difficulty.NORMAL)
                .warmup(70)
                .duration(duration)
                .setup((level, player, site) -> {
                    // the hall's floor layer replaces the grass layer, the altar ends up on the site centre
                    Trailer.run(level, "place template robotica:rusted_foundry " + (site.getX() - 12) + " " + (site.getY() - 1) + " " + (site.getZ() - 12));
                    Vec3 c = at(site, 3.5, 0, 0.5);
                    ScrapColossus boss = BossRegistry.SCRAP_COLOSSUS.get().spawn(level, BlockPos.containing(c), MobSpawnType.COMMAND);
                    boss.moveTo(c.x, c.y, c.z, 200, 0);
                    boss.setYHeadRot(200);
                    boss.setYBodyRot(200);
                    boss.setNoAi(true);
                    // the golem to fight next to it, and a far one to throw scrap at
                    Vec3 near = at(site, 6.2, 0, -1.2), far = at(site, 10.5, 0, -6.5);
                    golem(level, near.x, near.y, near.z, false);
                    golem(level, far.x, far.y, far.z, false);
                })
                .at(0, (level, player, site) -> {
                    ScrapColossus boss = first(level, ScrapColossus.class, site);
                    boss.setNoAi(false);
                    setInt(boss, "throwCooldown", 0);
                    setInt(boss, "slamCooldown", 30);
                });
        for (int t = 0; t <= duration; t++) {
            final int tick = t;
            b.at(t, (level, player, site) -> {
                ScrapColossus boss = first(level, ScrapColossus.class, site);
                if (boss == null) return;
                List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, boss.getBoundingBox().inflate(40));
                IronGolem near = null, far = null;
                for (IronGolem g : golems) {
                    if (g.distanceToSqr(at(site, 10.5, 0, -6.5)) < 9) far = g;
                    else near = g;
                }
                boss.setTarget(tick < 50 ? far : near);
                for (ScrapDrone d : boss.minions()) if (d.getTarget() == null) d.setTarget(near);
                if (tick == 85) boss.enterPhaseTwo();
                if (tick % 5 == 0) {
                    Robotica.LOGGER.info("Trailer colossus t={} action={} pos={} target={} hp={}", tick, boss.action(), boss.position(),
                            boss.getTarget() == null ? "-" : boss.getTarget().getName().getString(), boss.getHealth());
                }
            });
        }
        return b.camera(site -> {
            return CameraPath.builder()
                    .keyLookAt(0, at(site, 0.3, 0.8, -5.4), at(site, 3.9, 2.0, 0.5), 60)
                    .keyLookAt(duration * 0.5, at(site, 1.2, 1.1, -4.5), at(site, 3.9, 2.1, 0.5), 56)
                    .keyLookAt(duration, at(site, 2.0, 1.4, -3.5), at(site, 3.9, 2.5, 0.5), 52)
                    .build();
        }).build();
    }

    // ------------------------------------------------------------------ Forge Tyrant

    private static final BlockState[] ARENA_FLOOR = {Blocks.BLACKSTONE.defaultBlockState(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
            Blocks.NETHERRACK.defaultBlockState(), Blocks.BASALT.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()};

    /**
     * The Tyrant in a blackstone arena at night. During the warm-up it stands idle; recording starts with phase two (a ring
     * of eruptions around it, going off at 1.5 s). It breathes flames at a golem next to it, then lobs magma and erupts
     * the ground under a golem further away.
     */
    private static TrailerScene tyrant() {
        final int duration = 150;
        TrailerScene.Builder b = TrailerScene.builder("boss_tyrant")
                .site(44, 44)
                .time(14000)
                .difficulty(Difficulty.NORMAL)
                .warmup(60)
                .duration(duration)
                .setup((level, player, site) -> {
                    for (int x = -16; x <= 16; x++) {
                        for (int z = -16; z <= 16; z++) {
                            double d = Math.hypot(x, z);
                            if (d > 15.5) continue;
                            BlockPos p = site.offset(x, -1, z);
                            boolean lava = (d > 10 && d < 13 && Math.floorMod(x * 31 + z * 17, 11) < 3 && Math.abs(x) + Math.abs(z) > 12);
                            level.setBlock(p, lava ? Blocks.LAVA.defaultBlockState() : ARENA_FLOOR[Math.floorMod((x * 73856093) ^ (z * 19349663), ARENA_FLOOR.length)], 3);
                        }
                    }
                    // basalt pillars with shroomlights and magma on top around the rim
                    for (int i = 0; i < 9; i++) {
                        double a = i * Math.PI * 2 / 9 + 0.3;
                        BlockPos base = site.offset((int) Math.round(Math.cos(a) * 14), 0, (int) Math.round(Math.sin(a) * 14));
                        int h = 4 + (i * 5) % 4;
                        for (int y = 0; y < h; y++) level.setBlock(base.above(y), Blocks.POLISHED_BASALT.defaultBlockState(), 3);
                        level.setBlock(base.above(h), i % 2 == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                    }
                    Trailer.place(level, site.offset(-5, 0, -4), Trailer.block("forge_altar"), Direction.SOUTH);
                    Vec3 c = at(site, 0, 0, 0);
                    ForgeTyrant boss = BossRegistry.FORGE_TYRANT.get().spawn(level, BlockPos.containing(c), MobSpawnType.COMMAND);
                    boss.moveTo(c.x, c.y, c.z, -90, 0);
                    boss.setYHeadRot(-90);
                    boss.setYBodyRot(-90);
                    boss.setNoAi(true);
                    // it stays on its spot (the camera orbits it) and only turns and attacks
                    boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
                    Vec3 near = at(site, 4.5, 0, 0.0), far = at(site, -8.5, 0, 2.5);
                    golem(level, near.x, near.y, near.z, false);
                    golem(level, far.x, far.y, far.z, false);
                })
                .at(0, (level, player, site) -> {
                    ForgeTyrant boss = first(level, ForgeTyrant.class, site);
                    boss.setNoAi(false);
                    boss.setAttackCooldown(0);
                    boss.enterPhaseTwo();
                });
        final ForgeTyrant.Action[] last = {ForgeTyrant.Action.IDLE};
        final boolean[] farTarget = {false};
        for (int t = 0; t <= duration; t++) {
            final int tick = t;
            b.at(t, (level, player, site) -> {
                ForgeTyrant boss = first(level, ForgeTyrant.class, site);
                if (boss == null) return;
                IronGolem near = null, far = null;
                for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, boss.getBoundingBox().inflate(40))) {
                    if (g.distanceToSqr(at(site, -8.5, 0, 2.5)) < 9) far = g;
                    else near = g;
                }
                // after every finished attack the target changes between the near and the far golem
                if (last[0] != ForgeTyrant.Action.IDLE && boss.action() == ForgeTyrant.Action.IDLE) farTarget[0] = !farTarget[0];
                last[0] = boss.action();
                boss.setTarget(farTarget[0] ? far : near);
                if (tick == 80 && far != null) {
                    boss.fireMortar(far);
                    boss.setAttackCooldown(40);
                }
                if (tick % 5 == 0) {
                    Robotica.LOGGER.info("Trailer tyrant t={} action={} pos={} far={} spots={}", tick, boss.action(), boss.position(), farTarget[0], boss.pendingEruptions());
                }
            });
        }
        return b.camera(site -> CameraPath.orbit(at(site, 0, 0, 0), 6.5, 1.0, 1.6, 335, 15, duration, 60)).build();
    }

    // ------------------------------------------------------------------ Null Drill

    private static final Block[] ORES = {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE};
    private static final Block[] ROCK = {Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.STONE, Blocks.ANDESITE,
            Blocks.ANDESITE, Blocks.GRANITE, Blocks.TUFF, Blocks.DIORITE};

    /**
     * First person: a powered Null Drill in 5x5x5 mode eats a long ridge of ore-flecked stone, one big cube about every
     * 1.7 s, while the camera walks into the tunnel. The hand swings just before each cube goes.
     */
    private static TrailerScene drillMining() {
        final int duration = 150, spacing = 35, firstHit = 5;
        final int[] hitX = {0, -1, 1, 0};
        TrailerScene.Builder b = TrailerScene.builder("drill_mining")
                .site(40, 56)
                .time(5000)
                .warmup(40)
                .duration(duration)
                .firstPerson(spacing)
                .setup((level, player, site) -> {
                    Trailer.run(level, "gamerule doTileDrops false");
                    for (int x = -14; x <= 14; x++) {
                        for (int z = 4; z <= 24; z++) level.setBlock(site.offset(x, -1, z), Blocks.DEEPSLATE.defaultBlockState(), 2);
                    }
                    // a ridge from 4 to 24 blocks south of the centre: 5 high in the middle (what the cubes remove), higher to the sides
                    for (int x = -14; x <= 14; x++) {
                        int height = Math.abs(x) <= 5 ? 5 : Math.min(10, 5 + Math.abs(x) - 5);
                        for (int z = 4; z <= 24; z++) {
                            for (int y = 0; y < height; y++) {
                                int h = Math.floorMod((x * 73856093) ^ (y * 19349663) ^ (z * 83492791), 1000);
                                Block block = h < 230 ? ORES[Math.floorMod(h * 7 + x + z, ORES.length)]
                                        : ROCK[Math.floorMod(h, ROCK.length)];
                                level.setBlock(site.offset(x, y, z), block.defaultBlockState(), 2);
                            }
                        }
                    }
                    ItemStack drill = new ItemStack(BuiltInRegistries.ITEM.get(Robotica.id("null_drill")));
                    ItemEnergy.fill(drill);
                    drill.set(GearComponents.MODE.get(), AreaMode.CUBE_5);
                    player.setItemInHand(InteractionHand.MAIN_HAND, drill);
                });
        for (int i = 0; i < hitX.length; i++) {
            final int hit = i;
            b.at(firstHit + i * spacing, (level, player, site) -> {
                BlockPos origin = site.offset(hitX[hit], 2, 4 + hit * 5);
                BreakQueue.rememberFace(player, origin, Direction.NORTH);
                // the breaking player's own client does not get the break particles, so send them for the exposed blocks
                for (BlockPos p : AreaShape.positions(origin, Direction.NORTH, AreaMode.CUBE_5, 0, false)) {
                    BlockState st = level.getBlockState(p);
                    boolean exposed = false;
                    for (Direction d : Direction.values()) exposed |= level.getBlockState(p.relative(d)).isAir();
                    if (exposed && !st.isAir()) level.levelEvent(2001, p, Block.getId(st));
                }
                player.gameMode.destroyBlock(origin);
                ItemEnergy.fill(player.getMainHandItem());
            });
        }
        b.at(duration, (level, player, site) -> Trailer.run(level, "gamerule doTileDrops true"));
        return b.camera(site -> {
            CameraPath.Builder path = CameraPath.builder();
            for (int t = 0; t <= duration; t += 25) {
                double z = 0.3 + t * 0.143;
                Vec3 pos = at(site, 0.35 * Math.sin(t / 40.0), 2.55 + 0.08 * Math.sin(t / 23.0), z);
                path.key(t, pos, (float) (4 * Math.sin(t / 55.0)), (float) (4 + 2 * Math.sin(t / 31.0)), 72);
            }
            return path.build();
        }).build();
    }
}
