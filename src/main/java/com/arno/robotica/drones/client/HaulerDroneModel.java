package com.arno.robotica.drones.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.HaulerDrone;
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
 * Flat quadcopter with a winch hub; a cable lowers a four-prong claw onto the carried mob and reels it back in when empty.
 * Mk2 has bigger rotors, side winch drums and an antenna. Texture 64x64, layout in scripts/textures/drones.py.
 */
public class HaulerDroneModel extends EntityModel<HaulerDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("hauler_drone"), "main");
    private static final float[][] CORNERS = {{-5.0F, -5.0F}, {5.0F, -5.0F}, {-5.0F, 5.0F}, {5.0F, 5.0F}};
    /** Prong offsets (x, z) around the claw collar: north, south, west, east. */
    private static final float[][] PRONGS = {{0.0F, -1.5F}, {0.0F, 1.5F}, {-1.5F, 0.0F}, {1.5F, 0.0F}};
    private static final float ROOT_Y = 16.0F;
    /** Claw collar height (relative to the root) when reeled in and when lowered onto the mob. */
    private static final float CLAW_IN = 3.0F, CLAW_OUT = 6.0F;

    private final ModelPart root;
    private final ModelPart[] rotors = new ModelPart[4];
    private final ModelPart[] bigRotors = new ModelPart[4];
    private final ModelPart[] prongs = new ModelPart[4];
    private final ModelPart claw;
    private final ModelPart cable;
    private final ModelPart drums;
    private final ModelPart antenna;
    private float partial;

    public HaulerDroneModel(ModelPart part) {
        this.root = part.getChild("root");
        this.claw = root.getChild("claw");
        this.cable = root.getChild("cable");
        this.drums = root.getChild("drums");
        this.antenna = root.getChild("antenna");
        for (int i = 0; i < 4; i++) {
            rotors[i] = root.getChild("rotor_" + i);
            bigRotors[i] = root.getChild("big_rotor_" + i);
            prongs[i] = claw.getChild("prong_" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 3.0F, 8.0F)
                .texOffs(0, 14).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(40, 0).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, ROOT_Y, 0.0F));
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild("post_" + i, CubeListBuilder.create().texOffs(32, 8).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                    PartPose.offset(CORNERS[i][0], -1.0F, CORNERS[i][1]));
            root.addOrReplaceChild("rotor_" + i, CubeListBuilder.create().texOffs(0, 26).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F),
                    PartPose.offset(CORNERS[i][0], -5.0F, CORNERS[i][1]));
            root.addOrReplaceChild("big_rotor_" + i, CubeListBuilder.create().texOffs(0, 36).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 1.0F, 8.0F),
                    PartPose.offset(CORNERS[i][0], -5.0F, CORNERS[i][1]));
        }
        // the cable is a 1 px box scaled to the winch length
        root.addOrReplaceChild("cable", CubeListBuilder.create().texOffs(28, 14).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, CLAW_IN, 0.0F));
        PartDefinition claw = root.addOrReplaceChild("claw", CubeListBuilder.create()
                .texOffs(16, 14).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.offset(0.0F, CLAW_IN, 0.0F));
        for (int i = 0; i < 4; i++) {
            claw.addOrReplaceChild("prong_" + i, CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                    PartPose.offset(PRONGS[i][0], 0.5F, PRONGS[i][1]));
        }
        // Mk2: twin winch drums on the hub sides and a whip antenna at the back
        root.addOrReplaceChild("drums", CubeListBuilder.create()
                .texOffs(36, 14).addBox(-4.0F, 0.5F, -1.5F, 2.0F, 2.0F, 3.0F)
                .texOffs(36, 14).addBox(2.0F, 0.5F, -1.5F, 2.0F, 2.0F, 3.0F), PartPose.ZERO);
        root.addOrReplaceChild("antenna", CubeListBuilder.create()
                .texOffs(48, 0).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F)
                .texOffs(52, 0).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 1.0F, 2.0F), PartPose.offset(0.0F, -2.0F, 3.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void prepareMobModel(HaulerDrone drone, float limbSwing, float limbSwingAmount, float partialTick) {
        partial = partialTick;
    }

    @Override
    public void setupAnim(HaulerDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float p = partial;
        boolean mk2 = drone.tier() >= 2;
        float winch = Mth.lerp(p, drone.winchO, drone.winch);
        float open = Mth.lerp(p, drone.clawO, drone.claw);
        float tiltX = Mth.lerp(p, drone.tiltXO, drone.tiltX);
        float tiltZ = Mth.lerp(p, drone.tiltZO, drone.tiltZ);
        float spin = Mth.lerp(p, drone.rotorO, drone.rotor);

        // bob less with a load; the claw stays put on the mob while the body bobs
        float bob = Mth.sin(ageInTicks * 0.11F) * (0.5F - 0.3F * winch);
        root.y = ROOT_Y + bob;
        float sway = winch * Mth.sin(ageInTicks * 0.07F) * 0.03F;
        root.xRot = tiltX;
        root.zRot = tiltZ + sway;
        for (int i = 0; i < 4; i++) {
            float a = spin * (i % 2 == 0 ? 1.0F : -1.0F);
            rotors[i].yRot = a;
            bigRotors[i].yRot = a;
            rotors[i].visible = !mk2;
            bigRotors[i].visible = mk2;
        }
        drums.visible = mk2;
        antenna.visible = mk2;
        antenna.xRot = -tiltX * 0.8F + Mth.sin(ageInTicks * 0.2F) * 0.04F;
        antenna.zRot = -tiltZ * 0.8F;

        float clawY = Mth.lerp(winch, CLAW_IN, CLAW_OUT - bob);
        claw.y = clawY;
        claw.xRot = -tiltX;
        claw.zRot = -tiltZ - sway;
        float length = clawY - CLAW_IN;
        cable.visible = length > 0.05F;
        cable.yScale = Math.max(0.01F, length);
        // positive flare spreads the tips outward; closed they bite slightly inward
        float flare = -0.18F + 0.78F * open;
        prongs[0].xRot = -flare;
        prongs[1].xRot = flare;
        prongs[2].zRot = flare;
        prongs[3].zRot = -flare;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
