package com.arno.robotica.replicator.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.energy.block.ControllerBlock;
import com.arno.robotica.energy.client.ControllerHighlightRenderer;
import com.arno.robotica.replicator.farm.FarmControllerBlockEntity;
import com.arno.robotica.replicator.farm.FarmCycle;
import com.arno.robotica.replicator.farm.FarmKind;
import com.arno.robotica.replicator.farm.FarmStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Draws a working mob farm: the mob forms as a hologram in the farm's colour, stands while the three Impact Rams punch
 * out of the walls one after the other (each hit flashes it red and wears it down: cracks on the golem, the armour on
 * the Wither), then it falls over as the cycle ends. Timing from {@link FarmCycle}, counted on from the last sync.
 * The mob is a cached client entity ({@link HologramMobs}); its hurt, death and health fields are reset after drawing.
 */
public class FarmRenderer extends ControllerHighlightRenderer<FarmControllerBlockEntity> {
    private static final ResourceLocation RAM_TEXTURE = Robotica.id("textures/entity/impact_ram.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** How far a ram head travels out of its wall at full stroke (blocks). */
    private static final float STROKE = 0.7F;
    /** Ram head and rod half sizes (blocks). */
    private static final float HEAD = 0.34F, HEAD_DEPTH = 0.18F, ROD = 0.11F;
    private static final float WITHER_SCALE = 0.8F;

    private final EntityRenderDispatcher dispatcher;

    public FarmRenderer(BlockEntityRendererProvider.Context ctx) {
        super(ctx);
        this.dispatcher = ctx.getEntityRenderer();
    }

    @Override
    public void render(FarmControllerBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        super.render(be, partialTick, pose, buffers, light, overlay);
        Level level = be.getLevel();
        BlockState state = be.getBlockState();
        BlockPos center = be.center();
        if (level == null || center == null || !state.hasProperty(ControllerBlock.FORMED) || !state.getValue(ControllerBlock.FORMED)) return;
        int needed = Math.max(1, be.needed());
        float t = be.clientTick(level.getGameTime(), partialTick);
        BlockPos origin = be.getBlockPos();
        BoundingBox box = BoundingBox.fromCorners(center.offset(-2, -2, -2), center.offset(2, 2, 2));

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(RAM_TEXTURE));
        for (int i = 0; i < be.rams().size() && i < FarmStructure.RAMS; i++) {
            BlockPos ram = be.rams().get(i);
            float d = be.working() || t > 0 ? t - FarmCycle.strike(i, needed) : -10;
            drawRam(pose, vc, level, origin, ram, FarmStructure.ramPush(box, ram), extension(d));
        }
        if (t > 0 || be.working()) drawMob(be, pose, buffers, level, origin, center, state, t, needed, partialTick);
    }

    /** Ram stroke 0..1 at {@code d} ticks from its hit: a fast punch out, a short hold, a slow pull back. */
    private static float extension(float d) {
        if (d < -2) return 0;
        if (d < 0) return (d + 2) / 2;
        if (d < 1) return 1;
        if (d < 6) return 1 - (d - 1) / 5;
        return 0;
    }

    private void drawMob(FarmControllerBlockEntity be, PoseStack pose, MultiBufferSource buffers, Level level, BlockPos origin,
                         BlockPos center, BlockState state, float t, int needed, float partialTick) {
        FarmKind kind = be.kind();
        Entity entity = HologramMobs.get(kind.mob());
        if (!(entity instanceof LivingEntity living)) return;
        int form = FarmCycle.formTicks(needed);
        float fall = FarmCycle.fallTicks(needed);
        int hits = 0;
        float sinceHit = Float.MAX_VALUE;
        for (int i = 0; i < FarmStructure.RAMS; i++) {
            float since = t - FarmCycle.strike(i, needed);
            if (since >= 0) {
                hits++;
                sinceHit = since;
            }
        }
        float formed = form <= 0 ? 1 : Mth.clamp(t / form, 0, 1);

        float yaw = state.hasProperty(ControllerBlock.FACING) ? state.getValue(ControllerBlock.FACING).toYRot() : 0;
        living.setYRot(yaw);
        living.yRotO = yaw;
        living.yBodyRot = living.yBodyRotO = yaw;
        living.yHeadRot = living.yHeadRotO = yaw;
        living.tickCount = (int) (level.getGameTime());
        float max = living.getMaxHealth();
        living.setHealth(max * (1 - 0.3F * hits));
        living.hurtTime = sinceHit < 10 ? 10 - (int) sinceHit : 0;
        living.hurtDuration = 10;
        float sinceLast = t - FarmCycle.strike(FarmStructure.RAMS - 1, needed);
        living.deathTime = sinceLast > 0 && fall > 0 ? Math.min(19, 1 + (int) (sinceLast * 19 / fall)) : 0;
        if (living instanceof WitherBoss wither) wither.setInvulnerableTicks(0);

        float scale = (kind == FarmKind.WITHER ? WITHER_SCALE : 1.0F) * (0.6F + 0.4F * formed);
        pose.pushPose();
        pose.translate(center.getX() - origin.getX() + 0.5, center.getY() - origin.getY() - 1.0, center.getZ() - origin.getZ() + 0.5);
        pose.scale(scale, scale, scale);
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) dispatcher.getRenderer(living);
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        dispatcher.setRenderShadow(false);
        dispatcher.setRenderHitBoxes(false);
        try {
            if (formed < 1) {
                // still forming: a see-through hologram in the farm's colour
                int c = kind.color();
                int alpha = Math.round(255 * (0.25F + 0.6F * formed));
                dispatcher.render(living, 0, 0, 0, yaw, partialTick, pose,
                        new HologramBuffer(buffers, renderer.getTextureLocation(living), c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, alpha), FULL_BRIGHT);
            } else {
                dispatcher.render(living, 0, 0, 0, yaw, partialTick, pose, buffers, LevelRenderer.getLightColor(level, center.below()));
            }
        } finally {
            dispatcher.setRenderShadow(true);
            dispatcher.setRenderHitBoxes(hitBoxes);
            pose.popPose();
            // the cached entity is shared with the Replicator's hologram
            living.setHealth(max);
            living.hurtTime = 0;
            living.deathTime = 0;
        }
    }

    /** One ram head pushed {@code ext} of its stroke out of the inner face of its wall block. */
    private static void drawRam(PoseStack pose, VertexConsumer vc, Level level, BlockPos origin, BlockPos ram, Direction push, float ext) {
        int light = LevelRenderer.getLightColor(level, ram.relative(push));
        // along the push axis, from the ram block's middle: its inner face is at 0.5
        float headFront = 0.02F + ext * STROKE, headBack = headFront - 2 * HEAD_DEPTH;
        float cx = ram.getX() - origin.getX() + 0.5F, cy = ram.getY() - origin.getY() + 0.5F, cz = ram.getZ() - origin.getZ() + 0.5F;
        float sx = push.getStepX(), sz = push.getStepZ();
        // the head plate (front half of the texture)
        cuboid(pose, vc, cx, cy, cz, sx, sz, 0.5F + Math.max(0, headBack), 0.5F + headFront, HEAD, 0, 0, 1, 0.5F, light);
        // the rod from the wall to the head (back half)
        if (headBack > 0) cuboid(pose, vc, cx, cy, cz, sx, sz, 0.5F, 0.5F + headBack, ROD, 0, 0.5F, 1, 1, light);
    }

    /**
     * A square-section cuboid around the line through (cx, cy, cz) along (sx, 0, sz), from {@code a0} to {@code a1}
     * along it, half size {@code h} across; every face maps the UV rect.
     */
    private static void cuboid(PoseStack pose, VertexConsumer vc, float cx, float cy, float cz, float sx, float sz, float a0, float a1,
                               float h, float u0, float v0, float u1, float v1, int light) {
        float x0, x1, z0, z1;
        if (sx != 0) {
            x0 = cx + sx * a0;
            x1 = cx + sx * a1;
            z0 = cz - h;
            z1 = cz + h;
        } else {
            z0 = cz + sz * a0;
            z1 = cz + sz * a1;
            x0 = cx - h;
            x1 = cx + h;
        }
        box(pose, vc, Math.min(x0, x1), cy - h, Math.min(z0, z1), Math.max(x0, x1), cy + h, Math.max(z0, z1), u0, v0, u1, v1, light);
    }

    private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            float u0, float v0, float u1, float v1, int light) {
        PoseStack.Pose p = pose.last();
        quad(p, vc, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1, 0, 0, -1, light);
        quad(p, vc, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, u0, v0, u1, v1, 0, 0, 1, light);
        quad(p, vc, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, u0, v0, u1, v1, -1, 0, 0, light);
        quad(p, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1, 1, 0, 0, light);
        quad(p, vc, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, u0, v0, u1, v1, 0, 1, 0, light);
        quad(p, vc, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, u0, v0, u1, v1, 0, -1, 0, light);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz, int light) {
        vertex(p, vc, ax, ay, az, u0, v1, nx, ny, nz, light);
        vertex(p, vc, bx, by, bz, u1, v1, nx, ny, nz, light);
        vertex(p, vc, cx, cy, cz, u1, v0, nx, ny, nz, light);
        vertex(p, vc, dx, dy, dz, u0, v0, nx, ny, nz, light);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
        vc.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(FarmControllerBlockEntity be) {
        AABB own = super.getRenderBoundingBox(be);
        BlockPos c = be.center();
        return c == null ? own : own.minmax(new AABB(c).inflate(FarmStructure.SIZE / 2 + 1));
    }

    @Override
    public boolean shouldRenderOffScreen(FarmControllerBlockEntity be) {
        return true;
    }
}
