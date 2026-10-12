package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.NullPylon;
import com.arno.robotica.boss.entity.NullSentinel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
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

/**
 * Renderers of the Null Sentinel and its pylons. Besides the models (with a full-bright glow layer) they draw the
 * telegraphs as soft additive glow ({@link NullFx}): the null beam (white-hot core in a violet falloff, energy running
 * along it, a charge at the lens and a bloom where it hits), the aim line, the gravity well's ring, the gravity rings and
 * rifts on the floor, the shield around the Sentinel and the pylons' crackling tethers.
 */
public final class NullRenderers {
    private NullRenderers() {}

    private static final int VIOLET = 0xA060FF;
    private static final int LILAC = 0xCDA8FF;
    private static final int HOT = 0xFFF4FF;
    private static final int CYAN = 0x5FF2DC;
    private static final int MINT = 0xDFFFF8;
    private static final int DEEP = 0x6A30E0;
    /** Dark band under the floor rings, and the black of an open rift. */
    private static final int SHADOW = 0x14061F;
    private static final int VOID = 0x05010A;

    private static <T extends Mob, M extends EntityModel<T>> EyesLayer<T, M> glow(RenderLayerParent<T, M> parent, ResourceLocation texture) {
        RenderType type = RenderType.eyes(texture);
        return new EyesLayer<>(parent) {
            @Override
            public RenderType renderType() {
                return type;
            }
        };
    }

    /** Sets {@link NullFx#camera} for a pose at {@code origin} (the entity's drawn position). */
    private static void camera(Vec3 origin) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        NullFx.camera(cam.x - origin.x, cam.y - origin.y, cam.z - origin.z);
    }

    /** Small deterministic noise in [-1, 1]. */
    private static float hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 32767.5F - 1.0F;
    }

    public static class Sentinel extends MobRenderer<NullSentinel, NullSentinelModel> {
        private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/null_sentinel.png");
        public static final float SCALE = 1.25F;

        /** The beam's ends, worked out once per tick per Sentinel (a block clip), not every frame. */
        private int beamId = -1;
        private long beamTick = Long.MIN_VALUE;
        private double lx, ly, lz, dx, dy, dz, len;

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

        private void updateBeam(NullSentinel entity) {
            long tick = entity.level().getGameTime();
            if (beamId == entity.getId() && beamTick == tick) return;
            beamId = entity.getId();
            beamTick = tick;
            Vec3 lens = entity.lensPos();
            Vec3 dir = entity.aim();
            Vec3 end = entity.beamEnd(lens, dir);
            lx = lens.x - entity.getX();
            ly = lens.y - entity.getY();
            lz = lens.z - entity.getZ();
            dx = dir.x;
            dy = dir.y;
            dz = dir.z;
            len = end.distanceTo(lens);
        }

        /** Fills the line points from the lens along the beam, with energy running outwards and soft ends. */
        private int beamPoints(float t, float width, boolean taperEnd) {
            int n = Mth.clamp((int) (len / 0.35) + 2, 2, NullFx.MAX);
            for (int i = 0; i < n; i++) {
                float f = i / (float) (n - 1);
                float s = (float) (len * f);
                NullFx.X[i] = (float) (lx + dx * s);
                NullFx.Y[i] = (float) (ly + dy * s);
                NullFx.Z[i] = (float) (lz + dz * s);
                float start = Mth.clamp(s / 0.4F, 0.0F, 1.0F);
                float end = taperEnd ? Mth.clamp((float) (len - s) / 1.2F, 0.25F, 1.0F) : 1.0F;
                NullFx.A[i] = start * (0.78F + 0.22F * NullFx.wave(s, t, 3));
                NullFx.W[i] = width * (0.55F + 0.45F * start) * end * (0.9F + 0.1F * NullFx.wave(s * 1.7F, t * 1.3F, 7));
            }
            return n;
        }

        @Override
        public void render(NullSentinel entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            // its collapse is its own animation: no red hurt tint after the killing blow's flash
            if (entity.isDeadOrDying() && entity.dyingTicks() > 6) entity.hurtTime = 0;
            super.render(entity, yaw, partialTick, pose, buffers, light);
            if (entity.isDeadOrDying()) return;
            Vec3 origin = entity.getPosition(partialTick);
            camera(origin);
            Matrix4f m = pose.last().pose();
            float age = entity.tickCount + partialTick;
            float at = entity.actionTicks() + partialTick;
            NullSentinel.Action a = entity.action();
            boolean beam = a == NullSentinel.Action.BEAM_WINDUP || a == NullSentinel.Action.BEAM;
            if (beam) updateBeam(entity);

            // floor telegraphs first, in one batch (switching render types ends the batch before it)
            boolean well = a == NullSentinel.Action.WELL_WINDUP || a == NullSentinel.Action.WELL;
            if (well || !entity.clientSpots().isEmpty() || a == NullSentinel.Action.BEAM) {
                VertexConsumer shade = buffers.getBuffer(NullFx.SHADE);
                floor(entity, shade, m, origin, age, at, partialTick, a, well);
            }

            VertexConsumer buf = buffers.getBuffer(NullFx.GLOW);
            if (beam) {
                float ex = (float) (lx + dx * len), ey = (float) (ly + dy * len), ez = (float) (lz + dz * len);
                if (a == NullSentinel.Action.BEAM_WINDUP) {
                    float charge = Mth.clamp(at / NullSentinel.BEAM_WINDUP, 0.0F, 1.0F);
                    boolean locked = at > NullSentinel.BEAM_WINDUP - NullSentinel.BEAM_LOCK;
                    // the lens gathers
                    NullFx.dot(buf, m, (float) lx, (float) ly, (float) lz, 0.12F + 0.5F * charge, VIOLET, 70 + 130 * charge);
                    NullFx.dot(buf, m, (float) lx, (float) ly, (float) lz, 0.05F + 0.18F * charge, HOT, 130 + 125 * charge);
                    // aim line: thin, dashes marching out while it tracks, steady and blinking once it is frozen
                    int n = beamPoints(age, 1.0F, false);
                    for (int i = 0; i < n; i++) {
                        float s = (float) (len * i / (n - 1));
                        float dash = locked ? 1.0F : 0.3F + 0.7F * Math.max(0.0F, Mth.sin(s * 2.4F - age * 0.7F));
                        NullFx.A[i] *= dash;
                        NullFx.W[i] = 1.0F;
                    }
                    float blink = locked ? 0.75F + 0.25F * Mth.sin(age * 2.2F) : 1.0F;
                    NullFx.strip(buf, m, n, locked ? 0.1F : 0.07F, VIOLET, (locked ? 120 : 70) * blink);
                    NullFx.strip(buf, m, n, locked ? 0.028F : 0.018F, LILAC, (locked ? 255 : 200) * blink);
                    NullFx.dot(buf, m, ex, ey, ez, locked ? 0.4F : 0.25F, LILAC, (locked ? 200 : 120) * blink);
                } else {
                    float swell = Math.min(1.0F, at / 3.0F);
                    float fade = Mth.clamp((NullSentinel.BEAM_TICKS - at) / 5.0F, 0.0F, 1.0F);
                    float flick = 0.92F + 0.08F * Mth.sin(age * 3.7F) + 0.04F * hash(entity.getId(), (int) age, 1);
                    float k = swell * fade * flick;
                    int n = beamPoints(age * 1.6F, 1.0F, true);
                    NullFx.strip(buf, m, n, 0.5F * k, DEEP, 90 * fade);
                    NullFx.strip(buf, m, n, 0.2F * k, VIOLET, 180 * fade);
                    NullFx.strip(buf, m, n, 0.07F * k, LILAC, 235 * fade);
                    NullFx.strip(buf, m, n, 0.028F * k, HOT, 255 * fade);
                    // emitter and impact bloom
                    NullFx.dot(buf, m, (float) lx, (float) ly, (float) lz, 0.6F * k, VIOLET, 160 * fade);
                    NullFx.dot(buf, m, (float) lx, (float) ly, (float) lz, 0.24F * k, HOT, 255 * fade);
                    float pulse = 0.85F + 0.15F * Mth.sin(age * 1.9F);
                    NullFx.dot(buf, m, ex, ey, ez, 1.1F * k * pulse, VIOLET, 140 * fade);
                    NullFx.dot(buf, m, ex, ey, ez, 0.38F * k, HOT, 240 * fade);
                }
            }
            if (entity.shieldUp()) {
                float cy = (float) NullSentinel.CORE_HEIGHT;
                float pulse = 0.7F + 0.3F * Mth.sin(age * 0.25F);
                NullFx.ring(buf, m, 0.0F, cy - 0.7F, 0.0F, 1.7F, 0.045F, CYAN, 170 * pulse, 10, age * 0.08F);
                NullFx.ring(buf, m, 0.0F, cy + 0.7F, 0.0F, 1.55F, 0.04F, CYAN, 140 * pulse, 10, -age * 0.08F);
            }
        }

        /**
         * A crisp floor ring: a soft dark band that makes it read on bright floors, a coloured line and a white-hot
         * middle line (plain alpha blending, full bright).
         */
        private static void floorRing(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, float hw, int rgb, float alpha,
                                      int dashes, float spin) {
            NullFx.band(vc, m, x, y, z, r - hw * 2.4F, r - hw, r + hw, r + hw * 2.4F, SHADOW, alpha * 0.55F, 0, 0.0F);
            NullFx.band(vc, m, x, y + 0.002F, z, r - hw, r - hw * 0.45F, r + hw * 0.45F, r + hw, rgb, alpha, dashes, spin);
            NullFx.ring(vc, m, x, y + 0.004F, z, r, hw * 0.3F, HOT, alpha * 0.85F, dashes, spin);
        }

        private void floor(NullSentinel entity, VertexConsumer vc, Matrix4f m, Vec3 origin, float age, float at, float partialTick,
                           NullSentinel.Action a, boolean well) {
            float gy = 0.03F;
            if (a == NullSentinel.Action.BEAM) {
                // a scorch glow where the beam hits the floor
                float ex = (float) (lx + dx * len), ey = (float) (ly + dy * len), ez = (float) (lz + dz * len);
                float fade = Mth.clamp((NullSentinel.BEAM_TICKS - at) / 5.0F, 0.0F, 1.0F);
                NullFx.floorGlow(vc, m, ex, ey + gy, ez, 1.0F, SHADOW, 90 * fade);
                NullFx.floorGlow(vc, m, ex, ey + gy + 0.002F, ez, 0.5F, VIOLET, 120 * fade);
            }
            if (well) {
                boolean pull = a == NullSentinel.Action.WELL;
                float p = pull ? 1.0F : Mth.clamp(at / NullSentinel.WELL_WINDUP, 0.0F, 1.0F);
                float r = (float) NullSentinel.BURST_RADIUS;
                float pulse = pull ? 0.8F + 0.2F * Mth.sin(age * 1.4F) : 1.0F;
                NullFx.floorGlow(vc, m, 0.0F, gy - 0.002F, 0.0F, r, SHADOW, 40 * p);
                floorRing(vc, m, 0.0F, gy, 0.0F, r, 0.13F, VIOLET, (120 + 135 * p) * pulse, 18, -age * 0.12F);
                // the pull: rings closing in from the well's reach
                float reach = (float) NullSentinel.WELL_RANGE;
                for (int k = 0; k < 2; k++) {
                    float f = ((pull ? age * 0.06F : age * 0.03F) + k * 0.5F) % 1.0F;
                    float rr = reach - (reach - r) * f;
                    NullFx.ring(vc, m, 0.0F, gy, 0.0F, rr, 0.08F, LILAC, 150 * p * Mth.sin(f * Mth.PI), 0, 0.0F);
                }
            }
            long now = entity.level().getGameTime();
            for (NullSentinel.Spot s : entity.clientSpots()) {
                float px = (float) (s.pos().x - origin.x), py = (float) (s.pos().y - origin.y) + gy, pz = (float) (s.pos().z - origin.z);
                if (s.kind() == NullSentinel.SPOT_LAUNCH) {
                    float left = s.at() - now - partialTick;
                    if (left < 0.0F) continue;
                    float t = 1.0F - left / NullSentinel.LAUNCH_DELAY;
                    float blink = left < 10.0F ? 0.65F + 0.35F * Mth.sin(age * 2.6F) : 1.0F;
                    float r = (float) NullSentinel.LAUNCH_RADIUS;
                    NullFx.floorGlow(vc, m, px, py - 0.002F, pz, r, VIOLET, 90 * t * blink);
                    floorRing(vc, m, px, py, pz, r, 0.12F, VIOLET, (190 + 65 * t) * blink, 8, age * 0.15F);
                    // the countdown: a ring closing in on the middle
                    NullFx.ring(vc, m, px, py + 0.003F, pz, r * (1.0F - t) + 0.05F, 0.07F, LILAC, 230 * t, 0, 0.0F);
                } else {
                    float r = (float) NullSentinel.RIFT_RADIUS;
                    if (now < s.at()) {
                        float arm = 1.0F - (s.at() - now - partialTick) / NullSentinel.RIFT_ARM;
                        floorRing(vc, m, px, py, pz, r, 0.07F, VIOLET, 120 + 110 * arm, 12, age * 0.1F);
                    } else {
                        float closing = Mth.clamp((s.until() - now - partialTick) / 10.0F, 0.0F, 1.0F);
                        NullFx.floorGlow(vc, m, px, py - 0.002F, pz, r, VOID, 170 * closing);
                        floorRing(vc, m, px, py, pz, r, 0.13F, VIOLET, 240 * closing, 0, 0.0F);
                        NullFx.ring(vc, m, px, py + 0.003F, pz, r * 0.66F, 0.07F, VIOLET, 210 * closing, 6, age * 0.12F);
                        NullFx.ring(vc, m, px, py + 0.003F, pz, r * 0.33F, 0.07F, LILAC, 210 * closing, 4, -age * 0.18F);
                    }
                }
            }
        }
    }

    public static class Pylon extends MobRenderer<NullPylon, NullPylonModel> {
        private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/null_pylon.png");
        private static final int POINTS = 22;

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

        /** The tether: a thin crackling arc from the crystal to the Sentinel's core, with motes running along it. */
        @Override
        public void render(NullPylon entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            super.render(entity, yaw, partialTick, pose, buffers, light);
            NullSentinel owner = entity.owner();
            if (owner == null || entity.isDeadOrDying()) return;
            Vec3 origin = entity.getPosition(partialTick);
            Vec3 ownerPos = owner.getPosition(partialTick);
            double fx = 0.0, fy = 0.95, fz = 0.0;
            double tx = ownerPos.x - origin.x, ty = ownerPos.y + NullSentinel.CORE_HEIGHT - origin.y, tz = ownerPos.z - origin.z;
            double ddx = tx - fx, ddy = ty - fy, ddz = tz - fz;
            double len = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
            if (len < 0.5) return;
            camera(origin);
            VertexConsumer buf = buffers.getBuffer(NullFx.GLOW);
            Matrix4f m = pose.last().pose();
            float age = entity.tickCount + partialTick;
            int step = entity.tickCount / 2;
            // two sideways axes for the crackle
            double ux = -ddz, uz = ddx, ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1.0E-4) {
                ux = 1.0;
                uz = 0.0;
                ul = 1.0;
            }
            ux /= ul;
            uz /= ul;
            for (int i = 0; i < POINTS; i++) {
                float f = i / (float) (POINTS - 1);
                float env = Mth.sin(f * Mth.PI) * 0.12F;
                float jx = hash(entity.getId(), step, i) * env, jy = hash(entity.getId(), step, i + 101) * env;
                NullFx.X[i] = (float) (fx + ddx * f + ux * jx);
                NullFx.Y[i] = (float) (fy + ddy * f + jy - Mth.sin(f * Mth.PI) * 0.25F);
                NullFx.Z[i] = (float) (fz + ddz * f + uz * jx);
                NullFx.A[i] = 0.75F + 0.25F * Mth.sin(f * 9.0F - age * 0.5F);
                NullFx.W[i] = 1.0F;
            }
            float flicker = 0.75F + 0.25F * Mth.sin(age * 1.1F + entity.getId());
            NullFx.strip(buf, m, POINTS, 0.07F, CYAN, 55 * flicker);
            NullFx.strip(buf, m, POINTS, 0.016F, MINT, 210 * flicker);
            // motes flowing to the Sentinel
            for (int k = 0; k < 3; k++) {
                float f = (age * 0.025F + k / 3.0F) % 1.0F;
                int i = Math.min(POINTS - 2, (int) (f * (POINTS - 1)));
                float u = f * (POINTS - 1) - i;
                float x = Mth.lerp(u, NullFx.X[i], NullFx.X[i + 1]), y = Mth.lerp(u, NullFx.Y[i], NullFx.Y[i + 1]), z = Mth.lerp(u, NullFx.Z[i], NullFx.Z[i + 1]);
                float a = Mth.sin(f * Mth.PI);
                NullFx.dot(buf, m, x, y, z, 0.1F, CYAN, 120 * a);
                NullFx.dot(buf, m, x, y, z, 0.04F, MINT, 230 * a);
            }
            NullFx.dot(buf, m, (float) fx, (float) fy, (float) fz, 0.3F, CYAN, 80 * flicker);
        }
    }
}
