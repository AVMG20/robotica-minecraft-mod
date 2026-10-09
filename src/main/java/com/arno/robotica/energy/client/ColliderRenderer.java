package com.arno.robotica.energy.client;

import com.arno.robotica.energy.block.ColliderBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ring Collider: while charging, the pipe glows faintly, brighter as the charge fills. With the beam on, a glowing
 * beam runs through the whole loop and two particle bunches race around it in opposite directions, with comet tails,
 * faster as the beam grows; where they meet at the controller a white flash bursts. Plus the structure highlight.
 */
public class ColliderRenderer extends ControllerHighlightRenderer<ColliderBlockEntity> {
    private static final int TAIL = 6;
    private static final Pipe CHARGE_GLOW = new Pipe(0.05), BEAM_CORE = new Pipe(0.035), BEAM_HALO = new Pipe(0.11);

    /**
     * A glowing beam along one ring step, as two crossed ribbons. The ring only runs north, south, east and west, so
     * the ribbon widths are fixed vectors: nothing is allocated per segment and frame.
     */
    private record Pipe(Vec3 acrossX, Vec3 acrossZ, Vec3 up) {
        Pipe(double width) {
            this(new Vec3(width, 0, 0), new Vec3(0, 0, width), new Vec3(0, width, 0));
        }

        void draw(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, int r, int g, int bl, int alpha) {
            GlowDraw.ribbon(vc, m, a, b, a.x == b.x ? acrossX : acrossZ, r, g, bl, alpha);
            GlowDraw.ribbon(vc, m, a, b, up, r, g, bl, alpha);
        }
    }

    public ColliderRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ColliderBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, pose, buffers, light, overlay);
        Level level = be.getLevel();
        int beamStep = be.shownBeam(), chargeStep = be.shownCharge();
        if (level == null || be.ring().size() < 4 || (beamStep == 0 && chargeStep == 0)) return;
        Vec3[] pts = be.ringCentres();
        int n = pts.length;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        float time = level.getGameTime() % 24000L + partialTick;

        if (beamStep == 0) {
            // spinning up: the pipe glows in step with the charge
            float glow = chargeStep / 10.0F * (0.7F + 0.3F * Mth.sin(time * 0.2F));
            int alpha = (int) (25 + 60 * glow);
            for (int i = 0; i < n; i++) CHARGE_GLOW.draw(vc, m, pts[i], pts[(i + 1) % n], 80, 140, 255, alpha);
            return;
        }

        float beam = beamStep / 10.0F;
        float shimmer = 0.85F + 0.15F * Mth.sin(time * 0.9F);
        int coreAlpha = (int) (200 * beam * shimmer), haloAlpha = (int) (70 * beam);
        for (int i = 0; i < n; i++) {
            Vec3 a = pts[i], b = pts[(i + 1) % n];
            BEAM_CORE.draw(vc, m, a, b, 210, 245, 255, coreAlpha);
            BEAM_HALO.draw(vc, m, a, b, 70, 150, 255, haloAlpha);
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
        return super.getRenderBoundingBox(be).minmax(be.ringBounds()).inflate(2.5);
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
