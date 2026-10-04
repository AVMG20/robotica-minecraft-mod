package com.arno.robotica.architect.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.entity.BuilderDrone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renders the cosmetic builder drone as a small bobbing cube. */
public class BuilderDroneRenderer extends EntityRenderer<BuilderDrone> {
    private static final ResourceLocation TEXTURE = Robotica.id("textures/entity/builder_drone.png");
    private final BuilderDroneModel model;

    public BuilderDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new BuilderDroneModel(context.bakeLayer(BuilderDroneModel.LAYER));
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(BuilderDrone entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight) {
        pose.pushPose();
        float age = entity.tickCount + partialTick;
        pose.translate(0.0F, 0.2F + 0.04F * (float) Math.sin(age * 0.2F), 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        model.setupAnim(entity, 0.0F, 0.0F, age, 0.0F, 0.0F);
        model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BuilderDrone entity) {
        return TEXTURE;
    }
}
