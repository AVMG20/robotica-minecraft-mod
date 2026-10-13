package com.arno.robotica.exo.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.module.ModuleKind;
import com.arno.robotica.core.module.Modules;
import com.arno.robotica.exo.ExoData;
import com.arno.robotica.exo.ExoSuit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Two Jet Assist thruster pods on the back of an Exo chestplate with the module fitted, in the colors of the
 * chestplate's mark. While the pack fires the nozzles and the status strips glow (a full bright second pass, see
 * {@link ExoJetpack#glow}). Texture 64x32: textures/models/armor/exo_jetpack_mk{1-4}.png and exo_jetpack_glow.png.
 */
final class ExoJetpackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("exo_jetpack"), "main");
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[4];
    private static final ResourceLocation GLOW = Robotica.id("textures/models/armor/exo_jetpack_glow.png");

    static {
        for (int mk = 1; mk <= 4; mk++) TEXTURES[mk - 1] = Robotica.id("textures/models/armor/exo_jetpack_mk" + mk + ".png");
    }

    private final ModelPart pack;

    ExoJetpackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.pack = models.bakeLayer(LAYER).getChild("pack");
    }

    /** Body space of the player model: x -4..4, y 0 (shoulders) to 12 (waist), the back at z 2 (3 with the chestplate). */
    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition pack = mesh.getRoot().addOrReplaceChild("pack", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.0F, 1.5F, 3.0F, 6.0F, 4.0F, 1.0F), PartPose.ZERO);
        for (int side = -1; side <= 1; side += 2) {
            float cx = 3.0F * side;
            pack.addOrReplaceChild(side < 0 ? "left" : "right", CubeListBuilder.create()
                    .texOffs(0, 6).addBox(cx - 1.5F, 0.5F, 3.5F, 3.0F, 8.0F, 3.0F)
                    .texOffs(16, 0).addBox(cx - 1.0F, -0.5F, 4.0F, 2.0F, 1.0F, 2.0F)
                    .texOffs(24, 0).addBox(cx - 1.0F, 8.5F, 4.0F, 2.0F, 2.0F, 2.0F)
                    .texOffs(16, 6).addBox(cx - 1.5F, 10.5F, 3.5F, 3.0F, 1.0F, 3.0F), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new ExoJetpackLayer(renderer, event.getEntityModels()));
            }
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        ItemStack chest = ExoSuit.piece(player, EquipmentSlot.CHEST);
        if (chest.isEmpty() || Modules.installed(chest, ModuleKind.JET_ASSIST) <= 0) return;
        int mk = Mth.clamp(ExoData.mark(chest), 1, 4);
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pack.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[mk - 1])), light, OverlayTexture.NO_OVERLAY);
        float glow = ExoJetpack.glow(player, partialTick);
        if (glow > 0.02F) {
            // A fast flicker on top of the glow, so the nozzles look like they burn.
            float flicker = 0.85F + 0.15F * Mth.sin(ageInTicks * 2.7F + player.getId());
            int c = Mth.clamp((int) (255 * glow * flicker), 0, 255);
            pack.render(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    0xFF000000 | c << 16 | c << 8 | c);
        }
        pose.popPose();
    }
}
