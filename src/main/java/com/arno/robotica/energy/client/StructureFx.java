package com.arno.robotica.energy.client;

import com.arno.robotica.core.CoreSounds;
import com.arno.robotica.energy.EnergyClientConfig;
import com.arno.robotica.energy.net.StructureFxPayload;
import com.arno.robotica.replicator.ReplicatorClientConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
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
 * One-off multiblock effects from {@link StructureFxPayload}, in the soft additive style of the Tesla links:
 * <ul>
 *   <li>forming: a flash on the controller, thin feeders run across its face to the frame, then bright heads with
 *   fading trails race along every frame edge (the shortest way round, so they split at the corners and meet on the far
 *   side), the whole frame pulses once and fades, and a chime rings where the light closes;</li>
 *   <li>breaking: the frame lights up, flickers and turns ember red while the light drains back into the controller,
 *   sparks fall where it lets go, and a few puffs of smoke rise;</li>
 *   <li>Replicator spawn: a ring of light on the floor, a column with motes spiralling up and a few portal particles.</li>
 * </ul>
 * Camera-facing strips and soft discs in one batched render type, from static scratch arrays: nothing is allocated per
 * frame (an effect allocates its edge table once when it arrives). Faded out with the distance, skipped off-screen.
 * Render thread only.
 */
public final class StructureFx {
    private StructureFx() {}

    private static final RenderType GLOW = RenderType.create("robotica_structure_fx", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    /** Forming: ticks the light takes to the far side, then the pulse and fade. */
    static final int TRAVEL = 26, AFTER = 18;
    /** Breaking: ticks the light takes to drain back, then the sparks' fall. */
    static final int DRAIN = 16, SPARK_LIFE = 12;
    static final int SPAWN_TICKS = 22;
    /** Full detail up to NEAR, coarser up to FAR, faded out until GONE. */
    private static final double NEAR = 32, FAR = 64, GONE = 96;
    private static final int MAX_ACTIVE = 16, SPARKS = 24, MOTES = 10;
    private static final int WHITE = 0xEBFAFF, EMBER = 0xFF6A3A;

    /** One running effect. Built once when it arrives; nothing in it changes while it is drawn. */
    static final class Fx {
        final int kind, color;
        final long start, seed;
        final AABB bounds;
        final double ox, oy, oz;
        /** Box corners (inflated a little, so the light sits on the frame and not inside it). */
        final double x0, y0, z0, x1, y1, z1;
        /** Frame edges, split where a feeder meets them; stride 9: ax ay az bx by bz length distA distB. */
        final double[] edges;
        final int edgeCount;
        /** Feeders from the controller across its face; stride 4: seed x y z, length. */
        final double[] feeders;
        final int feederCount;
        final double maxDist, farX, farY, farZ;
        boolean chimed;

        Fx(StructureFxPayload p, long now) {
            kind = p.kind();
            color = p.color() & 0xFFFFFF;
            start = now;
            seed = p.origin().asLong() * 0x9E3779B97F4A7C15L + now;
            double e = kind == StructureFxPayload.SPAWN ? 0 : 0.03;
            x0 = p.minX() - e;
            y0 = p.minY() - e;
            z0 = p.minZ() - e;
            x1 = p.maxX() + 1 + e;
            y1 = p.maxY() + 1 + e;
            z1 = p.maxZ() + 1 + e;
            bounds = new AABB(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1);
            if (kind == StructureFxPayload.SPAWN) {
                ox = (x0 + x1) * 0.5;
                oy = y0;
                oz = (z0 + z1) * 0.5;
                edges = feeders = new double[0];
                edgeCount = feederCount = 0;
                maxDist = farX = farY = farZ = 0;
                return;
            }
            // the controller's face: axis and side, and the controller's centre on that face
            int bx = p.origin().getX(), by = p.origin().getY(), bz = p.origin().getZ();
            int axis = -1, side = 0;
            if (bx == p.minX() || bx == p.maxX()) {
                axis = 0;
                side = bx == p.maxX() ? 1 : 0;
            } else if (bz == p.minZ() || bz == p.maxZ()) {
                axis = 2;
                side = bz == p.maxZ() ? 1 : 0;
            } else if (by == p.minY() || by == p.maxY()) {
                axis = 1;
                side = by == p.maxY() ? 1 : 0;
            }
            double px = bx + 0.5, py = by + 0.5, pz = bz + 0.5;
            if (axis == 0) px = side == 1 ? x1 : x0;
            if (axis == 1) py = side == 1 ? y1 : y0;
            if (axis == 2) pz = side == 1 ? z1 : z0;
            ox = px;
            oy = py;
            oz = pz;

            // graph: 8 corners, plus one node per face edge where its feeder lands
            double[] nx = new double[12], ny = new double[12], nz = new double[12], dist = new double[12];
            for (int i = 0; i < 8; i++) {
                nx[i] = (i & 1) != 0 ? x1 : x0;
                ny[i] = (i & 2) != 0 ? y1 : y0;
                nz[i] = (i & 4) != 0 ? z1 : z0;
                dist[i] = Double.MAX_VALUE;
            }
            int nodes = 8;
            int[] eu = new int[16], ev = new int[16];
            int sub = 0;
            double[] feed = new double[16];
            int feedCount = 0;
            for (int i = 0; i < 8; i++) {
                for (int bit = 0; bit < 3; bit++) {
                    int j = i | (1 << bit);
                    if (j == i) continue;
                    boolean onFace = axis >= 0 && axis != bit && ((i >> axis) & 1) == side && ((j >> axis) & 1) == side;
                    if (!onFace) {
                        eu[sub] = i;
                        ev[sub++] = j;
                        continue;
                    }
                    // the feeder lands where the controller's centre projects onto this edge
                    int s = nodes++;
                    nx[s] = nx[i];
                    ny[s] = ny[i];
                    nz[s] = nz[i];
                    if (bit == 0) nx[s] = Mth.clamp(px, x0, x1);
                    if (bit == 1) ny[s] = Mth.clamp(py, y0, y1);
                    if (bit == 2) nz[s] = Mth.clamp(pz, z0, z1);
                    double r = Math.sqrt(sq(nx[s] - px) + sq(ny[s] - py) + sq(nz[s] - pz));
                    dist[s] = r;
                    feed[feedCount * 4] = nx[s];
                    feed[feedCount * 4 + 1] = ny[s];
                    feed[feedCount * 4 + 2] = nz[s];
                    feed[feedCount * 4 + 3] = r;
                    feedCount++;
                    eu[sub] = i;
                    ev[sub++] = s;
                    eu[sub] = s;
                    ev[sub++] = j;
                }
            }
            if (axis < 0) {
                // no face found: start from the corner nearest the controller
                int best = 0;
                double bestD = Double.MAX_VALUE;
                for (int i = 0; i < 8; i++) {
                    double d = sq(nx[i] - px) + sq(ny[i] - py) + sq(nz[i] - pz);
                    if (d < bestD) {
                        bestD = d;
                        best = i;
                    }
                }
                dist[best] = 0;
            }
            double[] len = new double[sub];
            for (int k = 0; k < sub; k++) {
                len[k] = Math.abs(nx[eu[k]] - nx[ev[k]]) + Math.abs(ny[eu[k]] - ny[ev[k]]) + Math.abs(nz[eu[k]] - nz[ev[k]]);
            }
            for (int round = 0; round < nodes; round++) {
                for (int k = 0; k < sub; k++) {
                    int u = eu[k], v = ev[k];
                    if (dist[u] + len[k] < dist[v]) dist[v] = dist[u] + len[k];
                    if (dist[v] + len[k] < dist[u]) dist[u] = dist[v] + len[k];
                }
            }
            edges = new double[sub * 9];
            double far = 0, fx = px, fy = py, fz = pz;
            for (int k = 0; k < sub; k++) {
                int u = eu[k], v = ev[k], o = k * 9;
                edges[o] = nx[u];
                edges[o + 1] = ny[u];
                edges[o + 2] = nz[u];
                edges[o + 3] = nx[v];
                edges[o + 4] = ny[v];
                edges[o + 5] = nz[v];
                edges[o + 6] = len[k];
                edges[o + 7] = dist[u];
                edges[o + 8] = dist[v];
                // the farthest point of an edge is where the light from both ends meets
                double meet = (dist[u] + dist[v] + len[k]) * 0.5;
                if (meet > far) {
                    far = meet;
                    double s = len[k] <= 0 ? 0 : Mth.clamp((dist[v] + len[k] - dist[u]) * 0.5, 0, len[k]) / len[k];
                    fx = nx[u] + (nx[v] - nx[u]) * s;
                    fy = ny[u] + (ny[v] - ny[u]) * s;
                    fz = nz[u] + (nz[v] - nz[u]) * s;
                }
            }
            edgeCount = sub;
            feeders = feed;
            feederCount = feedCount;
            maxDist = Math.max(0.5, far);
            farX = fx;
            farY = fy;
            farZ = fz;
        }

        int duration() {
            return switch (kind) {
                case StructureFxPayload.FORM -> TRAVEL + AFTER;
                case StructureFxPayload.UNFORM -> DRAIN + SPARK_LIFE + 4;
                default -> SPAWN_TICKS;
            };
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static ClientLevel level;

    // ------------------------------------------------------------------ tick

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel current = mc.level;
        if (current != level) {
            ACTIVE.clear();
            level = current;
        }
        if (current == null) {
            StructureFxPayload.drain(p -> {});
            return;
        }
        long now = current.getGameTime();
        StructureFxPayload.drain(StructureFx::accept);
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Fx fx = ACTIVE.get(i);
            long age = now - fx.start;
            if (age > fx.duration() || age < 0) {
                ACTIVE.remove(i);
                continue;
            }
            if (fx.kind == StructureFxPayload.FORM && !fx.chimed && age >= TRAVEL) {
                fx.chimed = true;
                current.playLocalSound(fx.farX, fx.farY, fx.farZ, CoreSounds.STRUCTURE_SETTLE.get(), SoundSource.BLOCKS, 0.8F,
                        0.95F + current.random.nextFloat() * 0.1F, false);
            }
        }
    }

    private static void accept(StructureFxPayload p) {
        ClientLevel lvl = level;
        if (lvl == null) return;
        boolean on = p.kind() == StructureFxPayload.SPAWN ? ReplicatorClientConfig.spawnEffects() : EnergyClientConfig.structureEffects();
        if (!on) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(p.origin().getX() + 0.5, p.origin().getY() + 0.5, p.origin().getZ() + 0.5) > (GONE + 32) * (GONE + 32)) return;
        Fx fx = new Fx(p, lvl.getGameTime());
        if (p.kind() != StructureFxPayload.SPAWN) {
            // a newer forming or breaking of the same structure replaces the running one
            for (int i = ACTIVE.size() - 1; i >= 0; i--) {
                Fx old = ACTIVE.get(i);
                if (old.kind != StructureFxPayload.SPAWN && old.ox == fx.ox && old.oy == fx.oy && old.oz == fx.oz) ACTIVE.remove(i);
            }
        }
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        ACTIVE.add(fx);
        double d2 = cam.distanceToSqr(fx.ox, fx.oy, fx.oz);
        RandomSource random = lvl.random;
        if (p.kind() == StructureFxPayload.UNFORM && d2 < 48 * 48) {
            // smoke from the corners and the controller
            for (int i = 0; i < 8; i++) {
                double x = (i & 1) != 0 ? fx.x1 : fx.x0, y = (i & 2) != 0 ? fx.y1 : fx.y0, z = (i & 4) != 0 ? fx.z1 : fx.z0;
                lvl.addParticle(ParticleTypes.SMOKE, x, y, z, (random.nextDouble() - 0.5) * 0.02, 0.02 + random.nextDouble() * 0.02,
                        (random.nextDouble() - 0.5) * 0.02);
            }
            for (int i = 0; i < 3; i++) {
                lvl.addParticle(ParticleTypes.LARGE_SMOKE, fx.ox + (random.nextDouble() - 0.5) * 0.4, fx.oy + (random.nextDouble() - 0.5) * 0.4,
                        fx.oz + (random.nextDouble() - 0.5) * 0.4, 0, 0.02, 0);
            }
        } else if (p.kind() == StructureFxPayload.SPAWN && d2 < 48 * 48) {
            for (int i = 0; i < 12; i++) {
                double x = fx.x0 + random.nextDouble() * (fx.x1 - fx.x0), y = fx.y0 + random.nextDouble() * (fx.y1 - fx.y0),
                        z = fx.z0 + random.nextDouble() * (fx.z1 - fx.z0);
                lvl.addParticle(ParticleTypes.REVERSE_PORTAL, x, y, z, 0, 0.03 + random.nextDouble() * 0.04, 0);
            }
        }
    }

    // ------------------------------------------------------------------ render

    private static final int MAX_POINTS = 96, DISC = 10;
    private static final float[] SX = new float[MAX_POINTS], SY = new float[MAX_POINTS], SZ = new float[MAX_POINTS], SA = new float[MAX_POINTS];
    private static final float[] WX = new float[MAX_POINTS], WY = new float[MAX_POINTS], WZ = new float[MAX_POINTS];
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
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        for (int i = 0; i < ACTIVE.size(); i++) {
            Fx fx = ACTIVE.get(i);
            float t = (tick - fx.start) + partial;
            if (t < 0 || t > fx.duration()) continue;
            AABB b = fx.bounds;
            double dx = Math.max(0, Math.max(b.minX - camX, camX - b.maxX)), dy = Math.max(0, Math.max(b.minY - camY, camY - b.maxY)),
                    dz = Math.max(0, Math.max(b.minZ - camZ, camZ - b.maxZ));
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > GONE || !event.getFrustum().isVisible(b)) continue;
            float fade = d <= FAR ? 1 : (float) (1 - (d - FAR) / (GONE - FAR));
            boolean near = d <= NEAR;
            if (vc == null) vc = buffers.getBuffer(GLOW);
            switch (fx.kind) {
                case StructureFxPayload.FORM -> drawForm(vc, fx, t, fade, near);
                case StructureFxPayload.UNFORM -> drawUnform(vc, fx, t, tick, fade, near);
                default -> drawSpawn(vc, fx, t, fade);
            }
        }
        if (vc != null) buffers.endBatch(GLOW);
    }

    // ------------------------------------------------------------------ forming

    private static void drawForm(VertexConsumer vc, Fx fx, float t, float fade, boolean near) {
        float u = Math.min(1, t / TRAVEL);
        // fast out of the controller, easing as it closes on the far side
        double front = fx.maxDist * u * (1.25 - 0.25 * u);
        float after = t - TRAVEL;
        float pulse = after <= 0 ? 0 : (float) Math.exp(-after / 4.0);
        float out = after <= 0 ? 1 : 1 - smooth(after / AFTER);
        int halo = fx.color, core = pale(halo);
        double step = near ? 0.35 : 1.0;
        boolean heads = after <= 0;
        for (int k = 0; k < fx.edgeCount; k++) {
            int o = k * 9;
            double len = fx.edges[o + 6], da = fx.edges[o + 7], db = fx.edges[o + 8];
            double la = Mth.clamp(front - da, 0, len), lb = Mth.clamp(front - db, 0, len);
            boolean tipA = la < len, tipB = lb < len;
            if (la + lb > len) {
                double meet = Mth.clamp((db + len - da) * 0.5, 0, len);
                la = Math.min(la, meet);
                lb = Math.min(lb, len - meet);
                tipA = tipB = false;
            }
            if (la > 1.0E-3) {
                lit(vc, fx, o, false, la, da, front, step, pulse, out * fade, halo, core);
                if (heads && tipA) head(vc, fx, o, false, la, fade, halo, core, 1);
            }
            if (lb > 1.0E-3) {
                lit(vc, fx, o, true, lb, db, front, step, pulse, out * fade, halo, core);
                if (heads && tipB) head(vc, fx, o, true, lb, fade, halo, core, 1);
            }
        }
        // feeders across the controller's face, thinner
        if (near) {
            for (int f = 0; f < fx.feederCount; f++) {
                double r = fx.feeders[f * 4 + 3], l = Mth.clamp(front, 0, r);
                if (l < 1.0E-3 || r < 1.0E-3) continue;
                double ex = fx.ox + (fx.feeders[f * 4] - fx.ox) * l / r, ey = fx.oy + (fx.feeders[f * 4 + 1] - fx.oy) * l / r,
                        ez = fx.oz + (fx.feeders[f * 4 + 2] - fx.oz) * l / r;
                int n = line(fx.ox, fx.oy, fx.oz, ex, ey, ez, l, 0.35);
                for (int i = 0; i < n; i++) SA[i] = trail(front - l * i / (n - 1)) * 0.7F * out * fade;
                strip(vc, n, 0.16F, halo, 60);
                strip(vc, n, 0.035F, core, 180);
                if (heads && l < r) {
                    disc(vc, ex, ey, ez, 0.3F, halo, (int) (90 * fade));
                    disc(vc, ex, ey, ez, 0.08F, core, (int) (190 * fade));
                }
            }
        }
        // the controller lights first, the far side flashes where the light closes
        if (t < 10) {
            float f = 1 - t / 10;
            disc(vc, fx.ox, fx.oy, fx.oz, 0.45F + 0.4F * (1 - f), halo, (int) (110 * f * fade));
            disc(vc, fx.ox, fx.oy, fx.oz, 0.14F, core, (int) (200 * f * fade));
            disc(vc, fx.ox, fx.oy, fx.oz, 0.05F, WHITE, (int) (240 * f * fade));
        }
        if (after >= 0 && after < 12) {
            float f = 1 - after / 12;
            disc(vc, fx.farX, fx.farY, fx.farZ, 0.5F + 0.5F * (1 - f), halo, (int) (120 * f * fade));
            disc(vc, fx.farX, fx.farY, fx.farZ, 0.16F, core, (int) (210 * f * fade));
            disc(vc, fx.farX, fx.farY, fx.farZ, 0.06F, WHITE, (int) (240 * f * fade));
        }
    }

    /** Brightness of a lit point that the front passed {@code age} blocks ago: a bright trail settling to a glow. */
    private static float trail(double age) {
        return 0.45F + 0.95F * (float) Math.exp(-Math.max(0, age) / 1.8);
    }

    /** The lit part of an edge from one end ({@code fromB}: the b end), {@code lit} blocks long. */
    private static void lit(VertexConsumer vc, Fx fx, int o, boolean fromB, double lit, double d0, double front, double step,
                            float pulse, float scale, int halo, int core) {
        double[] e = fx.edges;
        double len = e[o + 6];
        double ax = fromB ? e[o + 3] : e[o], ay = fromB ? e[o + 4] : e[o + 1], az = fromB ? e[o + 5] : e[o + 2];
        double bx = fromB ? e[o] : e[o + 3], by = fromB ? e[o + 1] : e[o + 4], bz = fromB ? e[o + 2] : e[o + 5];
        double f = len <= 0 ? 0 : lit / len;
        int n = line(ax, ay, az, ax + (bx - ax) * f, ay + (by - ay) * f, az + (bz - az) * f, lit, step);
        for (int i = 0; i < n; i++) {
            double s = lit * i / (n - 1);
            SA[i] = Math.min(2.2F, trail(front - (d0 + s)) + 0.9F * pulse) * scale;
        }
        strip(vc, n, 0.3F, halo, 80);
        strip(vc, n, 0.1F, halo, 110);
        strip(vc, n, 0.045F, core, 230);
    }

    /** The bright head at the tip of a lit part. */
    private static void head(VertexConsumer vc, Fx fx, int o, boolean fromB, double lit, float fade, int halo, int core, float gain) {
        double[] e = fx.edges;
        double len = e[o + 6], f = len <= 0 ? 0 : lit / len;
        double ax = fromB ? e[o + 3] : e[o], ay = fromB ? e[o + 4] : e[o + 1], az = fromB ? e[o + 5] : e[o + 2];
        double bx = fromB ? e[o] : e[o + 3], by = fromB ? e[o + 1] : e[o + 4], bz = fromB ? e[o + 2] : e[o + 5];
        double x = ax + (bx - ax) * f, y = ay + (by - ay) * f, z = az + (bz - az) * f;
        disc(vc, x, y, z, 0.65F, halo, (int) (100 * fade * gain));
        disc(vc, x, y, z, 0.22F, core, (int) (200 * fade * gain));
        disc(vc, x, y, z, 0.075F, WHITE, (int) (255 * fade * gain));
    }

    // ------------------------------------------------------------------ breaking

    private static void drawUnform(VertexConsumer vc, Fx fx, float t, long tick, float fade, boolean near) {
        float u = Math.min(1, t / DRAIN);
        double front = fx.maxDist * (1 - u);
        // flicker per 2 ticks, reddening as it drains
        float flick = 0.7F + 0.3F * hash((int) (fx.seed + (tick >> 1) * 31));
        int halo = lerp(fx.color, EMBER, u), core = pale(halo);
        float scale = flick * fade * (1 - smooth((u - 0.75F) / 0.25F));
        double step = near ? 0.35 : 1.0;
        if (u < 1) {
            for (int k = 0; k < fx.edgeCount; k++) {
                int o = k * 9;
                double len = fx.edges[o + 6], da = fx.edges[o + 7], db = fx.edges[o + 8];
                double la = Mth.clamp(front - da, 0, len), lb = Mth.clamp(front - db, 0, len);
                if (la + lb > len) {
                    double meet = Mth.clamp((db + len - da) * 0.5, 0, len);
                    la = Math.min(la, meet);
                    lb = Math.min(lb, len - meet);
                }
                if (la > 1.0E-3) drain(vc, fx, o, false, la, da, front, step, scale, halo, core);
                if (lb > 1.0E-3) drain(vc, fx, o, true, lb, db, front, step, scale, halo, core);
            }
        }
        // sparks fall from where the light lets go
        if (near) {
            for (int k = 0; k < SPARKS && fx.edgeCount > 0; k++) {
                int seed = (int) fx.seed + k * 7919;
                int o = Math.min(fx.edgeCount - 1, (int) (hash(seed) * fx.edgeCount)) * 9;
                double len = fx.edges[o + 6], s = hash(seed + 1) * len;
                double dist = Math.min(fx.edges[o + 7] + s, fx.edges[o + 8] + len - s);
                float release = (float) (DRAIN * (1 - dist / fx.maxDist));
                float age = t - release;
                if (age < 0 || age >= SPARK_LIFE) continue;
                double f = len <= 0 ? 0 : s / len;
                double x = fx.edges[o] + (fx.edges[o + 3] - fx.edges[o]) * f, y = fx.edges[o + 1] + (fx.edges[o + 4] - fx.edges[o + 1]) * f,
                        z = fx.edges[o + 2] + (fx.edges[o + 5] - fx.edges[o + 2]) * f;
                x += (hash(seed + 2) - 0.5F) * 0.06F * age;
                z += (hash(seed + 3) - 0.5F) * 0.06F * age;
                y += (0.02F + 0.03F * hash(seed + 4)) * age - 0.008F * age * age;
                float a = (1 - age / SPARK_LIFE) * fade;
                disc(vc, x, y, z, 0.12F, halo, (int) (130 * a));
                disc(vc, x, y, z, 0.035F, WHITE, (int) (255 * a));
            }
        }
        // the controller swallows the light and goes dark
        if (t >= DRAIN - 4 && t < DRAIN + 8) {
            float f = (t - (DRAIN - 4)) / 12;
            float a = (f < 0.3F ? f / 0.3F : 1 - (f - 0.3F) / 0.7F) * fade;
            disc(vc, fx.ox, fx.oy, fx.oz, 0.55F * (1 - f * 0.7F), halo, (int) (110 * a));
            disc(vc, fx.ox, fx.oy, fx.oz, 0.12F * (1 - f * 0.5F), core, (int) (200 * a));
        }
    }

    private static void drain(VertexConsumer vc, Fx fx, int o, boolean fromB, double lit, double d0, double front, double step,
                              float scale, int halo, int core) {
        double[] e = fx.edges;
        double len = e[o + 6];
        double ax = fromB ? e[o + 3] : e[o], ay = fromB ? e[o + 4] : e[o + 1], az = fromB ? e[o + 5] : e[o + 2];
        double bx = fromB ? e[o] : e[o + 3], by = fromB ? e[o + 1] : e[o + 4], bz = fromB ? e[o + 2] : e[o + 5];
        double f = len <= 0 ? 0 : lit / len;
        int n = line(ax, ay, az, ax + (bx - ax) * f, ay + (by - ay) * f, az + (bz - az) * f, lit, step);
        for (int i = 0; i < n; i++) {
            double s = lit * i / (n - 1);
            SA[i] = (0.45F + 0.55F * (float) Math.exp(-Math.max(0, front - (d0 + s)) / 1.2)) * scale;
        }
        strip(vc, n, 0.28F, halo, 80);
        strip(vc, n, 0.09F, halo, 100);
        strip(vc, n, 0.045F, core, 220);
    }

    // ------------------------------------------------------------------ spawn

    private static void drawSpawn(VertexConsumer vc, Fx fx, float t, float fade) {
        float u = Math.min(1, t / SPAWN_TICKS);
        int halo = fx.color, core = pale(halo);
        double cx = fx.ox, base = fx.oy + 0.03, cz = fx.oz, h = fx.y1 - fx.y0;
        double radius = Math.max(0.3, (fx.x1 - fx.x0) * 0.42);
        // a ring of light running out over the floor
        float ring = (float) Math.pow(1 - u, 1.5) * fade;
        double rr = radius * (0.25 + 0.95 * (1 - (1 - u) * (1 - u)));
        int n = 17;
        for (int i = 0; i < n; i++) {
            SX[i] = (float) (cx + Math.cos(i * Math.PI * 2 / 16) * rr - camX);
            SY[i] = (float) (base - camY);
            SZ[i] = (float) (cz + Math.sin(i * Math.PI * 2 / 16) * rr - camZ);
            SA[i] = ring;
        }
        strip(vc, n, 0.2F, halo, 100);
        strip(vc, n, 0.05F, core, 230);
        // a column that swells and fades
        float env = Mth.sin(Mth.PI * Math.min(1, u * 1.3F)) * fade;
        if (env > 0.01F) {
            int m = line(cx, base, cz, cx, base + h, cz, h, h / 6);
            for (int i = 0; i < m; i++) {
                float q = (float) i / (m - 1);
                SA[i] = (1 - q * q) * env;
            }
            strip(vc, m, (float) radius * 0.9F, halo, 70);
            strip(vc, m, 0.12F, core, 170);
        }
        // motes spiralling up around it
        for (int k = 0; k < MOTES; k++) {
            int seed = (int) fx.seed + k * 613;
            float delay = hash(seed) * 0.35F, v = (u - delay) / (1 - delay);
            if (v <= 0 || v >= 1) continue;
            double ang = hash(seed + 1) * Mth.TWO_PI + v * 4.5, r = radius * (1 - 0.55 * v);
            double x = cx + Math.cos(ang) * r, y = base + h * (0.05 + 0.95 * v), z = cz + Math.sin(ang) * r;
            float a = Mth.sin(Mth.PI * v) * fade;
            disc(vc, x, y, z, 0.16F, halo, (int) (120 * a));
            disc(vc, x, y, z, 0.045F, WHITE, (int) (255 * a));
        }
        if (u < 0.3F) {
            float f = 1 - u / 0.3F;
            disc(vc, cx, base + h * 0.5, cz, (float) radius * 1.4F, halo, (int) (90 * f * fade));
            disc(vc, cx, base + h * 0.5, cz, 0.18F, core, (int) (170 * f * fade));
        }
    }

    // ------------------------------------------------------------------ geometry (camera-relative, nothing allocated)

    /** Fills SX/SY/SZ with points from a to b about {@code step} apart (world coordinates in), returns the count. */
    private static int line(double ax, double ay, double az, double bx, double by, double bz, double len, double step) {
        int n = Math.max(2, Math.min(MAX_POINTS, (int) Math.ceil(len / step) + 1));
        for (int i = 0; i < n; i++) {
            double q = (double) i / (n - 1);
            SX[i] = (float) (ax + (bx - ax) * q - camX);
            SY[i] = (float) (ay + (by - ay) * q - camY);
            SZ[i] = (float) (az + (bz - az) * q - camZ);
        }
        return n;
    }

    /**
     * A camera-facing strip along the points in SX/SY/SZ with soft edges: {@code alpha} times SA on the line, zero at
     * {@code halfWidth} to either side.
     */
    private static void strip(VertexConsumer vc, int n, float halfWidth, int color, int alpha) {
        if (n < 2) return;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            float tx = SX[i1] - SX[i0], ty = SY[i1] - SY[i0], tz = SZ[i1] - SZ[i0];
            float vx = -SX[i], vy = -SY[i], vz = -SZ[i];
            float wx = ty * vz - tz * vy, wy = tz * vx - tx * vz, wz = tx * vy - ty * vx;
            float wl = Mth.sqrt(wx * wx + wy * wy + wz * wz);
            if (wl < 1.0E-6F) {
                wx = leftX;
                wy = leftY;
                wz = leftZ;
                wl = 1;
            }
            WX[i] = wx / wl * halfWidth;
            WY[i] = wy / wl * halfWidth;
            WZ[i] = wz / wl * halfWidth;
        }
        for (int i = 0; i + 1 < n; i++) {
            int a0 = Math.min(255, (int) (alpha * SA[i])), a1 = Math.min(255, (int) (alpha * SA[i + 1]));
            if (a0 <= 0 && a1 <= 0) continue;
            float x0 = SX[i], y0 = SY[i], z0 = SZ[i], x1 = SX[i + 1], y1 = SY[i + 1], z1 = SZ[i + 1];
            vc.addVertex(x0 - WX[i], y0 - WY[i], z0 - WZ[i]).setColor(r, g, b, 0);
            vc.addVertex(x0, y0, z0).setColor(r, g, b, a0);
            vc.addVertex(x1, y1, z1).setColor(r, g, b, a1);
            vc.addVertex(x1 - WX[i + 1], y1 - WY[i + 1], z1 - WZ[i + 1]).setColor(r, g, b, 0);
            vc.addVertex(x0, y0, z0).setColor(r, g, b, a0);
            vc.addVertex(x0 + WX[i], y0 + WY[i], z0 + WZ[i]).setColor(r, g, b, 0);
            vc.addVertex(x1 + WX[i + 1], y1 + WY[i + 1], z1 + WZ[i + 1]).setColor(r, g, b, 0);
            vc.addVertex(x1, y1, z1).setColor(r, g, b, a1);
        }
    }

    /** A camera-facing soft dot at a world position: full alpha in the middle, zero at the rim. */
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

    /** The colour pulled two thirds of the way to white: cores and inner halos. */
    private static int pale(int c) {
        return lerp(c, WHITE, 2 / 3.0F);
    }

    private static int lerp(int c0, int c1, float t) {
        int r = (int) ((c0 >> 16 & 255) + ((c1 >> 16 & 255) - (c0 >> 16 & 255)) * t);
        int g = (int) ((c0 >> 8 & 255) + ((c1 >> 8 & 255) - (c0 >> 8 & 255)) * t);
        int b = (int) ((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return r << 16 | g << 8 | b;
    }

    /** Integer hash to 0..1. */
    private static float hash(int x) {
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return (x & 0xFFFFFF) / (float) 0x1000000;
    }
}
