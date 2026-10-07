package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.ForgeTyrant;
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
 * Forge Tyrant: a walking blast furnace. A wide blackstone body with gold trim and two furnace doors over a white-hot
 * core, a big chimney on the back, a small hooded head, a crucible on the right arm (mortar), a hammer on the left (punch
 * and eruptions), two thick legs. About 44 px (2.75 blocks) tall. Texture 128x128, box layout in scripts/textures/boss.py.
 * Animations are keyframe math on the synced action and its age.
 */
public class ForgeTyrantModel extends EntityModel<ForgeTyrant> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("forge_tyrant"), "main");
    private static final float BODY_Y = 12.0F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart doorLeft;
    private final ModelPart doorRight;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public ForgeTyrantModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.doorLeft = body.getChild("door_left");
        this.doorRight = body.getChild("door_right");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-12.0F, -20.0F, -9.0F, 24.0F, 20.0F, 18.0F)      // furnace body
                        .texOffs(86, 16).addBox(-4.0F, -32.0F, 1.0F, 8.0F, 12.0F, 8.0F)        // chimney
                        .texOffs(20, 82).addBox(-6.0F, -17.0F, -9.3F, 12.0F, 10.0F, 1.0F),     // white-hot core
                PartPose.offset(0.0F, BODY_Y, 0.0F));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(86, 0).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 6.0F, 10.0F),
                PartPose.offset(0.0F, -20.0F, -4.0F));
        body.addOrReplaceChild("door_left", CubeListBuilder.create()
                        .texOffs(0, 82).addBox(-7.0F, -5.0F, -1.0F, 7.0F, 10.0F, 1.0F),
                PartPose.offset(7.0F, -12.0F, -9.5F));
        body.addOrReplaceChild("door_right", CubeListBuilder.create().mirror()
                        .texOffs(0, 82).addBox(0.0F, -5.0F, -1.0F, 7.0F, 10.0F, 1.0F),
                PartPose.offset(-7.0F, -12.0F, -9.5F));
        body.addOrReplaceChild("right_arm", CubeListBuilder.create()
                        .texOffs(72, 40).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 14.0F, 6.0F)      // arm
                        .texOffs(0, 62).addBox(-4.5F, 12.0F, -4.5F, 9.0F, 7.0F, 9.0F)        // crucible
                        .texOffs(78, 62).addBox(-4.5F, -4.0F, -4.5F, 9.0F, 5.0F, 9.0F),      // shoulder
                PartPose.offset(-16.0F, -16.0F, 0.0F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
                        .texOffs(72, 40).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 14.0F, 6.0F)      // arm
                        .texOffs(36, 62).addBox(-5.0F, 12.0F, -5.0F, 10.0F, 8.0F, 10.0F)     // hammer
                        .texOffs(78, 62).addBox(-4.5F, -4.0F, -4.5F, 9.0F, 5.0F, 9.0F),      // shoulder
                PartPose.offset(16.0F, -16.0F, 0.0F));
        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(0, 40).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 12.0F, 8.0F)
                .texOffs(32, 40).addBox(-5.0F, 8.0F, -6.0F, 10.0F, 4.0F, 10.0F);
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-6.5F, BODY_Y, 0.0F));
        root.addOrReplaceChild("left_leg", leg, PartPose.offset(6.5F, BODY_Y, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void setupAnim(ForgeTyrant boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - boss.tickCount;
        float at = boss.actionTicks() + partial;
        float walk = Mth.cos(limbSwing * 0.4F) * limbSwingAmount;

        // Rest pose plus a heavy walk cycle.
        body.y = BODY_Y + Math.abs(Mth.sin(limbSwing * 0.4F)) * limbSwingAmount * 1.0F;
        body.xRot = 0.0F;
        body.yRot = 0.0F;
        body.zRot = Mth.sin(limbSwing * 0.4F) * 0.05F * limbSwingAmount;
        rightLeg.xRot = walk * 0.9F;
        leftLeg.xRot = -walk * 0.9F;
        rightArm.xRot = -walk * 0.5F;
        leftArm.xRot = walk * 0.5F;
        rightArm.zRot = 0.1F;
        leftArm.zRot = -0.1F;
        rightArm.yRot = leftArm.yRot = 0.0F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5F;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        // a breathing glow: the doors stand a crack open
        float idle = 0.06F + Mth.sin(ageInTicks * 0.08F) * 0.04F;
        doorLeft.yRot = -idle;
        doorRight.yRot = idle;

        if (attackTime > 0.0F) {                                   // hammer punch with the left arm
            float swing = Mth.sin(attackTime * Mth.PI);
            leftArm.xRot = -1.8F * swing;
            body.yRot = 0.25F * swing;
        }

        switch (boss.action()) {
            case BREATH_WINDUP -> {
                float p = ease(at / ForgeTyrant.BREATH_WINDUP);
                body.xRot = -0.15F * p;
                head.xRot = -0.35F * p;
                doorLeft.yRot = -0.5F * p;
                doorRight.yRot = 0.5F * p;
                rightArm.zRot = 0.1F + 0.35F * p;
                leftArm.zRot = -0.1F - 0.35F * p;
            }
            case BREATH -> {
                float shake = Mth.sin(ageInTicks * 2.7F) * 0.03F;
                body.xRot = 0.2F + shake;
                head.xRot = 0.1F;
                doorLeft.yRot = -1.1F;
                doorRight.yRot = 1.1F;
                rightArm.zRot = 0.45F;
                leftArm.zRot = -0.45F;
            }
            case MORTAR_WINDUP -> {
                float p = ease(at / ForgeTyrant.MORTAR_WINDUP);
                rightArm.xRot = 0.9F * p;
                rightArm.zRot = 0.1F + 0.2F * p;
                body.yRot = -0.3F * p;
            }
            case MORTAR -> {
                float p = ease(at / 4.0F);
                float rec = ease((at - 4.0F) / (ForgeTyrant.MORTAR_RECOVER - 4.0F));
                rightArm.xRot = Mth.lerp(rec, Mth.lerp(p, 0.9F, -2.6F), 0.0F);
                body.yRot = Mth.lerp(rec, Mth.lerp(p, -0.3F, 0.2F), 0.0F);
            }
            case ERUPT_WINDUP -> {
                float p = ease(at / ForgeTyrant.ERUPT_WINDUP);
                float shake = at > ForgeTyrant.ERUPT_WINDUP * 0.6F ? Mth.sin(ageInTicks * 3.1F) * 0.05F : 0.0F;
                leftArm.xRot = -3.0F * p + shake;
                leftArm.zRot = -0.1F + 0.2F * p;
                body.xRot = -0.15F * p;
                body.yRot = 0.15F * p;
            }
            case ERUPT -> {
                float down = ease(at / 3.0F);
                float rec = ease((at - 4.0F) / (ForgeTyrant.ERUPT_RECOVER - 4.0F));
                leftArm.xRot = Mth.lerp(rec, Mth.lerp(down, -3.0F, -0.5F), 0.0F);
                body.xRot = Mth.lerp(rec, 0.3F * down, 0.0F);
                body.y = BODY_Y + 1.5F * down * (1.0F - rec);
            }
            case VENT -> {
                float open = ease(at / 6.0F);
                float shudder = Mth.sin(ageInTicks * 2.3F) * 0.025F;
                doorLeft.yRot = -1.9F * open;
                doorRight.yRot = 1.9F * open;
                body.xRot = 0.25F * open;
                body.zRot = shudder;
                head.xRot = 0.5F * open;
                head.yRot = 0.0F;
                rightArm.xRot = leftArm.xRot = 0.2F * open;
                rightArm.zRot = 0.1F + 0.2F * open;
                leftArm.zRot = -0.1F - 0.2F * open;
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
