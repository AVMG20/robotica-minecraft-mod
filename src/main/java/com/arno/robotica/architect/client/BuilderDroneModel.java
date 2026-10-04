package com.arno.robotica.architect.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.architect.entity.BuilderDrone;
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

/** A small cube with four spinning rotors. Texture 32x32: body in the top left, rotors below it. */
public class BuilderDroneModel extends EntityModel<BuilderDrone> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("builder_drone"), "main");
    private static final String[] ROTORS = {"rotor_0", "rotor_1", "rotor_2", "rotor_3"};

    private final ModelPart root;
    private final ModelPart[] rotors = new ModelPart[4];

    public BuilderDroneModel(ModelPart root) {
        this.root = root;
        for (int i = 0; i < 4; i++) rotors[i] = root.getChild(ROTORS[i]);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition part = mesh.getRoot();
        part.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        float[][] corners = {{-4.0F, -4.0F}, {4.0F, -4.0F}, {-4.0F, 4.0F}, {4.0F, 4.0F}};
        for (int i = 0; i < 4; i++) {
            part.addOrReplaceChild(ROTORS[i], CubeListBuilder.create().texOffs(0, 14).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 0.5F, 4.0F),
                    PartPose.offset(corners[i][0], -4.0F, corners[i][1]));
        }
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(BuilderDrone entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        for (int i = 0; i < 4; i++) rotors[i].yRot = ageInTicks * (i % 2 == 0 ? 1.6F : -1.6F);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
