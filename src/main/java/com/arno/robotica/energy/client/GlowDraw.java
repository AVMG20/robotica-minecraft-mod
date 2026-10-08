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
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 dir = d.normalize();
        Vec3 ref = Math.abs(dir.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 p1 = dir.cross(ref).normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        ribbon(vc, m, a, b, p1.scale(width), r, g, bl, alpha);
        ribbon(vc, m, a, b, p2.scale(width), r, g, bl, alpha);
    }

    static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 w, int r, int g, int bl, int alpha) {
        quad(vc, m,
                (float) (a.x - w.x), (float) (a.y - w.y), (float) (a.z - w.z),
                (float) (a.x + w.x), (float) (a.y + w.y), (float) (a.z + w.z),
                (float) (b.x + w.x), (float) (b.y + w.y), (float) (b.z + w.z),
                (float) (b.x - w.x), (float) (b.y - w.y), (float) (b.z - w.z), r, g, bl, alpha);
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

    /**
     * A jagged bolt from a to b in {@code steps} kinks that move up to {@code jitter} blocks sideways, re-rolled from
     * {@code seed}. Each kink is a bright core with a wider faint glow; {@code branches} short forks split off.
     */
    static void bolt(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, long seed, int steps, double jitter, float width,
                     int r, int g, int bl, int alpha, int branches) {
        java.util.Random rnd = new java.util.Random(seed);
        Vec3 prev = a;
        Vec3 d = b.subtract(a);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 next = i == steps ? b : a.add(d.scale(t)).add((rnd.nextDouble() - 0.5) * 2 * jitter,
                    (rnd.nextDouble() - 0.5) * jitter, (rnd.nextDouble() - 0.5) * 2 * jitter);
            beam(vc, m, prev, next, width * 3.0F, r, g, bl, alpha / 4);
            beam(vc, m, prev, next, width, Math.min(255, r + 80), Math.min(255, g + 80), 255, alpha);
            if (branches > 0 && i < steps && rnd.nextInt(steps) < branches) {
                Vec3 tip = next.add((rnd.nextDouble() - 0.5) * jitter * 4, -rnd.nextDouble() * d.length() / steps * 1.5,
                        (rnd.nextDouble() - 0.5) * jitter * 4);
                bolt(vc, m, next, tip, rnd.nextLong(), 3, jitter * 0.6, width * 0.6F, r, g, bl, alpha * 2 / 3, 0);
            }
            prev = next;
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
