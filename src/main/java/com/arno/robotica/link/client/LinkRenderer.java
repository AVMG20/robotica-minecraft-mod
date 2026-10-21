package com.arno.robotica.link.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.network.LinkNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The network colour of a Quantum Link: a faceted crystal with glowing edges and a white-hot heart floating in its
 * chamber, turning slowly inside a glow shell, two tilted rings orbiting it with sparks running along them, and the
 * socket in the flat top, all full bright (a dim grey without a network or while not connected). Further than
 * {@link #DETAIL} blocks away the crystal holds still and only the crystal and socket are drawn. Nothing is stored or
 * allocated per frame.
 */
public class LinkRenderer implements BlockEntityRenderer<QuantumLinkBlockEntity> {
    private static final ResourceLocation CORE = Robotica.id("block/quantum_core");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float P = 1.0F / 16.0F;
    /** The clock wraps here; every motion below repeats a whole number of times in it, so the wrap never shows. */
    private static final long LOOP = 72000L;
    /** Top socket x/z 5-11, a hair over the cap. */
    private static final float SOCKET_LO = 5 * P, SOCKET_HI = 11 * P, TOP = 1.002F;
    /** The crystal's middle, half width and half height (its glow shell stays clear of the floor and ceiling pads). */
    private static final float MIDDLE = 0.5F, CORE_W = 0.13F, CORE_H = 0.24F, GLOW = 1.25F;
    private static final float RING_R = 0.29F, RING_W = 0.014F, EDGE_W = 0.004F;
    /** Degrees per tick: the crystal (30 s a turn), the two rings and the sparks on them. */
    private static final float SPIN = 0.6F, RING_A = 0.4F, RING_B = -0.3F, SPARK_A = 1.2F, SPARK_B = -1.5F;
    private static final int RING_SEGMENTS = 24;
    /** Blocks from the camera within which the Link animates and draws its rings and sparks. */
    private static final double DETAIL = 24.0;
    /** Half the crystal's band height (unit crystal, points at -1 and 1). */
    private static final float BAND = 0.4F;
    /** Crystal and heart faces: four corners (a triangle repeats its last), the normal and a facet shade. */
    private static final float[][] CRYSTAL = crystal(), HEART = octahedron();
    /** The unit crystal's 30 edges, each two corners. */
    private static final float[][] EDGES = edges();
    /** Cosine and sine around a ring, so a frame needs no trigonometry per segment. */
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1], RING_SIN = new float[RING_SEGMENTS + 1];

    static {
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double a = Math.PI * 2 * i / RING_SEGMENTS;
            RING_COS[i] = (float) Math.cos(a);
            RING_SIN[i] = (float) Math.sin(a);
        }
    }

    /** Reused for every turn (render thread only). */
    private final Quaternionf turn = new Quaternionf();

    public LinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(QuantumLinkBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        int colorIndex = be.clientColor();
        BlockPos at = be.getBlockPos();
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean near = eye.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) < DETAIL * DETAIL;
        boolean online = be.clientOnline() && colorIndex >= 0;
        // far away the clock stands still and the rings and sparks are left out
        float time = near ? (level.getGameTime() % LOOP) + partialTick : 0.0F;
        int rgb = colorIndex >= 0 ? LinkNetwork.rgb(colorIndex) : 0x8A8F96;
        float bright = online ? 1.0F : 0.45F;
        int r = Math.round(((rgb >> 16) & 255) * bright), g = Math.round(((rgb >> 8) & 255) * bright), b = Math.round((rgb & 255) * bright);
        int wr = whiten(r), wg = whiten(g), wb = whiten(b);

        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(CORE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));

        // top socket, facing up
        PoseStack.Pose last = pose.last();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        corner(vc, last, SOCKET_LO, TOP, SOCKET_LO, u0, v0, r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_LO, TOP, SOCKET_HI, u0, v1, r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_HI, TOP, SOCKET_HI, u1, v1, r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_HI, TOP, SOCKET_LO, u1, v0, r, g, b, 255, 0, 1, 0);

        // the crystal turns slowly (much slower while not connected) around its bright heart, inside a still glow shell
        float spin = time * (online ? SPIN : SPIN / 6.0F) * Mth.DEG_TO_RAD;
        pose.pushPose();
        pose.translate(0.5F, MIDDLE, 0.5F);
        pose.pushPose();
        pose.mulPose(turn.rotationY(-spin));
        pose.scale(0.05F, 0.07F, 0.05F);
        draw(pose, vc, sprite, HEART, wr, wg, wb, online ? 255 : 120);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(turn.rotationY(spin));
        pose.scale(CORE_W, CORE_H, CORE_W);
        draw(pose, vc, sprite, CRYSTAL, r, g, b, 165);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(turn.rotationY(spin + 30.0F * Mth.DEG_TO_RAD));
        pose.scale(CORE_W * GLOW, CORE_H * GLOW, CORE_W * GLOW);
        draw(pose, vc, sprite, CRYSTAL, r, g, b, online ? 40 : 18);
        pose.popPose();
        pose.popPose();

        if (!online || !near) return;
        VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
        // glowing crystal edges, turning with it
        Matrix4f m = pose.last().pose();
        float cos = Mth.cos(spin), sin = Mth.sin(spin);
        for (float[] e : EDGES) {
            float ax = (e[0] * cos + e[2] * sin) * CORE_W, az = (e[2] * cos - e[0] * sin) * CORE_W;
            float bx = (e[3] * cos + e[5] * sin) * CORE_W, bz = (e[5] * cos - e[3] * sin) * CORE_W;
            ribbon(fx, m, 0.5F + ax, MIDDLE + e[1] * CORE_H, 0.5F + az, 0.5F + bx, MIDDLE + e[4] * CORE_H, 0.5F + bz, EDGE_W, wr, wg, wb, 150);
        }
        // two tilted rings orbiting the crystal against each other, a pair of sparks running on each
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(0.5F, MIDDLE, 0.5F);
            pose.mulPose(turn.rotationY((i == 0 ? time * RING_A : time * RING_B + 90.0F) * Mth.DEG_TO_RAD));
            pose.mulPose(turn.rotationX((i == 0 ? 62.0F : -62.0F) * Mth.DEG_TO_RAD));
            Matrix4f rm = pose.last().pose();
            ring(fx, rm, RING_R, r, g, b, 90);
            for (int s = 0; s < 2; s++) {
                float a = (time * (i == 0 ? SPARK_A : SPARK_B) + s * 180.0F) * Mth.DEG_TO_RAD;
                mote(fx, rm, Mth.cos(a) * RING_R, 0.0F, Mth.sin(a) * RING_R, 0.016F, wr, wg, wb, 190);
            }
            pose.popPose();
        }
    }

    /** Halfway to white, for the heart, the edges and the sparks. */
    private static int whiten(int channel) {
        return (channel + 255) >> 1;
    }

    /** A flat band of radius {@code radius} in the local x/z plane, both windings, for the additive lightning type. */
    private static void ring(VertexConsumer vc, Matrix4f m, float radius, int r, int g, int b, int alpha) {
        float in = radius - RING_W, out = radius + RING_W;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float c0 = RING_COS[i], s0 = RING_SIN[i], c1 = RING_COS[i + 1], s1 = RING_SIN[i + 1];
            quad(vc, m, c0 * in, 0, s0 * in, c0 * out, 0, s0 * out, c1 * out, 0, s1 * out, c1 * in, 0, s1 * in, r, g, b, alpha);
        }
    }

    /** A spark: three crossed squares of half size {@code s} around a point, so it shows from every side. */
    private static void mote(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, int r, int g, int b, int alpha) {
        if (alpha <= 0) return;
        quad(vc, m, x - s, y - s, z, x + s, y - s, z, x + s, y + s, z, x - s, y + s, z, r, g, b, alpha);
        quad(vc, m, x, y - s, z - s, x, y - s, z + s, x, y + s, z + s, x, y + s, z - s, r, g, b, alpha);
        quad(vc, m, x - s, y, z - s, x + s, y, z - s, x + s, y, z + s, x - s, y, z + s, r, g, b, alpha);
    }

    /** Two crossed quads (X and Z offsets) from one point to another, for the additive lightning type. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float w,
                               int r, int g, int b, int alpha) {
        quad(vc, m, ax - w, ay, az, ax + w, ay, az, bx + w, by, bz, bx - w, by, bz, r, g, b, alpha);
        quad(vc, m, ax, ay, az - w, ax, ay, az + w, bx, by, bz + w, bx, by, bz - w, r, g, b, alpha);
    }

    /** One quad drawn with both windings. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
                             float z2, float x3, float y3, float z3, int r, int g, int b, int alpha) {
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, alpha);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, alpha);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, alpha);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, alpha);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, alpha);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, alpha);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, alpha);
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, alpha);
    }

    /** Faces as quads, each face shaded so the facets catch the eye as the crystal turns; the sprite is mapped once per face. */
    private static void draw(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, float[][] faces, int r, int g, int b, int alpha) {
        PoseStack.Pose last = pose.last();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        for (float[] f : faces) {
            float nx = f[12], ny = f[13], nz = f[14], k = f[15];
            int fr = Math.round(r * k), fg = Math.round(g * k), fb = Math.round(b * k);
            corner(vc, last, f[0], f[1], f[2], u0, v0, fr, fg, fb, alpha, nx, ny, nz);
            corner(vc, last, f[3], f[4], f[5], u0, v1, fr, fg, fb, alpha, nx, ny, nz);
            corner(vc, last, f[6], f[7], f[8], u1, v1, fr, fg, fb, alpha, nx, ny, nz);
            corner(vc, last, f[9], f[10], f[11], u1, v0, fr, fg, fb, alpha, nx, ny, nz);
        }
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, int r, int g, int b,
                               int alpha, float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z).setColor(r, g, b, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(last, nx, ny, nz);
    }

    /**
     * A unit hexagonal crystal: a six sided band (y -BAND to BAND) between two points (y -1 and 1). Facets alternate light
     * and dark around it, the top brightest and the bottom darkest.
     */
    private static float[][] crystal() {
        float band = BAND;
        float[][] faces = new float[18][];
        for (int i = 0; i < 6; i++) {
            float a0 = Mth.TWO_PI * i / 6, a1 = Mth.TWO_PI * (i + 1) / 6;
            float x0 = Mth.cos(a0), z0 = Mth.sin(a0), x1 = Mth.cos(a1), z1 = Mth.sin(a1);
            boolean even = i % 2 == 0;
            faces[i] = face(even ? 1.0F : 0.86F, 0, 1, 0, x0, band, z0, x1, band, z1, x1, band, z1);
            faces[6 + i] = face(even ? 0.9F : 0.72F, x0, band, z0, x0, -band, z0, x1, -band, z1, x1, band, z1);
            faces[12 + i] = face(even ? 0.68F : 0.56F, 0, -1, 0, x1, -band, z1, x0, -band, z0, x0, -band, z0);
        }
        return faces;
    }

    private static float[][] edges() {
        float[][] edges = new float[30][];
        for (int i = 0; i < 6; i++) {
            float a0 = Mth.TWO_PI * i / 6, a1 = Mth.TWO_PI * (i + 1) / 6;
            float x0 = Mth.cos(a0), z0 = Mth.sin(a0), x1 = Mth.cos(a1), z1 = Mth.sin(a1);
            edges[i * 5] = new float[]{0, 1, 0, x0, BAND, z0};
            edges[i * 5 + 1] = new float[]{x0, BAND, z0, x1, BAND, z1};
            edges[i * 5 + 2] = new float[]{x0, BAND, z0, x0, -BAND, z0};
            edges[i * 5 + 3] = new float[]{x0, -BAND, z0, x1, -BAND, z1};
            edges[i * 5 + 4] = new float[]{x0, -BAND, z0, 0, -1, 0};
        }
        return edges;
    }

    /** A unit octahedron, one shade. */
    private static float[][] octahedron() {
        float[][] ring = {{1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}};
        float[][] faces = new float[8][];
        int n = 0;
        for (int apex = 1; apex >= -1; apex -= 2) {
            for (int i = 0; i < 4; i++) {
                float[] p = ring[i], q = ring[(i + 1) % 4];
                faces[n++] = face(1.0F, 0, apex, 0, p[0], p[1], p[2], q[0], q[1], q[2], q[0], q[1], q[2]);
            }
        }
        return faces;
    }

    /** One face {four corners, normal, shade}, its corners reordered to wind counter clockwise seen from outside. */
    private static float[] face(float shade, float... c) {
        float ux = c[3] - c[0], uy = c[4] - c[1], uz = c[5] - c[2];
        float vx = c[6] - c[0], vy = c[7] - c[1], vz = c[8] - c[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float mx = c[0] + c[3] + c[6], my = c[1] + c[4] + c[7], mz = c[2] + c[5] + c[8];
        if (nx * mx + ny * my + nz * mz < 0) {
            // reverse the corner order, keeping a triangle's repeated corner last
            boolean triangle = c[6] == c[9] && c[7] == c[10] && c[8] == c[11];
            float[] r = triangle
                    ? new float[]{c[0], c[1], c[2], c[6], c[7], c[8], c[3], c[4], c[5], c[3], c[4], c[5]}
                    : new float[]{c[9], c[10], c[11], c[6], c[7], c[8], c[3], c[4], c[5], c[0], c[1], c[2]};
            c = r;
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        float len = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        float[] f = new float[16];
        System.arraycopy(c, 0, f, 0, 12);
        f[12] = nx / len;
        f[13] = ny / len;
        f[14] = nz / len;
        f[15] = shade;
        return f;
    }

    @Override
    public AABB getRenderBoundingBox(QuantumLinkBlockEntity be) {
        return be.renderBox();
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
