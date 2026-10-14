package com.arno.robotica.exo.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.energy.ItemEnergy;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The energy conduits, visor and core of worn Exo armor, drawn full bright over the armor with a slow pulse while the
 * piece holds energy (an empty piece goes dark). Added to players and every humanoid renderer, armor stands included.
 * Textures: textures/models/armor/exo_mk{1-4}_layer_{1,2}_glow.png on {@link ExoArmorModel}.
 */
final class ExoArmorGlowLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};
    /** [mark - 1][0 = layer 1, 1 = layer 2]. */
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[4][2];

    static {
        for (int mk = 1; mk <= 4; mk++) {
            for (int i = 0; i < 2; i++) TEXTURES[mk - 1][i] = Robotica.id("textures/models/armor/exo_mk" + mk + "_layer_" + (i + 1) + "_glow.png");
        }
    }

    private ExoArmorGlowLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new ExoArmorGlowLayer<>(renderer));
        }
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?> renderer = event.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer<?, ?> living && living.getModel() instanceof HumanoidModel<?>) add(living);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void add(LivingEntityRenderer renderer) {
        renderer.addLayer(new ExoArmorGlowLayer(renderer));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        // A slow breathing pulse, out of step between wearers.
        float pulse = 0.82F + 0.18F * Mth.sin(ageInTicks * 0.08F + entity.getId() * 1.7F);
        int c = Mth.clamp((int) (255 * pulse), 0, 255);
        int color = 0xFF000000 | c << 16 | c << 8 | c;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ExoArmorItem item) || item.getEquipmentSlot() != slot || ItemEnergy.get(stack) <= 0) continue;
            boolean inner = slot == EquipmentSlot.LEGS;
            HumanoidModel<T> model = (HumanoidModel<T>) (HumanoidModel<?>) ExoArmorModel.get(item.mk, inner);
            getParentModel().copyPropertiesTo(model);
            show(model, slot);
            int mk = Mth.clamp(item.mk, 1, 4);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(TEXTURES[mk - 1][inner ? 1 : 0])), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, color);
        }
    }

    /** The parts a piece covers, like HumanoidArmorLayer. */
    private static void show(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {}
        }
    }
}
