package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.NullSentinel;
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
 * Null Sentinel: a hovering relay robot. A null steel hull with purpur trim and two chest plates over the antigrav core,
 * a lens head with an antenna, floating pauldrons and arms that end in emitter claws, a gyro ring of plates spinning on
 * its back and a thruster keel instead of legs. About 51 px tall, drawn 1.25x (BossRenderers). Texture 128x128, box
 * layout in scripts/textures/boss.py. Animations are keyframe math on the synced action and its age.
 */
public class NullSentinelModel extends EntityModel<NullSentinel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("null_sentinel"), "main");
    private static final float BODY_Y = 4.0F;
    private static final int RING_PLATES = 8;
    private static final float RING_RADIUS = 11.0F;
    private static final float ARM_X = 13.0F;

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart plateLeft;
    private final ModelPart plateRight;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightPauldron;
    private final ModelPart leftPauldron;
    private final ModelPart keel;
    private final ModelPart ring;
    private final ModelPart[] plates = new ModelPart[RING_PLATES];

    public NullSentinelModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.plateLeft = body.getChild("plate_left");
        this.plateRight = body.getChild("plate_right");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightPauldron = body.getChild("right_pauldron");
        this.leftPauldron = body.getChild("left_pauldron");
        this.keel = body.getChild("keel");
        this.ring = body.getChild("ring");
        for (int i = 0; i < RING_PLATES; i++) plates[i] = ring.getChild("plate" + i);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-9.0F, -16.0F, -6.0F, 18.0F, 16.0F, 12.0F)       // hull
                        .texOffs(100, 8).addBox(-4.0F, -12.0F, -6.4F, 8.0F, 8.0F, 1.0F),       // antigrav core
                PartPose.offset(0.0F, BODY_Y, 0.0F));
        body.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(64, 0).addBox(-4.5F, -9.0F, -4.5F, 9.0F, 9.0F, 9.0F)          // lens head
                        .texOffs(100, 0).addBox(-2.5F, -6.5F, -5.2F, 5.0F, 5.0F, 1.0F)         // lens
                        .texOffs(112, 0).addBox(2.0F, -15.0F, 1.0F, 1.0F, 6.0F, 1.0F),          // antenna
                PartPose.offset(0.0F, -16.0F, 0.0F));
        body.addOrReplaceChild("plate_left", CubeListBuilder.create()
                        .texOffs(118, 0).addBox(-4.0F, -4.0F, -1.0F, 4.0F, 8.0F, 1.0F),
                PartPose.offset(4.0F, -8.0F, -6.4F));
        body.addOrReplaceChild("plate_right", CubeListBuilder.create().mirror()
                        .texOffs(118, 0).addBox(0.0F, -4.0F, -1.0F, 4.0F, 8.0F, 1.0F),
                PartPose.offset(-4.0F, -8.0F, -6.4F));
        CubeListBuilder pauldron = CubeListBuilder.create().texOffs(0, 48).addBox(-3.5F, -3.0F, -4.0F, 7.0F, 6.0F, 8.0F);
        body.addOrReplaceChild("right_pauldron", pauldron, PartPose.offset(-ARM_X, -14.0F, 0.0F));
        body.addOrReplaceChild("left_pauldron", pauldron, PartPose.offset(ARM_X, -14.0F, 0.0F));
        CubeListBuilder arm = CubeListBuilder.create()
                .texOffs(32, 48).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F)       // upper arm
                .texOffs(52, 48).addBox(-3.0F, 10.0F, -3.0F, 6.0F, 8.0F, 6.0F)      // emitter
                .texOffs(76, 48).addBox(-2.5F, 18.0F, -1.0F, 2.0F, 5.0F, 2.0F)      // claws
                .texOffs(76, 48).addBox(0.5F, 18.0F, -1.0F, 2.0F, 5.0F, 2.0F);
        body.addOrReplaceChild("right_arm", arm, PartPose.offset(-ARM_X, -10.0F, 0.0F));
        body.addOrReplaceChild("left_arm", arm, PartPose.offset(ARM_X, -10.0F, 0.0F));
        body.addOrReplaceChild("keel", CubeListBuilder.create()
                        .texOffs(0, 30).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 6.0F, 10.0F)
                        .texOffs(40, 30).addBox(-3.0F, 6.0F, -3.0F, 6.0F, 6.0F, 6.0F)
                        .texOffs(64, 30).addBox(-1.5F, 12.0F, -1.5F, 3.0F, 4.0F, 3.0F),     // thruster tip
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition ring = body.addOrReplaceChild("ring", CubeListBuilder.create()
                        .texOffs(92, 48).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 2.0F),       // hub
                PartPose.offset(0.0F, -11.0F, 7.0F));
        for (int i = 0; i < RING_PLATES; i++) {
            float a = i * Mth.TWO_PI / RING_PLATES;
            ring.addOrReplaceChild("plate" + i, CubeListBuilder.create()
                            .texOffs(84, 48).addBox(-1.5F, -3.0F, 0.0F, 3.0F, 6.0F, 1.0F),
                    PartPose.offsetAndRotation(Mth.sin(a) * RING_RADIUS, -Mth.cos(a) * RING_RADIUS, 0.5F, 0.0F, 0.0F, a));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public void setupAnim(NullSentinel boss, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partial = ageInTicks - boss.tickCount;
        float at = boss.actionTicks() + partial;
        float bob = Mth.sin(ageInTicks * 0.09F);

        // rest: hovering, arms floating out of step, ring turning
        body.y = BODY_Y + bob * 1.0F;
        body.xRot = 0.08F * limbSwingAmount;
        body.yRot = 0.0F;
        body.zRot = Mth.sin(ageInTicks * 0.05F) * 0.02F;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6F;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.6F;
        head.y = -16.0F;
        float armBob = Mth.sin(ageInTicks * 0.09F + 1.6F) * 0.8F;
        rightArm.x = -ARM_X;
        leftArm.x = ARM_X;
        rightArm.y = -10.0F + armBob;
        leftArm.y = -10.0F - armBob;
        rightArm.z = leftArm.z = 0.0F;
        rightArm.xRot = -0.15F * limbSwingAmount + Mth.sin(ageInTicks * 0.07F) * 0.05F;
        leftArm.xRot = -0.15F * limbSwingAmount - Mth.sin(ageInTicks * 0.07F) * 0.05F;
        rightArm.yRot = leftArm.yRot = 0.0F;
        rightArm.zRot = 0.12F;
        leftArm.zRot = -0.12F;
        rightPauldron.x = -ARM_X;
        leftPauldron.x = ARM_X;
        rightPauldron.y = -14.0F + armBob * 0.5F;
        leftPauldron.y = -14.0F - armBob * 0.5F;
        plateLeft.yRot = plateRight.yRot = 0.0F;
        plateLeft.x = 4.0F;
        plateRight.x = -4.0F;
        keel.y = 0.0F;
        keel.yRot = ageInTicks * 0.04F;
        float ringScale = 1.0F;
        ring.y = -11.0F;
        ring.z = 7.0F;
        ring.xRot = 0.0F;

        if (attackTime > 0.0F) {                                   // claw swipe with the right arm
            float swing = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot = -1.9F * swing;
            rightArm.zRot = 0.12F - 0.6F * swing;
            body.yRot = -0.3F * swing;
        }

        switch (boss.action()) {
            case INTRO -> {
                float t = at / NullSentinel.INTRO_TICKS;
                float rise = ease(t / 0.75F);
                body.y = BODY_Y + 14.0F * (1.0F - rise) + bob * rise;
                head.xRot = 0.9F * (1.0F - ease((t - 0.4F) / 0.4F));
                float gather = ease((t - 0.15F) / 0.6F);
                rightArm.x = -ARM_X - 10.0F * (1.0F - gather);
                leftArm.x = ARM_X + 10.0F * (1.0F - gather);
                rightPauldron.x = -ARM_X - 8.0F * (1.0F - gather);
                leftPauldron.x = ARM_X + 8.0F * (1.0F - gather);
                rightArm.y = leftArm.y = -10.0F + 8.0F * (1.0F - gather);
                ringScale = 0.2F + 0.8F * ease((t - 0.3F) / 0.5F);
                if (t > 0.82F) {                                   // the wake: arms flung out
                    float f = ease((t - 0.82F) / 0.1F) * (1.0F - ease((t - 0.92F) / 0.08F));
                    rightArm.zRot = 0.12F + 1.1F * f;
                    leftArm.zRot = -0.12F - 1.1F * f;
                    body.xRot = -0.25F * f;
                }
            }
            case BEAM_WINDUP -> {
                float p = ease(at / 10.0F);
                float lock = at > NullSentinel.BEAM_WINDUP - NullSentinel.BEAM_LOCK ? Mth.sin(ageInTicks * 2.9F) * 0.03F : 0.0F;
                body.xRot = -0.12F * p;
                rightArm.xRot = leftArm.xRot = -1.35F * p + lock;
                rightArm.zRot = 0.12F - 0.25F * p;
                leftArm.zRot = -0.12F + 0.25F * p;
            }
            case BEAM -> {
                float shake = Mth.sin(ageInTicks * 3.3F) * 0.03F;
                body.xRot = 0.05F + shake;
                rightArm.xRot = leftArm.xRot = -1.45F + shake;
                rightArm.zRot = -0.18F;
                leftArm.zRot = 0.18F;
            }
            case WELL_WINDUP -> {
                float p = ease(at / NullSentinel.WELL_WINDUP);
                rightArm.zRot = 0.12F + 1.35F * p;
                leftArm.zRot = -0.12F - 1.35F * p;
                rightArm.xRot = leftArm.xRot = -0.2F * p;
                body.xRot = -0.18F * p;
                head.xRot = -0.3F * p;
                ringScale = 1.0F + 0.35F * p;
            }
            case WELL -> {
                float p = ease(at / NullSentinel.WELL_TICKS);
                float shake = Mth.sin(ageInTicks * 3.1F) * 0.04F;
                rightArm.zRot = 0.12F + 1.35F * (1.0F - p) + shake;
                leftArm.zRot = -0.12F - 1.35F * (1.0F - p) - shake;
                rightArm.xRot = leftArm.xRot = -0.2F - 1.2F * p;
                body.xRot = -0.18F + 0.3F * p;
                ringScale = 1.35F - 0.35F * p;
            }
            case LAUNCH_WINDUP -> {
                float p = ease(at / NullSentinel.LAUNCH_WINDUP);
                leftArm.xRot = -2.9F * p;
                leftArm.zRot = -0.12F + 0.2F * p;
                body.zRot = 0.08F * p;
                body.yRot = 0.15F * p;
            }
            case LAUNCH -> {
                float rec = ease((at - 3.0F) / (NullSentinel.LAUNCH_RECOVER - 3.0F));
                leftArm.xRot = Mth.lerp(rec, -3.1F, 0.0F);
                body.y = BODY_Y - 2.0F * (1.0F - rec) + bob;
                body.zRot = 0.08F * (1.0F - rec);
            }
            case SHIFT -> {
                float up = ease(at / NullSentinel.SHIFT_CLIMAX);
                float down = ease((at - NullSentinel.SHIFT_CLIMAX - 6.0F) / (NullSentinel.SHIFT_TICKS - NullSentinel.SHIFT_CLIMAX - 6.0F));
                float lift = up * (1.0F - down);
                float shake = Mth.sin(ageInTicks * 3.7F) * 0.04F * lift;
                body.y = BODY_Y - 10.0F * lift + bob;
                body.xRot = -0.3F * lift + shake;
                head.xRot = -0.5F * lift;
                rightArm.zRot = 0.12F + 1.5F * lift;
                leftArm.zRot = -0.12F - 1.5F * lift;
                plateLeft.yRot = -1.2F * lift;
                plateRight.yRot = 1.2F * lift;
                ringScale = 1.0F + 0.5F * lift;
            }
            case RECHARGE -> {
                float open = ease(at / 8.0F) * (1.0F - ease((at - NullSentinel.RECHARGE_TICKS + 8.0F) / 8.0F));
                float shudder = Mth.sin(ageInTicks * 2.3F) * 0.025F;
                body.y = BODY_Y + 5.0F * open + bob * (1.0F - open);
                body.xRot = 0.22F * open;
                body.zRot = shudder;
                head.xRot = 0.55F * open;
                head.yRot = 0.0F;
                plateLeft.yRot = -1.7F * open;
                plateRight.yRot = 1.7F * open;
                rightArm.xRot = leftArm.xRot = 0.15F * open;
                rightArm.zRot = 0.12F + 0.25F * open;
                leftArm.zRot = -0.12F - 0.25F * open;
                rightArm.y = -10.0F + 4.0F * open;
                leftArm.y = -10.0F + 4.0F * open;
                ring.y = -11.0F + 4.0F * open;
                ring.xRot = 0.35F * open;
            }
            default -> {
            }
        }

        float dying = boss.dyingTicks() > 0 ? Mth.clamp((boss.dyingTicks() + partial) / NullSentinel.DEATH_TICKS, 0.0F, 1.0F) : 0.0F;
        if (dying > 0.0F) {
            float shake = Mth.sin(ageInTicks * 4.1F) * 0.06F * dying;
            body.y = BODY_Y + 8.0F * dying;
            body.xRot = 0.35F * dying + shake;
            body.zRot = shake;
            head.xRot = 0.8F * dying;
            plateLeft.yRot = -1.8F * Math.min(1.0F, dying * 3.0F);
            plateRight.yRot = 1.8F * Math.min(1.0F, dying * 3.0F);
            rightArm.x = -ARM_X - 7.0F * dying;
            leftArm.x = ARM_X + 7.0F * dying;
            rightArm.y = leftArm.y = -10.0F + 10.0F * dying;
            rightArm.zRot = 0.12F + 0.9F * dying;
            leftArm.zRot = -0.12F - 0.9F * dying;
            rightPauldron.x = -ARM_X - 4.0F * dying;
            leftPauldron.x = ARM_X + 4.0F * dying;
            ringScale = 1.0F + 0.8F * dying;
            ring.xRot = 0.6F * dying;
        }

        ring.zRot = boss.ringAngle(partial);
        for (int i = 0; i < RING_PLATES; i++) {
            float a = i * Mth.TWO_PI / RING_PLATES;
            plates[i].x = Mth.sin(a) * RING_RADIUS * ringScale;
            plates[i].y = -Mth.cos(a) * RING_RADIUS * ringScale;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        body.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
