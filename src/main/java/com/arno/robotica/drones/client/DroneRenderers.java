package com.arno.robotica.drones.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.drones.entity.HaulerDrone;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renderers of both drones: the model faces the entity's yaw (not the movement direction) and a glow layer lights the lamp and eye. */
public final class DroneRenderers {
    private DroneRenderers() {}

    /** Shared renderer: yaw from the entity rotation, tier dependent texture, full bright glow layer. */
    public static class Renderer<T extends DroneBase, M extends EntityModel<T>> extends MobRenderer<T, M> {
        private final ResourceLocation tier1;
        private final ResourceLocation tier2;
        private final ResourceLocation tier3;

        public Renderer(EntityRendererProvider.Context ctx, M model, String name, float shadow) {
            this(ctx, model, name, shadow, false);
        }

        /** {@code mk3}: the drone has its own Mk3 texture ({@code <name>_mk3.png}), else Mk3 looks like Mk2. */
        public Renderer(EntityRendererProvider.Context ctx, M model, String name, float shadow, boolean mk3) {
            super(ctx, model, shadow);
            this.tier1 = Robotica.id("textures/entity/" + name + ".png");
            this.tier2 = Robotica.id("textures/entity/" + name + "_mk2.png");
            this.tier3 = mk3 ? Robotica.id("textures/entity/" + name + "_mk3.png") : tier2;
            ResourceLocation glow = Robotica.id("textures/entity/" + name + "_glow.png");
            addLayer(new EyesLayer<T, M>(this) {
                @Override
                public RenderType renderType() {
                    return RenderType.eyes(glow);
                }
            });
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return entity.tier() >= 3 ? tier3 : entity.tier() >= 2 ? tier2 : tier1;
        }

        @Override
        protected void setupRotations(T entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
            super.setupRotations(entity, pose, bob, Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()), partialTick, scale);
        }
    }

    public static Renderer<MiningDrone, MiningDroneModel> mining(EntityRendererProvider.Context ctx) {
        return new Renderer<>(ctx, new MiningDroneModel(ctx.bakeLayer(MiningDroneModel.LAYER)), "mining_drone", 0.25F, true);
    }

    public static Renderer<SentryDrone, SentryDroneModel> sentry(EntityRendererProvider.Context ctx) {
        return new Renderer<>(ctx, new SentryDroneModel(ctx.bakeLayer(SentryDroneModel.LAYER)), "sentry_drone", 0.25F);
    }

    public static Renderer<HaulerDrone, HaulerDroneModel> hauler(EntityRendererProvider.Context ctx) {
        return new HaulerRenderer(ctx);
    }

    /** Hauler: extra status lights glow while it carries a mob. */
    public static class HaulerRenderer extends Renderer<HaulerDrone, HaulerDroneModel> {
        private static final ResourceLocation ACTIVE = Robotica.id("textures/entity/hauler_drone_glow_active.png");

        public HaulerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new HaulerDroneModel(ctx.bakeLayer(HaulerDroneModel.LAYER)), "hauler_drone", 0.25F);
            addLayer(new RenderLayer<>(this) {
                @Override
                public void render(PoseStack pose, MultiBufferSource buffers, int light, HaulerDrone drone, float limbSwing, float limbSwingAmount,
                                   float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                    if (!drone.isActive()) return;
                    getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(ACTIVE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
            });
        }
    }

    public static Renderer<CourierDrone, CourierDroneModel> courier(EntityRendererProvider.Context ctx) {
        return new Renderer<>(ctx, new CourierDroneModel(ctx.bakeLayer(CourierDroneModel.LAYER)), "courier_drone", 0.25F);
    }
}
