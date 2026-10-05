package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.ScrapChunk;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Renderers of the boss module: the Colossus and its drones get a full-bright glow layer (core, eyes, vents). */
public final class BossRenderers {
    private BossRenderers() {}

    private static <T extends Mob, M extends EntityModel<T>> EyesLayer<T, M> glow(RenderLayerParent<T, M> parent, ResourceLocation texture) {
        RenderType type = RenderType.eyes(texture);
        return new EyesLayer<>(parent) {
            @Override
            public RenderType renderType() {
                return type;
            }
        };
    }

    public static class Colossus extends MobRenderer<ScrapColossus, ScrapColossusModel> {
        private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/scrap_colossus.png");

        public Colossus(EntityRendererProvider.Context ctx) {
            super(ctx, new ScrapColossusModel(ctx.bakeLayer(ScrapColossusModel.LAYER)), 1.3F);
            addLayer(glow(this, Robotica.id("textures/entity/scrap_colossus_glow.png")));
        }

        @Override
        public ResourceLocation getTextureLocation(ScrapColossus entity) {
            return TEXTURE;
        }
    }

    public static class Drone extends MobRenderer<ScrapDrone, ScrapDroneModel> {
        private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/scrap_drone.png");

        public Drone(EntityRendererProvider.Context ctx) {
            super(ctx, new ScrapDroneModel(ctx.bakeLayer(ScrapDroneModel.LAYER)), 0.25F);
            addLayer(glow(this, Robotica.id("textures/entity/scrap_drone_glow.png")));
        }

        @Override
        public ResourceLocation getTextureLocation(ScrapDrone entity) {
            return TEXTURE;
        }
    }

    /** A tumbling half-size weathered copper block. */
    public static class Chunk extends EntityRenderer<ScrapChunk> {
        private final BlockRenderDispatcher blocks;

        public Chunk(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.blocks = ctx.getBlockRenderDispatcher();
            this.shadowRadius = 0.25F;
        }

        @Override
        public void render(ScrapChunk entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float spin = (entity.tickCount + partialTick) * 18.0F;
            pose.pushPose();
            pose.translate(0.0F, 0.3F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(spin * 0.7F));
            pose.mulPose(Axis.XP.rotationDegrees(spin));
            pose.scale(0.55F, 0.55F, 0.55F);
            pose.translate(-0.5F, -0.5F, -0.5F);
            blocks.renderSingleBlock(ScrapChunk.LOOK, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ScrapChunk entity) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }
}
