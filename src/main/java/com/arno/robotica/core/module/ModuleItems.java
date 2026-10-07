package com.arno.robotica.core.module;

import com.arno.robotica.Robotica;
import com.arno.robotica.core.RoboticaTab;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Every module item: one per kind and level. Recipes: scripts/data/gear_recipes.py and exo_recipes.py. */
public final class ModuleItems {
    private ModuleItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Robotica.MODID);

    /** Module items per kind, index level - 1. */
    private static final Map<ModuleKind, List<DeferredItem<ModuleItem>>> ITEMS_BY_KIND = new EnumMap<>(ModuleKind.class);

    static {
        for (ModuleKind kind : ModuleKind.values()) {
            List<DeferredItem<ModuleItem>> levels = new ArrayList<>();
            for (int level = 1; level <= kind.maxLevel(); level++) {
                Rarity rarity = switch (kind.minTier(level)) {
                    case 1 -> Rarity.COMMON;
                    case 2 -> Rarity.UNCOMMON;
                    case 3 -> Rarity.RARE;
                    default -> Rarity.EPIC;
                };
                if (kind == ModuleKind.FLIGHT) rarity = Rarity.EPIC;
                final Rarity r = rarity;
                final int lv = level;
                levels.add(ITEMS.registerItem(kind.itemName(level), p -> new ModuleItem(p.rarity(r), kind, lv)));
            }
            ITEMS_BY_KIND.put(kind, List.copyOf(levels));
        }
    }

    /** The module item of a kind at a level (clamped to the kind's levels). */
    public static DeferredItem<ModuleItem> get(ModuleKind kind, int level) {
        List<DeferredItem<ModuleItem>> levels = ITEMS_BY_KIND.get(kind);
        return levels.get(Math.max(0, Math.min(levels.size(), level) - 1));
    }

    /** Adds the module items of the matching kinds to the creative tab (called by gear and exo for their place). */
    public static void addToTab(Predicate<ModuleKind> which) {
        for (ModuleKind kind : ModuleKind.values()) {
            if (which.test(kind)) ITEMS_BY_KIND.get(kind).forEach(RoboticaTab::add);
        }
    }
}
