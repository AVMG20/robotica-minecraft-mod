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
 * The network colour of a Quantum Link: a faceted crystal with a white-hot heart floating in its chamber, turning slowly
 * inside a glow shell, two tilted rings orbiting it with sparks running along them, motes spiralling up from the floor
 * pad, and the socket in the flat top, all full bright (a dim grey without a network or while not connected). When
 * something moves the crystal flares, the rings brighten and a beam joins the floor and ceiling pads for a moment.
 * Further than {@link #DETAIL} blocks away the crystal holds still and only the crystal and socket are drawn. Nothing is
 * stored or allocated per frame.
 */
public class LinkRenderer implements BlockEntityRenderer<QuantumLinkBlockEntity> {
    private static final ResourceLocation CORE = Robotica.id("block/quantum_core");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float PULSE_TICKS = 12.0F;
    private static final float P = 1.0F / 16.0F;
    private static final float TAU = Mth.TWO_PI;
    /** The clock wraps here; every motion below repeats a whole number of times in it, so the wrap never shows. */
    private static final long LOOP = 72000L;
    /** Top socket x/z 5-11, a hair over the cap. */
    private static final float SOCKET_LO = 5 * P, SOCKET_HI = 11 * P, TOP = 1.002F;
    /** Chamber floor and ceiling (pads), and the crystal's middle. */
    private static final float FLOOR = 3 * P, CEILING = 13 * P, MIDDLE = 0.5F;
    private static final float CORE_W = 0.14F, CORE_H = 0.22F, RING_R = 0.29F, RING_W = 0.014F;
    /** Degrees per tick: the crystal (20 s a turn), its glow shell, the two rings and the sparks on them. */
    private static final float SPIN = 0.9F, RING_A = 0.6F, RING_B = -0.45F, SPARK_A = 2.0F, SPARK_B = -2.5F;
    /** Ticks for a mote to rise from the floor pad to the ceiling. */
    private static final float RISE_TICKS = 80.0F;
    private static final int RING_SEGMENTS = 24, MOTES = 4;
    /** Blocks from the camera within which the Link animates and draws its rings, sparks and motes. */
    private static final double DETAIL = 24.0;
    /** Crystal and heart faces: four corners (a triangle repeats its last), the normal and a facet shade. */
    private static final float[][] CRYSTAL = crystal(), HEART = octahedron();
    /** Cosine and sine around a ring, so a frame needs no trigonometry per segment. */
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1], RING_SIN = new float[RING_SEGMENTS + 1];

    static {
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double a = Math.PI * 2 * i / RING_SEGMENTS;
            RING_COS[i] = (float) Math.cos(a);
            RING_SIN[i] = (float) Math.sin(a);
        }
    }

    /** Half height the crystal's glow may reach above its middle and stay under the ceiling while it bobs. */
    private static final float REACH = CEILING - MIDDLE - 0.03F;
    /** Reused for every turn (render thread only). */
    private final Quaternionf turn = new Quaternionf();

    public LinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(QuantumLinkBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float time = (level.getGameTime() % LOOP) + partialTick;
        int colorIndex = be.clientColor();
        BlockPos at = be.getBlockPos();
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean near = eye.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) < DETAIL * DETAIL;
        boolean online = be.clientOnline() && colorIndex >= 0;
        // far away: one still frame, no effects
        boolean live = online && near;
        int rgb = colorIndex >= 0 ? LinkNetwork.rgb(colorIndex) : 0x8A8F96;
        float age = (level.getGameTime() - be.clientPulse()) + partialTick;
        float pulse = age >= 0.0F && age < PULSE_TICKS ? 1.0F - age / PULSE_TICKS : 0.0F;
        pulse *= pulse;
        if (!live) pulse = 0.0F;
        float shade = !online ? 0.45F : near ? 0.85F + 0.15F * Mth.sin(time * TAU / 60.0F) : 1.0F;
        float bright = Math.min(1.0F, shade + pulse * 0.4F);
        int r = flash((rgb >> 16) & 255, bright, pulse), g = flash((rgb >> 8) & 255, bright, pulse), b = flash(rgb & 255, bright, pulse);

        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(CORE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));

        // top socket, facing up
        PoseStack.Pose last = pose.last();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        corner(vc, last, SOCKET_LO, TOP, SOCKET_LO, u0, v0, r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_LO, TOP, SOCKET_HI, u0, v1, r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_HI, TOP, SOCKET_HI, u1, v1, r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_HI, TOP, SOCKET_LO, u1, v0, r, g, b, 255, 0, 1, 0);

        // the crystal: bobs and turns slowly while connected, swells on a pulse; its heart turns the other way
        float bob = live ? Mth.sin(time * TAU / 120.0F) * 0.025F : 0.0F;
        float spin = !near ? 0.0F : time * (online ? SPIN : SPIN / 6.0F);
        float scale = 1.0F + pulse * 0.25F;
        pose.pushPose();
        pose.translate(0.5F, MIDDLE + bob, 0.5F);
        pose.pushPose();
        pose.mulPose(turn.rotationY(-spin * 2.0F * Mth.DEG_TO_RAD));
        pose.scale(0.055F * scale, 0.075F * scale, 0.055F * scale);
        draw(pose, vc, sprite, HEART, whiten(r), whiten(g), whiten(b), online ? 255 : 120);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(turn.rotationY(spin * Mth.DEG_TO_RAD));
        pose.scale(CORE_W * scale, CORE_H * scale, CORE_W * scale);
        draw(pose, vc, sprite, CRYSTAL, r, g, b, 170);
        pose.popPose();
        float glow = 1.35F + pulse * 0.35F + (live ? Mth.sin(time * TAU / 40.0F) * 0.05F : 0.0F);
        pose.pushPose();
        pose.mulPose(turn.rotationY((30.0F - spin * 0.5F) * Mth.DEG_TO_RAD));
        pose.scale(CORE_W * glow, Math.min(CORE_H * glow, REACH), CORE_W * glow);
        draw(pose, vc, sprite, CRYSTAL, r, g, b, online ? Math.round(40 + 90 * pulse) : 18);
        pose.popPose();
        pose.popPose();

        if (!live) return;
        VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
        int sr = whiten(r), sg = whiten(g), sb = whiten(b);
        // two rings orbiting the crystal against each other, their tilt rocking slowly, a pair of sparks running on each
        int ringAlpha = Math.round(80 + 120 * pulse);
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(0.5F, MIDDLE + bob, 0.5F);
            pose.mulPose(turn.rotationY((i == 0 ? time * RING_A : time * RING_B + 90.0F) * Mth.DEG_TO_RAD));
            float tilt = (i == 0 ? 62.0F : -62.0F) + 6.0F * Mth.sin(time * TAU / 360.0F + i * Mth.PI);
            pose.mulPose(turn.rotationX(tilt * Mth.DEG_TO_RAD));
            Matrix4f m = pose.last().pose();
            float radius = RING_R * (1.0F + pulse * 0.06F);
            ring(fx, m, radius, r, g, b, ringAlpha);
            for (int s = 0; s < 2; s++) {
                float a = (time * (i == 0 ? SPARK_A : SPARK_B) + s * 180.0F) * Mth.DEG_TO_RAD;
                mote(fx, m, Mth.cos(a) * radius, 0.0F, Mth.sin(a) * radius, 0.016F + 0.01F * pulse, sr, sg, sb, Math.round(170 + 80 * pulse));
            }
            pose.popPose();
        }
        Matrix4f m = pose.last().pose();
        // motes spiralling up from the floor pad, fading in and out
        for (int k = 0; k < MOTES; k++) {
            float phase = (time / RISE_TICKS + (float) k / MOTES) % 1.0F;
            float a = (k * 360.0F / MOTES + phase * 240.0F) * Mth.DEG_TO_RAD;
            float rad = 0.21F - 0.06F * phase;
            float y = FLOOR + 0.03F + phase * (CEILING - FLOOR - 0.06F);
            mote(fx, m, 0.5F + Mth.cos(a) * rad, y, 0.5F + Mth.sin(a) * rad, 0.011F, sr, sg, sb, Math.round(Mth.sin(phase * Mth.PI) * 150));
        }
        // a beam from pad to pad while something moves
        if (pulse > 0.0F) {
            float w = 0.012F + 0.03F * pulse;
            ribbon(fx, m, 0.5F, FLOOR, 0.5F, 0.5F, CEILING, 0.5F, w, r, g, b, Math.round(120 * pulse));
            ribbon(fx, m, 0.5F, FLOOR, 0.5F, 0.5F, CEILING, 0.5F, w * 0.35F, 255, 255, 255, Math.round(160 * pulse));
        }
    }

    private static int flash(int channel, float bright, float pulse) {
        return Math.min(255, Math.round(channel * bright + (255 - channel) * pulse * 0.35F));
    }

    /** Halfway to white, for the heart and the sparks. */
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
     * A unit hexagonal crystal: a six sided band (y -0.3 to 0.3) between two points (y -1 and 1). Facets alternate light and
     * dark around it, the top brightest and the bottom darkest.
     */
    private static float[][] crystal() {
        float band = 0.3F;
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
