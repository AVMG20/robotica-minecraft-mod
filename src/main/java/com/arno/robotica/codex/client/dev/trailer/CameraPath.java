package com.arno.robotica.codex.client.dev.trailer;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only camera track: keyframes (scene tick, position, yaw, pitch, fov) sampled with Catmull-Rom splines, so the
 * motion is smooth through every key. The camera is always computed from scene time, never from frame count.
 */
public final class CameraPath {
    /** A sampled camera. Yaw and pitch are Minecraft degrees (yaw 0 looks south, 90 west). */
    public record Pose(double x, double y, double z, float yaw, float pitch, float fov) {}

    private record Key(double tick, Vec3 pos, double yaw, double pitch, double fov) {}

    private final List<Key> keys;
    private final boolean ease;

    private CameraPath(List<Key> keys, boolean ease) {
        this.keys = keys;
        this.ease = ease;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Key> keys = new ArrayList<>();
        private boolean ease;

        /** Adds a key. Ticks must increase. Angles may be given freely (they are unwrapped). */
        public Builder key(double tick, Vec3 pos, double yaw, double pitch, double fov) {
            double y = yaw;
            if (!keys.isEmpty()) {
                // keep the shortest way round from the previous key
                double prev = keys.get(keys.size() - 1).yaw;
                y = prev + Mth.wrapDegrees(yaw - prev);
            }
            keys.add(new Key(tick, pos, y, pitch, fov));
            return this;
        }

        /** Key that looks at {@code target}. */
        public Builder keyLookAt(double tick, Vec3 pos, Vec3 target, double fov) {
            float[] a = angles(pos, target);
            return key(tick, pos, a[0], a[1], fov);
        }

        /** Slow start and end: scene time is remapped with a smootherstep over the whole path. */
        public Builder easeInOut() {
            ease = true;
            return this;
        }

        public CameraPath build() {
            if (keys.size() < 2) throw new IllegalStateException("A camera path needs at least two keys");
            return new CameraPath(List.copyOf(keys), ease);
        }
    }

    /** Yaw and pitch (degrees) that look from {@code from} to {@code to}. */
    public static float[] angles(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new float[]{yaw, pitch};
    }

    /**
     * Orbit around {@code center}: camera {@code height} above it at {@code radius}, always looking at the centre
     * (raised by {@code lookHeight}). The camera yaw goes from {@code startYaw} to {@code endYaw} degrees.
     */
    public static CameraPath orbit(Vec3 center, double radius, double height, double lookHeight, double startYaw, double endYaw,
                                   int durationTicks, double fov) {
        Builder b = builder();
        Vec3 target = center.add(0, lookHeight, 0);
        int keys = Math.max(4, durationTicks / 8);
        for (int i = 0; i <= keys; i++) {
            double u = i / (double) keys;
            double yaw = Math.toRadians(Mth.lerp(u, startYaw, endYaw));
            // camera behind the look direction: at yaw 0 it sits north of the centre and looks south
            Vec3 pos = center.add(radius * Math.sin(yaw), height, -radius * Math.cos(yaw));
            b.keyLookAt(u * durationTicks, pos, target, fov);
        }
        return b.build();
    }

    /** Straight move from one position to another, looking at {@code lookAt}. */
    public static CameraPath dolly(Vec3 from, Vec3 to, Vec3 lookAt, int durationTicks, double fov) {
        return builder().keyLookAt(0, from, lookAt, fov).keyLookAt(durationTicks, to, lookAt, fov).build();
    }

    /** One fixed camera. */
    public static CameraPath lookAt(Vec3 pos, Vec3 target, int durationTicks, double fov) {
        return dolly(pos, pos, target, durationTicks, fov);
    }

    public double duration() {
        return keys.get(keys.size() - 1).tick;
    }

    /** The camera at scene time {@code tick} (clamped to the path). */
    public Pose at(double tick) {
        double first = keys.get(0).tick, last = duration();
        double t = Mth.clamp(tick, first, last);
        if (ease && last > first) {
            double u = (t - first) / (last - first);
            u = u * u * u * (u * (u * 6 - 15) + 10);
            t = first + u * (last - first);
        }
        int i = 0;
        while (i < keys.size() - 2 && t > keys.get(i + 1).tick) i++;
        Key k1 = keys.get(i), k2 = keys.get(i + 1);
        Key k0 = i > 0 ? keys.get(i - 1) : k1, k3 = i + 2 < keys.size() ? keys.get(i + 2) : k2;
        double span = k2.tick - k1.tick;
        double u = span <= 0 ? 0 : (t - k1.tick) / span;
        // end segments reuse the end key as the missing neighbour, which flattens the speed there a little
        double x = spline(k0.pos.x, k1.pos.x, k2.pos.x, k3.pos.x, u);
        double y = spline(k0.pos.y, k1.pos.y, k2.pos.y, k3.pos.y, u);
        double z = spline(k0.pos.z, k1.pos.z, k2.pos.z, k3.pos.z, u);
        double yaw = spline(k0.yaw, k1.yaw, k2.yaw, k3.yaw, u);
        double pitch = spline(k0.pitch, k1.pitch, k2.pitch, k3.pitch, u);
        double fov = spline(k0.fov, k1.fov, k2.fov, k3.fov, u);
        return new Pose(x, y, z, (float) yaw, (float) Mth.clamp(pitch, -90, 90), (float) fov);
    }

    private static double spline(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }
}
