package com.arno.robotica.exo;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.exo.item.ExoArmorItem;
import com.arno.robotica.core.module.ModuleItems;
import com.arno.robotica.core.module.ModuleKind;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/** The four Exo armor pieces in four marks (module items: core ModuleItems). Recipes: scripts/data/exo_recipes.py. */
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

    private static DeferredItem<ExoArmorItem> armor(String name, ArmorItem.Type type, int mk) {
        DeferredItem<ExoArmorItem> item = ITEMS.registerItem(name, p -> new ExoArmorItem(p, ExoRegistry.material(mk), type, mk));
        TAB_ORDER.add(item);
        return item;
    }

    public static DeferredItem<ExoArmorItem> piece(int mk, EquipmentSlot slot) {
        return ARMOR[Math.max(1, Math.min(4, mk)) - 1][ExoSuit.index(slot)];
    }

    /** The armor pieces, then the armor-only module items (shared modules are listed with the tools). */
    public static void addToTab() {
        TAB_ORDER.forEach(RoboticaTab::add);
        ModuleItems.addToTab(ModuleKind::armorOnly);
    }
}
