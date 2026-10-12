package com.arno.robotica.logistics.client;

import com.arno.robotica.logistics.LogisticsClientConfig;
import com.arno.robotica.logistics.net.PipeGlowPayload;
import com.arno.robotica.logistics.pipe.ItemPipeBlock;
import com.arno.robotica.logistics.pipe.PipeConnection;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Arrays;

/**
 * The light along an item pipe route ({@link PipeGlowPayload}): the Extract connector flashes, a quick wave runs down
 * the channel of every pipe on the way to the Insert connector, which flashes too, and all of it fades in about half a
 * second. A route sent again while it still glows only refreshes the light. Additive full-bright quads just outside
 * the pipe model (sizes in step with scripts/data/logistics_data.py), drawn after the particles.
 * <p>
 * Per pipe one slot in flat arrays, at most {@link #MAX} of them: start times of its six arms, its node and its six
 * connectors. Nothing is allocated per frame or per tick. Render thread only.
 */
public final class ItemPipeGlow {
    private ItemPipeGlow() {}

    private static final RenderType GLOW = RenderType.create("robotica_pipe_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 8192, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    /** Pipes lit at once at most; more are skipped until some fade. */
    static final int MAX = 1024;
    /** Per slot: 0-5 the arm toward that face (3D data value), 6 the node, 7-12 the connector on that face. */
    private static final int NODE = 6, CONNECTOR = 7, STRIDE = 13;
    /** Ticks: fade out, fade in, the wave's time per pipe and for the whole route at most. */
    private static final float FADE = 10, RISE = 1, STEP = 0.4F, SWEEP = 6;
    private static final double RANGE = 64;
    private static final float EMPTY = -1.0E6F;

    /** Channel light per Mk, and the Extract and Insert connectors. */
    private static final int[] TIER_COLOR = {0xFF9A4A, 0x86F060, 0xFF5238, 0xA48CFF};
    private static final int EXTRACT = 0xFFB21E, INSERT = 0x5FE3F0;

    private static final float P = 1 / 16F, E = 1 / 256F;
    private static final float TUBE = 2 * P, CHANNEL = P, NODE_HALF = 3 * P, COLLAR = 3 * P, RING = 4 * P;

    private static final long[] POS = new long[MAX];
    private static final byte[] TIER = new byte[MAX];
    private static final boolean[] HAS_NODE = new boolean[MAX];
    /** Faces with an arm (bit per 3D data value): only those light. */
    private static final byte[] ARMS = new byte[MAX];
    /** Per slot and face: 0 none, 1 Extract, 2 Insert connector. */
    private static final byte[] KIND = new byte[MAX * 6];
    /** Start of the newest and the previous light per part (ticks since {@link #base}), so a refresh never dips. */
    private static final float[] START = new float[MAX * STRIDE], PREV = new float[MAX * STRIDE];
    /** When the slot's last light is out. */
    private static final float[] UNTIL = new float[MAX];
    private static final Long2IntOpenHashMap INDEX = new Long2IntOpenHashMap();
    private static final BlockPos.MutableBlockPos SCRATCH = new BlockPos.MutableBlockPos();
    private static final float[] Q = new float[3];
    private static int count;
    private static long base;
    private static ClientLevel level;

    static {
        INDEX.defaultReturnValue(-1);
    }

    // ------------------------------------------------------------------ state

    static void clear() {
        INDEX.clear();
        count = 0;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            clear();
            level = current;
        }
        if (current == null || !LogisticsClientConfig.pipeGlow()) {
            PipeGlowPayload.drain(p -> {});
            if (count > 0) clear();
            return;
        }
        PipeGlowPayload.drain(ItemPipeGlow::accept);
        float now = current.getGameTime() - base;
        for (int i = count - 1; i >= 0; i--) if (UNTIL[i] < now) remove(i);
    }

    private static void accept(PipeGlowPayload p) {
        if (count == 0) base = level.getGameTime();
        float t = level.getGameTime() - base;
        byte[] steps = p.steps();
        float step = steps.length == 0 ? 0 : Math.min(STEP, SWEEP / steps.length);
        long at = p.from();
        int slot = slot(at);
        light(slot, CONNECTOR + p.fromSide(), t);
        light(slot, p.fromSide(), t);
        for (byte s : steps) {
            light(slot, NODE, t);
            light(slot, s, t);
            t += step;
            Direction dir = Direction.from3DDataValue(s);
            at = BlockPos.offset(at, dir);
            slot = slot(at);
            light(slot, dir.getOpposite().get3DDataValue(), t);
        }
        light(slot, NODE, t);
        if (p.toSide() < 0) return;
        if (at != p.to()) slot = slot(p.to());
        light(slot, p.toSide(), t);
        light(slot, CONNECTOR + p.toSide(), t + step);
    }

    /** The slot of the pipe at {@code pos}, made when missing (-1: no pipe there, or no room). Refreshes its look. */
    private static int slot(long pos) {
        BlockState state = level.getBlockState(SCRATCH.set(pos));
        if (!(state.getBlock() instanceof ItemPipeBlock pipe)) return -1;
        int slot = INDEX.get(pos);
        if (slot < 0) {
            if (count >= MAX) return -1;
            slot = count++;
            INDEX.put(pos, slot);
            POS[slot] = pos;
            Arrays.fill(START, slot * STRIDE, slot * STRIDE + STRIDE, EMPTY);
            Arrays.fill(PREV, slot * STRIDE, slot * STRIDE + STRIDE, EMPTY);
            UNTIL[slot] = EMPTY;
        }
        TIER[slot] = (byte) (pipe.tier() - 1);
        HAS_NODE[slot] = !ItemPipeBlock.isStraight(state);
        int arms = 0;
        for (Direction dir : Direction.values()) {
            PipeConnection c = state.getValue(ItemPipeBlock.prop(dir));
            if (c.hasArm()) arms |= 1 << dir.ordinal();
            KIND[slot * 6 + dir.ordinal()] = (byte) (c == PipeConnection.EXTRACT ? 1 : c == PipeConnection.INSERT ? 2 : 0);
        }
        ARMS[slot] = (byte) arms;
        return slot;
    }

    private static void light(int slot, int part, float t) {
        if (slot < 0) return;
        int i = slot * STRIDE + part;
        PREV[i] = START[i];
        START[i] = t;
        UNTIL[slot] = Math.max(UNTIL[slot], t + FADE);
    }

    /** Moves the last slot into {@code slot}. */
    private static void remove(int slot) {
        INDEX.remove(POS[slot]);
        int last = --count;
        if (slot == last) return;
        POS[slot] = POS[last];
        TIER[slot] = TIER[last];
        HAS_NODE[slot] = HAS_NODE[last];
        ARMS[slot] = ARMS[last];
        UNTIL[slot] = UNTIL[last];
        System.arraycopy(KIND, last * 6, KIND, slot * 6, 6);
        System.arraycopy(START, last * STRIDE, START, slot * STRIDE, STRIDE);
        System.arraycopy(PREV, last * STRIDE, PREV, slot * STRIDE, STRIDE);
        INDEX.put(POS[slot], slot);
    }

    private static float bright(int i, float t) {
        return Math.max(curve(t - START[i]), curve(t - PREV[i]));
    }

    private static float curve(float age) {
        if (age < -RISE || age >= FADE) return 0;
        if (age < 0) return 1 + age / RISE;
        float u = age / FADE;
        return 1 - u * u * (3 - 2 * u);
    }

    // ------------------------------------------------------------------ render

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || count == 0) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl == null || lvl != level) return;
        Vec3 cam = event.getCamera().getPosition();
        float t = lvl.getGameTime() - base + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        for (int slot = 0; slot < count; slot++) {
            long pos = POS[slot];
            double dx = BlockPos.getX(pos) - cam.x, dy = BlockPos.getY(pos) - cam.y, dz = BlockPos.getZ(pos) - cam.z;
            if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
            float ox = (float) dx, oy = (float) dy, oz = (float) dz;
            int color = TIER_COLOR[TIER[slot] & 3], o = slot * STRIDE;
            for (int part = 0; part < 6; part++) {
                float a = (ARMS[slot] >> part & 1) == 0 ? 0 : bright(o + part, t);
                if (a <= 0.004F) continue;
                if (vc == null) vc = buffers.getBuffer(GLOW);
                arm(vc, ox, oy, oz, Direction.from3DDataValue(part), color, a);
            }
            if (HAS_NODE[slot]) {
                float a = bright(o + NODE, t);
                if (a > 0.004F) {
                    if (vc == null) vc = buffers.getBuffer(GLOW);
                    node(vc, ox, oy, oz, color, a);
                }
            }
            for (int face = 0; face < 6; face++) {
                int kind = KIND[slot * 6 + face];
                float a = kind == 0 ? 0 : bright(o + CONNECTOR + face, t);
                if (a <= 0.004F) continue;
                if (vc == null) vc = buffers.getBuffer(GLOW);
                connector(vc, ox, oy, oz, Direction.from3DDataValue(face), kind == 1 ? EXTRACT : INSERT, a);
            }
        }
        if (vc != null) buffers.endBatch(GLOW);
    }

    /** The channel on the four sides of the arm toward {@code dir}, from the centre to the block face, and a soft halo. */
    private static void arm(VertexConsumer vc, float ox, float oy, float oz, Direction dir, int color, float a) {
        int axis = dir.getAxis().ordinal(), b = (axis + 1) % 3, c = (axis + 2) % 3;
        float from = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.5F : 0, to = from + 0.5F;
        for (int side = 0; side < 4; side++) {
            int n = side < 2 ? b : c, across = side < 2 ? c : b;
            float sgn = (side & 1) == 0 ? -1 : 1;
            quad(vc, ox, oy, oz, n, 0.5F + sgn * (TUBE + E), axis, from, to, across, 0.5F - CHANNEL, 0.5F + CHANNEL, color, (int) (a * 255));
            quad(vc, ox, oy, oz, n, 0.5F + sgn * (TUBE + 2 * E), axis, from, to, across, 0.5F - TUBE, 0.5F + TUBE, color, (int) (a * 30));
        }
    }

    /** The node's six windows and a halo over its faces. */
    private static void node(VertexConsumer vc, float ox, float oy, float oz, int color, float a) {
        for (int n = 0; n < 3; n++) {
            int u = (n + 1) % 3, v = (n + 2) % 3;
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                quad(vc, ox, oy, oz, n, 0.5F + sgn * (NODE_HALF + E), u, 0.5F - CHANNEL, 0.5F + CHANNEL, v, 0.5F - CHANNEL, 0.5F + CHANNEL,
                        color, (int) (a * 255));
                quad(vc, ox, oy, oz, n, 0.5F + sgn * (NODE_HALF + 2 * E), u, 0.5F - NODE_HALF, 0.5F + NODE_HALF, v, 0.5F - NODE_HALF,
                        0.5F + NODE_HALF, color, (int) (a * 30));
            }
        }
    }

    /** The connector on face {@code dir}: its collar (the chevrons), its end and the ring on the plate. */
    private static void connector(VertexConsumer vc, float ox, float oy, float oz, Direction dir, int color, float a) {
        int axis = dir.getAxis().ordinal(), b = (axis + 1) % 3, c = (axis + 2) % 3;
        boolean pos = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        // depth from the block face: the plate is 1 px, the collar runs from 1 to 4 px
        float plate = pos ? 1 - P - E : P + E, end = pos ? 1 - 4 * P - E : 4 * P + E;
        float c0 = pos ? 1 - 4 * P : P, c1 = pos ? 1 - P : 4 * P;
        for (int side = 0; side < 4; side++) {
            int n = side < 2 ? b : c, across = side < 2 ? c : b;
            float sgn = (side & 1) == 0 ? -1 : 1;
            quad(vc, ox, oy, oz, n, 0.5F + sgn * (COLLAR + E), axis, c0, c1, across, 0.5F - COLLAR, 0.5F + COLLAR, color, (int) (a * 100));
        }
        quad(vc, ox, oy, oz, axis, plate, b, 0.5F - RING, 0.5F + RING, c, 0.5F - RING, 0.5F + RING, color, (int) (a * 230));
        quad(vc, ox, oy, oz, axis, end, b, 0.5F - COLLAR, 0.5F + COLLAR, c, 0.5F - COLLAR, 0.5F + COLLAR, color, (int) (a * 70));
    }

    /** A quad on the plane {@code axis n = at} (block-local), spanning {@code u0..u1} on axis u and {@code v0..v1} on axis v. */
    private static void quad(VertexConsumer vc, float ox, float oy, float oz, int n, float at, int u, float u0, float u1, int v, float v0, float v1,
                             int color, int alpha) {
        if (alpha <= 0) return;
        alpha = Math.min(255, alpha);
        int r = color >> 16 & 255, g = color >> 8 & 255, bl = color & 255;
        Q[n] = at;
        Q[u] = u0;
        Q[v] = v0;
        vc.addVertex(ox + Q[0], oy + Q[1], oz + Q[2]).setColor(r, g, bl, alpha);
        Q[u] = u1;
        vc.addVertex(ox + Q[0], oy + Q[1], oz + Q[2]).setColor(r, g, bl, alpha);
        Q[v] = v1;
        vc.addVertex(ox + Q[0], oy + Q[1], oz + Q[2]).setColor(r, g, bl, alpha);
        Q[u] = u0;
        vc.addVertex(ox + Q[0], oy + Q[1], oz + Q[2]).setColor(r, g, bl, alpha);
    }
}
