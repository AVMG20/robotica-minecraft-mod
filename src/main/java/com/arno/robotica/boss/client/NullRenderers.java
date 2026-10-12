package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.NullPylon;
import com.arno.robotica.boss.entity.NullSentinel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Renderers of the Null Sentinel and its pylons. Besides the models (with a full-bright glow layer) they draw the
 * telegraphs as glowing geometry: the aim line and the null beam, the gravity well's ring, the gravity rings and rifts on
 * the floor, the shield around the Sentinel and the pylons' tethers. All additive ({@link RenderType#lightning()}).
 */
public final class NullRenderers {
    private NullRenderers() {}

    private static final int VIOLET = 0xB07CFF;
    private static final int PALE = 0xE8D8FF;
    private static final int CYAN = 0x6FF2E0;
    private static final int DEEP = 0x7A3CFF;

    private static <T extends Mob, M extends EntityModel<T>> EyesLayer<T, M> glow(RenderLayerParent<T, M> parent, ResourceLocation texture) {
        RenderType type = RenderType.eyes(texture);
        return new EyesLayer<>(parent) {
            @Override
            public RenderType renderType() {
                return type;
            }
        };
    }

    public static class Sentinel extends MobRenderer<NullSentinel, NullSentinelModel> {
        private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/null_sentinel.png");
        public static final float SCALE = 1.25F;

        public Sentinel(EntityRendererProvider.Context ctx) {
            super(ctx, new NullSentinelModel(ctx.bakeLayer(NullSentinelModel.LAYER)), 1.2F);
            addLayer(glow(this, Robotica.id("textures/entity/null_sentinel_glow.png")));
        }

        @Override
        public ResourceLocation getTextureLocation(NullSentinel entity) {
            return TEXTURE;
        }

        @Override
        protected void scale(NullSentinel entity, PoseStack pose, float partialTick) {
            pose.scale(SCALE, SCALE, SCALE);
        }

        @Override
        protected float getFlipDegrees(NullSentinel entity) {
            return 0.0F;
        }

        /** The beam and the floor rings reach far past its body. */
        @Override
        public boolean shouldRender(NullSentinel entity, Frustum frustum, double x, double y, double z) {
            if (super.shouldRender(entity, frustum, x, y, z)) return true;
            NullSentinel.Action a = entity.action();
            return a == NullSentinel.Action.BEAM || a == NullSentinel.Action.BEAM_WINDUP || !entity.clientSpots().isEmpty();
        }

        @Override
        public void render(NullSentinel entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            super.render(entity, yaw, partialTick, pose, buffers, light);
            if (entity.isDeadOrDying()) return;
            VertexConsumer buf = buffers.getBuffer(RenderType.lightning());
            Vec3 origin = entity.getPosition(partialTick);
            float age = entity.tickCount + partialTick;
            float at = entity.actionTicks() + partialTick;
            NullSentinel.Action a = entity.action();
            if (a == NullSentinel.Action.BEAM_WINDUP || a == NullSentinel.Action.BEAM) {
                Vec3 lens = entity.lensPos();
                Vec3 dir = entity.aim();
                double len = entity.beamEnd(lens, dir).distanceTo(lens);
                Vec3 from = lens.subtract(entity.position());
                if (a == NullSentinel.Action.BEAM_WINDUP) {
                    boolean locked = at > NullSentinel.BEAM_WINDUP - NullSentinel.BEAM_LOCK;
                    float flicker = 0.55F + 0.45F * Mth.sin(age * (locked ? 2.4F : 0.9F));
                    float w = locked ? 0.06F : 0.03F;
                    beam(buf, pose, from, dir, len, w, PALE, (locked ? 0.9F : 0.45F) * flicker);
                    if (locked) beam(buf, pose, from, dir, len, 0.16F, VIOLET, 0.18F * flicker);
                } else {
                    float swell = Math.min(1.0F, at / 3.0F);
                    float fade = Mth.clamp((NullSentinel.BEAM_TICKS - at) / 4.0F, 0.0F, 1.0F);
                    float pulse = 1.0F + 0.12F * Mth.sin(age * 1.7F);
                    beam(buf, pose, from, dir, len, 0.5F * swell * pulse, VIOLET, 0.32F * fade);
                    beam(buf, pose, from, dir, len, 0.28F * swell * pulse, DEEP, 0.45F * fade);
                    beam(buf, pose, from, dir, len, 0.1F * swell, PALE, 0.95F * fade);
                }
            }
            double gy = 0.04;
            if (a == NullSentinel.Action.WELL_WINDUP || a == NullSentinel.Action.WELL) {
                boolean pull = a == NullSentinel.Action.WELL;
                float p = pull ? 1.0F : Mth.clamp(at / NullSentinel.WELL_WINDUP, 0.0F, 1.0F);
                float pulse = 0.7F + 0.3F * Mth.sin(age * (pull ? 1.6F : 0.7F));
                float r = (float) NullSentinel.BURST_RADIUS;
                ring(buf, pose, 0.0, gy, 0.0, r - 0.22F, r + 0.06F, 64, age * 0.02F, VIOLET, (0.4F + 0.6F * p) * pulse);
                ring(buf, pose, 0.0, gy, 0.0, 0.0F, r - 0.22F, 64, 0.0F, DEEP, 0.14F * p);
            }
            if (entity.shieldUp()) {
                double cy = entity.corePos().y - entity.getY();
                float pulse = 0.6F + 0.4F * Mth.sin(age * 0.3F);
                ring(buf, pose, 0.0, cy - 0.8, 0.0, 1.65F, 1.75F, 48, age * 0.05F, CYAN, 0.5F * pulse);
                ring(buf, pose, 0.0, cy + 0.6, 0.0, 1.55F, 1.65F, 48, -age * 0.05F, CYAN, 0.4F * pulse);
            }
            long now = entity.level().getGameTime();
            for (NullSentinel.Spot s : entity.clientSpots()) {
                Vec3 p = s.pos().subtract(origin);
                if (s.kind() == NullSentinel.SPOT_LAUNCH) {
                    float left = s.at() - now - partialTick;
                    if (left < 0.0F) continue;
                    float t = 1.0F - left / NullSentinel.LAUNCH_DELAY;
                    float blink = left < 10.0F ? 0.6F + 0.4F * Mth.sin(age * 2.5F) : 1.0F;
                    float r = (float) NullSentinel.LAUNCH_RADIUS;
                    ring(buf, pose, p.x, p.y + gy, p.z, r - 0.14F, r + 0.04F, 40, age * 0.08F, VIOLET, (0.45F + 0.5F * t) * blink);
                    ring(buf, pose, p.x, p.y + gy, p.z, 0.0F, r * t, 32, 0.0F, PALE, 0.22F * t * blink);
                } else {
                    float r = (float) NullSentinel.RIFT_RADIUS;
                    boolean open = now >= s.at();
                    float closing = Mth.clamp((s.until() - now - partialTick) / 10.0F, 0.0F, 1.0F);
                    if (!open) {
                        ring(buf, pose, p.x, p.y + gy, p.z, r - 0.06F, r, 40, 0.0F, DEEP, 0.35F + 0.25F * Mth.sin(age * 0.8F));
                    } else {
                        ring(buf, pose, p.x, p.y + gy, p.z, r - 0.18F, r, 40, age * 0.03F, VIOLET, 0.6F * closing);
                        for (int k = 0; k < 3; k++) {
                            float ri = r * (0.25F + 0.25F * k);
                            ring(buf, pose, p.x, p.y + gy + 0.005 * k, p.z, ri - 0.08F, ri, 24, age * (0.06F + k * 0.03F), DEEP, 0.35F * closing);
                        }
                        ring(buf, pose, p.x, p.y + gy, p.z, 0.0F, r - 0.18F, 32, 0.0F, DEEP, 0.12F * closing);
                    }
                }
            }
        }
    }

    public static class Pylon extends MobRenderer<NullPylon, NullPylonModel> {
        private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/null_pylon.png");

        public Pylon(EntityRendererProvider.Context ctx) {
            super(ctx, new NullPylonModel(ctx.bakeLayer(NullPylonModel.LAYER)), 0.4F);
            addLayer(glow(this, Robotica.id("textures/entity/null_pylon_glow.png")));
        }

        @Override
        public ResourceLocation getTextureLocation(NullPylon entity) {
            return TEXTURE;
        }

        @Override
        public boolean shouldRender(NullPylon entity, Frustum frustum, double x, double y, double z) {
            return super.shouldRender(entity, frustum, x, y, z) || entity.owner() != null;
        }

        /** The tether: a flickering cyan line from the crystal to the Sentinel's core. */
        @Override
        public void render(NullPylon entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            super.render(entity, yaw, partialTick, pose, buffers, light);
            NullSentinel owner = entity.owner();
            if (owner == null || entity.isDeadOrDying()) return;
            Vec3 origin = entity.getPosition(partialTick);
            Vec3 from = new Vec3(0.0, 0.95, 0.0);
            Vec3 core = owner.getPosition(partialTick).add(owner.corePos().subtract(owner.position()));
            Vec3 to = core.subtract(origin);
            Vec3 d = to.subtract(from);
            double len = d.length();
            if (len < 0.5) return;
            float age = entity.tickCount + partialTick;
            float flicker = 0.6F + 0.4F * Mth.sin(age * 1.3F + entity.getId());
            VertexConsumer buf = buffers.getBuffer(RenderType.lightning());
            beam(buf, pose, from, d.scale(1.0 / len), len, 0.05F, CYAN, 0.8F * flicker);
            beam(buf, pose, from, d.scale(1.0 / len), len, 0.14F, CYAN, 0.2F * flicker);
        }
    }

    // ------------------------------------------------------------------ geometry

    /** Scratch rotation and the unit corners of a beam's cross-section (render thread only, nothing allocated per frame). */
    private static final Quaternionf TURN = new Quaternionf();
    private static final float[][] CORNERS = {{-1.0F, -1.0F}, {1.0F, -1.0F}, {1.0F, 1.0F}, {-1.0F, 1.0F}};

    /** A square prism of half width {@code w} from {@code from} along {@code dir} (unit), both faces of every side. */
    static void beam(VertexConsumer buf, PoseStack pose, Vec3 from, Vec3 dir, double len, float w, int rgb, float alpha) {
        if (alpha <= 0.0F || w <= 0.0F) return;
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(TURN.rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z));
        Matrix4f m = pose.last().pose();
        float r = (rgb >> 16 & 255) / 255.0F;
        float g = (rgb >> 8 & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;
        float l = (float) len;
        for (int i = 0; i < 4; i++) {
            float x0 = CORNERS[i][0] * w, z0 = CORNERS[i][1] * w;
            float x1 = CORNERS[(i + 1) % 4][0] * w, z1 = CORNERS[(i + 1) % 4][1] * w;
            quad(buf, m, x0, 0.0F, z0, x1, 0.0F, z1, x1, l, z1, x0, l, z0, r, g, b, alpha);
        }
        pose.popPose();
    }

    /** A flat ring (annulus; a disc when {@code inner} is 0) on the floor, turned by {@code spin} radians. */
    static void ring(VertexConsumer buf, PoseStack pose, double x, double y, double z, float inner, float outer, int segments, float spin,
                     int rgb, float alpha) {
        if (alpha <= 0.0F || outer <= 0.0F) return;
        Matrix4f m = pose.last().pose();
        float r = (rgb >> 16 & 255) / 255.0F;
        float g = (rgb >> 8 & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;
        float fy = (float) y;
        for (int i = 0; i < segments; i++) {
            float a0 = spin + i * Mth.TWO_PI / segments;
            float a1 = spin + (i + 1) * Mth.TWO_PI / segments;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad(buf, m, (float) x + c0 * inner, fy, (float) z + s0 * inner, (float) x + c0 * outer, fy, (float) z + s0 * outer,
                    (float) x + c1 * outer, fy, (float) z + s1 * outer, (float) x + c1 * inner, fy, (float) z + s1 * inner, r, g, b, alpha);
        }
    }

    /** One quad, both windings (the lightning render type culls back faces). */
    private static void quad(VertexConsumer buf, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float b, float a) {
        buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        buf.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        buf.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        buf.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        buf.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
    }
}
