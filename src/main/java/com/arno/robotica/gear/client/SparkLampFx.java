package com.arno.robotica.gear.client;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.gear.GearClientConfig;
import com.arno.robotica.gear.GearParticles;
import com.arno.robotica.gear.lamp.SparkLampBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ambient effects of a Spark Lamp, run from {@link SparkLampBlock#animateTick}: white flickers at the core, embers
 * drifting up, now and then a spark spat out that falls, a rare crackle (short jagged arc and a quiet zap) and a rarer
 * faint hum. Nothing happens beyond the client config range from the camera, so a cave full of lamps only costs the
 * lamps near you.
 */
public final class SparkLampFx {
    private SparkLampFx() {}

    public static void install() {
        SparkLampBlock.ambientFx = SparkLampFx::tick;
    }

    private static void tick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean particles = GearClientConfig.lampParticles(), sounds = GearClientConfig.lampSounds();
        if (!particles && !sounds) return;
        Direction facing = state.getValue(SparkLampBlock.FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * SparkLampBlock.CORE_OFFSET;
        double y = pos.getY() + 0.5 + facing.getStepY() * SparkLampBlock.CORE_OFFSET;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * SparkLampBlock.CORE_OFFSET;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int range = GearClientConfig.lampFxRange();
        if (camera.distanceToSqr(x, y, z) > (double) range * range) return;

        if (particles) {
            if (random.nextInt(5) == 0) {           // white flicker at the core
                level.addParticle(GearParticles.LAMP_FLICK.get(), x + jitter(random, 0.1), y + jitter(random, 0.1), z + jitter(random, 0.1), 0, 0, 0);
            }
            if (random.nextInt(6) == 0) {           // an ember drifting up
                level.addParticle(GearParticles.LAMP_EMBER.get(), x + jitter(random, 0.08), y + jitter(random, 0.08), z + jitter(random, 0.08),
                        jitter(random, 0.006), 0.004 + random.nextDouble() * 0.006, jitter(random, 0.006));
            }
            if (random.nextInt(14) == 0) {          // a spark spat out, falling
                for (int i = random.nextInt(2); i >= 0; i--) spit(level, random, x, y, z, 0.05 + random.nextDouble() * 0.06);
            }
        }
        if (random.nextInt(45) == 0) {              // crackle: a short jagged arc, a spark off its tip and a quiet zap
            if (particles) {
                double dx = jitter(random, 1), dy = jitter(random, 1), dz = jitter(random, 1);
                double len = Math.max(0.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
                double px = x, py = y, pz = z;
                for (int i = 0; i < 4; i++) {
                    px += dx / len * 0.06 + jitter(random, 0.025);
                    py += dy / len * 0.06 + jitter(random, 0.025);
                    pz += dz / len * 0.06 + jitter(random, 0.025);
                    level.addParticle(GearParticles.LAMP_FLICK.get(), px, py, pz, 0, 0, 0);
                }
                spit(level, random, px, py, pz, 0.08);
            }
            if (sounds) {
                level.playLocalSound(x, y, z, CoreSounds.SPARK_LAMP_CRACKLE.get(), SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F, false);
            }
        } else if (sounds && random.nextInt(160) == 0) {
            level.playLocalSound(x, y, z, CoreSounds.SPARK_LAMP_HUM.get(), SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }

    private static void spit(Level level, RandomSource random, double x, double y, double z, double speed) {
        double dx = jitter(random, 1), dz = jitter(random, 1), dy = 0.3 + random.nextDouble() * 0.7;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        level.addParticle(GearParticles.LAMP_SPARK.get(), x, y, z, dx / len * speed, dy / len * speed, dz / len * speed);
    }

    private static double jitter(RandomSource random, double size) {
        return (random.nextDouble() - 0.5) * 2 * size;
    }
}
