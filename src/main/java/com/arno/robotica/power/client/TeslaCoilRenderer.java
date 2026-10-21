package com.arno.robotica.power.client;

import com.arno.robotica.Robotica;
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Tesla links as soft energy tethers. Every link is a thin, slightly sagging strand of additive glow: a faint halo with a
 * brighter core, both fading out at the edges and into the coil tips, with a slow shimmer running along it. While energy
 * goes over a link, soft motes with short tails travel its strand towards the target (a little faster the bigger
 * the share of the coil's rate that link takes, from its synced flow level), and while the coil sends a tiny spark now and
 * then crackles off the tip. A link whose target takes nothing has no motes.
 * <p>
 * Every Tesla block's energy crystal is drawn here too, floating over the lens of its platform: a six sided gem with a
 * lit line down each ridge that hovers, turns slowly and glows, brighter while it sends, with a faint beam from the
 * lens and a soft glint near its tip that breathes slowly. Around a Tesla Coil's crystal three small shards circle on a
 * faint tilted ring, each with a short trail, faster while it sends. {@link TeslaItemRenderer} draws the same crystal
 * in the item.
 * <p>
 * Smooth: everything runs on a client tick clock plus the partial tick (the level's game time jumps on every server time
 * sync), the motes advance per frame by an eased speed so a flow change speeds them up or slows them down instead of
 * moving them, and they keep their identity along the strand so their twinkle never pops. Lines never get
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
    private static final double[] MOTE_SPEED = {0, 0.05, 0.058, 0.068, 0.08};
    /**
     * Motes sit on a lattice this far apart (wider on long links, at most {@link #MAX_MOTES} slots). They fade in as a
     * link starts carrying energy and out when it stops.
     */
    private static final double MOTE_GAP = 1.6;
    private static final int MAX_MOTES = 24;
    /** How fast the drawn flow follows the synced level, per tick. */
    private static final double FLOW_EASE = 0.12;
    /** One crackle every this many spark windows on average, per flow level. Each spark fades out over its window. */
    private static final int[] SPARK_ODDS = {0, 40, 28, 18, 12};
    private static final double SPARK_TICKS = 3;
    /** Least half width of a line or radius of a dot per block of camera distance (about a pixel and a half). */
    private static final double MIN_WIDTH = 0.002;

    /**
     * Halo colours per tier (0xRRGGBB), after the tier's crystal: I cyan, II blue, III amber, IV ember, V violet. The
     * strand core and inner halo are these pulled towards the wisp's white. A link between two tiers fades from one
     * colour to the other.
     */
    private static final int[] TIER_HALO = {0x5FD8F0, 0x6F8CFF, 0xFFBE50, 0xFF7846, 0x9A5AFF};
    /** The wisp's core white. */
    private static final int WHITE = 0xEBFAFF;

    /**
     * A floating crystal: an elongated bipyramid of {@code sides} facets, its middle {@code y} blocks above the face the
     * block sits on. Ring radius, lower point, straight middle and upper point in blocks; hover is the bob amplitude.
     */
    record Crystal(double y, double radius, double bottom, double body, double top, double hover, int sides) {}

    /**
     * Keep in step with TeslaCoilBlock.TIP and TeslaEmitterBlock.EMITTER_TIP (the crystal middles), the shapes and
     * TESLA_CRYSTALS in scripts/model_preview.py.
     */
    static final Crystal COIL_CRYSTAL = new Crystal(9 / 16.0, 2.2 / 16, 3.0 / 16, 1.2 / 16, 4.8 / 16, 0.4 / 16, 6);
    static final Crystal EMITTER_CRYSTAL = new Crystal(6 / 16.0, 1.3 / 16, 1.8 / 16, 0.5 / 16, 3.8 / 16, 0.3 / 16, 6);
    /** Height of the lens the beam rises from (top of the platform's glowing well), in blocks. */
    private static final double LENS = 1.4 / 16;
    /** Hover period and turn speed (ticks, radians per tick). */
    static final double HOVER_TICKS = 90, SPIN = Math.PI * 2 / 300;
    /** Facet brightness: darkest facet, and the upper point, middle and lower point. Light comes from above. */
    private static final double FACET_LO = 0.62, FACET_TOP = 1.0, FACET_BODY = 0.9, FACET_BOTTOM = 0.72;
    private static final double LX = -0.35, LY = 0.8, LZ = -0.5, LN = Math.sqrt(LX * LX + LY * LY + LZ * LZ);
    /** Crystal glow: halo radius per block of crystal radius, halo and core alphas idle and while sending, beam width. */
    private static final double HALO_R = 2.6, BEAM_W = 0.04;
    private static final int HALO_IDLE = 32, HALO_BUSY = 54, CORE_IDLE = 45, CORE_BUSY = 75, BEAM_IDLE = 26, BEAM_BUSY = 60;
    /**
     * Tip glint: a four pointed star a little below the upper point, arm length per block of crystal radius, its
     * alpha idle and while sending, and how fast it breathes (radians per tick).
     */
    private static final double GLINT_DOWN = 0.3, GLINT_ARM = 1.5, GLINT_RATE = 0.035;
    private static final int GLINT_IDLE = 55, GLINT_BUSY = 100;
    /** How fast the drawn glow follows sending or not, per tick. */
    private static final double BUSY_EASE = 0.08;

    // Coil shards: count, orbit radius and tilt (radians), size (radius, lower and upper point), trail length (radians),
    // and orbit speed in radians per tick while idle and while sending. Glow, ring, trails and shards only within
    // GLOW_RANGE of the camera.
    private static final int SHARDS = 3, RING_POINTS = 33, TRAIL_POINTS = 10;
    private static final double ORBIT_R = 0.34, ORBIT_TILT = 0.22, SHARD_W = 0.026, SHARD_BOTTOM = 0.045, SHARD_TOP = 0.08,
            TRAIL = 1.1;
    private static final double TILT_SIN = Math.sin(ORBIT_TILT), TILT_COS = Math.cos(ORBIT_TILT);
    static final double ORBIT_IDLE = 0.03;
    private static final double ORBIT_BUSY = 0.08, GLOW_RANGE = 32;
    /** Solid block-atlas sheet (a fixed buffer, so switching to it never ends the glow batch); faces go both ways round. */
    static final RenderType CRYSTAL_TYPE = Sheets.cutoutBlockSheet();
    private static final ResourceLocation[] CRYSTAL_TEX = new ResourceLocation[5];
    /** Turns the up-pointing model to each facing (index = Direction ordinal), like the blockstate does. */
    private static final Quaternionf[] FACING_ROT = new Quaternionf[6];

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
    /** Crystal points in block space (0 top, 1 bottom, then the upper and lower ring), the shards' centres, and a
     * scratch vector for turning them. */
    private static final int MAX_SIDES = 6;
    private static final double[] PX = new double[2 + 2 * MAX_SIDES], PY = new double[2 + 2 * MAX_SIDES], PZ = new double[2 + 2 * MAX_SIDES];
    private static final double[] GX = new double[SHARDS], GY = new double[SHARDS], GZ = new double[SHARDS];
    private static final Vector3f SCRATCH = new Vector3f();
    private static final Random RNG = new Random();
    /** Client ticks while the game is not paused. */
    private static long clientTicks;
    /** Result of {@link #pointAt} and of {@link #orbitPoint} (that one before the facing turn). */
    private static double px, py, pz;
    /** Camera position in the coil's block space, and the camera's left and up axes. */
    private static double camX, camY, camZ;
    private static float leftX, leftY, leftZ, upX, upY, upZ;

    static {
        for (int t = 0; t < CRYSTAL_TEX.length; t++) {
            CRYSTAL_TEX[t] = ResourceLocation.fromNamespaceAndPath(Robotica.MODID, "block/tesla_crystal_" + (t + 1));
        }
        for (Direction d : Direction.values()) FACING_ROT[d.ordinal()] = d.getRotation();
        for (int i = 0; i <= DISC; i++) {
            COS[i] = Math.cos(Math.PI * 2 * i / DISC);
            SIN[i] = Math.sin(Math.PI * 2 * i / DISC);
        }
    }

    public TeslaCoilRenderer(BlockEntityRendererProvider.Context context) {}

    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().isPaused()) clientTicks++;
    }

    /** Client ticks while the game is not paused, for animations. */
    static long clientTicks() {
        return clientTicks;
    }

    @Override
    public void render(TeslaCoilBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(TeslaCoilBlock.FACING)) return;
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

        double time = clientTicks + partialTick;
        double dt = be.fxTime < 0 ? 0 : Math.max(0, Math.min(20, time - be.fxTime));
        be.fxTime = time;
        double ease = 1 - Math.exp(-dt * FLOW_EASE);
        int synced = Math.min(4, be.flowLevel());
        Direction facing = state.getValue(TeslaCoilBlock.FACING);
        floating(be, facing, pose, buffers, time, dt, synced);

        int linkCount = be.linkCount();
        if (linkCount == 0 || !PowerClientConfig.teslaArcs()) return;
        boolean linker = TeslaCoilBlock.isConfiguring();
        double near = linker ? NEAR_LINKER : NEAR, far = linker ? FAR_LINKER : FAR;
        linkCount = Math.min(linkCount, be.fxFlow.length);
        for (int i = 0; i < linkCount; i++) {
            int target = Math.min(4, be.linkFlowLevel(i));
            be.fxFlow[i] += (float) ((target - be.fxFlow[i]) * ease);
            if (Math.abs(target - be.fxFlow[i]) < 0.002F) be.fxFlow[i] = target;
            be.fxTravel[i] += dt * moteSpeed(be.fxFlow[i]);
        }
        int halo = halo(be.tier().ordinal());

        double tip = TeslaCoilBlock.tipHeight(state) - 0.5;
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
                double t = TeslaCoilBlock.tipHeight(other) - 0.5;
                bx = p.getX() - origin.getX() + 0.5 + f.getStepX() * t;
                by = p.getY() - origin.getY() + 0.5 + f.getStepY() * t;
                bz = p.getZ() - origin.getZ() + 0.5 + f.getStepZ() * t;
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

    /**
     * The block's floating crystal with its glow, and on a Tesla Coil the shards round it. Each block bobs and turns on
     * its own phase. Glow, beam, glint and shards only near the camera.
     */
    private static void floating(TeslaCoilBlockEntity be, Direction facing, PoseStack pose, MultiBufferSource buffers, double time,
                                 double dt, int synced) {
        Crystal c = be.isEmitter() ? EMITTER_CRYSTAL : COIL_CRYSTAL;
        Quaternionf rot = FACING_ROT[facing.ordinal()];
        int tier = Math.max(0, Math.min(CRYSTAL_TEX.length - 1, be.tier().ordinal()));
        double phase = ((be.getBlockPos().asLong() * 0x9E3779B97F4A7C15L) >>> 40) / (double) (1 << 24) * Math.PI * 2;
        double y = c.y() - 0.5 + c.hover() * Math.sin(time * Math.PI * 2 / HOVER_TICKS + phase);
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        crystal(buffers.getBuffer(CRYSTAL_TYPE), last, sprite(tier), rot, 0, y, 0, c, time * SPIN + phase);
        if (camX * camX + camY * camY + camZ * camZ > GLOW_RANGE * GLOW_RANGE) return;

        be.fxBusy += ((synced > 0 ? 1 : 0) - be.fxBusy) * (1 - Math.exp(-dt * BUSY_EASE));
        float busy = (float) be.fxBusy;
        double pulse = 0.9 + 0.1 * Math.sin(time * Math.PI * 4 / HOVER_TICKS + phase);
        int color = TIER_HALO[tier], inner = pale(color);
        VertexConsumer glow = buffers.getBuffer(GLOW);
        local(rot, 0, 0, y, 0);
        double gx = PX[0], gy = PY[0], gz = PZ[0];
        disc(glow, m, gx, gy, gz, (float) (c.radius() * HALO_R), color, (int) ((HALO_IDLE + (HALO_BUSY - HALO_IDLE) * busy) * pulse));
        // the core: a bright dot just in front of the crystal's middle, so it seems to shine from inside
        double ex = camX - gx, ey = camY - gy, ez = camZ - gz, el = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (el > 1.0E-3) {
            double k = c.radius() * 1.1 / el;
            disc(glow, m, gx + ex * k, gy + ey * k, gz + ez * k, (float) (c.radius() * 1.6), inner,
                    (int) ((CORE_IDLE + (CORE_BUSY - CORE_IDLE) * busy) * pulse));
        }
        // beam from the lens to the crystal's lower point, with a slow flicker
        double from = LENS - 0.5, to = y - c.body() / 2 - c.bottom();
        int n = 7;
        for (int k = 0; k < n; k++) {
            double t = (double) k / (n - 1);
            local(rot, 0, 0, from + (to - from) * t, 0);
            QX[k] = PX[0];
            QY[k] = PY[0];
            QZ[k] = PZ[0];
            QA[k] = Math.sqrt(Math.sin(Math.PI * t)) * (0.85 + 0.15 * Math.sin(time * 0.5 + k * 1.7 + phase));
        }
        strip(glow, m, QX, QY, QZ, QA, n, (float) (BEAM_W * c.radius() / COIL_CRYSTAL.radius() + 0.01), color, inner,
                (int) (BEAM_IDLE + (BEAM_BUSY - BEAM_IDLE) * busy));
        local(rot, 0, 0, y + c.body() / 2 + c.top() * (1 - GLINT_DOWN), 0);
        glint(glow, m, PX[0], PY[0], PZ[0], c.radius(), inner, time, phase, busy);
        if (!be.isEmitter()) shards(be, rot, tier, last, buffers, time, dt, synced);
    }

    /**
     * A small four pointed star at (x, y, z), pulled towards the camera so the crystal never hides it. It breathes
     * slowly and smoothly, brighter while the block sends.
     */
    private static void glint(VertexConsumer vc, Matrix4f m, double x, double y, double z, double radius, int color, double time,
                              double phase, float busy) {
        double ex = camX - x, ey = camY - y, ez = camZ - z, el = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (el < 1.0E-3) return;
        double k = radius * 0.6 / el;
        x += ex * k;
        y += ey * k;
        z += ez * k;
        double s = 0.5 + 0.5 * Math.sin(time * GLINT_RATE + phase * 3);
        int alpha = (int) ((GLINT_IDLE + (GLINT_BUSY - GLINT_IDLE) * busy) * (0.7 + 0.3 * s));
        double arm = radius * GLINT_ARM * (0.88 + 0.12 * s);
        float w = (float) (radius * 0.09);
        for (int axis = 0; axis < 2; axis++) {
            double ax = axis == 0 ? leftX : upX, ay = axis == 0 ? leftY : upY, az = axis == 0 ? leftZ : upZ;
            double len = axis == 0 ? arm : arm * 0.65;
            for (int i = 0; i < 5; i++) {
                double t = (i - 2) / 2.0;
                QX[i] = x + ax * len * t;
                QY[i] = y + ay * len * t;
                QZ[i] = z + az * len * t;
                QA[i] = i == 2 ? 1 : i == 0 || i == 4 ? 0 : 0.22;
            }
            strip(vc, m, QX, QY, QZ, QA, 5, w, color, color, alpha);
        }
        disc(vc, m, x, y, z, (float) (radius * 0.55), WHITE, (int) (alpha * 0.9));
    }

    /**
     * Tesla Coil only: three small crystal shards circle the floating crystal on a faint tilted orbit ring, spinning,
     * each with a soft glow and a short trail. They circle faster while the coil sends (eased, so they never jump).
     */
    private static void shards(TeslaCoilBlockEntity be, Quaternionf rot, int tier, PoseStack.Pose last, MultiBufferSource buffers,
                               double time, double dt, int synced) {
        double target = synced > 0 ? ORBIT_BUSY + 0.012 * synced : ORBIT_IDLE;
        be.fxOrbitSpeed += (target - be.fxOrbitSpeed) * (1 - Math.exp(-dt * FLOW_EASE));
        be.fxOrbit = (be.fxOrbit + dt * be.fxOrbitSpeed) % (Math.PI * 2);
        Matrix4f m = last.pose();
        shardCrystals(buffers.getBuffer(CRYSTAL_TYPE), last, sprite(tier), rot, be.fxOrbit, time);
        VertexConsumer glow = buffers.getBuffer(GLOW);
        int color = TIER_HALO[tier], inner = pale(color);
        float fast = (float) Math.min(1, (be.fxOrbitSpeed - ORBIT_IDLE) / (ORBIT_BUSY - ORBIT_IDLE));
        int n = RING_POINTS;
        for (int k = 0; k < n; k++) {
            orbitPoint(Math.PI * 2 * k / (n - 1));
            local(rot, 0, px, py, pz);
            QX[k] = PX[0];
            QY[k] = PY[0];
            QZ[k] = PZ[0];
            QA[k] = 1;
        }
        strip(glow, m, QX, QY, QZ, QA, n, 0.016F, color, color, (int) (12 + 18 * fast));
        for (int i = 0; i < SHARDS; i++) {
            // a short trail behind the shard, fading in from its far end
            double a = be.fxOrbit + i * Math.PI * 2 / SHARDS;
            for (int k = 0; k < TRAIL_POINTS; k++) {
                double t = (double) k / (TRAIL_POINTS - 1);
                orbitPoint(a - TRAIL * (1 - t));
                local(rot, 0, px, py, pz);
                QX[k] = PX[0];
                QY[k] = PY[0];
                QZ[k] = PZ[0];
                QA[k] = t * t;
            }
            strip(glow, m, QX, QY, QZ, QA, TRAIL_POINTS, 0.022F, color, inner, (int) (40 + 45 * fast));
            disc(glow, m, GX[i], GY[i], GZ[i], 0.12F, color, (int) (40 + 30 * fast));
            disc(glow, m, GX[i], GY[i], GZ[i], 0.045F, inner, (int) (70 + 50 * fast));
        }
    }

    /** The coil's three shards at orbit angle {@code orbit}; their centres go to GX/GY/GZ. */
    static void shardCrystals(VertexConsumer vc, PoseStack.Pose last, TextureAtlasSprite sprite, Quaternionf rot, double orbit, double time) {
        for (int i = 0; i < SHARDS; i++) {
            orbitPoint(orbit + i * Math.PI * 2 / SHARDS);
            double cx = px, cy = py, cz = pz;
            crystal(vc, last, sprite, rot, cx, cy, cz, SHARD_W, SHARD_BOTTOM, 0, SHARD_TOP, time * 0.06 + i * 1.3, 4);
            local(rot, 0, cx, cy, cz);
            GX[i] = PX[0];
            GY[i] = PY[0];
            GZ[i] = PZ[0];
        }
    }

    /** Point at angle {@code a} on the coil's tilted shard orbit (block centre space, before the facing turn), into px/py/pz. */
    private static void orbitPoint(double a) {
        double s = Math.sin(a) * ORBIT_R;
        px = Math.cos(a) * ORBIT_R;
        py = COIL_CRYSTAL.y() - 0.5 + s * TILT_SIN;
        pz = s * TILT_COS;
    }

    /** The tier's crystal texture on the block atlas. */
    static TextureAtlasSprite sprite(int tier) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(CRYSTAL_TEX[tier]);
    }

    /** Facing turn for the up-pointing model. */
    static Quaternionf facingRotation(Direction facing) {
        return FACING_ROT[facing.ordinal()];
    }

    /** Crystal {@code c} round (cx, cy, cz), turned by {@code spin}; see the other overload. */
    static void crystal(VertexConsumer vc, PoseStack.Pose last, TextureAtlasSprite sprite, Quaternionf rot, double cx, double cy,
                        double cz, Crystal c, double spin) {
        crystal(vc, last, sprite, rot, cx, cy, cz, c.radius(), c.bottom(), c.body(), c.top(), spin, c.sides());
    }

    /**
     * An elongated bipyramid round (cx, cy, cz) (block centre space, before {@code rot}): a ring of {@code sides}
     * points at {@code radius}, a straight middle {@code body} high, points {@code bottom} below and {@code top} above,
     * turned by {@code spin} round its axis. The texture runs down each facet from the upper point to the lower one,
     * its left column along the facet's first ridge (the lit line); facets facing the light from above are brighter.
     * Full bright.
     */
    static void crystal(VertexConsumer vc, PoseStack.Pose last, TextureAtlasSprite sprite, Quaternionf rot, double cx, double cy,
                        double cz, double radius, double bottom, double body, double top, double spin, int sides) {
        sides = Math.min(MAX_SIDES, sides);
        double y0 = cy - body / 2, y1 = cy + body / 2, total = bottom + body + top;
        local(rot, 0, cx, y1 + top, cz);
        local(rot, 1, cx, y0 - bottom, cz);
        for (int k = 0; k < sides; k++) {
            double e = spin + k * Math.PI * 2 / sides, dx = Math.cos(e) * radius, dz = Math.sin(e) * radius;
            local(rot, 2 + k, cx + dx, y1, cz + dz);
            local(rot, 2 + sides + k, cx + dx, y0, cz + dz);
        }
        SCRATCH.set((float) cx, (float) cy, (float) cz);
        rot.transform(SCRATCH);
        double mx = SCRATCH.x + 0.5, my = SCRATCH.y + 0.5, mz = SCRATCH.z + 0.5;
        float vTop = (float) (top / total), vBottom = (float) ((top + body) / total);
        for (int k = 0; k < sides; k++) {
            int u0 = 2 + k, u1 = 2 + (k + 1) % sides, l0 = u0 + sides, l1 = u1 + sides;
            float shade = facet(0, u0, u1, mx, my, mz) * (float) FACET_TOP;
            tri(vc, last, sprite, 0, u0, u1, 0, 0, 0, vTop, 1, vTop, shade);
            if (body > 0) {
                shade = facet(u0, l0, u1, mx, my, mz) * (float) FACET_BODY;
                tri(vc, last, sprite, u0, l0, l1, 0, vTop, 0, vBottom, 1, vBottom, shade);
                tri(vc, last, sprite, u0, l1, u1, 0, vTop, 1, vBottom, 1, vTop, shade);
            }
            shade = facet(1, l1, l0, mx, my, mz) * (float) FACET_BOTTOM;
            tri(vc, last, sprite, 1, l1, l0, 0, 1, 1, vBottom, 0, vBottom, shade);
        }
    }

    /** Brightness of the facet through points a, b, c of a crystal centred on (mx, my, mz): lit from above. */
    private static float facet(int a, int b, int c, double mx, double my, double mz) {
        double ux = PX[b] - PX[a], uy = PY[b] - PY[a], uz = PZ[b] - PZ[a];
        double vx = PX[c] - PX[a], vy = PY[c] - PY[a], vz = PZ[c] - PZ[a];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0E-12) return (float) FACET_LO;
        // point the normal away from the middle
        if (nx * (PX[a] - mx) + ny * (PY[a] - my) + nz * (PZ[a] - mz) < 0) nl = -nl;
        double d = (nx * LX + ny * LY + nz * LZ) / (nl * LN);
        return (float) (FACET_LO + (1 - FACET_LO) * Math.max(0, d));
    }

    /**
     * One crystal triangle both ways round (as quads with the last corner twice). UVs are fractions of the sprite;
     * {@code shade} darkens the white vertex colour.
     */
    private static void tri(VertexConsumer vc, PoseStack.Pose last, TextureAtlasSprite sprite, int a, int b, int c,
                            float ua, float va, float ub, float vb, float uc, float vc_, float shade) {
        double ux = PX[b] - PX[a], uy = PY[b] - PY[a], uz = PZ[b] - PZ[a];
        double vx = PX[c] - PX[a], vy = PY[c] - PY[a], vz = PZ[c] - PZ[a];
        float nx = (float) (uy * vz - uz * vy), ny = (float) (uz * vx - ux * vz), nz = (float) (ux * vy - uy * vx);
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1.0E-9F) return;
        nx /= nl;
        ny /= nl;
        nz /= nl;
        Matrix4f m = last.pose();
        int k = Math.max(0, Math.min(255, (int) (255 * shade)));
        float au = sprite.getU(ua), av = sprite.getV(va), bu = sprite.getU(ub), bv = sprite.getV(vb), cu = sprite.getU(uc), cv = sprite.getV(vc_);
        vertex(vc, last, m, a, au, av, k, nx, ny, nz);
        vertex(vc, last, m, b, bu, bv, k, nx, ny, nz);
        vertex(vc, last, m, c, cu, cv, k, nx, ny, nz);
        vertex(vc, last, m, c, cu, cv, k, nx, ny, nz);
        vertex(vc, last, m, a, au, av, k, -nx, -ny, -nz);
        vertex(vc, last, m, c, cu, cv, k, -nx, -ny, -nz);
        vertex(vc, last, m, b, bu, bv, k, -nx, -ny, -nz);
        vertex(vc, last, m, b, bu, bv, k, -nx, -ny, -nz);
    }

    /** Point {@code k} of the crystal scratch: (x, y, z) around the block centre, turned by {@code rot}, in block space. */
    private static void local(Quaternionf rot, int k, double x, double y, double z) {
        SCRATCH.set((float) x, (float) y, (float) z);
        rot.transform(SCRATCH);
        PX[k] = SCRATCH.x + 0.5;
        PY[k] = SCRATCH.y + 0.5;
        PZ[k] = SCRATCH.z + 0.5;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, Matrix4f m, int p, float u, float v, int k, float nx, float ny, float nz) {
        vc.addVertex(m, (float) PX[p], (float) PY[p], (float) PZ[p]).setColor(k, k, k, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(last, nx, ny, nz);
    }

    /** Mote speed at an eased flow level, blended between the two nearest levels. */
    private static double moteSpeed(float flow) {
        int i = Math.max(0, Math.min(3, (int) flow));
        double f = Math.max(0, Math.min(1, flow - i));
        return MOTE_SPEED[i] + (MOTE_SPEED[i + 1] - MOTE_SPEED[i]) * f;
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
        double gap = Math.max(MOTE_GAP, len / MAX_MOTES), tail = 0.45 + 0.04 * flow;
        double pos = travel + phase / (Math.PI * 2) * gap;
        long first = (long) Math.floor(pos / gap);
        double head0 = pos - first * gap;
        for (int k = 0; head0 + k * gap < len; k++) {
            double s = head0 + k * gap;
            long id = k - first;
            float a = fade * Math.min(1F, flow) * (float) (smooth(s / 0.5) * smooth((len - s) / 0.5))
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
            strip(vc, m, QX, QY, QZ, QA, TAIL_POINTS, 0.035F, halo, halo, (int) (70 * a));
            strip(vc, m, QX, QY, QZ, QA, TAIL_POINTS, 0.008F, inner, inner, (int) (115 * a));
            pointAt(n, s);
            // two-layer halo round a white point, like the Spark Lamp wisp
            disc(vc, m, px, py, pz, 0.18F, halo, (int) (66 * a));
            disc(vc, m, px, py, pz, 0.065F, inner, (int) (97 * a));
            disc(vc, m, px, py, pz, 0.025F, WHITE, (int) (195 * a));
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
