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
    /** 32x16: head face 0,0-10,10, head edge 10,0-20,4, rod 20,0-24,16, bore bottom 24,0-32,8 (scripts/textures/mob_farm.py). */
    private static final ResourceLocation RAM_TEXTURE = Robotica.id("textures/entity/impact_ram.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** The ram block's bore: the head rests at its bottom, 2 px into the block (models/block/impact_ram.json). */
    private static final float REST = 0.5F - 2 / 16F;
    /** Ram head half size (a hair under the 10 px bore), head depth and rod half size (blocks). */
    private static final float HEAD = 0.3F, HEAD_DEPTH = 0.25F, ROD = 0.125F;
    private static final float WITHER_SCALE = 0.8F;
    /** Half the chamber, minus a margin: how far from the middle a mob may reach before it pokes through the glass. */
    private static final float ROOM = 1.45F;

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
        Direction facing = state.hasProperty(ControllerBlock.FACING) ? state.getValue(ControllerBlock.FACING) : Direction.NORTH;
        for (int i = 0; i < be.rams().size() && i < FarmStructure.RAMS; i++) {
            BlockPos ram = be.rams().get(i);
            float d = be.working() || t > 0 ? t - FarmCycle.strike(i, needed) : -10;
            float ext = extension(d);
            if (ext <= 0) continue;                       // at rest the block model shows the head down its bore
            Direction push = FarmStructure.ramPush(box, ram);
            drawRam(pose, vc, level, origin, ram, push, ext * stroke(be.kind(), push.getAxis() == facing.getAxis()));
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

    /**
     * How far a ram head travels out of its bore to meet the mob: the chamber's inner wall is 1.5 from its middle, the
     * mob's side (or its front and back, when the ram pushes along the way the mob faces) {@code r} from it.
     */
    private static float stroke(FarmKind kind, boolean alongFacing) {
        float r = kind == FarmKind.WITHER ? (alongFacing ? 0.3F : 0.5F) : (alongFacing ? 0.4F : 0.8F);
        return 1.5F + (0.5F - REST) - r;
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
        if (living.deathTime > 0) {
            // falling over (LivingEntityRenderer tips it round its feet): shrink it so it stays inside the chamber
            float tip = Math.min(1.0F, Mth.sqrt((living.deathTime + partialTick - 1) / 20.0F * 1.6F));
            float reach = living.getBbHeight() * Mth.sin(tip * Mth.HALF_PI) + living.getBbWidth() * 0.5F;
            scale = Math.min(scale, ROOM / reach);
        }
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

    /**
     * One ram pushed {@code out} blocks out of its bore: the head, the rod behind it back to the bore bottom, and a dark
     * bore bottom over the model's resting head.
     */
    private static void drawRam(PoseStack pose, VertexConsumer vc, Level level, BlockPos origin, BlockPos ram, Direction push, float out) {
        int light = LevelRenderer.getLightColor(level, ram.relative(push));
        PoseStack.Pose p = pose.last();
        float cx = ram.getX() - origin.getX() + 0.5F, cy = ram.getY() - origin.getY() + 0.5F, cz = ram.getZ() - origin.getZ() + 0.5F;
        float ax = push.getStepX(), az = push.getStepZ();
        // along the push axis, from the ram block's middle
        float front = REST + out, back = front - HEAD_DEPTH;
        float b = REST + 0.002F;
        face(p, vc, cx + ax * b, cy, cz + az * b, ax, az, HEAD + 0.005F, 24 / 32F, 0, 32 / 32F, 8 / 16F, light);
        prism(p, vc, cx, cy, cz, ax, az, back, front, HEAD, 0, 0, 10 / 32F, 10 / 16F, 10 / 32F, 0, 20 / 32F, 4 / 16F, light);
        if (back > REST) {
            prism(p, vc, cx, cy, cz, ax, az, REST, back, ROD, 0, 0, 0, 0, 20 / 32F, 0, 24 / 32F, 1, light);
        }
    }

    /**
     * A square-section prism round the line through (cx, cy, cz) along (ax, 0, az), from {@code t0} to {@code t1} along
     * it, half size {@code h} across. The front end (at t1) maps the end UV rect (none when it is empty), the four sides
     * the side UV rect with u across and v from front to back.
     */
    private static void prism(PoseStack.Pose p, VertexConsumer vc, float cx, float cy, float cz, float ax, float az, float t0, float t1,
                              float h, float eu0, float ev0, float eu1, float ev1, float su0, float sv0, float su1, float sv1, int light) {
        // across: the horizontal perpendicular (px, 0, pz) and up
        float px = az, pz = -ax;
        float fx = cx + ax * t1, fz = cz + az * t1, len = t0 - t1;
        if (eu1 > eu0) {
            face(p, vc, fx, cy, fz, ax, az, h, eu0, ev0, eu1, ev1, light);
            face(p, vc, cx + ax * t0, cy, cz + az * t0, -ax, -az, h, eu0, ev0, eu1, ev1, light);
        }
        // sides facing +-across and +-up: origin at the front edge, u across (2h), v back along the axis
        quad(p, vc, fx + px * h, cy - h, fz + pz * h, 0, 2 * h, 0, ax * len, 0, az * len, su0, sv0, su1, sv1, px, 0, pz, light);
        quad(p, vc, fx - px * h, cy - h, fz - pz * h, 0, 2 * h, 0, ax * len, 0, az * len, su0, sv0, su1, sv1, -px, 0, -pz, light);
        quad(p, vc, fx - px * h, cy + h, fz - pz * h, px * 2 * h, 0, pz * 2 * h, ax * len, 0, az * len, su0, sv0, su1, sv1, 0, 1, 0, light);
        quad(p, vc, fx - px * h, cy - h, fz - pz * h, px * 2 * h, 0, pz * 2 * h, ax * len, 0, az * len, su0, sv0, su1, sv1, 0, -1, 0, light);
    }

    /** A square face of half size {@code h} centred on (x, y, z), facing (ax, 0, az), upright as seen from in front. */
    private static void face(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float ax, float az, float h,
                             float u0, float v0, float u1, float v1, int light) {
        float px = az, pz = -ax;                          // the viewer's right
        quad(p, vc, x - px * h, y + h, z - pz * h, px * 2 * h, 0, pz * 2 * h, 0, -2 * h, 0, u0, v0, u1, v1, ax, 0, az, light);
    }

    /** The quad o, o + u, o + u + v, o + v with UVs (u0, v0) .. (u1, v1) along the u and v edges. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, float ox, float oy, float oz, float ux, float uy, float uz,
                             float vx, float vy, float vz, float u0, float v0, float u1, float v1, float nx, float ny, float nz, int light) {
        vertex(p, vc, ox, oy, oz, u0, v0, nx, ny, nz, light);
        vertex(p, vc, ox + ux, oy + uy, oz + uz, u1, v0, nx, ny, nz, light);
        vertex(p, vc, ox + ux + vx, oy + uy + vy, oz + uz + vz, u1, v1, nx, ny, nz, light);
        vertex(p, vc, ox + vx, oy + vy, oz + vz, u0, v1, nx, ny, nz, light);
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
