package com.arno.robotica.drones.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.BuddyBot;
import com.arno.robotica.drones.entity.CourierDrone;
import com.arno.robotica.drones.entity.DroneBase;
import com.arno.robotica.drones.entity.HaulerDrone;
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

/** Drone renderers: the model faces the entity's yaw (not the movement direction) and a glow layer lights the lamps and eyes. */
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

    public static BuddyRenderer buddy(EntityRendererProvider.Context ctx) {
        return new BuddyRenderer(ctx);
    }

    /**
     * Buddy Bot: one texture per tier (Mk1 to Mk4), a full bright trim layer per tier (nozzle, muzzle, Mk3/Mk4 seams) and
     * a full bright eye tinted by stance (aggressive red, defense cyan, passive green) that dims and flickers on low energy.
     */
    public static class BuddyRenderer extends MobRenderer<BuddyBot, BuddyBotModel> {
        private static final ResourceLocation[] TEXTURES = {
                Robotica.id("textures/entity/buddy_bot.png"), Robotica.id("textures/entity/buddy_bot_mk2.png"),
                Robotica.id("textures/entity/buddy_bot_mk3.png"), Robotica.id("textures/entity/buddy_bot_mk4.png")};
        /** Mk1 and Mk2 share the plain glow sheet. */
        private static final ResourceLocation[] GLOW = {
                Robotica.id("textures/entity/buddy_bot_glow.png"), Robotica.id("textures/entity/buddy_bot_glow.png"),
                Robotica.id("textures/entity/buddy_bot_mk3_glow.png"), Robotica.id("textures/entity/buddy_bot_mk4_glow.png")};
        /** Indexed by {@link BuddyBot.Stance} ordinal. */
        private static final ResourceLocation[] EYES = {
                Robotica.id("textures/entity/buddy_bot_eye_aggressive.png"), Robotica.id("textures/entity/buddy_bot_eye_defense.png"),
                Robotica.id("textures/entity/buddy_bot_eye_passive.png")};
        private static final int EYE_DIM = 0xFF707070;
        private static final int EYE_FLICKER = 0xFF303030;

        public BuddyRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new BuddyBotModel(ctx.bakeLayer(BuddyBotModel.LAYER)), 0.3F);
            RenderType[] glow = new RenderType[GLOW.length];
            for (int i = 0; i < GLOW.length; i++) glow[i] = RenderType.eyes(GLOW[i]);
            RenderType[] eyes = new RenderType[EYES.length];
            for (int i = 0; i < EYES.length; i++) eyes[i] = RenderType.eyes(EYES[i]);
            addLayer(new RenderLayer<>(this) {
                @Override
                public void render(PoseStack pose, MultiBufferSource buffers, int light, BuddyBot bot, float limbSwing, float limbSwingAmount,
                                   float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                    if (bot.isInvisible()) return;
                    BuddyBotModel model = getParentModel();
                    model.renderToBuffer(pose, buffers.getBuffer(glow[tierIndex(bot)]), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    int color = -1;
                    if (bot.isLowEnergy()) color = ((int) ageInTicks + bot.getId()) % 23 < 2 ? EYE_FLICKER : EYE_DIM;
                    int stance = Mth.clamp(bot.stance().ordinal(), 0, eyes.length - 1);
                    model.renderToBuffer(pose, buffers.getBuffer(eyes[stance]), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
                }
            });
        }

        private static int tierIndex(BuddyBot bot) {
            return Mth.clamp(bot.tier(), 1, TEXTURES.length) - 1;
        }

        @Override
        public ResourceLocation getTextureLocation(BuddyBot bot) {
            return TEXTURES[tierIndex(bot)];
        }

        @Override
        protected void setupRotations(BuddyBot bot, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
            super.setupRotations(bot, pose, bob, Mth.rotLerp(partialTick, bot.yRotO, bot.getYRot()), partialTick, scale);
        }
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
