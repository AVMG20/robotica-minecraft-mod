package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.ScrapColossus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Scrap Colossus: stubby legs, a wide rusted copper torso with a furnace core behind a hatch, long gorilla arms with heavy
 * fists, a small visor head and two smoke stacks. About 47 px (3 blocks) tall. Texture 128x128, box layout in
 * scripts/textures/boss.py. Animations are plain keyframe math on the synced action and its age.
 */
public class ScrapColossusModel extends EntityModel<ScrapColossus> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("scrap_colossus"), "main");
    private static final float BODY_Y = 4.0F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart hatch;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ScrapColossusModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.hatch = body.getChild("hatch");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-11.0F, -23.0F, -7.0F, 22.0F, 20.0F, 14.0F)      // torso
                        .texOffs(0, 34).addBox(-8.0F, -3.0F, -5.0F, 16.0F, 5.0F, 10.0F)        // pelvis
                        .texOffs(24, 76).addBox(-4.0F, -16.0F, -7.5F, 8.0F, 6.0F, 1.0F)        // furnace core
                        .texOffs(92, 49).addBox(-9.0F, -31.0F, 2.0F, 3.0F, 8.0F, 3.0F)         // smoke stacks
                        .texOffs(92, 49).mirror().addBox(6.0F, -31.0F, 2.0F, 3.0F, 8.0F, 3.0F),
                PartPose.offset(0.0F, BODY_Y, 0.0F));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(72, 0).addBox(-4.5F, -7.0F, -4.5F, 9.0F, 7.0F, 9.0F),
                PartPose.offset(0.0F, -23.0F, -3.0F));
        body.addOrReplaceChild("hatch", CubeListBuilder.create()
                        .texOffs(0, 76).addBox(-10.0F, -4.0F, -1.0F, 10.0F, 8.0F, 1.0F),
                PartPose.offset(5.0F, -13.0F, -7.0F));
        CubeListBuilder arm = CubeListBuilder.create()
                .texOffs(28, 49).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 18.0F, 7.0F)
                .texOffs(56, 49).addBox(-4.5F, 16.0F, -4.5F, 9.0F, 8.0F, 9.0F)
                .texOffs(72, 16).addBox(-5.0F, -4.0F, -5.0F, 10.0F, 5.0F, 10.0F);
        CubeListBuilder armMirror = CubeListBuilder.create().mirror()
                .texOffs(28, 49).addBox(-3.5F, -2.0F, -3.5F, 7.0F, 18.0F, 7.0F)
                .texOffs(56, 49).addBox(-4.5F, 16.0F, -4.5F, 9.0F, 8.0F, 9.0F)
                .texOffs(72, 16).addBox(-5.0F, -4.0F, -5.0F, 10.0F, 5.0F, 10.0F);
        body.addOrReplaceChild("right_arm", arm, PartPose.offset(-14.5F, -18.0F, 0.0F));
        body.addOrReplaceChild("left_arm", armMirror, PartPose.offset(14.5F, -18.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                        .texOffs(0, 49).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 20.0F, 7.0F),
                PartPose.offset(-5.5F, BODY_Y, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
                        .texOffs(0, 49).addBox(-3.5F, 0.0F, -3.5F, 7.0F, 20.0F, 7.0F),
                PartPose.offset(5.5F, BODY_Y, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void setupAnim(ScrapColossus boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - boss.tickCount;
        float at = boss.actionTicks() + partial;
        float walk = Mth.cos(limbSwing * 0.45F) * limbSwingAmount;

        // Rest pose plus walk cycle.
        body.y = BODY_Y + Math.abs(Mth.sin(limbSwing * 0.45F)) * limbSwingAmount * 1.2F;
        body.xRot = 0.05F;
        body.yRot = 0.0F;
        body.zRot = Mth.sin(limbSwing * 0.45F) * 0.06F * limbSwingAmount;
        rightLeg.xRot = walk * 1.0F;
        leftLeg.xRot = -walk * 1.0F;
        rightLeg.y = leftLeg.y = BODY_Y;
        rightArm.xRot = -walk * 0.6F;
        leftArm.xRot = walk * 0.6F;
        rightArm.zRot = 0.08F;
        leftArm.zRot = -0.08F;
        rightArm.yRot = leftArm.yRot = 0.0F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6F;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        hatch.yRot = 0.0F;

        if (attackTime > 0.0F) {                                   // melee punch with the right fist
            float swing = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot = -1.7F * swing;
            body.yRot = -0.25F * swing;
        }

        switch (boss.action()) {
            case SLAM_WINDUP -> {
                float p = ease(at / ScrapColossus.SLAM_WINDUP);
                float shake = at > ScrapColossus.SLAM_WINDUP * 0.6F ? Mth.sin(ageInTicks * 3.1F) * 0.06F : 0.0F;
                rightArm.xRot = leftArm.xRot = -2.9F * p + shake;
                rightArm.zRot = 0.08F - 0.25F * p;
                leftArm.zRot = -0.08F + 0.25F * p;
                body.xRot = 0.05F - 0.2F * p;
                head.xRot = -0.3F * p;
            }
            case SLAM -> {
                float down = ease(at / 3.0F);
                float rec = ease((at - 4.0F) / (ScrapColossus.SLAM_RECOVER - 4.0F));
                float arms = Mth.lerp(down, -2.9F, -0.7F);
                rightArm.xRot = leftArm.xRot = Mth.lerp(rec, arms, 0.0F);
                body.xRot = Mth.lerp(rec, 0.05F + 0.35F * down, 0.05F);
                body.y = BODY_Y + 1.5F * down * (1.0F - rec);
            }
            case THROW_WINDUP -> {
                float p = ease(at / ScrapColossus.THROW_WINDUP);
                rightArm.xRot = -2.8F * p;
                rightArm.zRot = 0.08F - 0.3F * p;
                body.yRot = 0.3F * p;
                body.xRot = 0.05F - 0.1F * p;
            }
            case THROW -> {
                float p = ease(at / 4.0F);
                float rec = ease((at - 4.0F) / (ScrapColossus.THROW_RECOVER - 4.0F));
                rightArm.xRot = Mth.lerp(rec, Mth.lerp(p, -2.8F, -0.9F), 0.0F);
                body.yRot = Mth.lerp(rec, Mth.lerp(p, 0.3F, -0.25F), 0.0F);
                body.xRot = 0.05F + 0.15F * p * (1.0F - rec);
            }
            case OVERHEAT -> {
                float open = ease(at / 6.0F);
                float close = ease((at - (ScrapColossus.OVERHEAT_TICKS - 6)) / 6.0F);
                hatch.yRot = -1.9F * open * (1.0F - close);
                float shudder = Mth.sin(ageInTicks * 2.3F) * 0.025F;
                body.xRot = 0.05F + 0.2F * open;
                body.zRot = shudder;
                head.xRot = 0.45F * open;
                head.yRot = 0.0F;
                rightArm.xRot = leftArm.xRot = 0.15F * open;
                rightArm.zRot = 0.08F + 0.15F * open;
                leftArm.zRot = -0.08F - 0.15F * open;
                rightLeg.xRot = leftLeg.xRot = 0.0F;
            }
            default -> {
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        body.render(pose, buffer, packedLight, packedOverlay, color);
        rightLeg.render(pose, buffer, packedLight, packedOverlay, color);
        leftLeg.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
