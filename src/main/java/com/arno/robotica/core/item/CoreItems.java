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
import net.minecraft.world.item.ItemStack;

/** Balance-ladder parts, cells, boss cores and upgrade cards. Recipes: data/robotica/recipe. */
public final class CoreItems {
    private CoreItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);
    private static final List<DeferredItem<? extends Item>> TAB_ORDER = new ArrayList<>();

    // Age 0
    public static final DeferredItem<PartItem> COPPER_GEAR = part("copper_gear", 0);
    public static final DeferredItem<PartItem> CLOCKWORK_MECHANISM = part("clockwork_mechanism", 0);
    public static final DeferredItem<PartItem> WOODEN_CHASSIS = part("wooden_chassis", 0);
    public static final DeferredItem<CellItem> MAINSPRING = cell("mainspring", 240_000, 0, 200, Rarity.COMMON);

    // Age 1
    public static final DeferredItem<PartItem> IRON_PLATE = part("iron_plate", 1);
    public static final DeferredItem<PartItem> COPPER_PLATE = part("copper_plate", 1);
    public static final DeferredItem<PartItem> GOLD_PLATE = part("gold_plate", 1);
    public static final DeferredItem<PartItem> COPPER_COIL = part("copper_coil", 1);
    public static final DeferredItem<PartItem> IRON_CASING = part("iron_casing", 1);
    public static final DeferredItem<PartItem> BASIC_CIRCUIT = part("basic_circuit", 1);
    public static final DeferredItem<PartItem> ELECTRIC_MOTOR = part("electric_motor", 1);
    public static final DeferredItem<CellItem> COPPER_CELL = cell("copper_cell", 800_000, 2_000, 2_000, Rarity.COMMON);

    // Age 2
    public static final DeferredItem<PartItem> REINFORCED_CASING = part("reinforced_casing", 2);
    public static final DeferredItem<PartItem> ADVANCED_CIRCUIT = part("advanced_circuit", 2);
    public static final DeferredItem<PartItem> SERVO_ACTUATOR = part("servo_actuator", 2);
    public static final DeferredItem<CellItem> REDSTONE_CELL = cell("redstone_cell", 3_200_000, 8_000, 8_000, Rarity.UNCOMMON);
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

    /** One upgrade card per kind: robotica:upgrade_<kind>. Stackable kinds stack in a machine slot (see Upgrades). */
    public static final Map<UpgradeKind, DeferredItem<UpgradeCardItem>> CARDS = new EnumMap<>(UpgradeKind.class);

    static {
        for (UpgradeKind kind : UpgradeKind.values()) {
            Rarity rarity = kind.age >= 2 ? Rarity.UNCOMMON : Rarity.COMMON;
            CARDS.put(kind, ITEMS.registerItem(kind.itemName(), p -> new UpgradeCardItem(p.rarity(rarity), kind)));
        }
    }

    public static DeferredItem<UpgradeCardItem> card(UpgradeKind kind) {
        return CARDS.get(kind);
    }

    /** A stack of {@code count} cards of a kind, e.g. three speed cards for "speed 3". */
    public static ItemStack cards(UpgradeKind kind, int count) {
        return new ItemStack(CARDS.get(kind).get(), Math.max(1, count));
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
        CARDS.values().forEach(RoboticaTab::add);
    }
}
