package com.arno.robotica.automation.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.automation.rancher.Rancher;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Small farmer robot: boxy body with a feed tank on the back, a round-eyed head under a wide straw hat, two short arms
 * (the right one holds the tool) and stubby legs. Texture 64x64, layout in scripts/textures/rancher.py.
 */
public class RancherModel extends EntityModel<Rancher> implements ArmedModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("rancher"), "main");

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public RancherModel(ModelPart root) {
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F), PartPose.offset(0.0F, 11.0F, 0.0F));
        head.addOrReplaceChild("brim", CubeListBuilder.create()
                .texOffs(0, 44).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 1.0F, 10.0F), PartPose.ZERO);
        head.addOrReplaceChild("crown", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-3.0F, -8.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 12).addBox(-4.0F, 0.0F, -2.5F, 8.0F, 7.0F, 5.0F), PartPose.offset(0.0F, 11.0F, 0.0F));
        body.addOrReplaceChild("tank", CubeListBuilder.create()
                .texOffs(28, 24).addBox(-3.0F, 1.0F, 2.5F, 6.0F, 5.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(-5.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(48, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(5.0F, 12.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offset(-2.0F, 18.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(12, 24).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offset(2.0F, 18.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Rancher rancher, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        float walk = limbSwing * 0.6662F;
        rightLeg.xRot = Mth.cos(walk) * 1.4F * limbSwingAmount;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4F * limbSwingAmount;
        rightArm.xRot = Mth.cos(walk + Mth.PI) * 0.8F * limbSwingAmount;
        leftArm.xRot = Mth.cos(walk) * 0.8F * limbSwingAmount;
        rightArm.yRot = 0.0F;
        rightArm.zRot = 0.05F;
        leftArm.zRot = -0.05F - Mth.sin(ageInTicks * 0.067F) * 0.03F;
        if (!rancher.getMainHandItem().isEmpty()) rightArm.xRot = rightArm.xRot * 0.5F - Mth.PI / 8.0F;
        if (attackTime > 0.0F) {
            float swing = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot -= swing * 1.3F;
            rightArm.yRot = -swing * 0.3F;
            body.yRot = Mth.sin(Mth.sqrt(attackTime) * Mth.TWO_PI) * 0.15F;
        } else {
            body.yRot = 0.0F;
        }
    }

    @Override
    public void translateToHand(HumanoidArm side, PoseStack pose) {
        (side == HumanoidArm.LEFT ? leftArm : rightArm).translateAndRotate(pose);
        // the item layer places items 10 px down a vanilla arm; this arm is 7 px long and the robot is small
        pose.translate(0.0F, -1.5F / 16.0F, 0.0F);
        pose.scale(0.75F, 0.75F, 0.75F);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        head.render(pose, buffer, packedLight, packedOverlay, color);
        body.render(pose, buffer, packedLight, packedOverlay, color);
        rightArm.render(pose, buffer, packedLight, packedOverlay, color);
        leftArm.render(pose, buffer, packedLight, packedOverlay, color);
        rightLeg.render(pose, buffer, packedLight, packedOverlay, color);
        leftLeg.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
