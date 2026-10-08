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
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Small farmer robot: boxy body with a feed tank on the back and a status lamp on the chest, a round-eyed head under a straw
 * hat, two short arms (the right one holds the tool) and stubby legs. Mk2 has a wider brim, twin tanks, an antenna with a
 * second lamp and a badge. Idle: breathing bob, tank and antenna wobble, blinking. Each job has its own pose. Texture 64x64,
 * layout in scripts/textures/rancher.py.
 */
public class RancherModel extends EntityModel<Rancher> implements ArmedModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("rancher"), "main");
    private static final float TOP = 11.0F, ARM_Y = 12.0F, LEG_Y = 18.0F, LEG_LEN = 6.0F;

    private final ModelPart head;
    private final ModelPart brim;
    private final ModelPart bigBrim;
    private final ModelPart eyelid;
    private final ModelPart antenna;
    private final ModelPart body;
    private final ModelPart tank;
    private final ModelPart twinTanks;
    private final ModelPart badge;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private float partial;

    public RancherModel(ModelPart root) {
        this.head = root.getChild("head");
        this.brim = head.getChild("brim");
        this.bigBrim = head.getChild("big_brim");
        this.eyelid = head.getChild("eyelid");
        this.antenna = head.getChild("antenna");
        this.body = root.getChild("body");
        this.tank = body.getChild("tank");
        this.twinTanks = body.getChild("twin_tanks");
        this.badge = body.getChild("badge");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 5.0F, 6.0F), PartPose.offset(0.0F, TOP, 0.0F));
        head.addOrReplaceChild("brim", CubeListBuilder.create()
                .texOffs(0, 44).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 1.0F, 10.0F), PartPose.ZERO);
        head.addOrReplaceChild("big_brim", CubeListBuilder.create()
                .texOffs(0, 44).addBox(-5.0F, -6.0F, -5.0F, 10.0F, 1.0F, 10.0F, new CubeDeformation(1.5F, 0.0F, 1.5F)), PartPose.ZERO);
        head.addOrReplaceChild("crown", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-3.0F, -8.0F, -3.0F, 6.0F, 2.0F, 6.0F), PartPose.ZERO);
        head.addOrReplaceChild("eyelid", CubeListBuilder.create()
                .texOffs(26, 16).addBox(-3.0F, -4.0F, -3.2F, 6.0F, 2.0F, 1.0F), PartPose.ZERO);
        PartDefinition antenna = head.addOrReplaceChild("antenna", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offset(2.0F, -8.0F, 1.0F));
        antenna.addOrReplaceChild("bulb", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 12).addBox(-4.0F, 0.0F, -2.5F, 8.0F, 7.0F, 5.0F)
                .texOffs(48, 6).addBox(-3.5F, 0.5F, -3.5F, 2.0F, 1.0F, 1.0F), PartPose.offset(0.0F, TOP, 0.0F));
        body.addOrReplaceChild("tank", CubeListBuilder.create()
                .texOffs(28, 24).addBox(-3.0F, 0.0F, -1.0F, 6.0F, 5.0F, 2.0F), PartPose.offset(0.0F, 1.0F, 3.5F));
        body.addOrReplaceChild("twin_tanks", CubeListBuilder.create()
                .texOffs(44, 24).addBox(-3.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F)
                .texOffs(44, 24).addBox(0.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offset(0.0F, 0.5F, 4.0F));
        body.addOrReplaceChild("badge", CubeListBuilder.create()
                .texOffs(26, 12).addBox(2.0F, 1.0F, -3.0F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(-5.0F, ARM_Y, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(48, 12).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(5.0F, ARM_Y, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offset(-2.0F, LEG_Y, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(12, 24).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 6.0F, 3.0F), PartPose.offset(2.0F, LEG_Y, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void prepareMobModel(Rancher rancher, float limbSwing, float limbSwingAmount, float partialTick) {
        partial = partialTick;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    @Override
    public void setupAnim(Rancher rancher, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean mk2 = rancher.tier() >= 2;
        brim.visible = !mk2;
        bigBrim.visible = mk2;
        antenna.visible = mk2;
        tank.visible = !mk2;
        twinTanks.visible = mk2;
        badge.visible = mk2;

        // walk
        float walk = limbSwing * 0.6662F;
        float still = 1.0F - Mth.clamp(limbSwingAmount * 2.0F, 0.0F, 1.0F);
        rightLeg.xRot = Mth.cos(walk) * 1.4F * limbSwingAmount;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4F * limbSwingAmount;
        rightLeg.y = leftLeg.y = LEG_Y;
        rightArm.xRot = Mth.cos(walk + Mth.PI) * 0.8F * limbSwingAmount;
        leftArm.xRot = Mth.cos(walk) * 0.8F * limbSwingAmount;
        rightArm.yRot = leftArm.yRot = 0.0F;
        rightArm.zRot = 0.05F;
        leftArm.zRot = -0.05F - Mth.sin(ageInTicks * 0.067F) * 0.03F;
        if (!rancher.getMainHandItem().isEmpty()) rightArm.xRot = rightArm.xRot * 0.5F - Mth.PI / 8.0F;

        // idle: breathing bob, head tilt, tank and antenna wobble, blink
        float breathe = Mth.sin(ageInTicks * 0.1F) * 0.35F * still;
        float stepBob = Math.abs(Mth.cos(walk)) * 0.6F * limbSwingAmount;
        float lift = breathe - stepBob;
        head.y = TOP + lift;
        body.y = TOP + lift * 0.5F;
        rightArm.y = leftArm.y = ARM_Y + lift * 0.5F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        head.zRot = Mth.sin(ageInTicks * 0.05F) * 0.04F * still;
        tank.zRot = twinTanks.zRot = Mth.sin(ageInTicks * 0.15F) * 0.03F + Mth.cos(walk) * 0.12F * limbSwingAmount;
        tank.xRot = twinTanks.xRot = 0.08F * limbSwingAmount;
        antenna.zRot = Mth.sin(ageInTicks * 0.22F) * 0.1F + Mth.cos(walk) * 0.25F * limbSwingAmount;
        antenna.xRot = 0.2F * limbSwingAmount;
        eyelid.visible = Math.floorMod((int) ageInTicks + rancher.getId() * 37, 90) < 3;
        body.yRot = 0.0F;

        Rancher.Kind kind = rancher.actionKind;
        if (kind == null) {
            if (attackTime > 0.0F) {
                float swing = Mth.sin(attackTime * Mth.PI);
                rightArm.xRot -= swing * 1.3F;
                rightArm.yRot = -swing * 0.3F;
                body.yRot = Mth.sin(Mth.sqrt(attackTime) * Mth.TWO_PI) * 0.15F;
            }
            return;
        }
        float p = 1.0F - Mth.clamp((rancher.actionTicks - partial) / Rancher.ACTION_TICKS, 0.0F, 1.0F);
        float e = smooth(Math.min(p / 0.2F, (1.0F - p) / 0.25F));
        switch (kind) {
            case FEED -> {
                // arm held out low with the feed, head down
                rightArm.xRot = Mth.lerp(e, rightArm.xRot, -0.95F);
                rightArm.yRot = 0.15F * e;
                head.xRot += 0.45F * e;
                crouch(0.6F * e);
            }
            case SHEAR -> {
                // both hands forward, snipping
                float snip = Mth.sin(ageInTicks * 1.6F) * 0.14F * e;
                rightArm.xRot = Mth.lerp(e, rightArm.xRot, -1.15F);
                leftArm.xRot = Mth.lerp(e, leftArm.xRot, -1.15F);
                rightArm.yRot = -0.35F * e;
                leftArm.yRot = 0.35F * e;
                rightArm.zRot += snip;
                leftArm.zRot -= snip;
                head.xRot += 0.3F * e;
            }
            case MILK -> {
                // crouched, both arms low, pumping
                float pump = Mth.sin(ageInTicks * 0.9F) * 0.2F * e;
                rightArm.xRot = Mth.lerp(e, rightArm.xRot, -0.7F) + pump;
                leftArm.xRot = Mth.lerp(e, leftArm.xRot, -0.7F) - pump;
                head.xRot += 0.35F * e;
                crouch(1.4F * e);
            }
            case CULL -> {
                // overhead swing: quick wind-up, strike, slow recovery
                float raised = -2.8F, struck = -0.45F, rest = rightArm.xRot;
                float arm;
                if (p < 0.15F) arm = Mth.lerp(smooth(p / 0.15F), rest, raised);
                else if (p < 0.3F) arm = Mth.lerp(smooth((p - 0.15F) / 0.15F), raised, struck);
                else arm = Mth.lerp(smooth((p - 0.3F) / 0.7F), struck, rest);
                rightArm.xRot = arm;
                rightArm.zRot = 0.05F + 0.15F * e;
                body.yRot = (p < 0.15F ? -0.25F * smooth(p / 0.15F) : 0.2F * e);
                head.xRot += 0.2F * e;
            }
        }
    }

    /** Lowers head, body and arms by {@code dy} px and folds the legs forward so the feet stay on the ground. */
    private void crouch(float dy) {
        if (dy <= 0.0F) return;
        head.y += dy;
        body.y += dy;
        rightArm.y += dy;
        leftArm.y += dy;
        rightLeg.y = leftLeg.y = LEG_Y + dy;
        float fold = (float) Math.acos(Mth.clamp((LEG_LEN - dy) / LEG_LEN, 0.0F, 1.0F));
        rightLeg.xRot = -fold;
        leftLeg.xRot = -fold;
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
