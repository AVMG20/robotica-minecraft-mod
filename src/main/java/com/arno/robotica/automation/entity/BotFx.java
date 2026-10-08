package com.arno.robotica.automation.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Small work effects of the farm bots: a thin spark line to the target, slashes, sparkles and dirt puffs. Spawned on the
 * client from the bot's block event (plain Level calls, so the player's particle setting applies); the server never
 * calls these.
 */
public final class BotFx {
    private BotFx() {}

    public static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 1.0F), 0.55F);
    private static final DustParticleOptions LINE = new DustParticleOptions(new Vector3f(0.45F, 0.9F, 1.0F), 0.35F);
    public static final DustParticleOptions MINT = new DustParticleOptions(new Vector3f(0.4F, 1.0F, 0.7F), 0.55F);

    /** A thin dotted line of cyan motes from the bot's chest to {@code (x, y, z)}, with a spark or two on it. */
    public static void sparkLine(Level level, BlockPos bot, double x, double y, double z) {
        double fx = bot.getX() + 0.5, fy = bot.getY() + 0.45, fz = bot.getZ() + 0.5;
        double dx = x - fx, dy = y - fy, dz = z - fz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.6) return;
        int n = Mth.clamp((int) (len * 2.5), 2, 14);
        // Start outside the body, stop short of the target.
        double from = 0.45 / len;
        double to = 1.0 - 0.3 / len;
        RandomSource r = level.random;
        int sparkAt = r.nextInt(n);
        for (int i = 0; i < n; i++) {
            double t = from + (to - from) * (i + r.nextDouble() * 0.5) / n;
            ParticleOptions type = i == sparkAt || i == n - 1 ? ParticleTypes.ELECTRIC_SPARK : LINE;
            level.addParticle(type, fx + dx * t + jitter(r), fy + dy * t + jitter(r), fz + dz * t + jitter(r), 0, 0, 0);
        }
    }

    /** {@code (x, z)} moved from a block centre toward the bot by {@code inset} blocks: the block's face on the bot's side. */
    public static double[] faceToward(BlockPos bot, double x, double z, double inset) {
        double dx = bot.getX() + 0.5 - x, dz = bot.getZ() + 0.5 - z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) return new double[]{x, z};
        return new double[]{x + dx / len * inset, z + dz / len * inset};
    }

    /** A slash sprite; size 0.3 (small) to 0.8 (a trunk). */
    public static void slash(Level level, double x, double y, double z, float size) {
        level.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, (1.0F - size) * 2.0F, 0, 0);
    }

    /** A few sparks and dust motes around a point. */
    public static void burst(Level level, double x, double y, double z, int sparks, ParticleOptions dust, int motes) {
        RandomSource r = level.random;
        for (int i = 0; i < sparks; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + jitter(r) * 1.5, y + jitter(r), z + jitter(r) * 1.5,
                    r.nextGaussian() * 0.4, r.nextDouble() * 0.4, r.nextGaussian() * 0.4);
        }
        for (int i = 0; i < motes; i++) {
            level.addParticle(dust, x + jitter(r) * 2, y + jitter(r), z + jitter(r) * 2, 0, 0.02, 0);
        }
    }

    /** Dirt kicked up from the top of a block. */
    public static void puffs(Level level, BlockPos pos, BlockState state, int count) {
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, state);
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(dirt, pos.getX() + 0.2 + r.nextDouble() * 0.6, pos.getY() + 1.02, pos.getZ() + 0.2 + r.nextDouble() * 0.6,
                    (r.nextDouble() - 0.5) * 0.1, 0.12 + r.nextDouble() * 0.08, (r.nextDouble() - 0.5) * 0.1);
        }
    }

    /** The tiny sparkle every touched block gets. */
    public static void sparkle(Level level, BlockPos pos) {
        burst(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, CYAN, 1);
    }

    /** A soft swing whoosh at the bot. */
    public static void whoosh(Level level, BlockPos bot, float volume) {
        sound(level, bot.getX() + 0.5, bot.getY() + 0.5, bot.getZ() + 0.5, SoundEvents.PLAYER_ATTACK_SWEEP, volume, 1.5F + level.random.nextFloat() * 0.3F);
    }

    public static void sound(Level level, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(x, y, z, sound, SoundSource.NEUTRAL, volume, pitch, false);
    }

    private static double jitter(RandomSource r) {
        return (r.nextDouble() - 0.5) * 0.12;
    }
}
