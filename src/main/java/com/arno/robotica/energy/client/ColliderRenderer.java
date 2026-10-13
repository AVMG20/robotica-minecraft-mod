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
 * Ring Collider: while charging, the pipe and the slots on the magnets glow faintly, brighter as the charge fills.
 * With the beam on, a glowing beam runs through the whole loop and two particle bunches race around it in opposite
 * directions, with comet tails, faster as the beam grows; each magnet's slot glows in its segment's colour and flares
 * as a bunch passes. Where the bunches meet at the controller sparks fly from its pipe mouths, arcs leap off it and a
 * thin shockwave ring runs out. Plus the structure highlight.
 */
public class ColliderRenderer extends ControllerHighlightRenderer<ColliderBlockEntity> {
    private static final int TAIL = 6;
    private static final float FLASH_TICKS = 6.0F;
    /** Render thread only: re-seeded for the arcs of every collision. */
    private static final java.util.Random FLASH = new java.util.Random();
    private static final Pipe CHARGE_GLOW = new Pipe(0.05), BEAM_CORE = new Pipe(0.035), BEAM_HALO = new Pipe(0.11);
    /** Magnet slots (segment models in scripts/data/energy_models.py): on a straight's coil top at y 11, 4 px along
     * the beam and 2 px across; on a corner's top at y 12, 2 x 2 px. */
    private static final float SLOT_Y = 11 / 16.0F - 0.5F, CORNER_Y = 12 / 16.0F - 0.5F, LIFT = 0.004F;
    private static final float SLOT_ALONG = 2 / 16.0F, SLOT_ACROSS = 1 / 16.0F;
    /** Slot colours per segment tier: Accelerator cyan, Resonant violet. */
    private static final int[][] SLOT_RGB = {{120, 150, 255}, {70, 215, 255}, {165, 115, 255}};

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
            for (int i = 0; i < n; i++) CHARGE_GLOW.draw(vc, m, pts[i], pts[(i + 1) % n], 130, 105, 255, alpha);
            drawSlots(vc, m, be, pts, 0.15F + 0.35F * glow, -1, -1);
            return;
        }

        float beam = beamStep / 10.0F;
        float shimmer = 0.85F + 0.15F * Mth.sin(time * 0.9F);
        int coreAlpha = (int) (200 * beam * shimmer), haloAlpha = (int) (70 * beam);
        for (int i = 0; i < n; i++) {
            Vec3 a = pts[i], b = pts[(i + 1) % n];
            BEAM_CORE.draw(vc, m, a, b, 236, 228, 255, coreAlpha);
            BEAM_HALO.draw(vc, m, a, b, 140, 100, 255, haloAlpha);
        }

        // two bunches, one each way, meeting at the controller (index 0) once per lap
        float lapTicks = Mth.lerp(beam, 80.0F, 20.0F);
        float t = (time % lapTicks) / lapTicks;
        drawBunch(vc, m, pts, t, true, beam, 255, 130, 235);
        drawBunch(vc, m, pts, t, false, beam, 130, 235, 255);
        drawSlots(vc, m, be, pts, 0.35F + 0.4F * beam, t * n, (1.0F - t) * n);
        float age = t * lapTicks;                       // ticks since the bunches last met
        if (age < FLASH_TICKS) drawCollision(vc, m, pts, age / FLASH_TICKS, beam, be.getBlockPos().asLong() * 31 + (long) (time / lapTicks));
    }

    /**
     * The magnet slots: a calm glow at {@code glow} (0-1) in the segment's colour, flaring towards white within a few
     * blocks of a bunch at ring positions {@code bunchA} and {@code bunchB} (negative: none).
     */
    private static void drawSlots(VertexConsumer vc, Matrix4f m, ColliderBlockEntity be, Vec3[] pts, float glow, float bunchA, float bunchB) {
        int n = pts.length;
        for (int i = 1; i < n; i++) {
            Vec3 p = pts[i], prev = pts[i - 1], next = pts[(i + 1) % n];
            float flare = 0.0F;
            if (bunchA >= 0) flare = Math.max(flare(i, bunchA, n), flare(i, bunchB, n));
            int[] rgb = SLOT_RGB[be.ringTier(i)];
            int r = (int) Mth.lerp(flare, rgb[0], 245), g = (int) Mth.lerp(flare, rgb[1], 240), bl = (int) Mth.lerp(flare, rgb[2], 255);
            int alpha = (int) (255 * Math.min(1.0F, glow + 0.5F * flare));
            float x = (float) p.x, y = (float) p.y, z = (float) p.z;
            if (prev.z == next.z) {             // straight along x
                GlowDraw.box(vc, m, x - SLOT_ALONG, y + SLOT_Y + LIFT, z - SLOT_ACROSS, x + SLOT_ALONG, y + SLOT_Y + 2 * LIFT, z + SLOT_ACROSS, r, g, bl, alpha);
            } else if (prev.x == next.x) {      // straight along z
                GlowDraw.box(vc, m, x - SLOT_ACROSS, y + SLOT_Y + LIFT, z - SLOT_ALONG, x + SLOT_ACROSS, y + SLOT_Y + 2 * LIFT, z + SLOT_ALONG, r, g, bl, alpha);
            } else {                            // corner: the bending magnet
                GlowDraw.box(vc, m, x - SLOT_ACROSS, y + CORNER_Y + LIFT, z - SLOT_ACROSS, x + SLOT_ACROSS, y + CORNER_Y + 2 * LIFT, z + SLOT_ACROSS, r, g, bl, alpha);
            }
        }
    }

    /** 1 with a bunch on ring block i, fading to 0 three blocks away (round the loop). */
    private static float flare(int i, float bunch, int n) {
        float d = Math.abs(i - bunch);
        d = Math.min(d, n - d);
        return Math.max(0.0F, 1.0F - d / 3.0F);
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
            GlowDraw.bolt(vc, m, c.x, c.y, c.z, ex, ey, ez, FLASH.nextLong(), 4, 0.14, 0.022F, 190, 175, 255, (int) (220 * f), 0);
        }
        GlowDraw.ring(vc, m, c.x, c.y, c.z, 0.6 + 1.8 * age * beam, 0.02F, 20, 195, 175, 255, (int) (150 * f));
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
