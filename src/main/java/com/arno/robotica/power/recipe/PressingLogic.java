package com.arno.robotica.power.recipe;

import com.arno.robotica.Robotica;
import com.arno.robotica.power.PowerRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * What the Metal Press makes of an input. {@code robotica:pressing} recipes first, then the generic tag rule, so every
 * mod's metals work without a recipe (like the Grinder's tag rules): an item in {@code c:ingots/<m>} presses into one
 * {@code c:plates/<m>} when that tag has items. The plate is picked from the tag: a Robotica item first, then
 * Minecraft, then the alphabetically first id. Cached per item, cleared when tags reload.
 */
public final class PressingLogic {
    private PressingLogic() {}

    private static final Map<Item, Optional<PressingRecipe>> TAG_CACHE = new ConcurrentHashMap<>();

    public static void invalidate() {
        TAG_CACHE.clear();
    }

    /** The recipe for this input: a {@code robotica:pressing} recipe, else the ingot to plate tag rule, else null. */
    @Nullable
    public static PressingRecipe find(Level level, ItemStack input) {
        if (input.isEmpty()) return null;
        Optional<RecipeHolder<PressingRecipe>> holder = level.getRecipeManager()
                .getRecipeFor(PowerRegistry.PRESSING_TYPE.get(), new SingleRecipeInput(input), level);
        return holder.isPresent() ? holder.get().value() : tagRecipe(input);
    }

    /** The ingot to plate tag rule for this input, or null. Ignores {@code robotica:pressing} recipes. */
    @Nullable
    public static PressingRecipe tagRecipe(ItemStack input) {
        if (input.isEmpty()) return null;
        return TAG_CACHE.computeIfAbsent(input.getItem(), item -> Optional.ofNullable(compute(item))).orElse(null);
    }

    @Nullable
    private static PressingRecipe compute(Item item) {
        String metal = material(item);
        if (metal == null) return null;
        Item plate = preferred(cTag("plates/" + metal));
        if (plate == null || plate == item) return null;
        return new PressingRecipe(Ingredient.of(item), new ItemStack(plate), PressingRecipe.DEFAULT_TIME);
    }

    /** The {@code <m>} of the first {@code c:ingots/<m>} tag of this item, or null. */
    @Nullable
    private static String material(Item item) {
        for (TagKey<Item> tag : item.builtInRegistryHolder().tags().sorted(Comparator.comparing(t -> t.location().toString())).toList()) {
            ResourceLocation id = tag.location();
            if (!"c".equals(id.getNamespace()) || !id.getPath().startsWith("ingots/")) continue;
            String rest = id.getPath().substring("ingots/".length());
            if (!rest.isEmpty() && !rest.contains("/")) return rest;
        }
        return null;
    }

    private static TagKey<Item> cTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    /** The item a tag stands for: a Robotica item, else Minecraft, else the alphabetically first id. Null if empty. */
    @Nullable
    private static Item preferred(TagKey<Item> tag) {
        Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.getTag(tag);
        if (set.isEmpty()) return null;
        Item best = null;
        String bestKey = null;
        for (Holder<Item> holder : set.get()) {
            Item item = holder.value();
            if (item == Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String ns = id.getNamespace();
            String key = (Robotica.MODID.equals(ns) ? "0" : "minecraft".equals(ns) ? "1" : "2") + id;
            if (bestKey == null || key.compareTo(bestKey) < 0) {
                best = item;
                bestKey = key;
            }
        }
        return best;
    }

    /**
     * Every tag rule that applies in this pack, one per metal, for recipe viewers. Inputs that a {@code robotica:pressing}
     * recipe already covers are left out ({@code coveredByRecipe} answers that).
     */
    public static List<RecipeHolder<PressingRecipe>> tagRules(Predicate<ItemStack> coveredByRecipe) {
        Map<String, List<Item>> inputs = new TreeMap<>();
        Map<String, ItemStack> outputs = new TreeMap<>();
        Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.getTag(cTag("ingots"));
        if (set.isEmpty()) return List.of();
        for (Holder<Item> holder : set.get()) {
            ItemStack stack = new ItemStack(holder.value());
            PressingRecipe rule = tagRecipe(stack);
            if (rule == null || coveredByRecipe.test(stack)) continue;
            String metal = material(holder.value());
            inputs.computeIfAbsent(metal, m -> new ArrayList<>()).add(holder.value());
            outputs.putIfAbsent(metal, rule.result());
        }
        List<RecipeHolder<PressingRecipe>> rules = new ArrayList<>();
        inputs.forEach((metal, items) -> rules.add(new RecipeHolder<>(Robotica.id("pressing/tag/" + metal),
                new PressingRecipe(Ingredient.of(items.toArray(Item[]::new)), outputs.get(metal), PressingRecipe.DEFAULT_TIME))));
        return rules;
    }
}
