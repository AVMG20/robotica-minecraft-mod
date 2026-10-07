package com.arno.robotica.gear.client;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.gear.GearClientConfig;
import com.arno.robotica.gear.lamp.SparkLampBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ambient effects of a Spark Lamp, run from {@link SparkLampBlock#animateTick}: a tiny spark flickering at the core, a rare
 * crackle (short spark arc and a quiet zap) and a rarer faint hum. Nothing happens beyond the client config range
 * from the camera, so a cave full of lamps only costs the lamps near you.
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

        if (particles && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + jitter(random, 0.14), y + jitter(random, 0.14), z + jitter(random, 0.14),
                    jitter(random, 0.012), jitter(random, 0.012) + 0.004, jitter(random, 0.012));
        }
        if (random.nextInt(40) == 0) {
            if (particles) {
                double dx = jitter(random, 1), dy = jitter(random, 1), dz = jitter(random, 1);
                double len = Math.max(0.01, Math.sqrt(dx * dx + dy * dy + dz * dz));
                for (int i = 1; i <= 3; i++) {
                    double step = 0.06 * i / len;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + dx * step, y + dy * step, z + dz * step, 0, 0, 0);
                }
            }
            if (sounds) {
                level.playLocalSound(x, y, z, CoreSounds.SPARK_LAMP_CRACKLE.get(), SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F, false);
            }
        } else if (sounds && random.nextInt(160) == 0) {
            level.playLocalSound(x, y, z, CoreSounds.SPARK_LAMP_HUM.get(), SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F, false);
        }
    }

    private static double jitter(RandomSource random, double size) {
        return (random.nextDouble() - 0.5) * 2 * size;
    }
}
