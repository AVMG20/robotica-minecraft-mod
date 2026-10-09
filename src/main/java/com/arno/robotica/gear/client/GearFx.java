package com.arno.robotica.gear.client;

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
 * Draws the weapon effects the server sends as one {@link GearFxPayload} each: Arc Blade chains, the Null Lance beam
 * and the Lifesteal stream; area break static goes to {@link AreaStatic}. Particles only, spawned locally, so the client config and vanilla's particle setting
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
                    if (weapons && p.length >= 9) lance(level, p, fx.ints().length > 0 && fx.ints()[0] == 1);
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

    /** Sparks on the main target, then a jagged bolt and a spark burst for every arc. */
    private static void arcChain(ClientLevel level, float[] p) {
        burst(level, ParticleTypes.ELECTRIC_SPARK, p[0], p[1], p[2], 14, 0.3, 0.4, 0.3, 0.2);
        for (int i = 3; i + 2 < p.length; i += 3) {
            bolt(level, p[i - 3], p[i - 2], p[i - 1], p[i], p[i + 1], p[i + 2]);
            burst(level, ParticleTypes.ELECTRIC_SPARK, p[i], p[i + 1], p[i + 2], 10, 0.3, 0.4, 0.3, 0.15);
        }
    }

    /** A jagged bolt: a few kinked segments of sparks with a bright core. */
    private static void bolt(ClientLevel level, double ax, double ay, double az, double bx, double by, double bz) {
        RandomSource r = level.random;
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
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + r.nextGaussian() * 0.03, y + r.nextGaussian() * 0.03, z + r.nextGaussian() * 0.03, 0, 0, 0);
                if (i % 3 == 0) level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            }
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /** Bright core with a slow purple spiral around it, a burst at the muzzle, the hits and the impact. */
    private static void lance(ClientLevel level, float[] p, boolean hitBlock) {
        double sx = p[0], sy = p[1], sz = p[2], lx = p[3], ly = p[4], lz = p[5], ex = p[6], ey = p[7], ez = p[8];
        double length = Math.sqrt((ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz));
        double mx = sx + lx * 0.8, my = sy - 0.2 + ly * 0.8, mz = sz + lz * 0.8;
        // side = look x up, up2 = side x look, both 0.3 long
        double ax = -lz, az = lx, an = Math.sqrt(ax * ax + az * az);
        double sideX, sideY = 0, sideZ;
        if (an < 1.0E-2) {
            sideX = 0.3;
            sideZ = 0;
        } else {
            sideX = ax / an * 0.3;
            sideZ = az / an * 0.3;
        }
        double ux = sideY * lz - sideZ * ly, uy = sideZ * lx - sideX * lz, uz = sideX * ly - sideY * lx;
        double un = Math.max(1.0E-4, Math.sqrt(ux * ux + uy * uy + uz * uz));
        ux = ux / un * 0.3;
        uy = uy / un * 0.3;
        uz = uz / un * 0.3;
        burst(level, ParticleTypes.REVERSE_PORTAL, mx, my, mz, 10, 0.1, 0.1, 0.1, 0.08);
        for (double d = 0.5; d <= length; d += 0.5) {
            double x = mx + lx * d, y = my + ly * d, z = mz + lz * d;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            double a = d * 1.4, c = Math.cos(a), s = Math.sin(a);
            level.addParticle(ParticleTypes.WITCH, x + sideX * c + ux * s, y + sideY * c + uy * s, z + sideZ * c + uz * s, 0, 0, 0);
            if (((int) (d * 2)) % 4 == 0) burst(level, ParticleTypes.PORTAL, x, y, z, 2, 0.15, 0.15, 0.15, 0.2);
        }
        for (int i = 9; i + 2 < p.length; i += 3) {
            burst(level, ParticleTypes.REVERSE_PORTAL, p[i], p[i + 1], p[i + 2], 12, 0.25, 0.35, 0.25, 0.05);
            burst(level, ParticleTypes.END_ROD, p[i], p[i + 1], p[i + 2], 4, 0.2, 0.3, 0.2, 0.08);
        }
        if (hitBlock) {
            level.addParticle(ParticleTypes.FLASH, ex, ey, ez, 0, 0, 0);
            burst(level, ParticleTypes.END_ROD, ex, ey, ez, 14, 0.1, 0.1, 0.1, 0.12);
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
