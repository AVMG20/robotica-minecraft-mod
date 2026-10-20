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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The network colour of a Quantum Link: the core in its flat top and the window on each side, full bright (a dim grey
 * without a network or while not connected), breathing slowly while connected. When something moves they flash for a
 * moment. The top stays flat, so a Tesla Relay can sit on it.
 */
public class LinkRenderer implements BlockEntityRenderer<QuantumLinkBlockEntity> {
    private static final ResourceLocation CORE = Robotica.id("block/quantum_core");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float PULSE_TICKS = 12.0F;
    /** Pixel to block, and how far the quads sit off the faces under them. */
    private static final float P = 1.0F / 16.0F, OFF = 0.002F;
    /** Top core x/z 5-11; windows x 7-9, y 6-10 on the body faces (inset 2 px). */
    private static final float CORE_LO = 5 * P, CORE_HI = 11 * P, WIN_LO = 7 * P, WIN_HI = 9 * P, WIN_Y0 = 6 * P, WIN_Y1 = 10 * P;
    private static final float BODY_LO = 2 * P - OFF, BODY_HI = 14 * P + OFF, TOP = 1.0F + OFF;

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
        float shade = online ? 0.85F + 0.15F * Mth.sin(time * 0.1F) : 0.45F;
        float bright = Math.min(1.0F, shade + pulse * 0.4F);
        // the flash whitens the colour a little
        int r = flash((rgb >> 16) & 255, bright, pulse), g = flash((rgb >> 8) & 255, bright, pulse), b = flash(rgb & 255, bright, pulse);

        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(CORE);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose last = pose.last();
        int alpha = 255;
        // top core, facing up
        quad(vc, last, sprite, CORE_LO, TOP, CORE_LO, CORE_LO, TOP, CORE_HI, CORE_HI, TOP, CORE_HI, CORE_HI, TOP, CORE_LO,
                0, 1, 0, r, g, b, alpha);
        // windows: north (-z), south (+z), west (-x), east (+x), each wound to face out
        quad(vc, last, sprite, WIN_HI, WIN_Y1, BODY_LO, WIN_HI, WIN_Y0, BODY_LO, WIN_LO, WIN_Y0, BODY_LO, WIN_LO, WIN_Y1, BODY_LO,
                0, 0, -1, r, g, b, alpha);
        quad(vc, last, sprite, WIN_LO, WIN_Y1, BODY_HI, WIN_LO, WIN_Y0, BODY_HI, WIN_HI, WIN_Y0, BODY_HI, WIN_HI, WIN_Y1, BODY_HI,
                0, 0, 1, r, g, b, alpha);
        quad(vc, last, sprite, BODY_LO, WIN_Y1, WIN_LO, BODY_LO, WIN_Y0, WIN_LO, BODY_LO, WIN_Y0, WIN_HI, BODY_LO, WIN_Y1, WIN_HI,
                -1, 0, 0, r, g, b, alpha);
        quad(vc, last, sprite, BODY_HI, WIN_Y1, WIN_HI, BODY_HI, WIN_Y0, WIN_HI, BODY_HI, WIN_Y0, WIN_LO, BODY_HI, WIN_Y1, WIN_LO,
                1, 0, 0, r, g, b, alpha);
    }

    private static int flash(int channel, float bright, float pulse) {
        return Math.min(255, Math.round(channel * bright + (255 - channel) * pulse * 0.35F));
    }

    /** One quad with the whole sprite, corners counter clockwise seen from outside (first corner gets u0 v0). */
    private static void quad(VertexConsumer vc, PoseStack.Pose last, TextureAtlasSprite sprite, float x0, float y0, float z0, float x1,
                             float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz,
                             int r, int g, int b, int alpha) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        corner(vc, last, x0, y0, z0, u0, v0, r, g, b, alpha, nx, ny, nz);
        corner(vc, last, x1, y1, z1, u0, v1, r, g, b, alpha, nx, ny, nz);
        corner(vc, last, x2, y2, z2, u1, v1, r, g, b, alpha, nx, ny, nz);
        corner(vc, last, x3, y3, z3, u1, v0, r, g, b, alpha, nx, ny, nz);
    }

    private static void corner(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, float u, float v, int r, int g, int b,
                               int alpha, float nx, float ny, float nz) {
        vc.addVertex(last, x, y, z).setColor(r, g, b, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(last, nx, ny, nz);
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
