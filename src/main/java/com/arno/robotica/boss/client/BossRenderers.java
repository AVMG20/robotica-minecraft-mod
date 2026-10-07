package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.ForgeTyrant;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.arno.robotica.boss.entity.ScrapDrone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

/** Renderers of the boss module: the bosses and drones get a full-bright glow layer (cores, eyes, vents, magma seams). */
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

    public static class Tyrant extends MobRenderer<ForgeTyrant, ForgeTyrantModel> {
        private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/forge_tyrant.png");

        public Tyrant(EntityRendererProvider.Context ctx) {
            super(ctx, new ForgeTyrantModel(ctx.bakeLayer(ForgeTyrantModel.LAYER)), 1.4F);
            addLayer(glow(this, Robotica.id("textures/entity/forge_tyrant_glow.png")));
        }

        @Override
        public ResourceLocation getTextureLocation(ForgeTyrant entity) {
            return TEXTURE;
        }
    }

    /** A tumbling half-size block: thrown scrap (weathered copper) and magma globs (magma block). */
    public static class Chunk<T extends Entity> extends EntityRenderer<T> {
        private final BlockRenderDispatcher blocks;
        private final BlockState look;
        private final boolean bright;

        public Chunk(EntityRendererProvider.Context ctx, BlockState look, boolean bright) {
            super(ctx);
            this.blocks = ctx.getBlockRenderDispatcher();
            this.look = look;
            this.bright = bright;
            this.shadowRadius = 0.25F;
        }

        @Override
        public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float spin = (entity.tickCount + partialTick) * 18.0F;
            pose.pushPose();
            pose.translate(0.0F, 0.3F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(spin * 0.7F));
            pose.mulPose(Axis.XP.rotationDegrees(spin));
            pose.scale(0.55F, 0.55F, 0.55F);
            pose.translate(-0.5F, -0.5F, -0.5F);
            blocks.renderSingleBlock(look, pose, buffers, bright ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }
}
