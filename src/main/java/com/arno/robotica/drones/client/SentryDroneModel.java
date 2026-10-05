package com.arno.robotica.drones.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.SentryDrone;
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
 * Compact steel orb (three crossing boxes) with a cyan eye and a cannon on the front (-Z) and a helicopter rotor on top.
 * Texture 64x64, see scripts/textures/drones.py for the box layout.
 */
public class SentryDroneModel extends EntityModel<SentryDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("sentry_drone"), "main");

    private final ModelPart root;
    private final ModelPart bladeA;
    private final ModelPart bladeB;
    private final ModelPart cannon;

    public SentryDroneModel(ModelPart part) {
        this.root = part.getChild("root");
        this.bladeA = root.getChild("blade_a");
        this.bladeB = root.getChild("blade_b");
        this.cannon = root.getChild("cannon");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -3.0F, -3.0F, 8.0F, 6.0F, 6.0F)
                .texOffs(0, 12).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 8.0F, 6.0F)
                .texOffs(28, 0).addBox(-3.0F, -3.0F, -4.0F, 6.0F, 6.0F, 8.0F)
                .texOffs(0, 28).addBox(-2.5F, -1.5F, -5.0F, 5.0F, 3.0F, 1.0F)
                .texOffs(44, 14).addBox(-0.5F, -6.0F, -0.5F, 1.0F, 2.0F, 1.0F)
                .texOffs(0, 40).addBox(-6.0F, 0.0F, 2.0F, 2.0F, 2.0F, 3.0F)
                .texOffs(0, 40).addBox(4.0F, 0.0F, 2.0F, 2.0F, 2.0F, 3.0F),
                PartPose.offset(0.0F, 20.0F, 0.0F));
        CubeListBuilder blade = CubeListBuilder.create().texOffs(0, 36).addBox(-6.0F, 0.0F, -1.0F, 12.0F, 1.0F, 2.0F);
        root.addOrReplaceChild("blade_a", blade, PartPose.offset(0.0F, -6.5F, 0.0F));
        root.addOrReplaceChild("blade_b", blade, PartPose.offsetAndRotation(0.0F, -6.5F, 0.0F, 0.0F, (float) (Math.PI / 2.0), 0.0F));
        root.addOrReplaceChild("cannon", CubeListBuilder.create()
                .texOffs(16, 28).addBox(-1.0F, -1.0F, -10.0F, 2.0F, 2.0F, 6.0F)
                .texOffs(34, 28).addBox(-1.5F, -1.5F, -12.0F, 3.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, 3.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SentryDrone drone, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.y = 20.0F + (float) Math.sin(ageInTicks * 0.1F) * 0.9F;
        float spin = ageInTicks * (drone.isActive() ? 2.4F : 1.5F);
        bladeA.yRot = spin;
        bladeB.yRot = spin + (float) (Math.PI / 2.0);
        cannon.xRot = drone.isActive() ? Math.max(-0.5F, headPitch * 0.017453292F) : 0.0F;
        root.xRot = limbSwingAmount * 0.3F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
