package com.arno.robotica.power.client;

import com.arno.robotica.power.PowerClientConfig;
import com.arno.robotica.power.tesla.TeslaCoilBlock;
import com.arno.robotica.power.tesla.TeslaCoilBlockEntity;
import com.arno.robotica.power.tesla.TeslaLink;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Tesla links as soft energy tethers. Every link is a thin, slightly sagging strand of additive glow: a faint halo with a
 * brighter core, both fading out at the edges and into the coil tips, with a slow shimmer running along it. While energy
 * goes over a link, soft motes with short tails travel its strand towards the target (more and faster the bigger the
 * share of the coil's rate that link takes, from its synced flow level), and while the coil sends a tiny spark now and
 * then crackles off the tip. A link whose target takes nothing has no motes.
 * <p>
 * Smooth: everything runs on a client tick clock plus the partial tick (the level's game time jumps on every server time
 * sync), the motes advance per frame by an eased speed so a flow change speeds them up or slows them down instead of
 * moving them, and they keep their identity along the strand so their twinkle and density never pop. Lines never get
 * thinner than a pixel or so: further out they widen and dim instead, so they do not shimmer.
 * <p>
 * Cheap: camera-facing strips in one batched render type, all geometry from static scratch arrays and one re-seeded
 * Random (render thread only), nothing allocated per frame. Each link fades with its distance to the camera: motes and
 * sparks only up close, nothing beyond the fade range (wider while holding a Linker). The render box spans every link
 * end, so long links stay when the coil itself is off-screen.
 */
public class TeslaCoilRenderer implements BlockEntityRenderer<TeslaCoilBlockEntity> {
    /** Additive like lightning, but no depth writes (soft edges never hide glow behind them) and no culling (one winding). */
    private static final RenderType GLOW = RenderType.create("robotica_tesla_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.WEATHER_TARGET)
                    .createCompositeState(false));

    /** Links are fully visible this close, then fade out until the second distance. Wider while holding a Linker. */
    private static final double NEAR = 16, FAR = 26, NEAR_LINKER = 48, FAR_LINKER = 64;
    /** Motes travel only within this distance, sparks only within the second. Further out a link is just its strand. */
    private static final double MOTE_RANGE = 28, SPARK_RANGE = 14;
    /** The strand fades in over this length at both ends. */
    private static final double END_FADE = 0.45;

    // Strand: halo and core half widths (blocks) and alphas (0-255) of an idle link without a Linker in hand.
    private static final float HALO_W = 0.065F, CORE_W = 0.011F;
    private static final int HALO_A = 30, CORE_A = 46;
    /** Brightness of the strand while the coil sends, and while the player holds a Linker. */
    private static final float FLOW_GAIN = 1.3F, LINKER_GAIN = 2.0F;

    /** Mote speed (blocks per tick) per flow level 0-4, blended between levels by the eased flow. */
    private static final double[] MOTE_SPEED = {0, 0.045, 0.065, 0.09, 0.12};
    /**
     * Motes sit on a lattice this far apart (wider on long links, at most {@link #MAX_MOTES} slots). Flow level 1 lights
     * every fourth slot, 2 every second, 3 adds the rest at half strength, 4 lights all.
     */
    private static final double MOTE_GAP = 0.8;
    private static final int MAX_MOTES = 24;
    /** How fast the drawn flow follows the synced level, per tick. */
    private static final double FLOW_EASE = 0.12;
    /** One crackle every this many spark windows on average, per flow level. Each spark fades out over its window. */
    private static final int[] SPARK_ODDS = {0, 40, 28, 18, 12};
    private static final double SPARK_TICKS = 3;
    /** Least half width of a line or radius of a dot per block of camera distance (about a pixel and a half). */
    private static final double MIN_WIDTH = 0.002;

    /**
     * Halo colours per tier (0xRRGGBB), after the coil's winding: I copper and II steel a pale ice blue like the Spark Lamp
     * wisp, III a soft gold, IV a muted ember, V violet. The strand core and inner halo are these pulled towards the
     * wisp's white. A link between two tiers fades from one colour to the other.
     */
    private static final int[] TIER_HALO = {0x6EAAFF, 0x82B9FF, 0xFFBE50, 0xFF7846, 0x965AFF};
    /** The wisp's core white. */
    private static final int WHITE = 0xEBFAFF;

    // ---- render-thread scratch, nothing below is allocated per frame ----
    private static final int MAX_POINTS = 48, TAIL_POINTS = 6, DISC = 10;
    /** Strand points of the link being drawn, and their arc length from the coil tip. */
    private static final double[] SX = new double[MAX_POINTS], SY = new double[MAX_POINTS], SZ = new double[MAX_POINTS],
            SS = new double[MAX_POINTS], SA = new double[MAX_POINTS];
    /** A second polyline for tails and sparks. */
    private static final double[] QX = new double[MAX_POINTS], QY = new double[MAX_POINTS], QZ = new double[MAX_POINTS],
            QA = new double[MAX_POINTS];
    /** Per-point sideways vectors of the strip being emitted, and the alpha factor for any widening. */
    private static final double[] WX = new double[MAX_POINTS], WY = new double[MAX_POINTS], WZ = new double[MAX_POINTS],
            WK = new double[MAX_POINTS];
    private static final double[] COS = new double[DISC + 1], SIN = new double[DISC + 1];
    private static final Random RNG = new Random();
    /** Client ticks while the game is not paused. */
    private static long clientTicks;
    /** Result of {@link #pointAt}. */
    private static double px, py, pz;
    /** Camera position in the coil's block space, and the camera's left and up axes. */
    private static double camX, camY, camZ;
    private static float leftX, leftY, leftZ, upX, upY, upZ;

    static {
        for (int i = 0; i <= DISC; i++) {
            COS[i] = Math.cos(Math.PI * 2 * i / DISC);
            SIN[i] = Math.sin(Math.PI * 2 * i / DISC);
        }
    }

    public TeslaCoilRenderer(BlockEntityRendererProvider.Context context) {}

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().isPaused()) clientTicks++;
    }

    @Override
    public void render(TeslaCoilBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        int linkCount = be.linkCount();
        if (level == null || linkCount == 0 || !PowerClientConfig.teslaArcs()) return;
        BlockState state = be.getBlockState();
        if (!state.hasProperty(TeslaCoilBlock.FACING)) return;
        BlockPos origin = be.getBlockPos();
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        camX = cam.x - origin.getX();
        camY = cam.y - origin.getY();
        camZ = cam.z - origin.getZ();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        leftX = left.x();
        leftY = left.y();
        leftZ = left.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();

        boolean linker = TeslaCoilBlock.isConfiguring();
        double near = linker ? NEAR_LINKER : NEAR, far = linker ? FAR_LINKER : FAR;
        int synced = Math.min(4, be.flowLevel());
        double time = clientTicks + partialTick;
        double dt = be.fxTime < 0 ? 0 : Math.max(0, Math.min(20, time - be.fxTime));
        be.fxTime = time;
        double ease = 1 - Math.exp(-dt * FLOW_EASE);
        linkCount = Math.min(linkCount, be.fxFlow.length);
        for (int i = 0; i < linkCount; i++) {
            int target = Math.min(4, be.linkFlowLevel(i));
            be.fxFlow[i] += (float) ((target - be.fxFlow[i]) * ease);
            if (Math.abs(target - be.fxFlow[i]) < 0.002F) be.fxFlow[i] = target;
            be.fxTravel[i] += dt * moteSpeed(be.fxFlow[i]);
        }
        int halo = halo(be.tier().ordinal());

        Direction facing = state.getValue(TeslaCoilBlock.FACING);
        double tip = TeslaCoilBlock.TIP - 0.5;
        double ax = 0.5 + facing.getStepX() * tip, ay = 0.5 + facing.getStepY() * tip, az = 0.5 + facing.getStepZ() * tip;

        VertexConsumer vc = buffers.getBuffer(GLOW);
        Matrix4f m = pose.last().pose();
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < linkCount; i++) {
            TeslaLink link = be.link(i);
            BlockPos p = link.pos();
            double bx, by, bz;
            int haloEnd = halo;
            if (link.coil()) {
                BlockState other = level.getBlockState(p);
                if (other.getBlock() instanceof TeslaCoilBlock coil) haloEnd = halo(coil.tier().ordinal());
                Direction f = other.hasProperty(TeslaCoilBlock.FACING) ? other.getValue(TeslaCoilBlock.FACING) : Direction.UP;
                bx = p.getX() - origin.getX() + 0.5 + f.getStepX() * tip;
                by = p.getY() - origin.getY() + 0.5 + f.getStepY() * tip;
                bz = p.getZ() - origin.getZ() + 0.5 + f.getStepZ() * tip;
            } else {
                Direction f = link.face() == null ? Direction.UP : link.face();
                bx = p.getX() - origin.getX() + 0.5 + f.getStepX() * 0.52;
                by = p.getY() - origin.getY() + 0.5 + f.getStepY() * 0.52;
                bz = p.getZ() - origin.getZ() + 0.5 + f.getStepZ() * 0.52;
            }
            double dist = segmentDistance(ax, ay, az, bx, by, bz);
            closest = Math.min(closest, dist);
            if (dist >= far) continue;
            float fade = dist <= near ? 1F : (float) smooth((far - dist) / (far - near));
            double phase = ((p.asLong() * 0x9E3779B97F4A7C15L) >>> 40) / (double) (1 << 24) * Math.PI * 2;
            int n = strand(ax, ay, az, bx, by, bz, time, phase);
            if (n < 2) continue;
            double len = SS[n - 1];
            float flow = be.fxFlow[i];
            float a = fade * (1 + (FLOW_GAIN - 1) * Math.min(1F, flow)) * (linker ? LINKER_GAIN : 1F);
            strip(vc, m, SX, SY, SZ, SA, n, HALO_W, halo, haloEnd, Math.min(255, (int) (HALO_A * a)));
            strip(vc, m, SX, SY, SZ, SA, n, CORE_W, pale(halo), pale(haloEnd), Math.min(255, (int) (CORE_A * a)));
            if (flow > 0.01F && dist < MOTE_RANGE && len > 0.6) {
                float moteFade = fade * (float) smooth((MOTE_RANGE - dist) / 6.0) * (linker ? 1.3F : 1F);
                motes(vc, m, n, len, flow, be.fxTravel[i], time, phase, halo, haloEnd, moteFade);
            }
        }
        if (synced > 0 && closest < SPARK_RANGE) spark(vc, m, origin, ax, ay, az, facing, time, synced, pale(halo));
    }

    /** Mote speed at an eased flow level, blended between the two nearest levels. */
    private static double moteSpeed(float flow) {
        int i = Math.max(0, Math.min(3, (int) flow));
        double f = Math.max(0, Math.min(1, flow - i));
        return MOTE_SPEED[i] + (MOTE_SPEED[i + 1] - MOTE_SPEED[i]) * f;
    }

    /** How lit lattice slot {@code id} is at an eased flow level: every fourth from level 1, every second from 2, all at 4. */
    private static float moteWeight(long id, float flow) {
        long slot = Math.floorMod(id, 4L);
        float w = slot == 0 ? flow : slot == 2 ? flow - 1 : (flow - 2) / 2;
        return Math.max(0F, Math.min(1F, w));
    }

    /**
     * Fills the strand scratch arrays with the sagging curve from a to b and returns its point count. The sag is a
     * parabola, deeper for long and level links, breathing very slowly. SA holds the end fade times a slow shimmer.
     */
    private static int strand(double ax, double ay, double az, double bx, double by, double bz, double time, double phase) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.05) return 0;
        double level = Math.sqrt(dx * dx + dz * dz) / len;
        double sag = Math.min(0.06 * len, 0.8) * level * (1 + 0.06 * Math.sin(time * 0.025 + phase));
        int n = Math.max(8, Math.min(MAX_POINTS, (int) Math.ceil(len * 1.5) + 1));
        double s = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            double x = ax + dx * t, y = ay + dy * t - sag * 4 * t * (1 - t), z = az + dz * t;
            if (i > 0) {
                double ex = x - SX[i - 1], ey = y - SY[i - 1], ez = z - SZ[i - 1];
                s += Math.sqrt(ex * ex + ey * ey + ez * ez);
            }
            SX[i] = x;
            SY[i] = y;
            SZ[i] = z;
            SS[i] = s;
        }
        for (int i = 0; i < n; i++) {
            double end = smooth(Math.min(SS[i], s - SS[i]) / END_FADE);
            SA[i] = end * (0.78 + 0.22 * Math.sin(SS[i] * 0.9 - time * 0.06 + phase));
        }
        return n;
    }

    /** Point at arc length {@code s} on the current strand, into px/py/pz. */
    private static void pointAt(int n, double s) {
        int i = 1;
        while (i < n - 1 && SS[i] < s) i++;
        double s0 = SS[i - 1], span = SS[i] - s0;
        double t = span <= 1.0E-9 ? 0 : Math.max(0, Math.min(1, (s - s0) / span));
        px = SX[i - 1] + (SX[i] - SX[i - 1]) * t;
        py = SY[i - 1] + (SY[i] - SY[i - 1]) * t;
        pz = SZ[i - 1] + (SZ[i] - SZ[i - 1]) * t;
    }

    /**
     * Soft motes with a short tail on a lattice that slides {@code travel} blocks from the coil to the target. Each mote
     * keeps its lattice id while it travels, so its brightness and whether it is lit stay steady.
     */
    private static void motes(VertexConsumer vc, Matrix4f m, int n, double len, float flow, double travel, double time,
                              double phase, int halo0, int halo1, float fade) {
        double gap = Math.max(MOTE_GAP, len / MAX_MOTES), tail = 0.45 + 0.12 * flow;
        double pos = travel + phase / (Math.PI * 2) * gap;
        long first = (long) Math.floor(pos / gap);
        double head0 = pos - first * gap;
        for (int k = 0; head0 + k * gap < len; k++) {
            double s = head0 + k * gap;
            long id = k - first;
            float a = fade * moteWeight(id, flow) * (float) (smooth(s / 0.5) * smooth((len - s) / 0.5))
                    * (float) (0.85 + 0.15 * Math.sin(time * 0.3 + (id % 1000) * 1.7 + phase));
            if (a <= 0.01F) continue;
            // tail: a tapering strip behind the head
            double start = Math.max(0, s - tail);
            for (int j = 0; j < TAIL_POINTS; j++) {
                double t = (double) j / (TAIL_POINTS - 1);
                pointAt(n, start + (s - start) * t);
                QX[j] = px;
                QY[j] = py;
                QZ[j] = pz;
                QA[j] = t * t;
            }
            int halo = lerp(halo0, halo1, s / len), inner = pale(halo);
            strip(vc, m, QX, QY, QZ, QA, TAIL_POINTS, 0.035F, halo, halo, (int) (80 * a));
            strip(vc, m, QX, QY, QZ, QA, TAIL_POINTS, 0.008F, inner, inner, (int) (130 * a));
            pointAt(n, s);
            // two-layer halo round a white point, like the Spark Lamp wisp
            disc(vc, m, px, py, pz, 0.18F, halo, (int) (75 * a));
            disc(vc, m, px, py, pz, 0.065F, inner, (int) (110 * a));
            disc(vc, m, px, py, pz, 0.025F, WHITE, (int) (220 * a));
        }
    }

    /** Now and then a tiny jagged spark off the coil tip while it sends; each fades out over one spark window. */
    private static void spark(VertexConsumer vc, Matrix4f m, BlockPos origin, double ax, double ay, double az, Direction facing,
                              double time, int flow, int color) {
        double w = time / SPARK_TICKS;
        long window = (long) Math.floor(w);
        float life = 1 - (float) (w - window);
        RNG.setSeed(origin.asLong() * 31 + window * 0x9E3779B97F4A7C15L);
        if (RNG.nextInt(SPARK_ODDS[flow]) != 0) return;
        // a random direction, leaning away from the block the coil sits on
        double dx = RNG.nextGaussian(), dy = RNG.nextGaussian(), dz = RNG.nextGaussian();
        dx += facing.getStepX() * 1.2;
        dy += facing.getStepY() * 1.2;
        dz += facing.getStepZ() * 1.2;
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1.0E-3) return;
        dx /= dl;
        dy /= dl;
        dz /= dl;
        double r0 = 0.11, length = 0.22 + RNG.nextDouble() * 0.18;
        int points = 5;
        for (int i = 0; i < points; i++) {
            double t = (double) i / (points - 1), d = r0 + length * t, j = i == 0 ? 0 : 0.05;
            QX[i] = ax + dx * d + (RNG.nextDouble() - 0.5) * 2 * j;
            QY[i] = ay + dy * d + (RNG.nextDouble() - 0.5) * 2 * j;
            QZ[i] = az + dz * d + (RNG.nextDouble() - 0.5) * 2 * j;
            QA[i] = 1 - t * t;
        }
        strip(vc, m, QX, QY, QZ, QA, points, 0.03F, color, color, (int) (28 * life));
        strip(vc, m, QX, QY, QZ, QA, points, 0.006F, color, color, (int) (150 * life));
    }

    /**
     * A camera-facing strip along the polyline with soft edges: full {@code alpha} (times the per-point factor) on the
     * line, zero at {@code halfWidth} to either side. The colour runs from {@code c0} at the first point to {@code c1}.
     * Where that is under {@link #MIN_WIDTH} the strip widens and dims to match, so it keeps the same brightness.
     */
    private static void strip(VertexConsumer vc, Matrix4f m, double[] xs, double[] ys, double[] zs, double[] as, int n,
                              float halfWidth, int c0, int c1, int alpha) {
        if (alpha <= 0 || n < 2) return;
        for (int i = 0; i < n; i++) {
            int i0 = Math.max(0, i - 1), i1 = Math.min(n - 1, i + 1);
            double tx = xs[i1] - xs[i0], ty = ys[i1] - ys[i0], tz = zs[i1] - zs[i0];
            double vx = camX - xs[i], vy = camY - ys[i], vz = camZ - zs[i];
            double wx = ty * vz - tz * vy, wy = tz * vx - tx * vz, wz = tx * vy - ty * vx;
            double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
            double w = Math.max(halfWidth, Math.sqrt(vx * vx + vy * vy + vz * vz) * MIN_WIDTH);
            if (wl < 1.0E-9) {
                // looking straight down the line: any sideways vector will do
                wx = leftX;
                wy = leftY;
                wz = leftZ;
                wl = 1;
            }
            WX[i] = wx / wl * w;
            WY[i] = wy / wl * w;
            WZ[i] = wz / wl * w;
            WK[i] = halfWidth / w;
        }
        for (int i = 0; i + 1 < n; i++) {
            int a0 = (int) (alpha * as[i] * WK[i]), a1 = (int) (alpha * as[i + 1] * WK[i + 1]);
            if (a0 <= 0 && a1 <= 0) continue;
            int k0 = c0 == c1 ? c0 : lerp(c0, c1, (double) i / (n - 1)), k1 = c0 == c1 ? c0 : lerp(c0, c1, (double) (i + 1) / (n - 1));
            int r = k0 >> 16 & 255, g = k0 >> 8 & 255, b = k0 & 255, r1 = k1 >> 16 & 255, g1 = k1 >> 8 & 255, b1 = k1 & 255;
            float x0 = (float) xs[i], y0 = (float) ys[i], z0 = (float) zs[i];
            float x1 = (float) xs[i + 1], y1 = (float) ys[i + 1], z1 = (float) zs[i + 1];
            float w0x = (float) WX[i], w0y = (float) WY[i], w0z = (float) WZ[i];
            float w1x = (float) WX[i + 1], w1y = (float) WY[i + 1], w1z = (float) WZ[i + 1];
            vc.addVertex(m, x0 - w0x, y0 - w0y, z0 - w0z).setColor(r, g, b, 0);
            vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
            vc.addVertex(m, x1, y1, z1).setColor(r1, g1, b1, a1);
            vc.addVertex(m, x1 - w1x, y1 - w1y, z1 - w1z).setColor(r1, g1, b1, 0);
            vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
            vc.addVertex(m, x0 + w0x, y0 + w0y, z0 + w0z).setColor(r, g, b, 0);
            vc.addVertex(m, x1 + w1x, y1 + w1y, z1 + w1z).setColor(r1, g1, b1, 0);
            vc.addVertex(m, x1, y1, z1).setColor(r1, g1, b1, a1);
        }
    }

    /** A camera-facing soft dot: full alpha in the middle, zero at the rim. Widens and dims like a strip when tiny. */
    private static void disc(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, float size, int color, int alpha) {
        double ex = camX - cx, ey = camY - cy, ez = camZ - cz;
        float radius = (float) Math.max(size, Math.sqrt(ex * ex + ey * ey + ez * ez) * MIN_WIDTH);
        alpha = (int) (alpha * (size / radius) * (size / radius));
        if (alpha <= 0) return;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        float x = (float) cx, y = (float) cy, z = (float) cz;
        for (int i = 0; i < DISC; i++) {
            float c0 = (float) COS[i] * radius, s0 = (float) SIN[i] * radius;
            float c1 = (float) COS[i + 1] * radius, s1 = (float) SIN[i + 1] * radius;
            float x0 = x + leftX * c0 + upX * s0, y0 = y + leftY * c0 + upY * s0, z0 = z + leftZ * c0 + upZ * s0;
            float x1 = x + leftX * c1 + upX * s1, y1 = y + leftY * c1 + upY * s1, z1 = z + leftZ * c1 + upZ * s1;
            vc.addVertex(m, x, y, z).setColor(r, g, b, alpha);
            vc.addVertex(m, x0, y0, z0).setColor(r, g, b, 0);
            vc.addVertex(m, x1, y1, z1).setColor(r, g, b, 0);
            vc.addVertex(m, x1, y1, z1).setColor(r, g, b, 0);
        }
    }

    private static int halo(int tier) {
        return TIER_HALO[Math.max(0, Math.min(TIER_HALO.length - 1, tier))];
    }

    /** The colour pulled two thirds of the way to the wisp's white: strand core, inner halo, sparks. */
    private static int pale(int c) {
        return lerp(c, WHITE, 2 / 3.0);
    }

    /** Per-channel mix of two 0xRRGGBB colours. */
    private static int lerp(int c0, int c1, double t) {
        int r = (int) ((c0 >> 16 & 255) + ((c1 >> 16 & 255) - (c0 >> 16 & 255)) * t);
        int g = (int) ((c0 >> 8 & 255) + ((c1 >> 8 & 255) - (c0 >> 8 & 255)) * t);
        int b = (int) ((c0 & 255) + ((c1 & 255) - (c0 & 255)) * t);
        return r << 16 | g << 8 | b;
    }

    /** Distance from the camera to the straight segment a-b (block space). */
    private static double segmentDistance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double l2 = dx * dx + dy * dy + dz * dz;
        double t = l2 < 1.0E-9 ? 0 : Math.max(0, Math.min(1, ((camX - ax) * dx + (camY - ay) * dy + (camZ - az) * dz) / l2));
        double ex = ax + dx * t - camX, ey = ay + dy * t - camY, ez = az + dz * t - camZ;
        return Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    /** 0 below 0, 1 above 1, smooth in between. */
    private static double smooth(double x) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        return x * x * (3 - 2 * x);
    }

    @Override
    public boolean shouldRenderOffScreen(TeslaCoilBlockEntity be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TeslaCoilBlockEntity be) {
        return be.renderBox();
    }

    /** By the distance to the whole link box, not the coil, so a link still shows from its far end. */
    @Override
    public boolean shouldRender(TeslaCoilBlockEntity be, Vec3 camera) {
        AABB box = be.renderBox();
        double dx = Math.max(0, Math.max(box.minX - camera.x, camera.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - camera.y, camera.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - camera.z, camera.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz < (double) getViewDistance() * getViewDistance();
    }

    @Override
    public int getViewDistance() {
        return (int) FAR_LINKER;
    }
}
