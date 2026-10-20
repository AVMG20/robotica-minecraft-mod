package com.arno.robotica.link.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.link.block.QuantumLinkBlockEntity;
import com.arno.robotica.link.network.LinkNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The network colour of a Quantum Link: the quantum core floating in its chamber (an octahedron with a glow shell and
 * two tilted rings orbiting it) and the socket in its flat top, full bright (a dim grey without a network or while not
 * connected). When something moves the core flares, the rings brighten and a beam joins the floor and ceiling pads for
 * a moment. Nothing is stored or allocated per frame.
 */
public class LinkRenderer implements BlockEntityRenderer<QuantumLinkBlockEntity> {
    private static final ResourceLocation CORE = Robotica.id("block/quantum_core");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float PULSE_TICKS = 12.0F;
    private static final float P = 1.0F / 16.0F;
    /** Top socket x/z 5-11, a hair over the cap. */
    private static final float SOCKET_LO = 5 * P, SOCKET_HI = 11 * P, TOP = 1.002F;
    /** Chamber floor and ceiling (pads), and the core's middle. */
    private static final float FLOOR = 3 * P, CEILING = 13 * P, MIDDLE = 0.5F;
    private static final float CORE_W = 0.13F, CORE_H = 0.19F, RING_R = 0.29F, RING_W = 0.018F;
    private static final int RING_SEGMENTS = 16;
    /** The 8 faces of a unit octahedron: apex, two corners, normal. */
    private static final float[][] FACES = octahedron();
    /** Cosine and sine around a ring, so a frame needs no trigonometry per segment. */
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1], RING_SIN = new float[RING_SEGMENTS + 1];

    static {
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double a = Math.PI * 2 * i / RING_SEGMENTS;
            RING_COS[i] = (float) Math.cos(a);
            RING_SIN[i] = (float) Math.sin(a);
        }
    }

    public LinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(QuantumLinkBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float time = level.getGameTime() + partialTick;
        int colorIndex = be.clientColor();
        boolean online = be.clientOnline() && colorIndex >= 0;
        int rgb = colorIndex >= 0 ? LinkNetwork.rgb(colorIndex) : 0x8A8F96;
        float age = time - be.clientPulse();
        float pulse = age >= 0.0F && age < PULSE_TICKS ? 1.0F - age / PULSE_TICKS : 0.0F;
        pulse *= pulse;
        if (!online) pulse = 0.0F;
        float shade = online ? 0.85F + 0.15F * Mth.sin(time * 0.1F) : 0.45F;
        float bright = Math.min(1.0F, shade + pulse * 0.4F);
        int r = flash((rgb >> 16) & 255, bright, pulse), g = flash((rgb >> 8) & 255, bright, pulse), b = flash(rgb & 255, bright, pulse);

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(CORE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));

        // top socket, facing up
        PoseStack.Pose last = pose.last();
        corner(vc, last, SOCKET_LO, TOP, SOCKET_LO, sprite.getU0(), sprite.getV0(), r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_LO, TOP, SOCKET_HI, sprite.getU0(), sprite.getV1(), r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_HI, TOP, SOCKET_HI, sprite.getU1(), sprite.getV1(), r, g, b, 255, 0, 1, 0);
        corner(vc, last, SOCKET_HI, TOP, SOCKET_LO, sprite.getU1(), sprite.getV0(), r, g, b, 255, 0, 1, 0);

        // the core: bobs and turns while connected, swells on a pulse
        float bob = online ? Mth.sin(time * 0.08F) * 0.02F : 0.0F;
        float spin = time * (online ? 2.4F : 0.4F);
        float scale = 1.0F + pulse * 0.3F;
        pose.pushPose();
        pose.translate(0.5F, MIDDLE + bob, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.pushPose();
        pose.scale(CORE_W * scale, CORE_H * scale, CORE_W * scale);
        drawOctahedron(pose, vc, sprite, r, g, b, 240);
        pose.popPose();
        float glow = 1.4F + pulse * 0.4F + (online ? Mth.sin(time * 0.15F) * 0.06F : 0.0F);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-spin * 1.6F + 45.0F));
        pose.scale(CORE_W * glow, CORE_H * glow, CORE_W * glow);
        drawOctahedron(pose, vc, sprite, r, g, b, online ? Math.round(50 + 90 * pulse) : 20);
        pose.popPose();
        pose.popPose();

        if (!online) return;
        // two tilted rings orbiting the core, turning against each other
        VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
        int ringAlpha = Math.round(90 + 120 * pulse);
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(0.5F, MIDDLE + bob, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(i == 0 ? time * 1.5F : -time * 1.1F + 90.0F));
            pose.mulPose(Axis.XP.rotationDegrees(i == 0 ? 62.0F : -62.0F));
            ring(fx, pose.last().pose(), RING_R * (1.0F + pulse * 0.12F), r, g, b, ringAlpha);
            pose.popPose();
        }
        // a beam from pad to pad while something moves
        if (pulse > 0.0F) {
            Matrix4f m = pose.last().pose();
            float w = 0.012F + 0.03F * pulse;
            ribbon(fx, m, 0.5F, FLOOR, 0.5F, 0.5F, CEILING, 0.5F, w, r, g, b, Math.round(120 * pulse));
            ribbon(fx, m, 0.5F, FLOOR, 0.5F, 0.5F, CEILING, 0.5F, w * 0.35F, 255, 255, 255, Math.round(160 * pulse));
        }
    }

    private static int flash(int channel, float bright, float pulse) {
        return Math.min(255, Math.round(channel * bright + (255 - channel) * pulse * 0.35F));
    }

    /** A flat band of radius {@code radius} in the local x/z plane, both windings, for the additive lightning type. */
    private static void ring(VertexConsumer vc, Matrix4f m, float radius, int r, int g, int b, int alpha) {
        float in = radius - RING_W, out = radius + RING_W;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float c0 = RING_COS[i], s0 = RING_SIN[i], c1 = RING_COS[i + 1], s1 = RING_SIN[i + 1];
            quad(vc, m, c0 * in, 0, s0 * in, c0 * out, 0, s0 * out, c1 * out, 0, s1 * out, c1 * in, 0, s1 * in, r, g, b, alpha);
        }
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

    /** The octahedron as 8 quads with a repeated last corner; the sprite is mapped once per face. */
    private static void drawOctahedron(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, int r, int g, int b, int alpha) {
        PoseStack.Pose last = pose.last();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1(), um = (u0 + u1) * 0.5F;
        for (float[] f : FACES) {
            float nx = f[9], ny = f[10], nz = f[11];
            corner(vc, last, f[0], f[1], f[2], um, v0, r, g, b, alpha, nx, ny, nz);
            corner(vc, last, f[3], f[4], f[5], u0, v1, r, g, b, alpha, nx, ny, nz);
            corner(vc, last, f[6], f[7], f[8], u1, v1, r, g, b, alpha, nx, ny, nz);
            corner(vc, last, f[6], f[7], f[8], u1, v1, r, g, b, alpha, nx, ny, nz);
        }
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, int r, int g, int b,
                               int alpha, float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z).setColor(r, g, b, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(last, nx, ny, nz);
    }

    /** Faces as {apex xyz, corner xyz, corner xyz, normal xyz}, each wound counter clockwise seen from outside. */
    private static float[][] octahedron() {
        float[][] ring = {{1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}};
        float[][] faces = new float[8][];
        int n = 0;
        for (int apex = 1; apex >= -1; apex -= 2) {
            for (int i = 0; i < 4; i++) {
                float[] a = {0, apex, 0}, p = ring[i], q = ring[(i + 1) % 4];
                // normal = (p - a) x (q - a); flip the corner order when it points inwards
                float ux = p[0] - a[0], uy = p[1] - a[1], uz = p[2] - a[2];
                float vx = q[0] - a[0], vy = q[1] - a[1], vz = q[2] - a[2];
                float cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
                float mx = a[0] + p[0] + q[0], my = a[1] + p[1] + q[1], mz = a[2] + p[2] + q[2];
                if (cx * mx + cy * my + cz * mz < 0) {
                    float[] t = p;
                    p = q;
                    q = t;
                    cx = -cx;
                    cy = -cy;
                    cz = -cz;
                }
                float len = Mth.sqrt(cx * cx + cy * cy + cz * cz);
                faces[n++] = new float[]{a[0], a[1], a[2], p[0], p[1], p[2], q[0], q[1], q[2], cx / len, cy / len, cz / len};
            }
        }
        return faces;
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
