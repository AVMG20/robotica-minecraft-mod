package com.arno.robotica.core.client.particle;

import com.arno.robotica.core.particle.TintParticleOptions;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** The particle classes behind {@link com.arno.robotica.core.particle.CoreParticles}. Client only. */
final class CoreParticleKinds {
    private CoreParticleKinds() {}

    /** White-hot streak: gravity, two bounces, cools from white to the tint and fades at the end. */
    static final class Spark extends TintedParticle {
        Spark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, o, true);
            xd = vx;
            yd = vy;
            zd = vz;
            gravity = 0.85F;
            friction = 0.95F;
            lifetime = 10 + random.nextInt(12);
            quadSize = 0.03F * scale;
            setSize(0.04F, 0.04F);
            setBounces(2);
            pickSprite(sprites);
            whiteToTint(0);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            yd -= 0.04 * gravity;
            moveBouncing(xd, yd, zd);
            xd *= friction;
            yd *= friction;
            zd *= friction;
            if (onGround) {
                xd *= 0.6;
                zd *= 0.6;
            }
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float t = life(partialTick);
            whiteToTint(t * 2.2F);
            alpha = t < 0.6F ? 1.0F : 1.0F - (t - 0.6F) / 0.4F;
            Vec3 cam = camera.getPosition();
            float px = (float) (Mth.lerp(partialTick, xo, x) - cam.x);
            float py = (float) (Mth.lerp(partialTick, yo, y) - cam.y);
            float pz = (float) (Mth.lerp(partialTick, zo, z) - cam.z);
            float vx = (float) (x - xo), vy = (float) (y - yo), vz = (float) (z - zo);
            float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed < 0.01F) {
                super.render(buffer, camera, partialTick);
                return;
            }
            float dx = vx / speed, dy = vy / speed, dz = vz / speed;
            // side = dir x view: the streak's width faces the camera, its length runs along the motion
            float sx = dy * pz - dz * py, sy = dz * px - dx * pz, sz = dx * py - dy * px;
            float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
            if (sl < 1.0E-5F) {
                super.render(buffer, camera, partialTick);
                return;
            }
            float w = quadSize;
            sx *= w / sl;
            sy *= w / sl;
            sz *= w / sl;
            float tail = Math.min(0.45F, speed * 1.4F) + w;
            float hx = px + dx * w, hy = py + dy * w, hz = pz + dz * w;
            float tx = px - dx * tail, ty = py - dy * tail, tz = pz - dz * tail;
            int light = getLightColor(partialTick);
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            vertex(buffer, hx + sx, hy + sy, hz + sz, u1, v0, light);
            vertex(buffer, tx + sx, ty + sy, tz + sz, u1, v1, light);
            vertex(buffer, tx - sx, ty - sy, tz - sz, u0, v1, light);
            vertex(buffer, hx - sx, hy - sy, hz - sz, u0, v0, light);
        }

        private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
            buffer.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
        }
    }

    /** Tiny jagged flash: a new random shape every tick, flickering, gone after a few ticks. */
    static final class Zap extends TintedParticle {
        private final SpriteSet sprites;

        Zap(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, o, true);
            this.sprites = sprites;
            xd = vx * 0.2;
            yd = vy * 0.2;
            zd = vz * 0.2;
            hasPhysics = false;
            lifetime = 3 + random.nextInt(3);
            quadSize = (0.1F + random.nextFloat() * 0.06F) * scale;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!isAlive()) return;
            pickSprite(sprites);
            oRoll = roll = random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float t = life(partialTick);
            whiteToTint(0.25F + t);
            alpha = ((age & 1) == 0 ? 1.0F : 0.55F) * (1.0F - t * 0.5F);
            super.render(buffer, camera, partialTick);
        }
    }

    /** Soft light dot: drifts with its speed, eases in and fades out. */
    static final class Mote extends TintedParticle {
        Mote(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, o, true);
            xd = vx;
            yd = vy;
            zd = vz;
            hasPhysics = false;
            friction = 0.93F;
            lifetime = 18 + random.nextInt(18);
            quadSize = (0.07F + random.nextFloat() * 0.03F) * scale;
            pickSprite(sprites);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float t = life(partialTick);
            alpha = t < 0.15F ? t / 0.15F : t > 0.5F ? 1.0F - (t - 0.5F) / 0.5F : 1.0F;
            whiteToTint(0.6F + t);
            super.render(buffer, camera, partialTick);
        }

        @Override
        public float getQuadSize(float partialTick) {
            return quadSize * (1.0F - 0.4F * life(partialTick));
        }
    }

    /** Soft puff in the tint: swells, drifts up a little and thins out. Lit by the world. */
    static final class Puff extends TintedParticle {
        private final SpriteSet sprites;

        Puff(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, o, false);
            this.sprites = sprites;
            xd = vx;
            yd = vy;
            zd = vz;
            friction = 0.88F;
            gravity = -0.02F;
            lifetime = 16 + random.nextInt(14);
            quadSize = (0.14F + random.nextFloat() * 0.06F) * scale;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            float shade = 0.92F + random.nextFloat() * 0.16F;
            setColor(Math.min(1, tintR * shade), Math.min(1, tintG * shade), Math.min(1, tintB * shade));
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!isAlive()) return;
            setSpriteFromAge(sprites);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float t = life(partialTick);
            alpha = 0.7F * (1.0F - t * t);
            super.render(buffer, camera, partialTick);
        }

        @Override
        public float getQuadSize(float partialTick) {
            return quadSize * (0.7F + 0.9F * Mth.sqrt(life(partialTick)));
        }
    }

    /**
     * Vortex mote circling its spawn point. x speed = radius (positive spirals in, negative spirals out), y speed = rise
     * per tick, z speed = turn per tick (0: 0.32 rad).
     */
    static final class Warp extends TintedParticle {
        private final double cx, cz;
        private final double radius;
        private final boolean inward;
        private final double rise, turn;
        private double angle;

        Warp(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, o, true);
            cx = x;
            cz = z;
            inward = vx >= 0;
            radius = Math.max(0.05, Math.abs(vx));
            rise = vy;
            turn = vz == 0 ? 0.32 : vz;
            angle = random.nextDouble() * Math.PI * 2;
            hasPhysics = false;
            lifetime = inward ? 16 + random.nextInt(8) : 12 + random.nextInt(8);
            quadSize = (0.05F + random.nextFloat() * 0.03F) * scale;
            pickSprite(sprites);
            place();
            xo = this.x;
            yo = this.y;
            zo = this.z;
        }

        private void place() {
            float t = (float) age / lifetime;
            double r = inward ? radius * Math.pow(1.0 - t, 1.4) : radius * (0.1 + 0.9 * Math.sqrt(t));
            setPos(cx + Math.cos(angle) * r, y, cz + Math.sin(angle) * r);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            float t = (float) age / lifetime;
            angle += turn * (inward ? 0.7 + 1.6 * t : 1.6 - t);
            y += rise;
            place();
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float t = life(partialTick);
            // keep most of the tint: additive white washes out against a bright sky
            if (inward) {
                alpha = Math.min(1.0F, t * 4.0F) * 0.85F;
                whiteToTint(1.0F - 0.5F * t * t);
            } else {
                alpha = (1.0F - t * t) * 0.85F;
                whiteToTint(0.5F + t * 1.5F);
            }
            super.render(buffer, camera, partialTick);
        }
    }

    /** Metal fragment: tumbles, falls, bounces once and shrinks away. Lit by the world. */
    static final class Shard extends TintedParticle {
        private final float spin;

        Shard(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, o, false);
            xd = vx;
            yd = vy;
            zd = vz;
            gravity = 1.0F;
            friction = 0.97F;
            lifetime = 18 + random.nextInt(16);
            quadSize = (0.04F + random.nextFloat() * 0.03F) * scale;
            setSize(0.06F, 0.06F);
            setBounces(1);
            spin = (random.nextFloat() - 0.5F) * 0.8F;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            float shade = 0.75F + random.nextFloat() * 0.35F;
            setColor(Math.min(1, tintR * shade), Math.min(1, tintG * shade), Math.min(1, tintB * shade));
            pickSprite(sprites);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            oRoll = roll;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            yd -= 0.04 * gravity;
            moveBouncing(xd, yd, zd);
            xd *= friction;
            yd *= friction;
            zd *= friction;
            if (onGround) {
                xd *= 0.5;
                zd *= 0.5;
            } else {
                roll += spin;
            }
        }

        @Override
        public float getQuadSize(float partialTick) {
            float left = lifetime - (age + partialTick);
            return left < 5 ? quadSize * Math.max(0, left) / 5 : quadSize;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    /** Short bright burst that swells and fades from white to the tint. */
    static final class Flash extends TintedParticle {
        Flash(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, TintParticleOptions o, SpriteSet sprites) {
            super(level, x, y, z, o, true);
            hasPhysics = false;
            lifetime = 5 + (scale > 2.0F ? 2 : 0);
            quadSize = 0.5F * scale;
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            pickSprite(sprites);
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            float t = life(partialTick);
            alpha = (1.0F - t) * (1.0F - t) * 0.8F;
            whiteToTint(0.4F + t * 1.5F);
            super.render(buffer, camera, partialTick);
        }

        @Override
        public float getQuadSize(float partialTick) {
            return quadSize * (0.45F + 0.55F * Mth.sqrt(life(partialTick)));
        }
    }
}
