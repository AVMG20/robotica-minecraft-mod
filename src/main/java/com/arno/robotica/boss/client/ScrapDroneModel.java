package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.ScrapDrone;
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

/** Scrap Drone: a dented copper box with one red eye, a stinger and two little rotors. Texture 32x32 (scripts/textures/boss.py). */
public class ScrapDroneModel extends EntityModel<ScrapDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("scrap_drone"), "main");

    private final ModelPart root;
    private final ModelPart rotorLeft;
    private final ModelPart rotorRight;

    public ScrapDroneModel(ModelPart part) {
        this.root = part.getChild("root");
        this.rotorLeft = root.getChild("rotor_left");
        this.rotorRight = root.getChild("rotor_right");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 4.0F, 6.0F)        // body
                        .texOffs(0, 10).addBox(-1.5F, -1.0F, -3.5F, 3.0F, 2.0F, 1.0F)       // eye
                        .texOffs(0, 14).addBox(-6.0F, -2.5F, -0.5F, 12.0F, 1.0F, 1.0F)      // rotor arm
                        .texOffs(16, 10).addBox(-0.5F, 2.0F, -0.5F, 1.0F, 2.0F, 1.0F),      // stinger
                PartPose.offset(0.0F, 20.0F, 0.0F));
        CubeListBuilder rotor = CubeListBuilder.create().texOffs(0, 17).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 1.0F, 4.0F);
        root.addOrReplaceChild("rotor_left", rotor, PartPose.offset(5.5F, -3.5F, 0.0F));
        root.addOrReplaceChild("rotor_right", rotor, PartPose.offset(-5.5F, -3.5F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(ScrapDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.y = 20.0F + Mth.sin(ageInTicks * 0.25F) * 1.0F;
        root.xRot = limbSwingAmount * 0.4F;
        root.zRot = Mth.sin(ageInTicks * 0.13F) * 0.08F;
        rotorLeft.yRot = ageInTicks * 1.9F;
        rotorRight.yRot = -ageInTicks * 1.9F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
