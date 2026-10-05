package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The four Exo armor pieces (Mk1 and Mk2) and the module items. Recipes: scripts/data/exo_recipes.py. */
public final class ExoItems {
    private ExoItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    public static final DeferredItem<ExoArmorItem> HELMET_MK1 = armor("exo_helmet_mk1", ArmorItem.Type.HELMET, 1);
    public static final DeferredItem<ExoArmorItem> CHESTPLATE_MK1 = armor("exo_chestplate_mk1", ArmorItem.Type.CHESTPLATE, 1);
    public static final DeferredItem<ExoArmorItem> LEGGINGS_MK1 = armor("exo_leggings_mk1", ArmorItem.Type.LEGGINGS, 1);
    public static final DeferredItem<ExoArmorItem> BOOTS_MK1 = armor("exo_boots_mk1", ArmorItem.Type.BOOTS, 1);
    public static final DeferredItem<ExoArmorItem> HELMET_MK2 = armor("exo_helmet_mk2", ArmorItem.Type.HELMET, 2);
    public static final DeferredItem<ExoArmorItem> CHESTPLATE_MK2 = armor("exo_chestplate_mk2", ArmorItem.Type.CHESTPLATE, 2);
    public static final DeferredItem<ExoArmorItem> LEGGINGS_MK2 = armor("exo_leggings_mk2", ArmorItem.Type.LEGGINGS, 2);
    public static final DeferredItem<ExoArmorItem> BOOTS_MK2 = armor("exo_boots_mk2", ArmorItem.Type.BOOTS, 2);

    /** One item per module kind. */
    public static final Map<ExoModuleKind, DeferredItem<ExoModuleItem>> MODULES = new EnumMap<>(ExoModuleKind.class);

    static {
        for (ExoModuleKind kind : ExoModuleKind.values()) {
            Rarity rarity = switch (kind) {
                case FLIGHT -> Rarity.EPIC;
                case KINETIC_SHIELD, SERVO_STRIDE_3 -> Rarity.RARE;
                case REBREATHER, ROBOT_HUD, JET_ASSIST, SERVO_STRIDE_2, FALL_DAMPENER, MAGNET -> Rarity.UNCOMMON;
                default -> Rarity.COMMON;
            };
            DeferredItem<ExoModuleItem> item = ITEMS.registerItem(kind.itemName(), p -> new ExoModuleItem(p.rarity(rarity), kind));
            MODULES.put(kind, item);
        }
    }

    private static DeferredItem<ExoArmorItem> armor(String name, ArmorItem.Type type, int mk) {
        DeferredItem<ExoArmorItem> item = ITEMS.registerItem(name, p -> new ExoArmorItem(p, ExoRegistry.material(mk), type, mk));
        TAB_ORDER.add(item);
        return item;
    }

    public static DeferredItem<ExoModuleItem> module(ExoModuleKind kind) {
        return MODULES.get(kind);
    }

    public static DeferredItem<ExoArmorItem> piece(int mk, net.minecraft.world.entity.EquipmentSlot slot) {
        boolean two = mk >= 2;
        return switch (slot) {
            case HEAD -> two ? HELMET_MK2 : HELMET_MK1;
            case CHEST -> two ? CHESTPLATE_MK2 : CHESTPLATE_MK1;
            case LEGS -> two ? LEGGINGS_MK2 : LEGGINGS_MK1;
            case FEET -> two ? BOOTS_MK2 : BOOTS_MK1;
            default -> throw new IllegalArgumentException("not an armor slot: " + slot);
        };
    }

    public static void addToTab() {
        TAB_ORDER.forEach(RoboticaTab::add);
        MODULES.values().forEach(RoboticaTab::add);
    }
}
