package com.arno.robotica.core.item;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import com.arno.robotica.core.upgrade.UpgradeCardItem;
import com.arno.robotica.core.upgrade.UpgradeKind;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Balance-ladder parts, cells, boss cores and upgrade cards. Recipes: data/robotica/recipe. */
public final class CoreItems {
    private CoreItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    // Age 0
    public static final DeferredItem<PartItem> COPPER_GEAR = part("copper_gear", 0);
    public static final DeferredItem<PartItem> CLOCKWORK_MECHANISM = part("clockwork_mechanism", 0);
    public static final DeferredItem<PartItem> WOODEN_CHASSIS = part("wooden_chassis", 0);
    public static final DeferredItem<CellItem> MAINSPRING = cell("mainspring", 576_000, 0, 200, Rarity.COMMON);

    // Age 1
    public static final DeferredItem<PartItem> IRON_PLATE = part("iron_plate", 1);
    public static final DeferredItem<PartItem> COPPER_PLATE = part("copper_plate", 1);
    public static final DeferredItem<PartItem> GOLD_PLATE = part("gold_plate", 1);
    public static final DeferredItem<PartItem> COPPER_COIL = part("copper_coil", 1);
    public static final DeferredItem<PartItem> IRON_CASING = part("iron_casing", 1);
    public static final DeferredItem<PartItem> BASIC_CIRCUIT = part("basic_circuit", 1);
    public static final DeferredItem<PartItem> ELECTRIC_MOTOR = part("electric_motor", 1);
    public static final DeferredItem<CellItem> COPPER_CELL = cell("copper_cell", 2_304_000, 2_000, 2_000, Rarity.COMMON);

    // Age 2
    public static final DeferredItem<PartItem> REINFORCED_CASING = part("reinforced_casing", 2);
    public static final DeferredItem<PartItem> ADVANCED_CIRCUIT = part("advanced_circuit", 2);
    public static final DeferredItem<PartItem> SERVO_ACTUATOR = part("servo_actuator", 2);
    public static final DeferredItem<CellItem> REDSTONE_CELL = cell("redstone_cell", 6_912_000, 8_000, 8_000, Rarity.UNCOMMON);
    public static final DeferredItem<PartItem> SERVO_CORE = core("servo_core", 2, Rarity.UNCOMMON);

    // Age 3
    public static final DeferredItem<PartItem> BLAZING_CASING = part("blazing_casing", 3);
    public static final DeferredItem<PartItem> QUANTUM_CIRCUIT = part("quantum_circuit", 3);
    public static final DeferredItem<PartItem> PLASMA_ACTUATOR = part("plasma_actuator", 3);
    public static final DeferredItem<PartItem> MAGMA_CORE = core("magma_core", 3, Rarity.RARE);

    // Age 4
    public static final DeferredItem<PartItem> NULL_CASING = part("null_casing", 4);
    public static final DeferredItem<PartItem> NULL_CIRCUIT = part("null_circuit", 4);
    public static final DeferredItem<CellItem> ENDER_CELL = cell("ender_cell", 20_736_000, 32_000, 32_000, Rarity.RARE);
    public static final DeferredItem<PartItem> ANTIGRAV_CORE = core("antigrav_core", 4, Rarity.EPIC);

    /** Upgrade cards by kind and level. Fortune has levels 2-4, silk and void only level 1. */
    public static final Map<UpgradeKind, Map<Integer, DeferredItem<UpgradeCardItem>>> UPGRADE_CARDS = new EnumMap<>(UpgradeKind.class);

    static {
        for (UpgradeKind kind : UpgradeKind.values()) {
            Map<Integer, DeferredItem<UpgradeCardItem>> byLevel = new TreeMap<>();
            for (int level = kind.minLevel; level <= kind.maxLevel; level++) {
                final int lvl = level;
                Rarity rarity = level >= 4 ? Rarity.EPIC : level == 3 ? Rarity.RARE : level == 2 ? Rarity.UNCOMMON : Rarity.COMMON;
                DeferredItem<UpgradeCardItem> card = ITEMS.registerItem(kind.itemName(level),
                        p -> new UpgradeCardItem(p.rarity(rarity), kind, lvl));
                byLevel.put(level, card);
            }
            UPGRADE_CARDS.put(kind, byLevel);
        }
    }

    public static DeferredItem<UpgradeCardItem> card(UpgradeKind kind, int level) {
        return UPGRADE_CARDS.get(kind).get(level);
    }

    private static DeferredItem<PartItem> part(String name, int age) {
        DeferredItem<PartItem> item = ITEMS.registerItem(name, p -> new PartItem(p, age));
        TAB_ORDER.add(item);
        return item;
    }

    private static DeferredItem<PartItem> core(String name, int age, Rarity rarity) {
        DeferredItem<PartItem> item = ITEMS.registerItem(name, p -> new PartItem(p.rarity(rarity).fireResistant(), age));
        TAB_ORDER.add(item);
        return item;
    }

    private static DeferredItem<CellItem> cell(String name, int capacity, int in, int out, Rarity rarity) {
        DeferredItem<CellItem> item = ITEMS.registerItem(name, p -> new CellItem(p.rarity(rarity), capacity, in, out));
        TAB_ORDER.add(item);
        return item;
    }

    public static void addToTab() {
        TAB_ORDER.forEach(RoboticaTab::add);
        UPGRADE_CARDS.values().forEach(m -> m.values().forEach(RoboticaTab::add));
    }
}
