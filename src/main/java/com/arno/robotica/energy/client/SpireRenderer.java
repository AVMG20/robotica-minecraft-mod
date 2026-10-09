package com.arno.robotica.energy.client;

import com.arno.robotica.energy.block.SpireBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Tesla Spire: while it runs, a pulsing ball of light on the crown, arcs crawling down the column and leaping from the
 * crown into the air. A lightning strike draws a forked bolt from the sky onto the crown for half a second, flashes the
 * sky and lights the whole column. While formed, every metal block of the column wears a coil shell tinted with its map
 * colour (scripts/scene_preview.py draws the same), its arc slits glowing in a wave up the column while the spire runs.
 * Plus the structure highlight of every controller.
 */
public class SpireRenderer extends ControllerHighlightRenderer<SpireBlockEntity> {
    private static final int STRIKE_TICKS = 12;
    private static final int BOLT_HEIGHT = 72;

    private long flashedFor = Long.MIN_VALUE;
    private final Vector3f normal = new Vector3f();
    private final int[] lights = new int[4];
    private static final float[] FULL = {1.0F, 1.0F, 1.0F, 1.0F};

    public SpireRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(SpireBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, pose, buffers, light, overlay);
        Level level = be.getLevel();
        int n = be.conductors();
        if (level == null || n <= 0) return;
        // float keeps tick precision only up to 2^24: work from the long game time
        long gameTime = level.getGameTime();
        float time = (gameTime % 24000L) + partialTick;
        float sinceStrike = (gameTime - be.clientStrike()) + partialTick;
        boolean striking = sinceStrike >= 0 && sinceStrike < STRIKE_TICKS;
        drawColumn(be, level, n, time, striking ? 1.0F - sinceStrike / STRIKE_TICKS : 0, pose, buffers);
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
            // both ends just outside the conductor's face, so the arc never dips into the block
            Vec3 a = onSurface(side, y0), b = onSurface(side + 0.6, y1);
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
            GlowDraw.box(vc, m, -0.06F, 0.94F, -0.06F, 1.06F, n + 1.06F, 1.06F, 150, 200, 255, (int) (70 * fade));
            GlowDraw.cube(vc, m, crown, 1.4F * fade + 0.3F, 220, 240, 255, (int) (90 * fade));
        }
    }

    /** Coil shells over the conductors; the slits light in a wave climbing the column while it runs, white on a strike. */
    private void drawColumn(SpireBlockEntity be, Level level, int n, float time, float strike, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        BlockPos base = be.getBlockPos();
        for (int i = 1; i <= n; i++) {
            BlockPos p = base.above(i);
            BlockState state = level.getBlockState(p);
            if (state.isAir()) continue;
            int col = state.getMapColor(level, p).col;
            float r = coilTint(col >> 16), g = coilTint(col >> 8), b = coilTint(col);
            int light = sideLight(level, p);
            lights[0] = lights[1] = lights[2] = lights[3] = light;
            pose.pushPose();
            pose.translate(0, i, 0);
            draw(level, pose, vc, PartModel.SPIRE_COIL, r, g, b, false);
            float wave = be.running() ? 0.35F + 0.65F * Math.max(0, Mth.sin(time * 0.3F - i * 0.9F)) : 0;
            float k = Math.max(wave, strike);
            if (k > 0.02F) {
                lights[0] = lights[1] = lights[2] = lights[3] = LightTexture.FULL_BRIGHT;
                draw(level, pose, vc, PartModel.SPIRE_COIL_GLOW, Mth.lerp(strike, 0.45F, 1.0F) * k, Mth.lerp(strike, 0.85F, 1.0F) * k, k, true);
            }
            pose.popPose();
        }
    }

    /** Same lift as scene_preview.coil_tint: the map colour raised so dark metals still show their windings. */
    private static float coilTint(int channel) {
        return Math.min(1.0F, 0.3F + 0.85F * (channel & 255) / 255.0F);
    }

    /** The conductor itself is opaque (no light inside): the brightest of its four sides. */
    private static int sideLight(Level level, BlockPos p) {
        int block = 0, sky = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int packed = LevelRenderer.getLightColor(level, p.relative(d));
            block = Math.max(block, LightTexture.block(packed));
            sky = Math.max(sky, LightTexture.sky(packed));
        }
        return LightTexture.pack(block, sky);
    }

    /** Emits a part's quads, tinted quads times (r, g, b), shaded like chunk faces unless {@code bright}. */
    private void draw(Level level, PoseStack pose, VertexConsumer vc, PartModel part, float r, float g, float b, boolean bright) {
        PoseStack.Pose last = pose.last();
        for (BakedQuad quad : part.quads()) {
            float shade = 1.0F;
            if (!bright) {
                Vec3i dir = quad.getDirection().getNormal();
                last.transformNormal(dir.getX(), dir.getY(), dir.getZ(), normal);
                shade = level.getShade(normal.x(), normal.y(), normal.z(), quad.isShade());
            }
            float tr = quad.isTinted() ? r : 1.0F, tg = quad.isTinted() ? g : 1.0F, tb = quad.isTinted() ? b : 1.0F;
            vc.putBulkData(last, quad, FULL, shade * tr, shade * tg, shade * tb, 1.0F, lights, OverlayTexture.NO_OVERLAY, false);
        }
    }

    /** A point on the column's surface at angle {@code ang}, 0.06 out from the block face. */
    private static Vec3 onSurface(double ang, double y) {
        double c = Math.cos(ang), s = Math.sin(ang), k = 0.56 / Math.max(Math.abs(c), Math.abs(s));
        return new Vec3(0.5 + c * k, y, 0.5 + s * k);
    }

    @Override
    public AABB getRenderBoundingBox(SpireBlockEntity be) {
        AABB own = super.getRenderBoundingBox(be);
        var p = be.getBlockPos();
        return own.minmax(new AABB(p.getX() - 6, p.getY(), p.getZ() - 6, p.getX() + 7, p.getY() + be.conductors() + 4 + BOLT_HEIGHT, p.getZ() + 7));
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
