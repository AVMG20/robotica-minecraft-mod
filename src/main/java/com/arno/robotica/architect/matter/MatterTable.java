package com.arno.robotica.architect.matter;

import com.arno.robotica.Robotica;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Item to matter conversion, driven by item tags in the namespace {@code robotica}:
 * {@code robotica:matter/<grade>_<value>} (for example {@code matter/refined_4} lists items worth 4 refined matter each).
 * Datapacks can add or replace those tags, or add new ones with another value (any positive integer).
 * An item listed under several tags gets the highest value per grade.
 */
public final class MatterTable {
    private MatterTable() {}

    private static final Matter NONE = Matter.ZERO;
    private static volatile Map<Item, Matter> cache;

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        cache = null;
    }

    /** Matter of one item of this stack (not multiplied by the count). {@link Matter#ZERO} if it has no value. */
    public static Matter valueOf(ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        return valueOf(stack.getItem());
    }

    public static Matter valueOf(Item item) {
        Map<Item, Matter> map = cache;
        if (map == null) {
            map = build();
            cache = map;
        }
        return map.getOrDefault(item, NONE);
    }

    public static boolean hasValue(ItemStack stack) {
        return !valueOf(stack).isZero();
    }

    private static Map<Item, Matter> build() {
        Map<Item, int[]> values = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            HolderSet.Named<Item> named = pair.getSecond();
            var key = pair.getFirst().location();
            if (!key.getNamespace().equals(Robotica.MODID) || !key.getPath().startsWith("matter/")) return;
            String name = key.getPath().substring("matter/".length());
            int us = name.lastIndexOf('_');
            if (us < 0) return;
            Matter.Grade grade;
            int value;
            try {
                grade = Matter.Grade.valueOf(name.substring(0, us).toUpperCase(java.util.Locale.ROOT));
                value = Integer.parseInt(name.substring(us + 1));
            } catch (IllegalArgumentException e) {
                return;
            }
            if (value <= 0) return;
            for (Holder<Item> holder : named) {
                int[] v = values.computeIfAbsent(holder.value(), i -> new int[3]);
                v[grade.ordinal()] = Math.max(v[grade.ordinal()], value);
            }
        });
        Map<Item, Matter> result = new HashMap<>();
        values.forEach((item, v) -> result.put(item, new Matter(v[0], v[1], v[2])));
        return result;
    }
}
