package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.item.CoreItems;
import com.arno.robotica.exo.menu.ExoMenu;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/** Registries of the exo module: the core socket component, armor materials and the module menu. */
public final class ExoRegistry {
    private ExoRegistry() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Robotica.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Robotica.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Robotica.MODID);

    /** The core in the chestplate's core socket (Mk2+): one Servo, Magma or Antigrav Core, never consumed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> CORE =
            COMPONENTS.registerComponentType("exo_core",
                    b -> b.persistent(ItemContainerContents.CODEC).networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** Iron-tier protection (Mk1). Defense 2/5/6/2, no toughness. Layers: textures/models/armor/exo_mk1_layer_1/2.png. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MK1 = MATERIALS.register("exo_mk1", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 2);
                m.put(ArmorItem.Type.LEGGINGS, 5);
                m.put(ArmorItem.Type.CHESTPLATE, 6);
                m.put(ArmorItem.Type.HELMET, 2);
                m.put(ArmorItem.Type.BODY, 5);
            }),
            9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(CoreItems.IRON_PLATE.get()),
            List.of(new ArmorMaterial.Layer(Robotica.id("exo_mk1"))), 0.0F, 0.0F));

    /** Diamond-tier protection (Mk2). Defense 3/6/8/3, toughness 2. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MK2 = MATERIALS.register("exo_mk2", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 3);
                m.put(ArmorItem.Type.LEGGINGS, 6);
                m.put(ArmorItem.Type.CHESTPLATE, 8);
                m.put(ArmorItem.Type.HELMET, 3);
                m.put(ArmorItem.Type.BODY, 11);
            }),
            10, SoundEvents.ARMOR_EQUIP_DIAMOND, () -> Ingredient.of(CoreItems.REINFORCED_CASING.get()),
            List.of(new ArmorMaterial.Layer(Robotica.id("exo_mk2"))), 2.0F, 0.0F));

    /** Netherite-tier protection (Mk3). Defense 3/6/8/3, toughness 3, knockback resistance 0.1. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MK3 = MATERIALS.register("exo_mk3", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 3);
                m.put(ArmorItem.Type.LEGGINGS, 6);
                m.put(ArmorItem.Type.CHESTPLATE, 8);
                m.put(ArmorItem.Type.HELMET, 3);
                m.put(ArmorItem.Type.BODY, 11);
            }),
            15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(CoreItems.BLAZING_CASING.get()),
            List.of(new ArmorMaterial.Layer(Robotica.id("exo_mk3"))), 3.0F, 0.1F));

    /** Above netherite (Mk4). Defense 4/7/9/4, toughness 4, knockback resistance 0.2. */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MK4 = MATERIALS.register("exo_mk4", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 4);
                m.put(ArmorItem.Type.LEGGINGS, 7);
                m.put(ArmorItem.Type.CHESTPLATE, 9);
                m.put(ArmorItem.Type.HELMET, 4);
                m.put(ArmorItem.Type.BODY, 13);
            }),
            15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(CoreItems.NULL_CASING.get()),
            List.of(new ArmorMaterial.Layer(Robotica.id("exo_mk4"))), 4.0F, 0.2F));

    public static final DeferredHolder<MenuType<?>, MenuType<ExoMenu>> EXO_MENU = MENUS.register("exo_modules",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new ExoMenu(id, inv, ExoMenu.readSections(buf), false)));

    public static Holder<ArmorMaterial> material(int mk) {
        return switch (mk) {
            case 1 -> MK1;
            case 2 -> MK2;
            case 3 -> MK3;
            default -> MK4;
        };
    }
}
