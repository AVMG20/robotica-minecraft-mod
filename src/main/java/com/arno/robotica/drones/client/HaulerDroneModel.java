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

/** Flat quadcopter with a winch hub and a four-prong claw underneath; the claw opens when empty. Texture 64x64. */
public class HaulerDroneModel extends EntityModel<HaulerDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("hauler_drone"), "main");
    private static final float[][] CORNERS = {{-5.0F, -5.0F}, {5.0F, -5.0F}, {-5.0F, 5.0F}, {5.0F, 5.0F}};
    /** Prong offsets (x, z) around the hub: north, south, west, east. */
    private static final float[][] PRONGS = {{0.0F, -1.5F}, {0.0F, 1.5F}, {-1.5F, 0.0F}, {1.5F, 0.0F}};

    private final ModelPart root;
    private final ModelPart[] rotors = new ModelPart[4];
    private final ModelPart[] prongs = new ModelPart[4];
    private float open;

    public HaulerDroneModel(ModelPart part) {
        this.root = part.getChild("root");
        for (int i = 0; i < 4; i++) {
            rotors[i] = root.getChild("rotor_" + i);
            prongs[i] = root.getChild("prong_" + i);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 3.0F, 8.0F)
                .texOffs(0, 14).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 2.0F, 4.0F)
                .texOffs(40, 0).addBox(-1.5F, -1.5F, -5.0F, 3.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild("post_" + i, CubeListBuilder.create().texOffs(32, 8).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                    PartPose.offset(CORNERS[i][0], -1.0F, CORNERS[i][1]));
            root.addOrReplaceChild("rotor_" + i, CubeListBuilder.create().texOffs(0, 26).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F),
                    PartPose.offset(CORNERS[i][0], -5.0F, CORNERS[i][1]));
            root.addOrReplaceChild("prong_" + i, CubeListBuilder.create().texOffs(32, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F),
                    PartPose.offset(PRONGS[i][0], 2.5F, PRONGS[i][1]));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void prepareMobModel(HaulerDrone drone, float limbSwing, float limbSwingAmount, float partialTick) {
        open = drone.isActive() ? 0.0F : 1.0F;
    }

    @Override
    public void setupAnim(HaulerDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.y = 18.0F + (float) Math.sin(ageInTicks * 0.11F) * 0.5F;
        for (int i = 0; i < 4; i++) rotors[i].yRot = ageInTicks * (i % 2 == 0 ? 2.0F : -2.0F);
        float a = 0.12F + 0.45F * open;
        prongs[0].xRot = a;
        prongs[1].xRot = -a;
        prongs[2].zRot = -a;
        prongs[3].zRot = a;
        root.xRot = limbSwingAmount * 0.3F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
