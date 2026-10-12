package com.arno.robotica.warp.client;

import com.arno.robotica.core.client.particle.CoreParticleRender;
import com.arno.robotica.core.particle.CoreParticles;
import com.arno.robotica.warp.WarpFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The shared warp effect of pads, remotes and portals, from {@link WarpFxPayload}: a violet vortex gathers around the
 * traveller while the trip winds up, then a flash where they leave (the vortex collapses) and where they arrive (it
 * unwinds, with a ring of dust at the feet). Portal trips get a quieter version.
 */
final class WarpFx {
    private WarpFx() {}

    private static final int LAVENDER = 0xCBB8FF;

    static void play(WarpFxPayload fx) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (fx.kind() == WarpFxPayload.STOP) {
            var player = Minecraft.getInstance().player;
            if (player != null && player.isUsingItem() && player.getUseItem().getItem() instanceof com.arno.robotica.warp.item.RemoteItem) {
                player.stopUsingItem();
            }
            return;
        }
        switch (fx.kind()) {
            case WarpFxPayload.CHARGE -> charge(level, fx.x(), fx.y(), fx.z(), Mth.clamp(fx.value(), 0, 1));
            case WarpFxPayload.DEPART -> depart(level, fx.x(), fx.y(), fx.z(), fx.value() < 0.5F);
            case WarpFxPayload.ARRIVE -> arrive(level, fx.x(), fx.y(), fx.z(), fx.value() < 0.5F);
            default -> {}
        }
    }

    private static int colour(RandomSource r) {
        return r.nextInt(3) == 0 ? CoreParticles.WARP_CYAN : CoreParticles.WARP_VIOLET;
    }

    /** Wind-up: motes spiral in around the body, tighter and faster as the charge fills; crackle near the end. */
    private static void charge(ClientLevel level, double x, double y, double z, float progress) {
        RandomSource r = level.random;
        int n = CoreParticleRender.budget(3 + Math.round(progress * 5));
        for (int i = 0; i < n; i++) {
            double radius = 0.8 + 0.6 * (1 - progress) + r.nextDouble() * 0.3;
            level.addParticle(CoreParticles.warp(colour(r), 1.0F + progress * 0.4F), x, y + 0.1 + r.nextDouble() * 1.8, z,
                    radius, 0.01 + r.nextDouble() * 0.015, 0.25 + 0.25 * progress);
        }
        if (progress > 0.5F && CoreParticleRender.budget(1) > 0 && r.nextBoolean()) {
            level.addParticle(CoreParticles.mote(CoreParticles.WARP_VIOLET, 1.2F), x + (r.nextDouble() - 0.5) * 0.5, y + 0.9 + r.nextDouble() * 0.4,
                    z + (r.nextDouble() - 0.5) * 0.5, 0, 0.02, 0);
        }
        if (progress > 0.8F && CoreParticleRender.budget(1) > 0) {
            level.addParticle(CoreParticles.zap(CoreParticles.WARP_CYAN, 1.0F), x + (r.nextDouble() - 0.5) * 0.7, y + 0.3 + r.nextDouble() * 1.4,
                    z + (r.nextDouble() - 0.5) * 0.7, 0, 0, 0);
        }
    }

    /** Leaving: the vortex collapses into a flash with a few sparks. */
    private static void depart(ClientLevel level, double x, double y, double z, boolean quiet) {
        RandomSource r = level.random;
        if (CoreParticleRender.budget(1) == 0) return;
        level.addParticle(CoreParticles.flash(CoreParticles.WARP_VIOLET, quiet ? 1.4F : 2.4F), x, y + 1.0, z, 0, 0, 0);
        if (!quiet) level.addParticle(CoreParticles.flash(CoreParticles.WARP_CYAN, 1.2F), x, y + 1.0, z, 0, 0, 0);
        int swirl = CoreParticleRender.budget(quiet ? 10 : 24);
        for (int i = 0; i < swirl; i++) {
            level.addParticle(CoreParticles.warp(colour(r), 1.3F), x, y + r.nextDouble() * 2.0, z,
                    0.9 + r.nextDouble() * 0.5, 0.0, 0.55);
        }
        if (quiet) return;
        int sparks = CoreParticleRender.budget(10);
        for (int i = 0; i < sparks; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            level.addParticle(CoreParticles.spark(CoreParticles.WARP_VIOLET, 1.0F), x, y + 1.0, z,
                    Math.cos(a) * 0.18, 0.1 + r.nextDouble() * 0.15, Math.sin(a) * 0.18);
        }
    }

    /** Arriving: a flash, the vortex unwinds outwards, motes drift up and a ring of dust runs out from the feet. */
    private static void arrive(ClientLevel level, double x, double y, double z, boolean quiet) {
        RandomSource r = level.random;
        if (CoreParticleRender.budget(1) == 0) return;
        level.addParticle(CoreParticles.flash(CoreParticles.WARP_VIOLET, quiet ? 1.4F : 2.4F), x, y + 1.0, z, 0, 0, 0);
        if (!quiet) level.addParticle(CoreParticles.flash(CoreParticles.WARP_CYAN, 1.2F), x, y + 1.0, z, 0, 0, 0);
        int swirl = CoreParticleRender.budget(quiet ? 10 : 28);
        for (int i = 0; i < swirl; i++) {
            level.addParticle(CoreParticles.warp(colour(r), 1.3F), x, y + r.nextDouble() * 2.0, z,
                    -(1.0 + r.nextDouble() * 0.5), 0.012, 0.4);
        }
        int motes = CoreParticleRender.budget(quiet ? 3 : 8);
        for (int i = 0; i < motes; i++) {
            level.addParticle(CoreParticles.mote(CoreParticles.WARP_VIOLET, 1.0F), x + (r.nextDouble() - 0.5) * 0.8, y + r.nextDouble() * 1.8,
                    z + (r.nextDouble() - 0.5) * 0.8, 0, 0.03 + r.nextDouble() * 0.02, 0);
        }
        if (quiet) return;
        int ring = CoreParticleRender.budget(14);
        float off = r.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < ring; i++) {
            float a = off + i * Mth.TWO_PI / Math.max(1, ring);
            level.addParticle(CoreParticles.puff(LAVENDER, 1.1F), x + Mth.cos(a) * 0.3, y + 0.05, z + Mth.sin(a) * 0.3,
                    Mth.cos(a) * 0.12, 0.005, Mth.sin(a) * 0.12);
        }
        int sparks = CoreParticleRender.budget(8);
        for (int i = 0; i < sparks; i++) {
            double a = r.nextDouble() * Mth.TWO_PI;
            level.addParticle(CoreParticles.spark(CoreParticles.WARP_CYAN, 1.0F), x, y + 1.0, z,
                    Math.cos(a) * 0.15, 0.12 + r.nextDouble() * 0.12, Math.sin(a) * 0.15);
        }
    }
}
