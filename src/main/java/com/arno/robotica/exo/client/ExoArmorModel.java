package com.arno.robotica.exo.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.exo.ExoItems;
import com.arno.robotica.exo.item.ExoArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The worn Exo-Frame: the vanilla armor boxes plus extra parts per mark (ear pods, chest core, pauldrons, knee pads,
 * toe caps, and from Mk2 on crests, fins, heel thrusters, shin guards and blades). One model per mark for layer 1
 * (helmet, chestplate, boots) and one for layer 2 (leggings). The 64x64 textures keep the vanilla armor UVs in the top
 * half and the extra parts below; scripts/textures/exo_armor.py paints them from the same part list (EXTRAS), keep the
 * two in step.
 */
final class ExoArmorModel {
    private ExoArmorModel() {}

    private static final int MK1 = 1, MK2 = 2, MK3 = 4, MK4 = 8, ALL = MK1 | MK2 | MK3 | MK4;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * One extra box in its bone's space. Parts on the right arm and leg are mirrored onto the left limb; a head part with
     * {@code pair} gets a mirrored twin.
     */
    private record Part(String bone, int u, int v, float x, float y, float z, int w, int h, int d, int marks, boolean pair) {}

    private static final Part[] OUTER = {
            // helmet: ear pods, Mk2 ridge, Mk3 heat fins, Mk4 crest and antenna
            new Part("head", 32, 0, -6.0F, -6.0F, -1.0F, 1, 4, 4, ALL, true),
            new Part("head", 32, 8, -0.5F, -10.0F, -3.0F, 1, 1, 6, MK2, false),
            new Part("head", 46, 0, -3.5F, -10.0F, -2.0F, 1, 1, 5, MK3, true),
            new Part("head", 46, 0, -0.5F, -11.0F, -4.0F, 1, 2, 7, MK4, false),
            new Part("head", 50, 48, -6.0F, -10.0F, 1.0F, 1, 4, 1, MK4, false),
            // chestplate: core, pauldrons (flat on Mk1), Mk3 heat sinks, Mk4 blades
            new Part("body", 40, 48, -2.0F, 1.0F, -4.0F, 4, 4, 1, ALL, false),
            new Part("right_arm", 0, 32, -5.0F, -4.0F, -3.5F, 6, 3, 7, MK1, false),
            new Part("right_arm", 0, 32, -5.0F, -4.0F, -3.5F, 6, 4, 7, MK2 | MK3 | MK4, false),
            new Part("right_arm", 28, 32, -4.0F, -5.0F, -2.5F, 1, 1, 5, MK3, false),
            new Part("right_arm", 28, 32, -2.0F, -5.0F, -2.5F, 1, 1, 5, MK3, false),
            new Part("right_arm", 28, 32, -5.5F, -6.0F, -3.0F, 1, 3, 6, MK4, false),
            // boots: toe caps, Mk2 heel thrusters, Mk3 shin guards
            new Part("right_leg", 28, 44, -2.5F, 10.0F, -4.0F, 5, 3, 1, ALL, false),
            new Part("right_leg", 40, 44, -1.5F, 9.0F, 3.0F, 3, 3, 1, MK2 | MK3 | MK4, false),
            new Part("right_leg", 28, 48, -2.0F, 5.0F, -4.0F, 4, 4, 1, MK3 | MK4, false),
    };
    private static final Part[] INNER = {
            // leggings: knee pads, Mk4 thigh plates
            new Part("right_leg", 0, 32, -2.0F, 3.0F, -3.5F, 4, 3, 1, ALL, false),
            new Part("right_leg", 12, 32, -3.5F, 0.5F, -2.0F, 1, 4, 4, MK4, false),
    };

    /** [mark - 1][0 = layer 1, 1 = layer 2 (leggings)]. */
    private static final ModelLayerLocation[][] LAYERS = new ModelLayerLocation[4][2];
    @SuppressWarnings("unchecked")
    private static final HumanoidModel<LivingEntity>[][] MODELS = new HumanoidModel[4][2];

    static {
        for (int mk = 1; mk <= 4; mk++) {
            LAYERS[mk - 1][0] = new ModelLayerLocation(Robotica.id("exo_armor_mk" + mk), "outer");
            LAYERS[mk - 1][1] = new ModelLayerLocation(Robotica.id("exo_armor_mk" + mk), "inner");
        }
    }

    static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            return stack.getItem() instanceof ExoArmorItem item ? get(item.mk, slot == EquipmentSlot.LEGS) : original;
        }
    };

    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (int mk = 1; mk <= 4; mk++) {
            int mark = mk;
            event.registerLayerDefinition(LAYERS[mk - 1][0], () -> create(mark, false));
            event.registerLayerDefinition(LAYERS[mk - 1][1], () -> create(mark, true));
        }
    }

    static void registerExtensions(RegisterClientExtensionsEvent event) {
        for (int mk = 1; mk <= 4; mk++) {
            for (EquipmentSlot slot : SLOTS) event.registerItem(EXTENSIONS, ExoItems.piece(mk, slot).get());
        }
    }

    /** Bakes the models again after every resource reload. */
    static void bake(EntityRenderersEvent.AddLayers event) {
        bakeAll(event.getEntityModels());
    }

    private static void bakeAll(EntityModelSet models) {
        for (int mk = 0; mk < 4; mk++) {
            for (int i = 0; i < 2; i++) MODELS[mk][i] = new HumanoidModel<>(models.bakeLayer(LAYERS[mk][i]));
        }
    }

    /** The model of a mark: layer 2 (inner, for leggings) or layer 1. */
    static HumanoidModel<LivingEntity> get(int mk, boolean inner) {
        if (MODELS[0][0] == null) bakeAll(Minecraft.getInstance().getEntityModels());
        return MODELS[Math.max(1, Math.min(4, mk)) - 1][inner ? 1 : 0];
    }

    static LayerDefinition create(int mk, boolean inner) {
        CubeDeformation grow = new CubeDeformation(inner ? 0.5F : 1.0F);
        Part[] parts = inner ? INNER : OUTER;
        int mark = 1 << (mk - 1);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", parts(CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, grow), parts, "head", mark, false), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", parts(CubeListBuilder.create().texOffs(16, 16)
                .addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, grow), parts, "body", mark, false), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", parts(CubeListBuilder.create().texOffs(40, 16)
                .addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, grow), parts, "right_arm", mark, false), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", parts(CubeListBuilder.create().texOffs(40, 16).mirror()
                .addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, grow), parts, "right_arm", mark, true), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", parts(CubeListBuilder.create().texOffs(0, 16)
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, grow), parts, "right_leg", mark, false), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", parts(CubeListBuilder.create().texOffs(0, 16).mirror()
                .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, grow), parts, "right_leg", mark, true), PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Adds the extra parts of one bone (the right limb's, mirrored, for a left limb). */
    private static CubeListBuilder parts(CubeListBuilder cubes, Part[] parts, String bone, int mark, boolean left) {
        for (Part p : parts) {
            if (!p.bone().equals(bone) || (p.marks() & mark) == 0) continue;
            if (!left) cubes.mirror(false).texOffs(p.u(), p.v()).addBox(p.x(), p.y(), p.z(), p.w(), p.h(), p.d());
            if (left || p.pair()) cubes.mirror(true).texOffs(p.u(), p.v()).addBox(-(p.x() + p.w()), p.y(), p.z(), p.w(), p.h(), p.d());
        }
        return cubes.mirror(false);
    }
}
