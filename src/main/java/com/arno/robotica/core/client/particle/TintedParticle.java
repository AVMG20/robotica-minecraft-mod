package com.arno.robotica.core.client.particle;

import com.arno.robotica.core.particle.TintParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Base of the Robotica particles: tint and size from {@link TintParticleOptions}; glowing ones are full bright and
 * drawn with {@link CoreParticleRender#ADDITIVE}, the others are lit by the world and drawn translucent.
 */
abstract class TintedParticle extends TextureSheetParticle {
    protected final float tintR, tintG, tintB;
    protected final float scale;
    private final boolean glow;
    private int bouncesLeft;

    TintedParticle(ClientLevel level, double x, double y, double z, TintParticleOptions options, boolean glow) {
        super(level, x, y, z);
        this.tintR = options.red();
        this.tintG = options.green();
        this.tintB = options.blue();
        this.scale = options.scale();
        this.glow = glow;
        setColor(tintR, tintG, tintB);
    }

    /** 0 at birth, 1 at the end of the lifetime. */
    protected final float life(float partialTick) {
        return Mth.clamp((age + partialTick) / Math.max(1, lifetime), 0.0F, 1.0F);
    }

    /** Colour between white ({@code f} = 0) and the tint ({@code f} = 1). */
    protected final void whiteToTint(float f) {
        f = Mth.clamp(f, 0.0F, 1.0F);
        setColor(1.0F + (tintR - 1.0F) * f, 1.0F + (tintG - 1.0F) * f, 1.0F + (tintB - 1.0F) * f);
    }

    protected final void setBounces(int bounces) {
        this.bouncesLeft = bounces;
    }

    /**
     * Moves with block collision like vanilla, but a hit floor or ceiling bounces the particle back while bounces are
     * left (instead of vanilla's stop), and walls take most of the sideways speed.
     */
    protected final void moveBouncing(double dx, double dy, double dz) {
        if (dx == 0 && dy == 0 && dz == 0) return;
        Vec3 moved = dx * dx + dy * dy + dz * dz < 100.0
                ? Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), getBoundingBox(), level, List.of())
                : new Vec3(dx, dy, dz);
        setBoundingBox(getBoundingBox().move(moved.x, moved.y, moved.z));
        setLocationFromBoundingbox();
        onGround = false;
        if (moved.y != dy) {
            if (bouncesLeft > 0 && Math.abs(dy) > 0.04) {
                bouncesLeft--;
                yd = -dy * 0.42;
                xd *= 0.6;
                zd *= 0.6;
            } else {
                yd = 0;
                onGround = dy < 0;
            }
        }
        if (moved.x != dx) xd = -dx * 0.25;
        if (moved.z != dz) zd = -dz * 0.25;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return glow ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return glow ? CoreParticleRender.ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
