package com.arno.robotica.drones.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.MiningDrone;
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

/**
 * Stubby copper body with two rotor pods, a headlamp and a spinning drill on the front (-Z). Texture 64x64, see
 * scripts/textures/drones.py for the box layout. The whole body bobs; the rotors always spin, the drill spins fast while it digs.
 */
public class MiningDroneModel extends EntityModel<MiningDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("mining_drone"), "main");

    private final ModelPart root;
    private final ModelPart rotorLeft;
    private final ModelPart rotorRight;
    private final ModelPart drill;

    public MiningDroneModel(ModelPart part) {
        this.root = part.getChild("root");
        this.rotorLeft = root.getChild("rotor_left");
        this.rotorRight = root.getChild("rotor_right");
        this.drill = root.getChild("drill");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 8.0F)
                .texOffs(0, 16).addBox(-3.0F, -5.0F, -3.0F, 6.0F, 2.0F, 6.0F)
                .texOffs(0, 26).addBox(-3.0F, 3.0F, -3.0F, 6.0F, 2.0F, 6.0F)
                .texOffs(32, 0).addBox(-1.5F, -4.0F, -6.0F, 3.0F, 2.0F, 2.0F)
                .texOffs(32, 8).addBox(-2.5F, -2.5F, -6.0F, 5.0F, 5.0F, 2.0F)
                .texOffs(46, 0).addBox(-0.5F, -4.0F, 4.0F, 1.0F, 4.0F, 3.0F)
                .texOffs(0, 36).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F)
                .texOffs(0, 36).addBox(4.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offset(0.0F, 20.0F, 0.0F));
        root.addOrReplaceChild("drill", CubeListBuilder.create()
                .texOffs(32, 16).addBox(-2.0F, -2.0F, -9.0F, 4.0F, 4.0F, 3.0F)
                .texOffs(32, 24).addBox(-1.0F, -1.0F, -12.0F, 2.0F, 2.0F, 3.0F),
                PartPose.ZERO);
        CubeListBuilder rotor = CubeListBuilder.create().texOffs(16, 36).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 1.0F, 6.0F);
        root.addOrReplaceChild("rotor_left", rotor, PartPose.offset(-6.0F, -4.0F, 0.0F));
        root.addOrReplaceChild("rotor_right", rotor, PartPose.offset(6.0F, -4.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MiningDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.y = 20.0F + (float) Math.sin(ageInTicks * 0.12F) * 0.8F;
        rotorLeft.yRot = ageInTicks * 1.9F;
        rotorRight.yRot = -ageInTicks * 1.9F;
        drill.zRot = ageInTicks * (drone.isActive() ? 1.4F : 0.12F);
        root.xRot = limbSwingAmount * 0.35F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
