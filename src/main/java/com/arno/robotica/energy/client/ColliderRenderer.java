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
 * faster as the beam grows; where they meet at the controller sparks fly from its pipe mouths, arcs leap off it and a
 * thin shockwave ring runs out. Plus the structure highlight.
 */
public class ColliderRenderer extends ControllerHighlightRenderer<ColliderBlockEntity> {
    private static final int TAIL = 6;
    private static final float FLASH_TICKS = 6.0F;
    /** Render thread only: re-seeded for the arcs of every collision. */
    private static final java.util.Random FLASH = new java.util.Random();
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
        float age = t * lapTicks;                       // ticks since the bunches last met
        if (age < FLASH_TICKS) drawCollision(vc, m, pts, age / FLASH_TICKS, beam, be.getBlockPos().asLong() * 31 + (long) (time / lapTicks));
    }

    /**
     * Where the bunches meet: a white spark at both pipe mouths of the controller, short arcs leaping off it (re-rolled
     * every other tick) and a thin shockwave ring running out level with the pipe, all fading over {@link #FLASH_TICKS}.
     */
    private static void drawCollision(VertexConsumer vc, Matrix4f m, Vec3[] pts, float age, float beam, long lapSeed) {
        float f = 1.0F - age;
        Vec3 c = pts[0];
        for (int side = 0; side < 2; side++) {
            Vec3 next = pts[side == 0 ? 1 : pts.length - 1];
            double mx = c.x + (next.x - c.x) * 0.6, mz = c.z + (next.z - c.z) * 0.6;
            GlowDraw.box(vc, m, (float) mx - 0.12F * f, (float) c.y - 0.12F * f, (float) mz - 0.12F * f,
                    (float) mx + 0.12F * f, (float) c.y + 0.12F * f, (float) mz + 0.12F * f, 255, 255, 255, (int) (180 * f));
        }
        FLASH.setSeed(lapSeed * 7 + (long) (age * FLASH_TICKS / 2));
        int arcs = 3 + (int) (3 * beam);
        for (int i = 0; i < arcs; i++) {
            double yaw = FLASH.nextDouble() * Math.PI * 2, pitch = FLASH.nextDouble() * 1.1;
            double reach = (0.9 + FLASH.nextDouble() * 0.8) * (0.6 + 0.4 * age);
            double ex = c.x + Math.cos(yaw) * Math.cos(pitch) * reach, ey = c.y + Math.sin(pitch) * reach,
                    ez = c.z + Math.sin(yaw) * Math.cos(pitch) * reach;
            GlowDraw.bolt(vc, m, c.x, c.y, c.z, ex, ey, ez, FLASH.nextLong(), 4, 0.14, 0.022F, 170, 210, 255, (int) (220 * f), 0);
        }
        GlowDraw.ring(vc, m, c.x, c.y, c.z, 0.6 + 1.8 * age * beam, 0.02F, 20, 170, 215, 255, (int) (150 * f));
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
