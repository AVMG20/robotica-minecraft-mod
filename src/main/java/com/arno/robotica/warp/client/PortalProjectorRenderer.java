package com.arno.robotica.warp.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.warp.gate.PortalGeometry;
import com.arno.robotica.warp.gate.PortalProjectorBlock;
import com.arno.robotica.warp.gate.PortalProjectorBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the projected portal of an active Portal Projector: an animated, slowly turning vortex on a filled ellipse (two
 * counter rotating layers, full bright), a bright rim ring with a soft glow, and a thin teal light cone from the lens up
 * to the portal. Nothing is drawn while the projector is idle. Sizes come from {@link PortalGeometry}, the same numbers the
 * server uses for teleport detection.
 */
public class PortalProjectorRenderer implements BlockEntityRenderer<PortalProjectorBlockEntity> {
    private static final ResourceLocation VORTEX = Robotica.id("block/portal_vortex");
    private static final ResourceLocation RIM = Robotica.id("block/portal_rim");
    private static final ResourceLocation BEAM = ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int SEGMENTS = 48;
    private static final int CONE_SIDES = 16;
    private static final float OPEN_TICKS = 14.0F;
    /** Radians per tick of the vortex layers (one turn per ~17 s). */
    private static final float SPIN = 0.018F;
    /** Disc rings: radius fraction and alpha (0-255) of the vertices on that ring. */
    private static final float[] RING_R = {0.0F, 0.3F, 0.65F, 1.0F};
    private static final int[] RING_A = {150, 190, 225, 240};

    public PortalProjectorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PortalProjectorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        if (level == null || !state.hasProperty(PortalProjectorBlock.ACTIVE) || !state.getValue(PortalProjectorBlock.ACTIVE)) {
            be.clientOpenedAt = Long.MIN_VALUE;
            return;
        }
        float time = level.getGameTime() + partialTick;
        if (be.clientOpenedAt == Long.MIN_VALUE) be.clientOpenedAt = level.getGameTime();
        float open = Mth.clamp((time - be.clientOpenedAt) / OPEN_TICKS, 0.0F, 1.0F);
        open = 1.0F - (1.0F - open) * (1.0F - open); // ease out
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.12F);

        Direction facing = state.getValue(PortalProjectorBlock.FACING);
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite vortex = atlas.apply(VORTEX);
        TextureAtlasSprite rim = atlas.apply(RIM);

        // ---- light cone from the lens up to the portal ----
        pose.pushPose();
        double lensY = PortalGeometry.TOP;
        float coneTop = (float) (PortalGeometry.TOP + PortalGeometry.GAP + 0.35);
        pose.translate(0.5, 0.0, 0.5);
        drawCone(pose, buffers.getBuffer(RenderType.beaconBeam(BEAM, true)), (float) lensY, coneTop, 0.07F, 0.5F * (0.4F + 0.6F * open), time, 0.8F + 0.2F * pulse);
        pose.popPose();

        // ---- the portal ----
        pose.pushPose();
        pose.translate(0.5, PortalGeometry.TOP + PortalGeometry.CENTER_ABOVE_TOP, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float rx = (float) (PortalGeometry.WIDTH / 2.0) * open;
        float ry = (float) (PortalGeometry.HEIGHT / 2.0) * open;
        VertexConsumer fx = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        float shade = 0.82F + 0.18F * pulse;
        drawDisc(pose, fx, vortex, rx, ry, 0.0F, time * SPIN, shade, 1.0F);
        drawDisc(pose, fx, vortex, rx * 0.98F, ry * 0.98F, 0.01F, -time * SPIN * 0.6F + 1.7F, shade, 0.5F);
        drawDisc(pose, fx, vortex, rx * 0.98F, ry * 0.98F, -0.01F, -time * SPIN * 0.6F + 4.1F, shade, 0.5F);
        // rim: bright band with a soft glow outside it
        drawRing(pose, fx, rim, rx, ry, 0.92F, 1.0F, 0.0F, 215, 255, 0.55F + 0.45F * pulse, 1.0F);
        drawRing(pose, fx, rim, rx, ry, 1.0F, 1.14F, 0.0F, 150, 0, 0.55F + 0.45F * pulse, 0.7F);
        drawRing(pose, fx, rim, rx, ry, 0.80F, 0.92F, 0.0F, 0, 215, 0.55F + 0.45F * pulse, 0.7F);
        pose.popPose();
    }

    /** Filled ellipse in the XY plane, UV mapped on a circle of the sprite that turns by {@code rot}. */
    private static void drawDisc(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, float rx, float ry, float z, float rot, float shade,
                                 float alphaScale) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            for (int r = 0; r < RING_R.length - 1; r++) {
                float r0 = RING_R[r], r1 = RING_R[r + 1];
                vertex(pose, vc, sprite, a0, r0, rx, ry, z, rot, shade, RING_A[r] * alphaScale);
                vertex(pose, vc, sprite, a0, r1, rx, ry, z, rot, shade, RING_A[r + 1] * alphaScale);
                vertex(pose, vc, sprite, a1, r1, rx, ry, z, rot, shade, RING_A[r + 1] * alphaScale);
                vertex(pose, vc, sprite, a1, r0, rx, ry, z, rot, shade, RING_A[r] * alphaScale);
            }
        }
    }

    private static void vertex(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, float angle, float radius, float rx, float ry, float z,
                               float rot, float shade, float alpha) {
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        float u = 0.5F + 0.48F * radius * Mth.cos(angle + rot);
        float v = 0.5F + 0.48F * radius * Mth.sin(angle + rot);
        int c = Math.round(255.0F * shade);
        vc.addVertex(pose.last(), cos * radius * rx, sin * radius * ry, z)
                .setColor(Math.round(c * 0.9F), c, c, Mth.clamp(Math.round(alpha), 0, 255))
                .setUv(sprite.getU(u), sprite.getV(v))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose.last(), 0.0F, 0.0F, 1.0F);
    }

    /** Elliptic band between two radius fractions with an alpha on each edge, UV at the centre of the sprite (solid colour). */
    private static void drawRing(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sprite, float rx, float ry, float inner, float outer, float z,
                                 int alphaInner, int alphaOuter, float brightness, float alphaScale) {
        float u = sprite.getU(0.5F), v = sprite.getV(0.5F);
        int g = Math.round(255.0F * brightness);
        int red = Math.round(g * 0.82F);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            ringVertex(pose, vc, u, v, a0, inner, rx, ry, z, red, g, alphaInner * alphaScale);
            ringVertex(pose, vc, u, v, a0, outer, rx, ry, z, red, g, alphaOuter * alphaScale);
            ringVertex(pose, vc, u, v, a1, outer, rx, ry, z, red, g, alphaOuter * alphaScale);
            ringVertex(pose, vc, u, v, a1, inner, rx, ry, z, red, g, alphaInner * alphaScale);
        }
    }

    private static void ringVertex(PoseStack pose, VertexConsumer vc, float u, float v, float angle, float radius, float rx, float ry, float z,
                                   int red, int green, float alpha) {
        vc.addVertex(pose.last(), Mth.cos(angle) * radius * rx, Mth.sin(angle) * radius * ry, z)
                .setColor(red, green, green, Mth.clamp(Math.round(alpha), 0, 255))
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose.last(), 0.0F, 0.0F, 1.0F);
    }

    /** Truncated cone around the vertical axis, translucent teal, brightest at the lens and fading out at the top. Drawn from both sides. */
    private static void drawCone(PoseStack pose, VertexConsumer vc, float y0, float y1, float r0, float r1, float time, float brightness) {
        int red = Math.round(110 * brightness), green = Math.round(235 * brightness), blue = Math.round(215 * brightness);
        float scroll = -time * 0.02F;
        for (int i = 0; i < CONE_SIDES; i++) {
            float a0 = Mth.TWO_PI * i / CONE_SIDES, a1 = Mth.TWO_PI * (i + 1) / CONE_SIDES;
            float u0 = (float) i / CONE_SIDES, u1 = (float) (i + 1) / CONE_SIDES;
            float v0 = scroll, v1 = scroll + 0.9F;
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 0) {
                    coneVertex(pose, vc, a0, r0, y0, u0, v0, red, green, blue, 190);
                    coneVertex(pose, vc, a0, r1, y1, u0, v1, red, green, blue, 10);
                    coneVertex(pose, vc, a1, r1, y1, u1, v1, red, green, blue, 10);
                    coneVertex(pose, vc, a1, r0, y0, u1, v0, red, green, blue, 190);
                } else {
                    coneVertex(pose, vc, a1, r0, y0, u1, v0, red, green, blue, 190);
                    coneVertex(pose, vc, a1, r1, y1, u1, v1, red, green, blue, 10);
                    coneVertex(pose, vc, a0, r1, y1, u0, v1, red, green, blue, 10);
                    coneVertex(pose, vc, a0, r0, y0, u0, v0, red, green, blue, 190);
                }
            }
        }
    }

    private static void coneVertex(PoseStack pose, VertexConsumer vc, float angle, float radius, float y, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(pose.last(), Mth.cos(angle) * radius, y, Mth.sin(angle) * radius)
                .setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose.last(), 0.0F, 1.0F, 0.0F);
    }

    @Override
    public boolean shouldRenderOffScreen(PortalProjectorBlockEntity be) {
        return true;
    }

    /** The portal floats above the block, so the default one-block box would cull it. */
    @Override
    public AABB getRenderBoundingBox(PortalProjectorBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 2, p.getY(), p.getZ() - 2, p.getX() + 3, p.getY() + 5, p.getZ() + 3);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
