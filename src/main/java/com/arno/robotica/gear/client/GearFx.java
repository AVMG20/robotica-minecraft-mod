package com.arno.robotica.gear.client;

import com.arno.robotica.core.particle.CoreParticles;
import com.arno.robotica.core.particle.TintParticleOptions;
import com.arno.robotica.gear.GearClientConfig;
import com.arno.robotica.gear.GearFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Draws the weapon effects the server sends as one {@link GearFxPayload} each: Arc Blade chains and the Lifesteal
 * stream; the Null Lance beam and bolt muzzle go to {@link LanceFx}, area break static to {@link AreaStatic}. Particles only, spawned locally, so the client config and vanilla's particle setting
 * decide how much shows.
 */
final class GearFx {
    private GearFx() {}

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.85F, 0.08F, 0.18F), 0.9F);
    private static ClientLevel lastLevel;

    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            GearFxPayload.clearQueue();
            lastLevel = level;
        }
        if (level == null) return;
        boolean weapons = GearClientConfig.weaponParticles();
        GearFxPayload fx;
        while ((fx = GearFxPayload.poll()) != null) {
            float[] p = fx.points();
            switch (fx.kind()) {
                case GearFxPayload.ARC_CHAIN -> {
                    if (weapons && p.length >= 3) arcChain(level, p);
                }
                case GearFxPayload.LANCE -> {
                    // drawn only with weapon particles on, but the own shot's camera kick and HUD flash always play
                    int[] ints = fx.ints();
                    if (p.length >= 9) LanceFx.beam(level, p, ints.length > 0 && ints[0] == 1, ints.length > 1 ? ints[1] : -1);
                }
                case GearFxPayload.BOLT -> {
                    if (weapons && p.length >= 6) LanceFx.boltMuzzle(level, p, fx.ints().length > 0 ? fx.ints()[0] : -1);
                }
                case GearFxPayload.DRAIN -> {
                    if (weapons && p.length >= 6) drain(level, p);
                }
                case GearFxPayload.STATIC -> {
                    if (GearClientConfig.areaStatic() && p.length >= 3) AreaStatic.add(level, p);
                }
                default -> {}
            }
        }
    }

    /** Like ServerLevel.sendParticles with a count: gaussian spread around the point and gaussian speed. */
    private static void burst(ClientLevel level, ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(type, x + r.nextGaussian() * dx, y + r.nextGaussian() * dy, z + r.nextGaussian() * dz,
                    r.nextGaussian() * speed, r.nextGaussian() * speed, r.nextGaussian() * speed);
        }
    }

    /** Sparks and a crackle on the main target, then a jagged bolt and a smaller burst for every arc. */
    private static void arcChain(ClientLevel level, float[] p) {
        TintParticleOptions spark = CoreParticles.spark(CoreParticles.ELECTRIC, 1.0F);
        TintParticleOptions zap = CoreParticles.zap(CoreParticles.ELECTRIC, 1.3F);
        sparks(level, spark, p[0], p[1], p[2], 8, 0.25);
        burst(level, zap, p[0], p[1], p[2], 3, 0.25, 0.35, 0.25, 0);
        for (int i = 3; i + 2 < p.length; i += 3) {
            bolt(level, p[i - 3], p[i - 2], p[i - 1], p[i], p[i + 1], p[i + 2]);
            sparks(level, spark, p[i], p[i + 1], p[i + 2], 5, 0.2);
            burst(level, zap, p[i], p[i + 1], p[i + 2], 2, 0.25, 0.35, 0.25, 0);
        }
    }

    /** Sparks flying out and up from a point; they fall and bounce. */
    private static void sparks(ClientLevel level, ParticleOptions spark, double x, double y, double z, int count, double speed) {
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            level.addParticle(spark, x, y, z, r.nextGaussian() * speed, 0.08 + r.nextDouble() * speed, r.nextGaussian() * speed);
        }
    }

    /** A jagged bolt: a few kinked segments of crackle with a bright core. */
    private static void bolt(ClientLevel level, double ax, double ay, double az, double bx, double by, double bz) {
        RandomSource r = level.random;
        TintParticleOptions zap = CoreParticles.zap(CoreParticles.ELECTRIC, 0.8F);
        TintParticleOptions core = CoreParticles.mote(CoreParticles.ELECTRIC, 0.7F);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int kinks = Math.max(2, (int) (len / 1.5));
        double px = ax, py = ay, pz = az;
        for (int k = 1; k <= kinks; k++) {
            double t = k / (double) kinks;
            double nx = ax + dx * t, ny = ay + dy * t, nz = az + dz * t;
            if (k < kinks) {
                nx += (r.nextDouble() - 0.5) * 0.6;
                ny += (r.nextDouble() - 0.5) * 0.6;
                nz += (r.nextDouble() - 0.5) * 0.6;
            }
            double sx = nx - px, sy = ny - py, sz = nz - pz;
            int steps = Math.max(2, (int) (Math.sqrt(sx * sx + sy * sy + sz * sz) / 0.25));
            for (int i = 0; i <= steps; i++) {
                double f = i / (double) steps;
                double x = px + sx * f, y = py + sy * f, z = pz + sz * f;
                if (i % 2 == 0) level.addParticle(zap, x + r.nextGaussian() * 0.03, y + r.nextGaussian() * 0.03, z + r.nextGaussian() * 0.03, 0, 0, 0);
                if (i % 3 == 0) level.addParticle(core, x, y, z, 0, 0, 0);
            }
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /** A thin crimson stream from the target to the player and a heart. */
    private static void drain(ClientLevel level, float[] p) {
        double fx = p[0], fy = p[1], fz = p[2], dx = p[3] - fx, dy = p[4] - fy, dz = p[5] - fz;
        int steps = Math.min(24, Math.max(3, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.6)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            burst(level, BLOOD, fx + dx * t, fy + dy * t, fz + dz * t, 1, 0.05, 0.05, 0.05, 0);
        }
        burst(level, ParticleTypes.HEART, p[3], p[4] + 0.6, p[5], 1, 0.2, 0.1, 0.2, 0);
    }
}
