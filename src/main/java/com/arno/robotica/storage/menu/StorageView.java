package com.arno.robotica.storage.menu;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The view model of the terminal GUI (pure logic, no client classes): the stored stacks merged by item and components,
 * filtered by a search text, sorted, and cut into proper stacks (at most the item's max stack size each), so the grid
 * looks like a sorted chest. The result is only a picture of the inventory; every click is resolved against the real
 * inventory on the server.
 *
 * <p>Search: words must all match the item's name (in the server's language) or its id. {@code @mod} matches the mod
 * (namespace prefix), {@code #tag} matches an item tag path.
 */
public final class StorageView {
    private StorageView() {}

    public static final int MAX_FILTER = 64;

    public enum Sort {
        NAME, COUNT;

        public static Sort byOrdinal(int ordinal) {
            Sort[] all = values();
            return all[Math.floorMod(ordinal, all.length)];
        }

        public Sort next() {
            return byOrdinal(ordinal() + 1);
        }
    }

    private record Entry(ItemStack key, int total, String name) {}

    /** Builds the view list from the stored stacks (empty ones are skipped). */
    public static List<ItemStack> build(List<ItemStack> source, String filter, Sort sort) {
        Object2IntLinkedOpenCustomHashMap<ItemStack> totals = new Object2IntLinkedOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        for (ItemStack stack : source) {
            if (stack.isEmpty()) continue;
            totals.addTo(stack.copyWithCount(1), stack.getCount());
        }
        List<String> terms = terms(filter);
        List<Entry> entries = new ArrayList<>();
        for (Object2IntLinkedOpenCustomHashMap.Entry<ItemStack> e : totals.object2IntEntrySet()) {
            ItemStack key = e.getKey();
            String name = key.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (matches(key, name, terms)) entries.add(new Entry(key, e.getIntValue(), name));
        }
        Comparator<Entry> byName = Comparator.<Entry, String>comparing(Entry::name)
                .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.key().getItem()).toString());
        entries.sort(sort == Sort.COUNT
                ? Comparator.<Entry>comparingInt(Entry::total).reversed().thenComparing(byName)
                : byName);

        List<ItemStack> out = new ArrayList<>();
        for (Entry e : entries) {
            int max = Math.max(1, e.key().getMaxStackSize());
            int left = e.total();
            while (left > 0) {
                int n = Math.min(max, left);
                out.add(e.key().copyWithCount(n));
                left -= n;
            }
        }
        return out;
    }

    /** True when the stack passes the search text (an empty text passes everything). */
    public static boolean matches(ItemStack stack, String filter) {
        return matches(stack, stack.getHoverName().getString().toLowerCase(Locale.ROOT), terms(filter));
    }

    private static List<String> terms(String filter) {
        List<String> terms = new ArrayList<>();
        if (filter == null) return terms;
        String f = filter.length() > MAX_FILTER ? filter.substring(0, MAX_FILTER) : filter;
        for (String t : f.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!t.isEmpty() && !t.equals("@") && !t.equals("#")) terms.add(t);
        }
        return terms;
    }

    private static boolean matches(ItemStack stack, String name, List<String> terms) {
        if (terms.isEmpty()) return true;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        String spaced = path.replace('_', ' ');
        for (String term : terms) {
            boolean ok;
            if (term.startsWith("@")) {
                ok = id.getNamespace().startsWith(term.substring(1));
            } else if (term.startsWith("#")) {
                String tag = term.substring(1);
                ok = stack.getTags().anyMatch(t -> t.location().getPath().contains(tag) || t.location().toString().contains(tag));
            } else {
                ok = name.contains(term) || path.contains(term) || spaced.contains(term);
            }
            if (!ok) return false;
        }
        return true;
    }
}
