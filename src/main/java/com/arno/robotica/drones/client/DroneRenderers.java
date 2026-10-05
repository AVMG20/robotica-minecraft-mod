package com.arno.robotica.drones.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.drones.entity.MiningDrone;
import com.arno.robotica.drones.entity.SentryDrone;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
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

        public Renderer(EntityRendererProvider.Context ctx, M model, String name, float shadow) {
            super(ctx, model, shadow);
            this.tier1 = Robotica.id("textures/entity/" + name + ".png");
            this.tier2 = Robotica.id("textures/entity/" + name + "_mk2.png");
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
            return entity.tier() >= 2 ? tier2 : tier1;
        }

        @Override
        protected void setupRotations(T entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
            super.setupRotations(entity, pose, bob, Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()), partialTick, scale);
        }
    }

    public static Renderer<MiningDrone, MiningDroneModel> mining(EntityRendererProvider.Context ctx) {
        return new Renderer<>(ctx, new MiningDroneModel(ctx.bakeLayer(MiningDroneModel.LAYER)), "mining_drone", 0.25F);
    }

    public static Renderer<SentryDrone, SentryDroneModel> sentry(EntityRendererProvider.Context ctx) {
        return new Renderer<>(ctx, new SentryDroneModel(ctx.bakeLayer(SentryDroneModel.LAYER)), "sentry_drone", 0.25F);
    }

    public static Renderer<CourierDrone, CourierDroneModel> courier(EntityRendererProvider.Context ctx) {
        return new Renderer<>(ctx, new CourierDroneModel(ctx.bakeLayer(CourierDroneModel.LAYER)), "courier_drone", 0.25F);
    }
}
