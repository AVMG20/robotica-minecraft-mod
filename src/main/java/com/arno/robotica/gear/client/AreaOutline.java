package com.arno.robotica.gear.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.gear.GearClientConfig;
import com.arno.robotica.gear.tool.AreaBreaker;
import com.arno.robotica.gear.tool.AreaMode;
import com.arno.robotica.gear.tool.GearToolItem;
import com.arno.robotica.gear.tool.ToolSettings;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Soft glowing outline of the blocks the held tool would break (target included), using the same math as the server.
 * Only the outer edges of the whole area are drawn, as camera-facing ribbons (pale thread in a blue halo) with soft
 * glows on the outer corners and a faint tint on the outer faces; a slow shimmer runs along the edges and the outline
 * fades in on a new target. The area and its geometry are worked out on the client tick, only when the target, mode or
 * blocks change; a frame only writes vertices.
 */
final class AreaOutline {
    private AreaOutline() {}

    private static final int MAX_BLOCKS = 600;
    private static final int RECOMPUTE_TICKS = 10;
    private static final float FADE_TICKS = 3;
    private static final Direction[] DIRS = Direction.values();

    private static final ResourceLocation TEXTURE = Robotica.id("textures/misc/area_glow.png");
    /** Additive, full-bright, depth tested, no depth write, drawn into the particle target (Fabulous) or main. */
    private static final RenderType GLOW = RenderType.create("robotica_area_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            16384, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TEXTURE, true, false))
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    // Sheet regions in 32 px texture units (u0, v0, u1, v1); the edge strip is sampled away from its ends.
    private static final float T = 1 / 32.0F;
    private static final float[] EDGE = {8 * T, 0.5F * T, 24 * T, 15.5F * T}, DOT = {0, 16 * T, 16 * T, 1}, FLAT = {20 * T, 20 * T, 28 * T, 28 * T};

    // Colours (r, g, b).
    private static final int[] HALO = {60, 150, 255}, CORE = {200, 240, 255}, CORNER = {150, 215, 255}, TINT = {70, 160, 255};

    // ---------------------------------------------------------------- state from the client tick

    private static BlockPos origin;
    private static Direction face;
    private static AreaMode mode;
    private static int flags, feetY;
    private static long builtAt = Long.MIN_VALUE, shownAt;
    private static List<BlockPos> area = List.of();
    private static boolean drawThisFrame;

    private static final LongOpenHashSet CELLS = new LongOpenHashSet();
    private static final LongOpenHashSet[] EDGES = {new LongOpenHashSet(), new LongOpenHashSet(), new LongOpenHashSet()};
    private static final LongOpenHashSet SEEN = new LongOpenHashSet();
    /** Edge runs: x, y, z of the start, axis (0 x, 1 y, 2 z), length in blocks. */
    private static int[] runs = new int[5 * 64];
    /** Outer faces: x, y, z of the block, direction ordinal. */
    private static int[] faces = new int[4 * 128];
    /** Outer corners: x, y, z of the lattice point. */
    private static int[] corners = new int[3 * 32];
    private static int runCount, faceCount, cornerCount;

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            clear();
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GearToolItem tool)) {
            clear();
            return;
        }
        AreaMode m = tool.activeMode(stack, player);
        if (m == AreaMode.SINGLE) {
            clear();
            return;
        }
        BlockPos o = hit.getBlockPos();
        Direction f = hit.getDirection();
        int fl = ToolSettings.flags(stack);
        int fy = player.blockPosition().getY();
        long time = level.getGameTime();
        boolean moved = !o.equals(origin) || f != face || m != mode || fl != flags || fy != feetY;
        if (!moved && time - builtAt < RECOMPUTE_TICKS && time >= builtAt) return;
        List<BlockPos> next = AreaBreaker.collect(level, player, stack, tool, o, f);
        if (moved || !next.equals(area)) {
            if (!o.equals(origin) || m != mode) shownAt = time;
            build(o, next);
        }
        origin = o.immutable();
        face = f;
        mode = m;
        flags = fl;
        feetY = fy;
        area = next;
        builtAt = time;
    }

    private static void clear() {
        if (origin == null) return;
        origin = null;
        mode = null;
        area = List.of();
        runCount = faceCount = cornerCount = 0;
    }

    /** The vanilla outline is drawn just before particles: only show ours on frames where the game shows one here. */
    static void onHighlight(RenderHighlightEvent.Block event) {
        drawThisFrame = origin != null && event.getTarget().getBlockPos().equals(origin) && (runCount > 0 || faceCount > 0);
    }

    // ---------------------------------------------------------------- geometry (on change only)

    private static void build(BlockPos o, List<BlockPos> extra) {
        CELLS.clear();
        CELLS.add(o.asLong());
        int n = Math.min(extra.size(), MAX_BLOCKS - 1);
        for (int i = 0; i < n; i++) CELLS.add(extra.get(i).asLong());
        for (LongOpenHashSet e : EDGES) e.clear();
        SEEN.clear();
        runCount = faceCount = cornerCount = 0;

        var it = CELLS.longIterator();
        while (it.hasNext()) {
            long c = it.nextLong();
            int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
            for (Direction d : DIRS) {
                if (!has(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) addFace(x, y, z, d.ordinal());
            }
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    edge(0, x, y + a, z + b);
                    edge(1, x + a, y, z + b);
                    edge(2, x + a, y + b, z);
                }
            }
            for (int k = 0; k < 8; k++) {
                int vx = x + (k & 1), vy = y + (k >> 1 & 1), vz = z + (k >> 2 & 1);
                if (SEEN.add(BlockPos.asLong(vx, vy, vz)) && around(vx, vy, vz) == 1) addCorner(vx, vy, vz);
            }
        }
        // join neighbouring edges on one line into runs
        for (int axis = 0; axis < 3; axis++) {
            LongOpenHashSet set = EDGES[axis];
            int sx = axis == 0 ? 1 : 0, sy = axis == 1 ? 1 : 0, sz = axis == 2 ? 1 : 0;
            var edges = set.longIterator();
            while (edges.hasNext()) {
                long e = edges.nextLong();
                int x = BlockPos.getX(e), y = BlockPos.getY(e), z = BlockPos.getZ(e);
                if (set.contains(BlockPos.asLong(x - sx, y - sy, z - sz))) continue;
                int len = 1;
                while (set.contains(BlockPos.asLong(x + sx * len, y + sy * len, z + sz * len))) len++;
                addRun(x, y, z, axis, len);
            }
        }
    }

    private static boolean has(int x, int y, int z) {
        return CELLS.contains(BlockPos.asLong(x, y, z));
    }

    /** Keeps the edge along {@code axis} from lattice point (x, y, z) when it is an outer edge of the area. */
    private static void edge(int axis, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (EDGES[axis].contains(key)) return;
        // the four cells around the edge, in the two other axes: c00, c10, c01, c11
        boolean c00, c10, c01, c11;
        if (axis == 0) {
            c00 = has(x, y - 1, z - 1);
            c10 = has(x, y, z - 1);
            c01 = has(x, y - 1, z);
            c11 = has(x, y, z);
        } else if (axis == 1) {
            c00 = has(x - 1, y, z - 1);
            c10 = has(x, y, z - 1);
            c01 = has(x - 1, y, z);
            c11 = has(x, y, z);
        } else {
            c00 = has(x - 1, y - 1, z);
            c10 = has(x, y - 1, z);
            c01 = has(x - 1, y, z);
            c11 = has(x, y, z);
        }
        int count = (c00 ? 1 : 0) + (c10 ? 1 : 0) + (c01 ? 1 : 0) + (c11 ? 1 : 0);
        if (count == 1 || count == 3 || count == 2 && c00 == c11) EDGES[axis].add(key);
    }

    /** How many area cells touch lattice point (x, y, z). */
    private static int around(int x, int y, int z) {
        int n = 0;
        for (int k = 0; k < 8; k++) if (has(x - 1 + (k & 1), y - 1 + (k >> 1 & 1), z - 1 + (k >> 2 & 1))) n++;
        return n;
    }

    private static void addRun(int x, int y, int z, int axis, int len) {
        if (runCount * 5 + 5 > runs.length) runs = java.util.Arrays.copyOf(runs, runs.length * 2);
        int i = runCount++ * 5;
        runs[i] = x;
        runs[i + 1] = y;
        runs[i + 2] = z;
        runs[i + 3] = axis;
        runs[i + 4] = len;
    }

    private static void addFace(int x, int y, int z, int dir) {
        if (faceCount * 4 + 4 > faces.length) faces = java.util.Arrays.copyOf(faces, faces.length * 2);
        int i = faceCount++ * 4;
        faces[i] = x;
        faces[i + 1] = y;
        faces[i + 2] = z;
        faces[i + 3] = dir;
    }

    private static void addCorner(int x, int y, int z) {
        if (cornerCount * 3 + 3 > corners.length) corners = java.util.Arrays.copyOf(corners, corners.length * 2);
        int i = cornerCount++ * 3;
        corners[i] = x;
        corners[i + 1] = y;
        corners[i + 2] = z;
    }

    // ---------------------------------------------------------------- drawing

    private static double camX, camY, camZ;
    private static float time, rightX, rightY, rightZ, upX, upY, upZ;
    private static boolean animated;

    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!drawThisFrame) return;
        drawThisFrame = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || origin == null) return;
        Vec3 cam = event.getCamera().getPosition();
        camX = cam.x;
        camY = cam.y;
        camZ = cam.z;
        var left = event.getCamera().getLeftVector();
        var up = event.getCamera().getUpVector();
        rightX = -left.x();
        rightY = -left.y();
        rightZ = -left.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = mc.level.getGameTime();
        time = (tick % 24000L) + partial;
        animated = GearClientConfig.outlineAnimated();
        float fade = animated ? Mth.clamp((tick - shownAt + partial) / FADE_TICKS, 0, 1) : 1;
        fade = fade * fade * (3 - 2 * fade);
        float breath = animated ? 0.88F + 0.12F * Mth.sin(time * 0.1F) : 1;
        float a = fade * breath;
        if (a <= 0.01F) return;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(GLOW);
        for (int i = 0; i < faceCount; i++) face(vc, i * 4, 0.055F * a);
        for (int i = 0; i < runCount; i++) {
            run(vc, i * 5, 0.08F, HALO, 0.5F * a);
            run(vc, i * 5, 0.016F, CORE, 0.85F * a);
        }
        for (int i = 0; i < cornerCount; i++) {
            int j = i * 3;
            float x = (float) (corners[j] - camX), y = (float) (corners[j + 1] - camY), z = (float) (corners[j + 2] - camZ);
            sprite(vc, x, y, z, 0.13F, CORNER, 0.4F * a * shimmer(corners[j], corners[j + 1], corners[j + 2]));
        }
        buffers.endBatch(GLOW);
    }

    /** A soft wave running slowly across the area, 0.7 to 1. */
    private static float shimmer(int x, int y, int z) {
        return animated ? 0.85F + 0.15F * Mth.sin(time * 0.13F - (x + y + z) * 0.7F) : 1;
    }

    /** A faint tint on one outer face, lifted a hair off the block. */
    private static void face(VertexConsumer vc, int i, float alpha) {
        int al = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (al <= 0) return;
        Direction d = DIRS[faces[i + 3]];
        float x = (float) (faces[i] - camX), y = (float) (faces[i + 1] - camY), z = (float) (faces[i + 2] - camZ);
        float lift = 0.004F;
        switch (d.getAxis()) {
            case X -> {
                float fx = x + (d.getStepX() > 0 ? 1 + lift : -lift);
                quad(vc, fx, y, z, fx, y + 1, z, fx, y + 1, z + 1, fx, y, z + 1, al);
            }
            case Y -> {
                float fy = y + (d.getStepY() > 0 ? 1 + lift : -lift);
                quad(vc, x, fy, z, x + 1, fy, z, x + 1, fy, z + 1, x, fy, z + 1, al);
            }
            case Z -> {
                float fz = z + (d.getStepZ() > 0 ? 1 + lift : -lift);
                quad(vc, x, y, fz, x + 1, y, fz, x + 1, y + 1, fz, x, y + 1, fz, al);
            }
        }
    }

    private static void quad(VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, int alpha) {
        vertex(vc, x0, y0, z0, FLAT[0], FLAT[1], TINT, alpha);
        vertex(vc, x1, y1, z1, FLAT[2], FLAT[1], TINT, alpha);
        vertex(vc, x2, y2, z2, FLAT[2], FLAT[3], TINT, alpha);
        vertex(vc, x3, y3, z3, FLAT[0], FLAT[3], TINT, alpha);
    }

    /**
     * One edge run as a camera-facing ribbon, one piece per block so the shimmer can run along it; the pieces share
     * their joints, and the ends reach a little past the corner so meeting runs overlap into a brighter corner.
     */
    private static void run(VertexConsumer vc, int i, float width, int[] rgb, float alpha) {
        int x = runs[i], y = runs[i + 1], z = runs[i + 2], axis = runs[i + 3], len = runs[i + 4];
        int sx = axis == 0 ? 1 : 0, sy = axis == 1 ? 1 : 0, sz = axis == 2 ? 1 : 0;
        float bx = (float) (x - camX), by = (float) (y - camY), bz = (float) (z - camZ);
        float ext = width * 0.6F;
        float pax = 0, pay = 0, paz = 0, pcx = 0, pcy = 0, pcz = 0;
        int pal = 0;
        for (int k = 0; k <= len; k++) {
            float off = k == 0 ? -ext : k == len ? len + ext : k;
            float px = bx + sx * off, py = by + sy * off, pz = bz + sz * off;
            // across = run direction x direction to the camera
            float cx = sy * pz - sz * py, cy = sz * px - sx * pz, cz = sx * py - sy * px;
            float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            if (cl < 1.0E-4F) {
                cx = upX;
                cy = upY;
                cz = upZ;
                cl = 1;
            }
            cx = cx / cl * width;
            cy = cy / cl * width;
            cz = cz / cl * width;
            int al = (int) (Mth.clamp(alpha * shimmer(x + sx * k, y + sy * k, z + sz * k), 0, 1) * 255);
            if (k > 0 && (al > 1 || pal > 1)) {
                vertex(vc, pax - pcx, pay - pcy, paz - pcz, EDGE[0], EDGE[1], rgb, pal);
                vertex(vc, pax + pcx, pay + pcy, paz + pcz, EDGE[0], EDGE[3], rgb, pal);
                vertex(vc, px + cx, py + cy, pz + cz, EDGE[2], EDGE[3], rgb, al);
                vertex(vc, px - cx, py - cy, pz - cz, EDGE[2], EDGE[1], rgb, al);
            }
            pax = px;
            pay = py;
            paz = pz;
            pcx = cx;
            pcy = cy;
            pcz = cz;
            pal = al;
        }
    }

    /** A camera-facing soft dot centred on a camera-relative point. */
    private static void sprite(VertexConsumer vc, float x, float y, float z, float half, int[] rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        if (a <= 1) return;
        float ax = rightX * half, ay = rightY * half, az = rightZ * half, bx = upX * half, by = upY * half, bz = upZ * half;
        vertex(vc, x - ax - bx, y - ay - by, z - az - bz, DOT[0], DOT[3], rgb, a);
        vertex(vc, x + ax - bx, y + ay - by, z + az - bz, DOT[2], DOT[3], rgb, a);
        vertex(vc, x + ax + bx, y + ay + by, z + az + bz, DOT[2], DOT[1], rgb, a);
        vertex(vc, x - ax + bx, y - ay + by, z - az + bz, DOT[0], DOT[1], rgb, a);
    }

    private static void vertex(VertexConsumer vc, float x, float y, float z, float u, float v, int[] rgb, int alpha) {
        vc.addVertex(x, y, z).setColor(rgb[0], rgb[1], rgb[2], alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
