package com.arno.robotica.boss.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.boss.entity.NullPylon;
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

/** Null Pylon: a cyan crystal between two null steel caps, with three shards orbiting it. Texture 64x32 (scripts/textures/boss.py). */
public class NullPylonModel extends EntityModel<NullPylon> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("null_pylon"), "main");

    private final ModelPart root;
    private final ModelPart crystal;
    private final ModelPart orbit;

    public NullPylonModel(ModelPart part) {
        this.root = part.getChild("root");
        this.crystal = root.getChild("crystal");
        this.orbit = root.getChild("orbit");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create()
                        .texOffs(16, 0).addBox(-4.0F, -2.0F, -4.0F, 8.0F, 2.0F, 8.0F)      // top cap
                        .texOffs(16, 0).addBox(-4.0F, 14.0F, -4.0F, 8.0F, 2.0F, 8.0F),     // bottom cap
                PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("crystal", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -5.0F, -2.0F, 4.0F, 10.0F, 4.0F),
                PartPose.offset(0.0F, 7.0F, 0.0F));
        PartDefinition orbit = root.addOrReplaceChild("orbit", CubeListBuilder.create(), PartPose.offset(0.0F, 7.0F, 0.0F));
        for (int i = 0; i < 3; i++) {
            float a = i * Mth.TWO_PI / 3.0F;
            orbit.addOrReplaceChild("shard" + i, CubeListBuilder.create().texOffs(48, 0).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F),
                    PartPose.offsetAndRotation(Mth.cos(a) * 6.0F, (i - 1) * 2.5F, Mth.sin(a) * 6.0F, 0.0F, -a, 0.3F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(NullPylon pylon, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.y = Mth.sin(ageInTicks * 0.1F + pylon.getId()) * 1.2F;
        root.yRot = ageInTicks * 0.02F;
        crystal.yRot = ageInTicks * 0.08F;
        orbit.yRot = -ageInTicks * 0.12F;
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
