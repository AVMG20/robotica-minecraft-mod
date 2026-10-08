package com.arno.robotica.energy.client;

import com.arno.robotica.energy.block.ColliderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Ring Collider: while charging, the pipe glows faintly, brighter as the charge fills. With the beam on, a glowing
 * beam runs through the whole loop and two particle bunches race around it in opposite directions, with comet tails,
 * faster as the beam grows; where they meet at the controller a white flash bursts. Plus the structure highlight.
 */
public class ColliderRenderer extends ControllerHighlightRenderer<ColliderBlockEntity> {
    private static final double PIPE_Y = 0.5;
    private static final int TAIL = 6;

    public ColliderRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ColliderBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, pose, buffers, light, overlay);
        Level level = be.getLevel();
        List<BlockPos> ring = be.ring();
        int beamStep = be.shownBeam(), chargeStep = be.shownCharge();
        if (level == null || ring.size() < 4 || (beamStep == 0 && chargeStep == 0)) return;
        BlockPos origin = be.getBlockPos();
        int n = ring.size();
        Vec3[] pts = new Vec3[n];
        for (int i = 0; i < n; i++) {
            BlockPos p = ring.get(i);
            pts[i] = new Vec3(p.getX() - origin.getX() + 0.5, p.getY() - origin.getY() + PIPE_Y, p.getZ() - origin.getZ() + 0.5);
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float time = level.getGameTime() + partialTick;

        if (beamStep == 0) {
            // spinning up: the pipe glows in step with the charge
            float glow = chargeStep / 10.0F * (0.7F + 0.3F * Mth.sin(time * 0.2F));
            int alpha = (int) (25 + 60 * glow);
            for (int i = 0; i < n; i++) GlowDraw.beam(vc, m, pts[i], pts[(i + 1) % n], 0.05F, 80, 140, 255, alpha);
            return;
        }

        float beam = beamStep / 10.0F;
        float shimmer = 0.85F + 0.15F * Mth.sin(time * 0.9F);
        for (int i = 0; i < n; i++) {
            Vec3 a = pts[i], b = pts[(i + 1) % n];
            GlowDraw.beam(vc, m, a, b, 0.035F, 210, 245, 255, (int) (200 * beam * shimmer));
            GlowDraw.beam(vc, m, a, b, 0.11F, 70, 150, 255, (int) (70 * beam));
        }

        // two bunches, one each way, meeting at the controller (index 0) once per lap
        float lapTicks = Mth.lerp(beam, 80.0F, 20.0F);
        float t = (time % lapTicks) / lapTicks;
        drawBunch(vc, m, pts, t, true, beam, 255, 120, 220);
        drawBunch(vc, m, pts, t, false, beam, 120, 255, 220);
        float meet = Math.min(t, 1.0F - t) * lapTicks;
        if (meet < 4.0F) {
            float f = 1.0F - meet / 4.0F;
            GlowDraw.cube(vc, m, pts[0], 0.2F + 0.9F * f * beam, 255, 255, 255, (int) (160 * f));
            GlowDraw.cube(vc, m, pts[0], 0.6F + 1.6F * f * beam, 160, 200, 255, (int) (60 * f));
        }
    }

    /** A bunch at lap fraction t (the reverse one runs the other way) with a fading tail. */
    private static void drawBunch(VertexConsumer vc, Matrix4f m, Vec3[] pts, float t, boolean forward, float beam, int r, int g, int b) {
        int n = pts.length;
        for (int k = 0; k <= TAIL; k++) {
            double pos = (forward ? t : 1.0 - t) * n + (forward ? -k * 0.35 : k * 0.35);
            Vec3 p = along(pts, pos);
            float fade = 1.0F - (float) k / (TAIL + 1);
            GlowDraw.cube(vc, m, p, (0.09F + 0.06F * beam) * fade + 0.02F, r, g, b, (int) (220 * fade * beam));
        }
    }

    private static Vec3 along(Vec3[] pts, double pos) {
        int n = pts.length;
        double wrapped = ((pos % n) + n) % n;
        int i = (int) Math.floor(wrapped);
        double f = wrapped - i;
        Vec3 a = pts[i], b = pts[(i + 1) % n];
        return a.add(b.subtract(a).scale(f));
    }

    @Override
    public AABB getRenderBoundingBox(ColliderBlockEntity be) {
        AABB own = super.getRenderBoundingBox(be);
        for (BlockPos p : be.ring()) own = own.minmax(new AABB(p));
        return own.inflate(2.5);
    }

    @Override
    public boolean shouldRenderOffScreen(ColliderBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }
}
