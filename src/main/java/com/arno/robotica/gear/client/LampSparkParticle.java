package com.arno.robotica.gear.client;

import com.arno.robotica.gear.GearParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Spark Lamp particles, full-bright and tiny (about one block texel). A spark flies out, falls and lands; an ember
 * drifts up; a flick blinks white in place for a few ticks.
 */
public class LampSparkParticle extends TextureSheetParticle {
    private enum Kind { SPARK, EMBER, FLICK }

    private final Kind kind;

    private LampSparkParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        switch (kind) {
            case SPARK -> {
                quadSize = 0.03F;
                lifetime = 8 + random.nextInt(8);
                gravity = 0.45F;
                friction = 0.9F;
            }
            case EMBER -> {
                quadSize = 0.022F;
                lifetime = 20 + random.nextInt(14);
                gravity = -0.04F;
                friction = 0.95F;
                hasPhysics = false;
            }
            case FLICK -> {
                quadSize = 0.025F;
                lifetime = 2 + random.nextInt(3);
                gravity = 0;
                hasPhysics = false;
            }
        }
        tint(0);
    }

    public static void register(RegisterParticleProvidersEvent event) {
        provider(event, GearParticles.LAMP_SPARK.get(), Kind.SPARK);
        provider(event, GearParticles.LAMP_EMBER.get(), Kind.EMBER);
        provider(event, GearParticles.LAMP_FLICK.get(), Kind.FLICK);
    }

    private static void provider(RegisterParticleProvidersEvent event, SimpleParticleType type, Kind kind) {
        event.registerSpriteSet(type, sprites -> (options, level, x, y, z, xd, yd, zd) -> {
            LampSparkParticle p = new LampSparkParticle(level, x, y, z, xd, yd, zd, kind);
            p.pickSprite(sprites);
            return p;
        });
    }

    @Override
    public void tick() {
        super.tick();
        // a flick stays white; sparks and embers cool from white through yellow to gold
        if (kind != Kind.FLICK) tint((float) age / lifetime);
        if (kind == Kind.SPARK && onGround) remove();
    }

    /** White, then yellow, then gold; fades out over the last 40 % of its life. */
    private void tint(float t) {
        float g = t < 0.4F ? Mth.lerp(t / 0.4F, 1.0F, 0.88F) : Mth.lerp((t - 0.4F) / 0.6F, 0.88F, 0.62F);
        float b = t < 0.4F ? Mth.lerp(t / 0.4F, 1.0F, 0.45F) : Mth.lerp((t - 0.4F) / 0.6F, 0.45F, 0.18F);
        setColor(1.0F, g, b);
        setAlpha(t < 0.6F ? 0.95F : Mth.lerp((t - 0.6F) / 0.4F, 0.95F, 0.0F));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }
}
