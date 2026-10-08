package com.arno.robotica.energy.client;

import com.arno.robotica.energy.block.SpireBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Tesla Spire: while it runs, a pulsing ball of light on the crown, arcs crawling down the column and leaping from the
 * crown into the air. A lightning strike draws a forked bolt from the sky onto the crown for half a second, flashes the
 * sky and lights the whole column. Plus the structure highlight of every controller.
 */
public class SpireRenderer extends ControllerHighlightRenderer<SpireBlockEntity> {
    private static final int STRIKE_TICKS = 12;
    private static final int BOLT_HEIGHT = 72;

    private long flashedFor = Long.MIN_VALUE;

    public SpireRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SpireBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, pose, buffers, light, overlay);
        Level level = be.getLevel();
        int n = be.conductors();
        if (level == null || n <= 0) return;
        float time = level.getGameTime() + partialTick;
        float sinceStrike = time - be.clientStrike();
        boolean striking = sinceStrike >= 0 && sinceStrike < STRIKE_TICKS;
        if (!be.running() && !striking) return;

        if (striking && flashedFor != be.clientStrike() && level instanceof ClientLevel cl) {
            flashedFor = be.clientStrike();
            cl.setSkyFlashTime(2);
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        Vec3 crown = new Vec3(0.5, n + 1.75, 0.5); // the discharge sphere of the crown
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.35F);

        // ball of light on the crown
        GlowDraw.cube(vc, m, crown, 0.18F + 0.05F * pulse, 200, 235, 255, 120);
        GlowDraw.cube(vc, m, crown, 0.32F + 0.08F * pulse, 90, 160, 255, 45);
        GlowDraw.cube(vc, m, crown, 0.5F + 0.1F * pulse, 60, 110, 255, 18);

        // arcs: re-rolled four times a second, a few crawl down the column, a few leap off the crown
        long frame = (long) (time / 5);
        long seed = be.getBlockPos().asLong() * 31 + frame;
        java.util.Random rnd = new java.util.Random(seed);
        int down = 1 + Math.min(3, n / 6);
        for (int i = 0; i < down; i++) {
            double y0 = 1 + rnd.nextDouble() * n, y1 = Math.max(1, y0 - 1.5 - rnd.nextDouble() * 3);
            double side = rnd.nextInt(4) * Math.PI / 2;
            Vec3 a = new Vec3(0.5 + 0.55 * Math.cos(side), y0, 0.5 + 0.55 * Math.sin(side));
            Vec3 b = new Vec3(0.5 + 0.55 * Math.cos(side + 0.6), y1, 0.5 + 0.55 * Math.sin(side + 0.6));
            GlowDraw.bolt(vc, m, a, b, rnd.nextLong(), 4, 0.12, 0.012F, 110, 170, 255, 170, 0);
        }
        int leaps = 2 + (int) (pulse * 2);
        for (int i = 0; i < leaps; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2, reach = 1.2 + rnd.nextDouble() * 1.8;
            Vec3 tip = crown.add(Math.cos(ang) * reach, (rnd.nextDouble() - 0.6) * 1.6, Math.sin(ang) * reach);
            GlowDraw.bolt(vc, m, crown, tip, rnd.nextLong(), 5, 0.18, 0.014F, 120, 180, 255, 190, 1);
        }

        if (striking) {
            float fade = 1.0F - sinceStrike / STRIKE_TICKS;
            int alpha = (int) (255 * fade);
            long boltSeed = be.clientStrike() * 7919 + be.getBlockPos().asLong();
            Vec3 sky = crown.add(0, BOLT_HEIGHT, 0);
            // flickers like vanilla lightning: re-rolled every other tick
            long flicker = (long) (sinceStrike / 2);
            GlowDraw.bolt(vc, m, sky, crown, boltSeed + flicker, 18, 1.6, 0.09F, 160, 190, 255, alpha, 4);
            for (int y = 1; y <= n; y++) {
                GlowDraw.cube(vc, m, new Vec3(0.5, y + 0.5, 0.5), 0.56F, 150, 200, 255, (int) (70 * fade));
            }
            GlowDraw.cube(vc, m, crown, 1.4F * fade + 0.3F, 220, 240, 255, (int) (90 * fade));
        }
    }

    @Override
    public AABB getRenderBoundingBox(SpireBlockEntity be) {
        AABB own = super.getRenderBoundingBox(be);
        var p = be.getBlockPos();
        return own.minmax(new AABB(p.getX() - 4, p.getY(), p.getZ() - 4, p.getX() + 5, p.getY() + be.conductors() + 4 + BOLT_HEIGHT, p.getZ() + 5));
    }

    @Override
    public boolean shouldRenderOffScreen(SpireBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
