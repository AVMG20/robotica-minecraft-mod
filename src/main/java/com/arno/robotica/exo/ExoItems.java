package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.exo.item.ExoModuleItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The four Exo armor pieces in four marks and the module items (one per kind and level). Recipes: scripts/data/exo_recipes.py. */
public final class ExoItems {
    private ExoItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    private static final String[] PIECE_NAMES = {"helmet", "chestplate", "leggings", "boots"};
    private static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
    /** [mark - 1][piece index]. */
    @SuppressWarnings("unchecked")
    private static final DeferredItem<ExoArmorItem>[][] ARMOR = new DeferredItem[4][4];

    static {
        for (int mk = 1; mk <= 4; mk++) {
            for (int piece = 0; piece < 4; piece++) ARMOR[mk - 1][piece] = armor("exo_" + PIECE_NAMES[piece] + "_mk" + mk, TYPES[piece], mk);
        }
    }

    public static final DeferredItem<ExoArmorItem> HELMET_MK1 = ARMOR[0][0];
    public static final DeferredItem<ExoArmorItem> CHESTPLATE_MK1 = ARMOR[0][1];
    public static final DeferredItem<ExoArmorItem> LEGGINGS_MK1 = ARMOR[0][2];
    public static final DeferredItem<ExoArmorItem> BOOTS_MK1 = ARMOR[0][3];
    public static final DeferredItem<ExoArmorItem> HELMET_MK2 = ARMOR[1][0];
    public static final DeferredItem<ExoArmorItem> CHESTPLATE_MK2 = ARMOR[1][1];
    public static final DeferredItem<ExoArmorItem> LEGGINGS_MK2 = ARMOR[1][2];
    public static final DeferredItem<ExoArmorItem> BOOTS_MK2 = ARMOR[1][3];
    public static final DeferredItem<ExoArmorItem> HELMET_MK3 = ARMOR[2][0];
    public static final DeferredItem<ExoArmorItem> CHESTPLATE_MK3 = ARMOR[2][1];
    public static final DeferredItem<ExoArmorItem> LEGGINGS_MK3 = ARMOR[2][2];
    public static final DeferredItem<ExoArmorItem> BOOTS_MK3 = ARMOR[2][3];
    public static final DeferredItem<ExoArmorItem> HELMET_MK4 = ARMOR[3][0];
    public static final DeferredItem<ExoArmorItem> CHESTPLATE_MK4 = ARMOR[3][1];
    public static final DeferredItem<ExoArmorItem> LEGGINGS_MK4 = ARMOR[3][2];
    public static final DeferredItem<ExoArmorItem> BOOTS_MK4 = ARMOR[3][3];

    /** Module items per kind, index level - 1. */
    private static final Map<ExoModuleKind, List<DeferredItem<ExoModuleItem>>> MODULES = new EnumMap<>(ExoModuleKind.class);

    static {
        for (ExoModuleKind kind : ExoModuleKind.values()) {
            List<DeferredItem<ExoModuleItem>> levels = new ArrayList<>();
            for (int level = 1; level <= kind.maxLevel(); level++) {
                Rarity rarity = switch (kind.minMark(level)) {
                    case 1 -> Rarity.COMMON;
                    case 2 -> Rarity.UNCOMMON;
                    case 3 -> Rarity.RARE;
                    default -> Rarity.EPIC;
                };
                if (kind == ExoModuleKind.FLIGHT) rarity = Rarity.EPIC;
                final Rarity r = rarity;
                final int lv = level;
                levels.add(ITEMS.registerItem(kind.itemName(level), p -> new ExoModuleItem(p.rarity(r), kind, lv)));
            }
            MODULES.put(kind, List.copyOf(levels));
        }
    }

    private static DeferredItem<ExoArmorItem> armor(String name, ArmorItem.Type type, int mk) {
        DeferredItem<ExoArmorItem> item = ITEMS.registerItem(name, p -> new ExoArmorItem(p, ExoRegistry.material(mk), type, mk));
        TAB_ORDER.add(item);
        return item;
    }

    /** Level I of a module kind. */
    public static DeferredItem<ExoModuleItem> module(ExoModuleKind kind) {
        return module(kind, 1);
    }

    public static DeferredItem<ExoModuleItem> module(ExoModuleKind kind, int level) {
        List<DeferredItem<ExoModuleItem>> levels = MODULES.get(kind);
        return levels.get(Math.max(0, Math.min(levels.size(), level) - 1));
    }

    public static DeferredItem<ExoArmorItem> piece(int mk, EquipmentSlot slot) {
        return ARMOR[Math.max(1, Math.min(4, mk)) - 1][ExoModuleKind.slotIndex(slot)];
    }

    public static void addToTab() {
        TAB_ORDER.forEach(RoboticaTab::add);
        MODULES.values().forEach(levels -> levels.forEach(RoboticaTab::add));
    }
}
