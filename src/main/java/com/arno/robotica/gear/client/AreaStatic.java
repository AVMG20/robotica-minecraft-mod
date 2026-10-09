package com.arno.robotica.gear.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Static pops of an area, vein or tree break: on a few of the broken blocks (picked by the server, one
 * {@link com.arno.robotica.gear.GearFxPayload} per batch) a tiny flickering micro-arc with a flash and a few sparks,
 * each starting a little later than the last. Drawn with the Spark Lamp sheet (SparkWispRenderer helpers) from a fixed
 * pool, so nothing is allocated per frame.
 */
final class AreaStatic {
    private AreaStatic() {}

    private static final int MAX = 32;
    /** Ticks a pop's arcs flicker; its sparks fly for up to {@link #SPARK_TICKS}. */
    private static final int ARC_TICKS = 4, SPARK_TICKS = 9;
    private static final float[] X = new float[MAX], Y = new float[MAX], Z = new float[MAX];
    private static final long[] START = new long[MAX];
    private static final int[] SEED = new int[MAX];
    private static int next;
    private static long activeUntil = Long.MIN_VALUE;
    private static ClientLevel level;

    static {
        java.util.Arrays.fill(START, Long.MIN_VALUE / 2);
    }

    /** Queues a pop on each block centre in {@code p} (x, y, z each), with a random delay of up to 6 ticks. */
    static void add(ClientLevel current, float[] p) {
        if (Minecraft.getInstance().options.particles().get() == ParticleStatus.MINIMAL) return;
        if (current != level) {
            level = current;
            activeUntil = Long.MIN_VALUE;
        }
        RandomSource r = current.random;
        long now = current.getGameTime();
        for (int i = 0; i + 2 < p.length; i += 3) {
            int k = next;
            next = (next + 1) % MAX;
            X[k] = p[i] + (r.nextFloat() - 0.5F) * 0.5F;
            Y[k] = p[i + 1] + (r.nextFloat() - 0.5F) * 0.5F;
            Z[k] = p[i + 2] + (r.nextFloat() - 0.5F) * 0.5F;
            START[k] = now + r.nextInt(7);
            SEED[k] = r.nextInt();
            activeUntil = Math.max(activeUntil, START[k] + SPARK_TICKS);
        }
    }

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || activeUntil == Long.MIN_VALUE) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current == null || current != level || current.getGameTime() > activeUntil) {
            activeUntil = Long.MIN_VALUE;
            return;
        }
        SparkWispRenderer.beginFrame(event, current);
        long tick = SparkWispRenderer.tick;
        float partial = SparkWispRenderer.partial;
        double camX = SparkWispRenderer.camX, camY = SparkWispRenderer.camY, camZ = SparkWispRenderer.camZ;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        VertexConsumer vc = buffers.getBuffer(SparkWispRenderer.GLOW);
        for (int k = 0; k < MAX; k++) {
            float since = (tick - START[k]) + partial;
            if (since < 0 || since >= ARC_TICKS) continue;
            float a = since / ARC_TICKS, fade = (1 - a * a) * ((tick & 1) == 0 ? 1 : 0.7F);
            float x = (float) (X[k] - camX), y = (float) (Y[k] - camY), z = (float) (Z[k] - camZ);
            SparkWispRenderer.sprite(vc, x, y, z, 0.2F * (1 - 0.5F * a), 0, SparkWispRenderer.HALO, SparkWispRenderer.INNER, 0.55F * (1 - a));
            // two short arcs out of the point, re-kinked every tick so they crackle
            for (int j = 0; j < 2; j++) {
                int seed = SEED[k] + j * 7717, flick = seed + (int) tick * 7919;
                float yaw = SparkWispRenderer.hash(seed) * Mth.TWO_PI, pitch = (SparkWispRenderer.hash(seed + 1) - 0.5F) * 2.6F;
                float len = 0.25F + 0.25F * SparkWispRenderer.hash(flick + 2);
                double ex = X[k] + Mth.cos(yaw) * Mth.cos(pitch) * len, ey = Y[k] + Mth.sin(pitch) * len, ez = Z[k] + Mth.sin(yaw) * Mth.cos(pitch) * len;
                SparkWispRenderer.bolt(vc, X[k], Y[k], Z[k], ex, ey, ez, flick, 0.08F, fade * 0.3F);
                SparkWispRenderer.bolt(vc, X[k], Y[k], Z[k], ex, ey, ez, flick, 0.028F, fade);
            }
        }
        buffers.endBatch(SparkWispRenderer.GLOW);

        vc = buffers.getBuffer(SparkWispRenderer.SPRITES);
        for (int k = 0; k < MAX; k++) {
            float since = (tick - START[k]) + partial;
            if (since < 0 || since >= SPARK_TICKS) continue;
            float x = (float) (X[k] - camX), y = (float) (Y[k] - camY), z = (float) (Z[k] - camZ);
            if (since < 2) SparkWispRenderer.sprite(vc, x, y, z, 0.05F * (1 - since / 2), 0, SparkWispRenderer.CORE, SparkWispRenderer.WHITE, 1);
            for (int j = 0; j < 3; j++) {
                int seed = SEED[k] + j * 389;
                float life = 5 + 4 * SparkWispRenderer.hash(seed + 3);
                if (since > life) continue;
                float vx = (SparkWispRenderer.hash(seed) - 0.5F) * 0.12F, vy = (SparkWispRenderer.hash(seed + 1) - 0.2F) * 0.1F,
                        vz = (SparkWispRenderer.hash(seed + 2) - 0.5F) * 0.12F;
                SparkWispRenderer.sprite(vc, x + vx * since, y + vy * since - 0.006F * since * since, z + vz * since, 0.018F, 0,
                        SparkWispRenderer.SPARK, SparkWispRenderer.SPARK_C, 1 - since / life);
            }
        }
        buffers.endBatch(SparkWispRenderer.SPRITES);
    }
}
