package com.arno.robotica.link.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.link.block.QuantumLinkBlock;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * The crystal above a Quantum Link: an octahedron in the network colour that bobs and turns (full bright, a dim grey
 * one without a network), with a soft glow shell. When something moves it swells for a moment; a send also throws
 * jagged arcs from the emitter to the crystal and a short beam straight up. Arc paths come from a hash of the game
 * time, nothing is stored per frame.
 */
public class LinkRenderer implements BlockEntityRenderer<QuantumLinkBlockEntity> {
    private static final ResourceLocation CRYSTAL = Robotica.id("block/quantum_crystal");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float PULSE_TICKS = 12.0F;
    private static final float EMITTER_Y = 0.8125F;
    private static final int ARCS = 3, ARC_SEGMENTS = 5;
    /** The 8 faces of a unit octahedron, 3 corners each, wound outwards. */
    private static final float[][] FACES = octahedron();

    public LinkRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(QuantumLinkBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        float time = level.getGameTime() + partialTick;
        int colorIndex = be.clientColor();
        boolean online = be.clientOnline() && colorIndex >= 0;
        int rgb = colorIndex >= 0 ? LinkNetwork.rgb(colorIndex) : 0x8A8F96;
        float shade = online ? 1.0F : 0.45F;
        float age = time - be.clientPulse();
        float pulse = age >= 0.0F && age < PULSE_TICKS ? 1.0F - age / PULSE_TICKS : 0.0F;
        pulse *= pulse;
        float bright = Math.min(1.0F, shade + pulse * 0.5F);
        int r = Math.round(((rgb >> 16) & 255) * bright), g = Math.round(((rgb >> 8) & 255) * bright), b = Math.round((rgb & 255) * bright);

        float bob = online ? Mth.sin(time * 0.08F) * 0.035F : 0.0F;
        float spin = time * (online ? 2.4F : 0.6F);
        float scale = 1.0F + pulse * 0.28F;
        float cy = (float) QuantumLinkBlock.CRYSTAL_Y + bob;

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(CRYSTAL);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        pose.translate(0.5, cy, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.pushPose();
        pose.scale(0.12F * scale, 0.19F * scale, 0.12F * scale);
        drawOctahedron(pose, vc, sprite, r, g, b, 235);
        pose.popPose();
        // glow shell, turning the other way
        pose.mulPose(Axis.YP.rotationDegrees(-spin * 1.7F + 45.0F));
        float glow = 1.35F + pulse * 0.35F + (online ? Mth.sin(time * 0.15F) * 0.05F : 0.0F);
        pose.scale(0.12F * glow, 0.19F * glow, 0.12F * glow);
        drawOctahedron(pose, vc, sprite, r, g, b, online ? Math.round(55 + 80 * pulse) : 25);
        pose.popPose();

        if (pulse > 0.0F && online) {
            VertexConsumer fx = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = pose.last().pose();
            int alpha = Math.round(170 * pulse);
            long roll = level.getGameTime() / 2 + be.getBlockPos().asLong();
            if (be.clientPulseKind() == QuantumLinkBlockEntity.EVENT_SEND) {
                for (int a = 0; a < ARCS; a++) arc(fx, m, roll, a, cy, r, g, b, alpha);
                float top = cy + 0.6F + 2.6F * (1.0F - pulse);
                beam(fx, m, cy + 0.15F, top, 0.05F * pulse + 0.01F, r, g, b, Math.round(110 * pulse));
                beam(fx, m, cy + 0.15F, top, 0.015F, 255, 255, 255, Math.round(150 * pulse));
            } else {
                arc(fx, m, roll, 0, cy, r, g, b, alpha);
            }
        }
    }

    /** One jagged arc from a point on the emitter rim up to the crystal. */
    private static void arc(VertexConsumer vc, Matrix4f m, long roll, int index, float cy, int r, int g, int b, int alpha) {
        float angle = (float) (noise(roll, index, 0, 3) * Math.PI);
        float x0 = 0.5F + Mth.cos(angle) * 0.17F, z0 = 0.5F + Mth.sin(angle) * 0.17F, y0 = EMITTER_Y;
        float x1 = 0.5F, z1 = 0.5F, y1 = cy - 0.12F;
        float px = x0, py = y0, pz = z0;
        for (int s = 1; s <= ARC_SEGMENTS; s++) {
            float t = (float) s / ARC_SEGMENTS;
            float nx = Mth.lerp(t, x0, x1), ny = Mth.lerp(t, y0, y1), nz = Mth.lerp(t, z0, z1);
            if (s < ARC_SEGMENTS) {
                nx += (float) noise(roll, index, s, 0) * 0.06F;
                nz += (float) noise(roll, index, s, 2) * 0.06F;
            }
            ribbon(vc, m, px, py, pz, nx, ny, nz, 0.018F, r, g, b, alpha / 2);
            ribbon(vc, m, px, py, pz, nx, ny, nz, 0.006F, 255, 255, 255, alpha);
            px = nx;
            py = ny;
            pz = nz;
        }
    }

    private static void beam(VertexConsumer vc, Matrix4f m, float y0, float y1, float width, int r, int g, int b, int alpha) {
        ribbon(vc, m, 0.5F, y0, 0.5F, 0.5F, y1, 0.5F, width, r, g, b, alpha);
    }

    /** Two crossed quads (X and Z offsets) from one point to another, both windings, for the additive lightning type. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float w,
                               int r, int g, int b, int alpha) {
        quad(vc, m, ax - w, ay, az, ax + w, ay, az, bx + w, by, bz, bx - w, by, bz, r, g, b, alpha);
        quad(vc, m, ax, ay, az - w, ax, ay, az + w, bx, by, bz + w, bx, by, bz - w, r, g, b, alpha);
    }

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
                float cx = uy * vz - uz * vy, cyy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
                float mx = a[0] + p[0] + q[0], my = a[1] + p[1] + q[1], mz = a[2] + p[2] + q[2];
                if (cx * mx + cyy * my + cz * mz < 0) {
                    float[] t = p;
                    p = q;
                    q = t;
                    cx = -cx;
                    cyy = -cyy;
                    cz = -cz;
                }
                float len = Mth.sqrt(cx * cx + cyy * cyy + cz * cz);
                faces[n++] = new float[]{a[0], a[1], a[2], p[0], p[1], p[2], q[0], q[1], q[2], cx / len, cyy / len, cz / len};
            }
        }
        return faces;
    }

    /** Deterministic noise in [-1, 1]. */
    private static double noise(long roll, int arc, int segment, int axis) {
        long h = roll * 0x9E3779B97F4A7C15L + arc * 0xC2B2AE3D27D4EB4FL + segment * 0x165667B19E3779F9L + axis * 0x27D4EB2F165667C5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return ((h & 0xFFFF) / 32767.5) - 1.0;
    }

    @Override
    public boolean shouldRenderOffScreen(QuantumLinkBlockEntity be) {
        return true;
    }

    /** The crystal floats above the block and the send beam goes a few blocks up. */
    @Override
    public AABB getRenderBoundingBox(QuantumLinkBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 4.5, p.getZ() + 1);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
