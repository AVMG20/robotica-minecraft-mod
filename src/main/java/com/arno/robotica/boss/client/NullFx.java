package com.arno.robotica.boss.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Glow geometry of the Null Sentinel's telegraphs: camera-facing soft strips (a line with a bright middle that fades to
 * nothing at its edges), soft dots and flat soft rings, all additive. Render thread only; every helper works in the
 * space of the current pose with the camera at {@link #camera}, from static scratch arrays (nothing allocated per frame).
 */
final class NullFx {
    private NullFx() {}

    /** Additive like lightning, no depth writes (soft edges never hide glow behind them), no culling (one winding). */
    static final RenderType GLOW = RenderType.create("robotica_null_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.WEATHER_TARGET)
                    .createCompositeState(false));

    /**
     * Plain alpha blending for the dark underlay of the floor telegraphs: additive glow alone washes out on bright end
     * stone, so a ring first darkens the floor under it.
     */
    static final RenderType SHADE = RenderType.create("robotica_null_shade", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .createCompositeState(false));

    static final int MAX = 96;
    /** Points of the line being drawn: position, alpha factor, half width. */
    static final float[] X = new float[MAX], Y = new float[MAX], Z = new float[MAX], A = new float[MAX], W = new float[MAX];
    private static final float[] WX = new float[MAX], WY = new float[MAX], WZ = new float[MAX];
    private static final int DISC = 12;
    private static final float[] COS = new float[DISC + 1], SIN = new float[DISC + 1];

    static {
        for (int i = 0; i <= DISC; i++) {
            COS[i] = Mth.cos(i * Mth.TWO_PI / DISC);
            SIN[i] = Mth.sin(i * Mth.TWO_PI / DISC);
        }
    }

    private static float camX, camY, camZ;

    /** Where the camera is in the current pose's space (set once per render call). */
    static void camera(double x, double y, double z) {
        camX = (float) x;
        camY = (float) y;
        camZ = (float) z;
    }

    /**
     * A camera-facing strip along the first {@code n} points of {@link #X}/{@link #Y}/{@link #Z}: full {@code alpha}
     * (0-255, times {@link #A}) on the line, nothing at {@link #W} times {@code scale} to either side.
     */
    static void strip(VertexConsumer vc, Matrix4f m, int n, float scale, int rgb, float alpha) {
        if (alpha <= 0.0F || n < 2) return;
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            float tx = X[i1] - X[i0], ty = Y[i1] - Y[i0], tz = Z[i1] - Z[i0];
            float vx = camX - X[i], vy = camY - Y[i], vz = camZ - Z[i];
            float wx = ty * vz - tz * vy, wy = tz * vx - tx * vz, wz = tx * vy - ty * vx;
            float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1.0E-6F) {
                wx = 0.0F;
                wy = 1.0F;
                wz = 0.0F;
                wl = 1.0F;
            }
            float w = W[i] * scale;
            WX[i] = wx / wl * w;
            WY[i] = wy / wl * w;
            WZ[i] = wz / wl * w;
        }
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        for (int i = 0; i + 1 < n; i++) {
            int a0 = Mth.clamp((int) (alpha * A[i]), 0, 255), a1 = Mth.clamp((int) (alpha * A[i + 1]), 0, 255);
            if (a0 == 0 && a1 == 0) continue;
            float x0 = X[i], y0 = Y[i], z0 = Z[i], x1 = X[i + 1], y1 = Y[i + 1], z1 = Z[i + 1];
            vc.addVertex(m, x0 - WX[i], y0 - WY[i], z0 - WZ[i]).setColor(r, g, b, 0);
            vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
            vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a1);
            vc.addVertex(m, x1 - WX[i + 1], y1 - WY[i + 1], z1 - WZ[i + 1]).setColor(r, g, b, 0);
            vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
            vc.addVertex(m, x0 + WX[i], y0 + WY[i], z0 + WZ[i]).setColor(r, g, b, 0);
            vc.addVertex(m, x1 + WX[i + 1], y1 + WY[i + 1], z1 + WZ[i + 1]).setColor(r, g, b, 0);
            vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a1);
        }
    }

    /** A camera-facing soft dot: {@code alpha} in the middle, nothing at {@code radius}. */
    static void dot(VertexConsumer vc, Matrix4f m, float x, float y, float z, float radius, int rgb, float alpha) {
        int a = Mth.clamp((int) alpha, 0, 255);
        if (a == 0 || radius <= 0.0F) return;
        float vx = camX - x, vy = camY - y, vz = camZ - z;
        float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (vl < 1.0E-4F) return;
        vx /= vl;
        vy /= vl;
        vz /= vl;
        // two axes across the view direction
        float ux = 0.0F, uy = 1.0F, uz = 0.0F;
        if (Math.abs(vy) > 0.95F) {
            ux = 1.0F;
            uy = 0.0F;
        }
        float lx = uy * vz - uz * vy, ly = uz * vx - ux * vz, lz = ux * vy - uy * vx;
        float ll = Mth.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll;
        ly /= ll;
        lz /= ll;
        float upx = vy * lz - vz * ly, upy = vz * lx - vx * lz, upz = vx * ly - vy * lx;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        for (int i = 0; i < DISC; i++) {
            float c0 = COS[i] * radius, s0 = SIN[i] * radius, c1 = COS[i + 1] * radius, s1 = SIN[i + 1] * radius;
            vc.addVertex(m, x, y, z).setColor(r, g, b, a);
            vc.addVertex(m, x + lx * c0 + upx * s0, y + ly * c0 + upy * s0, z + lz * c0 + upz * s0).setColor(r, g, b, 0);
            vc.addVertex(m, x + lx * c1 + upx * s1, y + ly * c1 + upy * s1, z + lz * c1 + upz * s1).setColor(r, g, b, 0);
            vc.addVertex(m, x, y, z).setColor(r, g, b, a);
        }
    }

    /**
     * A flat soft ring on the floor: {@code alpha} along {@code radius}, nothing {@code halfWidth} inside and outside.
     * With {@code dashes} > 0 the brightness runs in that many soft dashes, turned by {@code spin} radians.
     */
    static void ring(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float radius, float halfWidth, int rgb, float alpha,
                     int dashes, float spin) {
        if (alpha <= 0.0F || radius <= 0.0F) return;
        int segments = Mth.clamp((int) (radius * 20.0F), 24, 96);
        float in = Math.max(0.0F, radius - halfWidth), out = radius + halfWidth;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        for (int i = 0; i < segments; i++) {
            float t0 = i * Mth.TWO_PI / segments, t1 = (i + 1) * Mth.TWO_PI / segments;
            float k0 = dashes > 0 ? 0.35F + 0.65F * Math.max(0.0F, Mth.sin(t0 * dashes + spin)) : 1.0F;
            float k1 = dashes > 0 ? 0.35F + 0.65F * Math.max(0.0F, Mth.sin(t1 * dashes + spin)) : 1.0F;
            int a0 = Mth.clamp((int) (alpha * k0), 0, 255), a1 = Mth.clamp((int) (alpha * k1), 0, 255);
            if (a0 == 0 && a1 == 0) continue;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            vc.addVertex(m, cx + c0 * in, cy, cz + s0 * in).setColor(r, g, b, 0);
            vc.addVertex(m, cx + c0 * radius, cy, cz + s0 * radius).setColor(r, g, b, a0);
            vc.addVertex(m, cx + c1 * radius, cy, cz + s1 * radius).setColor(r, g, b, a1);
            vc.addVertex(m, cx + c1 * in, cy, cz + s1 * in).setColor(r, g, b, 0);
            vc.addVertex(m, cx + c0 * radius, cy, cz + s0 * radius).setColor(r, g, b, a0);
            vc.addVertex(m, cx + c0 * out, cy, cz + s0 * out).setColor(r, g, b, 0);
            vc.addVertex(m, cx + c1 * out, cy, cz + s1 * out).setColor(r, g, b, 0);
            vc.addVertex(m, cx + c1 * radius, cy, cz + s1 * radius).setColor(r, g, b, a1);
        }
    }

    /** A flat soft disc on the floor: {@code alpha} out to two thirds of {@code radius}, then fading to nothing. */
    static void floorGlow(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float radius, int rgb, float alpha) {
        band(vc, m, cx, cy, cz, 0.0F, 0.0F, radius * 0.66F, radius, rgb, alpha, 0, 0.0F);
    }

    /**
     * A flat ring with a solid middle: nothing at {@code r0}, {@code alpha} from {@code r1} to {@code r2}, nothing at
     * {@code r3}. Dashes as in {@link #ring}.
     */
    static void band(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r0, float r1, float r2, float r3, int rgb, float alpha,
                     int dashes, float spin) {
        if (alpha <= 0.0F || r3 <= 0.0F) return;
        int segments = Mth.clamp((int) (r3 * 20.0F), 24, 96);
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        for (int i = 0; i < segments; i++) {
            float t0 = i * Mth.TWO_PI / segments, t1 = (i + 1) * Mth.TWO_PI / segments;
            float k0 = dashes > 0 ? 0.35F + 0.65F * Math.max(0.0F, Mth.sin(t0 * dashes + spin)) : 1.0F;
            float k1 = dashes > 0 ? 0.35F + 0.65F * Math.max(0.0F, Mth.sin(t1 * dashes + spin)) : 1.0F;
            int a0 = Mth.clamp((int) (alpha * k0), 0, 255), a1 = Mth.clamp((int) (alpha * k1), 0, 255);
            if (a0 == 0 && a1 == 0) continue;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            if (r1 > r0) {
                vc.addVertex(m, cx + c0 * r0, cy, cz + s0 * r0).setColor(r, g, b, 0);
                vc.addVertex(m, cx + c0 * r1, cy, cz + s0 * r1).setColor(r, g, b, a0);
                vc.addVertex(m, cx + c1 * r1, cy, cz + s1 * r1).setColor(r, g, b, a1);
                vc.addVertex(m, cx + c1 * r0, cy, cz + s1 * r0).setColor(r, g, b, 0);
            }
            vc.addVertex(m, cx + c0 * r1, cy, cz + s0 * r1).setColor(r, g, b, a0);
            vc.addVertex(m, cx + c0 * r2, cy, cz + s0 * r2).setColor(r, g, b, a0);
            vc.addVertex(m, cx + c1 * r2, cy, cz + s1 * r2).setColor(r, g, b, a1);
            vc.addVertex(m, cx + c1 * r1, cy, cz + s1 * r1).setColor(r, g, b, a1);
            vc.addVertex(m, cx + c0 * r2, cy, cz + s0 * r2).setColor(r, g, b, a0);
            vc.addVertex(m, cx + c0 * r3, cy, cz + s0 * r3).setColor(r, g, b, 0);
            vc.addVertex(m, cx + c1 * r3, cy, cz + s1 * r3).setColor(r, g, b, 0);
            vc.addVertex(m, cx + c1 * r2, cy, cz + s1 * r2).setColor(r, g, b, a1);
        }
    }

    /** Cheap smooth noise in [-1, 1] along a line, scrolling with {@code t}. */
    static float wave(float s, float t, int seed) {
        return 0.6F * Mth.sin(s * 2.3F - t * 0.9F + seed) + 0.4F * Mth.sin(s * 5.1F + t * 1.7F + seed * 1.7F);
    }
}
