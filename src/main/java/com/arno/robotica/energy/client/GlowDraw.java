package com.arno.robotica.energy.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Additive glow shapes for {@code RenderType.lightning()} (position + colour quads, no texture): ribbons, prisms,
 * glowing cubes and jagged bolts. Every quad is drawn with both windings so it shows from any side.
 */
final class GlowDraw {
    private GlowDraw() {}

    /** A straight beam from a to b: two crossed ribbons. */
    static void beam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float width, int r, int g, int bl, int alpha) {
        beam(vc, m, a.x, a.y, a.z, b.x, b.y, b.z, width, r, g, bl, alpha);
    }

    /** {@link #beam(VertexConsumer, Matrix4f, Vec3, Vec3, float, int, int, int, int)} on plain numbers (no allocation, it runs per frame). */
    static void beam(VertexConsumer vc, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz,
                     float width, int r, int g, int bl, int alpha) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-3) return;
        dx /= len;
        dy /= len;
        dz /= len;
        // p1 = dir x ref (ref straight up, or along x for a near vertical beam), p2 = dir x p1
        double p1x, p1y, p1z;
        if (Math.abs(dy) < 0.95) {
            p1x = -dz;
            p1y = 0;
            p1z = dx;
        } else {
            p1x = 0;
            p1y = dz;
            p1z = -dy;
        }
        double n1 = Math.sqrt(p1x * p1x + p1y * p1y + p1z * p1z);
        p1x /= n1;
        p1y /= n1;
        p1z /= n1;
        double p2x = dy * p1z - dz * p1y, p2y = dz * p1x - dx * p1z, p2z = dx * p1y - dy * p1x;
        ribbon(vc, m, ax, ay, az, bx, by, bz, p1x * width, p1y * width, p1z * width, r, g, bl, alpha);
        ribbon(vc, m, ax, ay, az, bx, by, bz, p2x * width, p2y * width, p2z * width, r, g, bl, alpha);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz,
                               double wx, double wy, double wz, int r, int g, int bl, int alpha) {
        quad(vc, m,
                (float) (ax - wx), (float) (ay - wy), (float) (az - wz),
                (float) (ax + wx), (float) (ay + wy), (float) (az + wz),
                (float) (bx + wx), (float) (by + wy), (float) (bz + wz),
                (float) (bx - wx), (float) (by - wy), (float) (bz - wz), r, g, bl, alpha);
    }

    /** A glowing cube of half size {@code h} around c. */
    static void cube(VertexConsumer vc, Matrix4f m, Vec3 c, float h, int r, int g, int bl, int alpha) {
        box(vc, m, (float) c.x - h, (float) c.y - h, (float) c.z - h, (float) c.x + h, (float) c.y + h, (float) c.z + h, r, g, bl, alpha);
    }

    /** A glowing box between two corners. */
    static void box(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                    int r, int g, int bl, int alpha) {
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, alpha);
        quad(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, bl, alpha);
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, bl, alpha);
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, alpha);
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, alpha);
        quad(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, bl, alpha);
    }

    /** Render thread only: re-seeded for every bolt, one for the bolts and one for their forks. */
    private static final java.util.Random BOLT = new java.util.Random(), FORK = new java.util.Random();

    /**
     * A jagged bolt from a to b in {@code steps} kinks that move up to {@code jitter} blocks sideways, re-rolled from
     * {@code seed}. Each kink is a bright core with a wider faint glow; {@code branches} short forks split off.
     */
    static void bolt(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, long seed, int steps, double jitter, float width,
                     int r, int g, int bl, int alpha, int branches) {
        bolt(vc, m, a.x, a.y, a.z, b.x, b.y, b.z, branches > 0 ? BOLT : FORK, seed, steps, jitter, width, r, g, bl, alpha, branches);
    }

    private static void bolt(VertexConsumer vc, Matrix4f m, double ax, double ay, double az, double bx, double by, double bz,
                             java.util.Random rnd, long seed, int steps, double jitter, float width,
                             int r, int g, int bl, int alpha, int branches) {
        rnd.setSeed(seed);
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double px = ax, py = ay, pz = az;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double nx = bx, ny = by, nz = bz;
            if (i < steps) {
                nx = ax + dx * t + (rnd.nextDouble() - 0.5) * 2 * jitter;
                ny = ay + dy * t + (rnd.nextDouble() - 0.5) * jitter;
                nz = az + dz * t + (rnd.nextDouble() - 0.5) * 2 * jitter;
            }
            beam(vc, m, px, py, pz, nx, ny, nz, width * 3.0F, r, g, bl, alpha / 4);
            beam(vc, m, px, py, pz, nx, ny, nz, width, Math.min(255, r + 80), Math.min(255, g + 80), 255, alpha);
            if (branches > 0 && i < steps && rnd.nextInt(steps) < branches) {
                double tx = nx + (rnd.nextDouble() - 0.5) * jitter * 4, ty = ny - rnd.nextDouble() * len / steps * 1.5,
                        tz = nz + (rnd.nextDouble() - 0.5) * jitter * 4;
                // forks never fork again and use their own random, so this bolt's sequence goes on unchanged
                bolt(vc, m, nx, ny, nz, tx, ty, tz, FORK, rnd.nextLong(), 3, jitter * 0.6, width * 0.6F, r, g, bl, alpha * 2 / 3, 0);
            }
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int bl, int alpha) {
        vc.addVertex(m, ax, ay, az).setColor(r, g, bl, alpha);
        vc.addVertex(m, bx, by, bz).setColor(r, g, bl, alpha);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, bl, alpha);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, bl, alpha);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, bl, alpha);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, bl, alpha);
        vc.addVertex(m, bx, by, bz).setColor(r, g, bl, alpha);
        vc.addVertex(m, ax, ay, az).setColor(r, g, bl, alpha);
    }
}
