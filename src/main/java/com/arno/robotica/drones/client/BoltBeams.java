package com.arno.robotica.drones.client;

import com.arno.robotica.drones.DronesClientConfig;
import com.arno.robotica.drones.net.SentryBoltPayload;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Buddy Bot shots from {@link SentryBoltPayload}, in the soft additive style of the structure effects: a muzzle flash,
 * a bright bolt head that streaks to the target with a straight beam behind it, a flash where it hits, then the beam
 * thins out and fades. Color by stance, thicker per Mk. A few sparks fly off the hit. Camera-facing strips and soft
 * discs in one batched render type; a shot allocates its record once when it arrives, nothing per frame. Render thread only.
 */
public final class BoltBeams {
    private BoltBeams() {}

    private static final RenderType GLOW = RenderType.create("robotica_bolt_beam", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 2048, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    /** Ticks a shot is drawn; the head takes at most MAX_TRAVEL of them to arrive. */
    private static final int LIFE = 9;
    private static final float MAX_TRAVEL = 2.5F;
    private static final double FAR = 64, GONE = 96;
    private static final int MAX_ACTIVE = 48, DISC = 10;
    private static final int WHITE = 0xF4FDFF;

    /** One shot. Built once when it arrives. */
    private static final class Shot {
        final double fx, fy, fz, tx, ty, tz;
        final int color, core;
        final float width, travel;
        final long start;
        final AABB bounds;
        boolean hit;

        Shot(SentryBoltPayload p, long start) {
            fx = p.fx();
            fy = p.fy();
            fz = p.fz();
            tx = p.tx();
            ty = p.ty();
            tz = p.tz();
            color = p.color() & 0xFFFFFF;
            core = lerp(color, WHITE, 0.75F);
            width = 0.05F + 0.012F * (Mth.clamp(p.tier(), 1, 4) - 1);
            travel = Mth.clamp((float) p.length() / 10.0F, 1.0F, MAX_TRAVEL);
            this.start = start;
            bounds = new AABB(Math.min(fx, tx), Math.min(fy, ty), Math.min(fz, tz), Math.max(fx, tx), Math.max(fy, ty), Math.max(fz, tz)).inflate(0.5);
        }
    }

    private static final List<Shot> ACTIVE = new ArrayList<>();
    private static ClientLevel level;

    // ------------------------------------------------------------------ tick

    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            ACTIVE.clear();
            level = current;
        }
        if (current == null) {
            SentryBoltPayload.drain(p -> {});
            return;
        }
        long now = current.getGameTime();
        SentryBoltPayload.drain(BoltBeams::accept);
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Shot s = ACTIVE.get(i);
            long age = now - s.start;
            if (age > LIFE || age < 0) {
                ACTIVE.remove(i);
                continue;
            }
            if (!s.hit && age >= s.travel) {
                s.hit = true;
                sparks(current, s);
            }
        }
    }

    private static void accept(SentryBoltPayload p) {
        ClientLevel lvl = level;
        if (lvl == null) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(p.tx(), p.ty(), p.tz()) > GONE * GONE && cam.distanceToSqr(p.fx(), p.fy(), p.fz()) > GONE * GONE) return;
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        ACTIVE.add(new Shot(p, lvl.getGameTime()));
    }

    /** A few sparks thrown back off the hit. */
    private static void sparks(ClientLevel lvl, Shot s) {
        RandomSource random = lvl.random;
        double dx = s.fx - s.tx, dy = s.fy - s.ty, dz = s.fz - s.tz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0E-3) {
            dx /= len;
            dy /= len;
            dz /= len;
        }
        var spark = com.arno.robotica.core.particle.CoreParticles.spark(com.arno.robotica.core.particle.CoreParticles.ELECTRIC, 0.9F);
        for (int i = 0; i < 4; i++) {
            lvl.addParticle(spark, s.tx, s.ty, s.tz,
                    dx * 0.12 + random.nextGaussian() * 0.06, dy * 0.12 + random.nextGaussian() * 0.06 + 0.03, dz * 0.12 + random.nextGaussian() * 0.06);
        }
    }

    // ------------------------------------------------------------------ render

    private static final float[] COS = new float[DISC + 1], SIN = new float[DISC + 1];
    private static float leftX, leftY, leftZ, upX, upY, upZ;
    private static double camX, camY, camZ;

    static {
        for (int i = 0; i <= DISC; i++) {
            COS[i] = (float) Math.cos(Math.PI * 2 * i / DISC);
            SIN[i] = (float) Math.sin(Math.PI * 2 * i / DISC);
        }
    }

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl == null || lvl != level) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        leftX = left.x();
        leftY = left.y();
        leftZ = left.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = lvl.getGameTime();
        boolean beams = DronesClientConfig.boltTrails();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        for (int i = 0; i < ACTIVE.size(); i++) {
            Shot s = ACTIVE.get(i);
            float t = (tick - s.start) + partial;
            if (t < 0 || t > LIFE || !event.getFrustum().isVisible(s.bounds)) continue;
            double d = Math.sqrt(Math.min(sq(s.tx - camX) + sq(s.ty - camY) + sq(s.tz - camZ), sq(s.fx - camX) + sq(s.fy - camY) + sq(s.fz - camZ)));
            if (d > GONE) continue;
            float far = d <= FAR ? 1 : (float) (1 - (d - FAR) / (GONE - FAR));
            if (vc == null) vc = buffers.getBuffer(GLOW);
            draw(vc, s, t, far, beams);
        }
        if (vc != null) buffers.endBatch(GLOW);
    }

    private static void draw(VertexConsumer vc, Shot s, float t, float far, boolean beams) {
        float w = s.width;
        float head = Math.min(1, t / s.travel);
        double hx = s.fx + (s.tx - s.fx) * head, hy = s.fy + (s.ty - s.fy) * head, hz = s.fz + (s.tz - s.fz) * head;
        // after the hit the beam thins out and fades
        float after = Math.max(0, t - s.travel);
        float fade = 1 - smooth(after / (LIFE - s.travel));
        if (beams && fade > 0) {
            float thin = 0.35F + 0.65F * fade;
            // the tail lags behind the head while it flies, then the whole line is lit
            float tail = t < s.travel ? 0 : Math.min(1, after / (LIFE - s.travel) * 0.8F);
            double ax = s.fx + (s.tx - s.fx) * tail, ay = s.fy + (s.ty - s.fy) * tail, az = s.fz + (s.tz - s.fz) * tail;
            beam(vc, ax, ay, az, hx, hy, hz, w * 2.6F * thin, s.color, (int) (70 * fade * far), (int) (120 * fade * far));
            beam(vc, ax, ay, az, hx, hy, hz, w * 0.7F * thin, s.core, (int) (170 * fade * far), (int) (240 * fade * far));
        }
        // muzzle flash
        if (t < 3) {
            float m = 1 - t / 3;
            disc(vc, s.fx, s.fy, s.fz, w * 4.5F * (0.6F + 0.4F * m), s.color, (int) (150 * m * far));
            disc(vc, s.fx, s.fy, s.fz, w * 2.0F, s.core, (int) (230 * m * far));
        }
        // the bolt head while it flies
        if (t < s.travel + 0.5F) {
            float a = t < s.travel ? 1 : 1 - (t - s.travel) * 2;
            disc(vc, hx, hy, hz, w * 4.0F, s.color, (int) (130 * a * far));
            disc(vc, hx, hy, hz, w * 1.8F, WHITE, (int) (255 * a * far));
        }
        // the hit: a flash that grows and fades
        if (t >= s.travel && after < 5) {
            float u = after / 5;
            disc(vc, s.tx, s.ty, s.tz, w * (5 + 7 * u), s.color, (int) (170 * (1 - u) * far));
            disc(vc, s.tx, s.ty, s.tz, w * (2.5F + 1.5F * u), s.core, (int) (255 * (1 - u) * (1 - u) * far));
        }
    }

    /** A camera-facing line with soft edges: full alpha on the line ({@code a0} at the start, {@code a1} at the end), zero at {@code halfWidth}. */
    private static void beam(VertexConsumer vc, double wx0, double wy0, double wz0, double wx1, double wy1, double wz1, float halfWidth, int color,
                             int a0, int a1) {
        a0 = Math.min(255, a0);
        a1 = Math.min(255, a1);
        if (a0 <= 1 && a1 <= 1) return;
        float x0 = (float) (wx0 - camX), y0 = (float) (wy0 - camY), z0 = (float) (wz0 - camZ);
        float x1 = (float) (wx1 - camX), y1 = (float) (wy1 - camY), z1 = (float) (wz1 - camZ);
        float tx = x1 - x0, ty = y1 - y0, tz = z1 - z0;
        if (tx * tx + ty * ty + tz * tz < 1.0E-6F) return;
        // across the line, facing the camera (which sits at the origin here)
        float vx = -(x0 + x1) * 0.5F, vy = -(y0 + y1) * 0.5F, vz = -(z0 + z1) * 0.5F;
        float px = ty * vz - tz * vy, py = tz * vx - tx * vz, pz = tx * vy - ty * vx;
        float pl = Mth.sqrt(px * px + py * py + pz * pz);
        if (pl < 1.0E-6F) {
            px = leftX;
            py = leftY;
            pz = leftZ;
            pl = 1;
        }
        px = px / pl * halfWidth;
        py = py / pl * halfWidth;
        pz = pz / pl * halfWidth;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        vc.addVertex(x0 - px, y0 - py, z0 - pz).setColor(r, g, b, 0);
        vc.addVertex(x0, y0, z0).setColor(r, g, b, a0);
        vc.addVertex(x1, y1, z1).setColor(r, g, b, a1);
        vc.addVertex(x1 - px, y1 - py, z1 - pz).setColor(r, g, b, 0);
        vc.addVertex(x0, y0, z0).setColor(r, g, b, a0);
        vc.addVertex(x0 + px, y0 + py, z0 + pz).setColor(r, g, b, 0);
        vc.addVertex(x1 + px, y1 + py, z1 + pz).setColor(r, g, b, 0);
        vc.addVertex(x1, y1, z1).setColor(r, g, b, a1);
    }

    /** A camera-facing soft dot: full alpha in the middle, zero at the rim. */
    private static void disc(VertexConsumer vc, double wx, double wy, double wz, float radius, int color, int alpha) {
        if (alpha <= 1) return;
        alpha = Math.min(255, alpha);
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        float x = (float) (wx - camX), y = (float) (wy - camY), z = (float) (wz - camZ);
        for (int i = 0; i < DISC; i++) {
            float c0 = COS[i] * radius, s0 = SIN[i] * radius, c1 = COS[i + 1] * radius, s1 = SIN[i + 1] * radius;
            float x0 = x + leftX * c0 + upX * s0, y0 = y + leftY * c0 + upY * s0, z0 = z + leftZ * c0 + upZ * s0;
            float x1 = x + leftX * c1 + upX * s1, y1 = y + leftY * c1 + upY * s1, z1 = z + leftZ * c1 + upZ * s1;
            vc.addVertex(x, y, z).setColor(r, g, b, alpha);
            vc.addVertex(x0, y0, z0).setColor(r, g, b, 0);
            vc.addVertex(x1, y1, z1).setColor(r, g, b, 0);
            vc.addVertex(x1, y1, z1).setColor(r, g, b, 0);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static double sq(double v) {
        return v * v;
    }

    private static float smooth(float x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }

    private static int lerp(int c0, int c1, float t) {
        int r = (int) ((c0 >> 16 & 255) + ((c1 >> 16 & 255) - (c0 >> 16 & 255)) * t);
        int g = (int) ((c0 >> 8 & 255) + ((c1 >> 8 & 255) - (c0 >> 8 & 255)) * t);
        int b = (int) ((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return r << 16 | g << 8 | b;
    }
}
