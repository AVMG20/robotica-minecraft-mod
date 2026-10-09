package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.GearClientConfig;
import com.arno.robotica.gear.client.SparkWisps.Section;
import com.arno.robotica.gear.client.SparkWisps.Wisp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws every Spark Lamp wisp in two batched draw calls per frame (soft glow, then pixel sprites), additive and
 * full-bright, all as camera-facing quads. Level of detail by distance from the camera:
 * <ul>
 *   <li>up to {@link #NEAR}: the full wisp. It floats and drifts around its spot, breathes in a two-layer halo with a
 *   slowly turning flare, crackling filaments flicker round a white core, plus arcs, strike sparks, dripping sparks,
 *   the zap-in and the pop-out, and it brightens and leans toward a player close by;</li>
 *   <li>up to {@link #MID}: halo and core, still floating (3 quads);</li>
 *   <li>up to {@link #FAR}: two glows and a core dot (3 quads); beyond, nothing (the block light stays).</li>
 * </ul>
 * Sections out of range or out of view are skipped whole. Nothing is allocated per frame.
 */
public final class SparkWispRenderer {
    private SparkWispRenderer() {}

    static final double NEAR = 24, MID = 48, FAR = 96;

    private static final ResourceLocation GLOW_TEXTURE = Robotica.id("textures/misc/spark_wisp_glow.png");
    private static final ResourceLocation SPRITE_TEXTURE = Robotica.id("textures/misc/spark_wisp.png");

    /** Soft parts: linear filtered, additive, no depth write, drawn into the particle target (Fabulous) or main. */
    static final RenderType GLOW = type("robotica_spark_wisp_glow", GLOW_TEXTURE, true);
    /** Pixel parts: nearest filtered, otherwise like {@link #GLOW}. */
    static final RenderType SPRITES = type("robotica_spark_wisp", SPRITE_TEXTURE, false);

    private static RenderType type(String name, ResourceLocation texture, boolean blur) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 8192, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, blur, false))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setOutputState(RenderStateShard.PARTICLES_TARGET)
                        .createCompositeState(false));
    }

    // Sheet regions in 64 px texture units (u0, v0, u1, v1).
    private static final float T = 1 / 64.0F;
    static final float[] HALO = {0, 0, 32 * T, 32 * T}, FLARE = {32 * T, 0, 1, 32 * T}, ARC = {1 * T, 32 * T, 31 * T, 48 * T},
            RING = {32 * T, 32 * T, 1, 1}, CORE = {0, 32 * T, 15 * T, 47 * T}, SPARK = {16 * T, 32 * T, 19 * T, 35 * T};

    // Colours (r, g, b).
    static final int[] OUTER = {45, 115, 255}, INNER = {140, 210, 255}, FLARE_C = {160, 220, 255}, THREAD = {110, 195, 255},
            THREAD_HOT = {190, 235, 255}, WHITE = {235, 250, 255}, BOLT = {160, 215, 255}, SPARK_C = {200, 240, 255};

    private static final List<Wisp> VISIBLE = new ArrayList<>();
    // camera-relative frame state: camera position, sprite axes (right, up)
    private static float rightX, rightY, rightZ, upX, upY, upZ;
    private static float time;
    static float partial;
    static long tick;
    static double camX, camY, camZ;

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (SparkWisps.SECTIONS.isEmpty() && SparkWisps.GHOSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level != SparkWisps.level) return;
        beginFrame(event, level);
        boolean animated = GearClientConfig.lampAnimated(), effects = GearClientConfig.lampParticles();
        Frustum frustum = event.getFrustum();

        VISIBLE.clear();
        double sectionFar2 = (FAR + 14) * (FAR + 14);
        for (int s = 0; s < SparkWisps.SECTIONS.size(); s++) {
            Section section = SparkWisps.SECTIONS.get(s);
            AABB b = section.box;
            double cx = (b.minX + b.maxX) * 0.5 - camX, cy = (b.minY + b.maxY) * 0.5 - camY, cz = (b.minZ + b.maxZ) * 0.5 - camZ;
            if (cx * cx + cy * cy + cz * cz > sectionFar2 || !frustum.isVisible(b)) continue;
            for (Wisp w : section.wisps) {
                double dx = w.x - camX, dy = w.y - camY, dz = w.z - camZ, d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > FAR * FAR) continue;
                w.lod = d2 <= NEAR * NEAR ? (animated ? 0 : 1) : d2 <= MID * MID ? 1 : 2;
                w.distant = (float) Mth.clamp((Math.sqrt(d2) - 4) / 24, 0, 1);
                place(w, animated);
                VISIBLE.add(w);
            }
        }
        List<Wisp> ghosts = SparkWisps.GHOSTS;
        if (VISIBLE.isEmpty() && ghosts.isEmpty()) return;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(GLOW);
        for (int i = 0; i < VISIBLE.size(); i++) drawGlow(vc, VISIBLE.get(i), effects);
        for (int i = 0; i < ghosts.size(); i++) drawGhostGlow(vc, ghosts.get(i));
        buffers.endBatch(GLOW);
        vc = buffers.getBuffer(SPRITES);
        for (int i = 0; i < VISIBLE.size(); i++) drawSprites(vc, VISIBLE.get(i), effects);
        for (int i = 0; i < ghosts.size(); i++) drawGhostSprites(vc, ghosts.get(i));
        buffers.endBatch(SPRITES);
    }

    /** Sets the camera-relative frame state the drawing helpers use. Also called by {@link AreaStatic}. */
    static void beginFrame(RenderLevelStageEvent event, ClientLevel level) {
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        rightX = -left.x();
        rightY = -left.y();
        rightZ = -left.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();
        partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        tick = level.getGameTime();
        time = (tick % 24000L) + partial;
    }

    // ------------------------------------------------------------------ per wisp

    /** Where the wisp is this frame and how bright: float, drift, flicker, reaction, arc pull, zap-in. */
    private static void place(Wisp w, boolean animated) {
        float ph = w.phase;
        float react = Mth.lerp(partial, w.reactPrev, w.react);
        float arc = arcAge(w);
        float arcPull = arc < 1 ? 1 - arc : 0;
        double ox = 0, oy = 0, oz = 0;
        if (animated && w.lod < 2) {
            oy = 0.03 * Mth.sin(time * 0.07F + ph * 9) + 0.012 * Mth.sin(time * 0.19F + ph * 4);
            ox = 0.018 * Mth.sin(time * 0.045F + ph * 5);
            oz = 0.018 * Mth.cos(time * 0.052F + ph * 7);
            float lean = 0.05F * react;
            ox += w.leanX * lean;
            oy += w.leanY * lean;
            oz += w.leanZ * lean;
            if (arcPull > 0) {
                double dx = w.ax - w.x, dy = w.ay - w.y, dz = w.az - w.z, len = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
                ox += dx / len * 0.035 * arcPull;
                oy += dy / len * 0.035 * arcPull;
                oz += dz / len * 0.035 * arcPull;
            }
        }
        w.rx = w.x + ox;
        w.ry = w.y + oy;
        w.rz = w.z + oz;
        float flicker = animated ? 0.82F + 0.18F * noise(w.seed, time * 0.6F) : 1;
        w.glow = flicker * (1 + 0.35F * react + 0.3F * arcPull);
        float b = birthAge(w);
        w.scale = b >= 1 ? 1 : overshoot(b);
    }

    private static void drawGlow(VertexConsumer vc, Wisp w, boolean effects) {
        float x = (float) (w.rx - camX), y = (float) (w.ry - camY), z = (float) (w.rz - camZ);
        float g = w.glow, s = w.scale;
        if (w.lod == 2) {
            sprite(vc, x, y, z, 1.18F, 0, HALO, OUTER, 0.6F);           // as the near halo looks at MID
            sprite(vc, x, y, z, 0.27F, 0, HALO, INNER, 0.75F);
            return;
        }
        float breath = 0.5F + 0.5F * Mth.sin(time * 0.09F + w.phase * 6);
        float react = Mth.lerp(partial, w.reactPrev, w.react);
        // farther away the halo grows a little, so a lamp still reads as a glow across a cave
        float far = w.distant;
        sprite(vc, x, y, z, 0.75F * (0.92F + 0.12F * breath) * (1 + 0.25F * react) * (1 + 0.6F * far) * s, 0, HALO, OUTER,
                (0.4F + 0.15F * breath) * (1 + 0.3F * far) * g);
        sprite(vc, x, y, z, 0.27F * (0.95F + 0.1F * breath) * s, 0, HALO, INNER, 0.7F * g);
        if (w.lod != 0) return;
        sprite(vc, x, y, z, 0.44F * s, time * 0.02F + w.phase, FLARE, FLARE_C, 0.45F * g);

        float b = birthAge(w);
        if (b < 1) {                                            // zap-in: a flash and a ring running out
            float f = 1 - b;
            sprite(vc, x, y, z, 0.9F * f, 0, HALO, INNER, 0.8F * f);
            sprite(vc, x, y, z, 0.15F + 0.7F * b, 0, RING, INNER, 0.7F * f);
            if (b < 0.6F) {
                for (int i = 0; i < 3; i++) {
                    int seed = w.seed + i * 977 + (int) (tick / 2) * 131;
                    float yaw = hash(seed) * Mth.TWO_PI, pitch = (hash(seed + 1) - 0.5F) * 2.4F, len = 0.35F + 0.25F * hash(seed + 2);
                    double ex = w.rx + Mth.cos(yaw) * Mth.cos(pitch) * len, ey = w.ry + Mth.sin(pitch) * len, ez = w.rz + Mth.sin(yaw) * Mth.cos(pitch) * len;
                    bolt(vc, w.rx, w.ry, w.rz, ex, ey, ez, seed, 0.035F, (1 - b / 0.6F) * 0.9F);
                }
            }
        }
        if (!effects) return;
        float arc = arcAge(w);
        if (arc < 1) {
            float fade = (1 - arc * arc) * ((tick & 1) == 0 ? 1 : 0.75F);
            int seed = w.arcSeed + (int) tick * 7919;
            bolt(vc, w.rx, w.ry, w.rz, w.ax, w.ay, w.az, seed, 0.13F, fade * 0.3F);
            bolt(vc, w.rx, w.ry, w.rz, w.ax, w.ay, w.az, seed, 0.045F, fade);
            if (w.arcHit) {
                sprite(vc, (float) (w.ax - camX), (float) (w.ay - camY), (float) (w.az - camZ), 0.16F * (1.2F - arc), 0, HALO, INNER, 0.9F * (1 - arc));
            }
        }
        float drip = dripAge(w);
        if (drip >= 0) {
            float t = drip * w.dripLife;
            sprite(vc, x0(w, t), y0(w, t), z0(w, t), 0.06F, 0, HALO, INNER, 0.45F * dripFade(drip));
        }
    }

    private static void drawSprites(VertexConsumer vc, Wisp w, boolean effects) {
        float x = (float) (w.rx - camX), y = (float) (w.ry - camY), z = (float) (w.rz - camZ);
        float s = w.scale;
        if (w.lod == 0) {
            int a = (int) (tick >> 1) + (w.seed & 0xFF), c = (int) (tick / 3) + (w.seed >>> 8 & 0xFF);
            int frameA = (int) (hash(a) * 8), frameB = (int) (hash(c + 5) * 8);
            if (frameB == frameA) frameB = (frameB + 3) & 7;
            sprite(vc, x, y, z, 0.2F * s, quarter(a + 11), thread(frameA), THREAD, 0.95F * Math.min(1, w.glow));
            sprite(vc, x, y, z, 0.155F * s, quarter(c + 17), thread(frameB), THREAD_HOT, 0.6F * Math.min(1, w.glow));
        }
        sprite(vc, x, y, z, (w.lod == 2 ? 0.1F : 0.085F) * s, 0, CORE, WHITE, 1);
        if (w.lod != 0 || !effects) return;
        // sparks thrown off where an arc struck, falling and fading
        float since = (tick - w.arcStart) + partial;
        if (w.arcHit && w.arcStart != Long.MIN_VALUE && since >= 0 && since < 14) {
            for (int k = 0; k < 4; k++) {
                int seed = w.arcSeed + k * 613;
                float life = 8 + 6 * hash(seed);
                if (since > life) continue;
                float vx = w.nx * 0.05F + (hash(seed + 1) - 0.5F) * 0.07F, vy = w.ny * 0.05F + (hash(seed + 2) - 0.5F) * 0.07F + 0.02F,
                        vz = w.nz * 0.05F + (hash(seed + 3) - 0.5F) * 0.07F;
                float px = (float) (w.ax - camX) + vx * since, py = (float) (w.ay - camY) + vy * since - 0.006F * since * since,
                        pz = (float) (w.az - camZ) + vz * since;
                sprite(vc, px, py, pz, 0.022F, 0, SPARK, SPARK_C, 1 - since / life);
            }
        }
        float drip = dripAge(w);
        if (drip >= 0) {
            float t = drip * w.dripLife;
            sprite(vc, x0(w, t), y0(w, t), z0(w, t), 0.02F, 0, SPARK, SPARK_C, dripFade(drip));
        }
    }

    /** Pop-out: the core flares and collapses, the halo shrinks, a ring pulls in and a few sparks scatter. */
    private static void drawGhostGlow(VertexConsumer vc, Wisp w) {
        float age = ((tick - w.death) + partial) / SparkWisps.GHOST_TICKS;
        if (age < 0 || age >= 1) return;
        float x = (float) (w.x - camX), y = (float) (w.y - camY), z = (float) (w.z - camZ);
        float pop = Math.min(1, age * 2);
        sprite(vc, x, y, z, 0.75F * (1 - pop), 0, HALO, OUTER, 0.55F * (1 - pop));
        sprite(vc, x, y, z, 0.2F + 0.35F * (1 - pop) * pop * 4, 0, HALO, INNER, 1 - pop);
        sprite(vc, x, y, z, 0.6F * (1 - pop), 0, RING, INNER, 0.6F * (1 - pop));
    }

    private static void drawGhostSprites(VertexConsumer vc, Wisp w) {
        float since = (tick - w.death) + partial;
        if (since < 0 || since >= SparkWisps.GHOST_TICKS) return;
        float x = (float) (w.x - camX), y = (float) (w.y - camY), z = (float) (w.z - camZ);
        if (since < 6) sprite(vc, x, y, z, 0.075F * (1 + since / 3) * (1 - since / 6), 0, CORE, WHITE, 1);
        for (int k = 0; k < 6; k++) {
            int seed = w.seed + k * 389;
            float vx = (hash(seed) - 0.5F) * 0.14F, vy = (hash(seed + 1) - 0.3F) * 0.12F, vz = (hash(seed + 2) - 0.5F) * 0.14F;
            sprite(vc, x + vx * since, y + vy * since - 0.006F * since * since, z + vz * since, 0.022F, 0, SPARK, SPARK_C,
                    1 - since / SparkWisps.GHOST_TICKS);
        }
    }

    // ------------------------------------------------------------------ timing helpers

    /** 0..1 through the running arc, 1 when none runs. */
    private static float arcAge(Wisp w) {
        if (w.arcStart == Long.MIN_VALUE) return 1;
        float a = ((tick - w.arcStart) + partial) / w.arcLen;
        return a < 0 || a >= 1 ? 1 : a;
    }

    private static float birthAge(Wisp w) {
        if (w.birth == Long.MIN_VALUE) return 1;
        float a = ((tick - w.birth) + partial) / SparkWisps.BIRTH_TICKS;
        return a < 0 ? 0 : Math.min(1, a);
    }

    /** 0..1 through the falling drip spark, -1 when none falls. */
    private static float dripAge(Wisp w) {
        if (w.dripStart == Long.MIN_VALUE || w.dripLife <= 0) return -1;
        float a = ((tick - w.dripStart) + partial) / w.dripLife;
        return a < 0 || a >= 1 ? -1 : a;
    }

    private static float dripFade(float age) {
        return age < 0.6F ? 1 : (1 - age) / 0.4F;
    }

    private static float x0(Wisp w, float t) {
        return (float) (w.x - camX) + (hash(w.seed + (int) w.dripStart) - 0.5F) * 0.004F * t;
    }

    private static float y0(Wisp w, float t) {
        return (float) (w.y - 0.03 - camY) + 0.01F * t - 0.5F * SparkWisps.DRIP_G * t * t;
    }

    private static float z0(Wisp w, float t) {
        return (float) (w.z - camZ) + (hash(w.seed * 3 + (int) w.dripStart) - 0.5F) * 0.004F * t;
    }

    /** Zap-in scale: overshoots a little, then settles at 1. */
    private static float overshoot(float t) {
        float c = 1.9F, u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static float quarter(int seed) {
        return (int) (hash(seed) * 4) * Mth.HALF_PI;
    }

    private static float[] thread(int frame) {
        float u = (frame & 3) * 16 * T, v = (frame >> 2) * 16 * T;
        THREAD_UV[0] = u;
        THREAD_UV[1] = v;
        THREAD_UV[2] = u + 15 * T;
        THREAD_UV[3] = v + 15 * T;
        return THREAD_UV;
    }

    private static final float[] THREAD_UV = new float[4];

    /** Smooth value noise in 0..1 along t, different per seed. */
    private static float noise(int seed, float t) {
        int i = Mth.floor(t);
        float f = t - i;
        f = f * f * (3 - 2 * f);
        return Mth.lerp(f, hash(seed + i * 0x632BE5AB), hash(seed + (i + 1) * 0x632BE5AB));
    }

    /** Integer hash to 0..1. */
    static float hash(int x) {
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }

    // ------------------------------------------------------------------ geometry

    /** A camera-facing square of half size {@code half} turned by {@code roll}, centred on a camera-relative point. */
    static void sprite(VertexConsumer vc, float x, float y, float z, float half, float roll, float[] uv, int[] rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (a <= 1 || half <= 0) return;
        float c = Mth.cos(roll) * half, s = Mth.sin(roll) * half;
        float ax = rightX * c + upX * s, ay = rightY * c + upY * s, az = rightZ * c + upZ * s;
        float bx = upX * c - rightX * s, by = upY * c - rightY * s, bz = upZ * c - rightZ * s;
        vertex(vc, x - ax - bx, y - ay - by, z - az - bz, uv[0], uv[3], rgb, a);
        vertex(vc, x + ax - bx, y + ay - by, z + az - bz, uv[2], uv[3], rgb, a);
        vertex(vc, x + ax + bx, y + ay + by, z + az + bz, uv[2], uv[1], rgb, a);
        vertex(vc, x - ax + bx, y - ay + by, z - az + bz, uv[0], uv[1], rgb, a);
    }

    /**
     * A jagged bolt from a to b (world coordinates): five or six kinks off the straight line, most in the middle, each
     * piece a camera-facing ribbon with a bright thread in a faint glow; now and then a short fork.
     */
    static void bolt(VertexConsumer vc, double ax, double ay, double az, double bx, double by, double bz, int seed, float width, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (a <= 1) return;
        float sx = (float) (ax - camX), sy = (float) (ay - camY), sz = (float) (az - camZ);
        float dx = (float) (bx - ax), dy = (float) (by - ay), dz = (float) (bz - az);
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.02F) return;
        // two directions across the bolt
        float p1x, p1y, p1z;
        if (Math.abs(dy) < 0.95F * len) {
            p1x = -dz;
            p1y = 0;
            p1z = dx;
        } else {
            p1x = 0;
            p1y = dz;
            p1z = -dy;
        }
        float n1 = Mth.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        p1x /= n1;
        p1y /= n1;
        p1z /= n1;
        float p2x = (dy * p1z - dz * p1y) / len, p2y = (dz * p1x - dx * p1z) / len, p2z = (dx * p1y - dy * p1x) / len;
        int steps = len < 0.6F ? 4 : 6;
        float amp = Math.min(0.22F, 0.2F * len);
        float px = sx, py = sy, pz = sz;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float nx = sx + dx * t, ny = sy + dy * t, nz = sz + dz * t;
            if (i < steps) {
                float env = Mth.sin(t * Mth.PI) * amp;
                float j1 = (hash(seed + i * 31) - 0.5F) * 2 * env, j2 = (hash(seed + i * 57) - 0.5F) * 2 * env;
                nx += p1x * j1 + p2x * j2;
                ny += p1y * j1 + p2y * j2;
                nz += p1z * j1 + p2z * j2;
                if (hash(seed + i * 91) > 0.72F) {          // a short fork
                    float fl = 0.12F + 0.15F * hash(seed + i * 13);
                    float fx = dx / len * 0.4F + (hash(seed + i * 17) - 0.5F) * 2, fy = dy / len * 0.4F + (hash(seed + i * 19) - 0.5F) * 2,
                            fz = dz / len * 0.4F + (hash(seed + i * 23) - 0.5F) * 2;
                    float fn = Mth.sqrt(fx * fx + fy * fy + fz * fz);
                    if (fn > 1.0E-3F) {
                        ribbon(vc, nx, ny, nz, nx + fx / fn * fl, ny + fy / fn * fl, nz + fz / fn * fl, width * 0.6F, a / 2);
                    }
                }
            }
            ribbon(vc, px, py, pz, nx, ny, nz, width, a);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    /** One straight bolt piece between camera-relative points, as wide as {@code width} to each side, facing the camera. */
    private static void ribbon(VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float width, int alpha) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) * 0.5F, my = (ay + by) * 0.5F, mz = (az + bz) * 0.5F;
        // across = segment x view direction
        float cx = dy * mz - dz * my, cy = dz * mx - dx * mz, cz = dx * my - dy * mx;
        float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1.0E-5F) return;
        cx = cx / cl * width;
        cy = cy / cl * width;
        cz = cz / cl * width;
        // overlap the joints a little
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        float ex = dx / len * width * 0.5F, ey = dy / len * width * 0.5F, ez = dz / len * width * 0.5F;
        ax -= ex;
        ay -= ey;
        az -= ez;
        bx += ex;
        by += ey;
        bz += ez;
        vertex(vc, ax - cx, ay - cy, az - cz, ARC[0], ARC[1], BOLT, alpha);
        vertex(vc, ax + cx, ay + cy, az + cz, ARC[0], ARC[3], BOLT, alpha);
        vertex(vc, bx + cx, by + cy, bz + cz, ARC[2], ARC[3], BOLT, alpha);
        vertex(vc, bx - cx, by - cy, bz - cz, ARC[2], ARC[1], BOLT, alpha);
    }

    private static void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int[] rgb, int alpha) {
        vc.addVertex(x, y, z).setColor(rgb[0], rgb[1], rgb[2], alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
