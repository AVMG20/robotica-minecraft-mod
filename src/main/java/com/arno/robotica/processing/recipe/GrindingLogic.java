package com.arno.robotica.processing.recipe;

import com.arno.robotica.Robotica;
import com.arno.robotica.processing.ProcessingConfig;
import com.arno.robotica.processing.ProcessingRegistry;
import com.arno.robotica.processing.media.GrindingByproducts;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What the Grinder makes of an input. Order: {@code robotica:grinding} recipes first, then the generic tag rules, so
 * every mod's ores work without a recipe:
 * <ul>
 *   <li>{@code c:ores/<m>} -> {@code oreDustCount} (2) x {@code c:dusts/<m>}; without a dust but with {@code c:gems/<m>},
 *       {@code gemOreCount} (2) gems</li>
 *   <li>{@code c:raw_materials/<m>} -> 1 dust plus a {@code rawBonusChance} (25%) chance of one more</li>
 *   <li>{@code c:ingots/<m>} -> 1 dust</li>
 * </ul>
 * The output is picked from the tag: a Robotica item first, then Minecraft, then the alphabetically first id.
 * Items in {@code robotica:grinding_blacklist} are never ground. Grinding media and Fortune cards only boost inputs in
 * {@code c:ores} or {@code c:raw_materials} (never ingots, so dust -> ingot -> dust can not loop).
 */
public final class GrindingLogic {
    private GrindingLogic() {}

    public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM, Robotica.id("grinding_blacklist"));
    public static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));
    public static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "raw_materials"));

    public enum Kind { RECIPE, ORE, GEM_ORE, RAW, INGOT }

    /**
     * One grinding job: the main output, rolled extras, the lowest Mk, the base ticks (0 = config) and whether media and
     * Fortune cards boost it.
     */
    public record Plan(Kind kind, ItemStack main, List<GrindingRecipe.Extra> extras, int minTier, int time, boolean boostable) {}

    /** {@code plan} is null when the input can not be ground here; {@code neededTier} > 0 names the Mk a recipe wants. */
    public record Lookup(@Nullable Plan plan, int neededTier) {
        public static final Lookup NONE = new Lookup(null, 0);
    }

    /** Result of the tag rules for one item (cached, cleared when tags reload). */
    private record TagPlan(Kind kind, Item output, String material) {}

    private static final Map<Item, Optional<TagPlan>> TAG_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger GENERATION = new AtomicInteger();

    /** Bumps on every tag or data reload; block entities drop their cached plan when it changes. */
    public static int generation() {
        return GENERATION.get();
    }

    public static void invalidate() {
        TAG_CACHE.clear();
        GENERATION.incrementAndGet();
    }

    // ------------------------------------------------------------------ lookup

    public static Lookup find(Level level, ItemStack input, int tier) {
        if (input.isEmpty() || input.is(BLACKLIST)) return Lookup.NONE;
        List<RecipeHolder<GrindingRecipe>> matches = level.getRecipeManager()
                .getRecipesFor(ProcessingRegistry.GRINDING_TYPE.get(), new SingleRecipeInput(input), level);
        List<GrindingRecipe> recipes = new ArrayList<>(matches.size());
        for (RecipeHolder<GrindingRecipe> holder : matches) {
            if (!holder.value().byTag()) recipes.add(holder.value());
        }
        if (!recipes.isEmpty()) return pick(recipes, tier, isBoostable(input));
        Plan plan = tagPlan(input);
        return plan == null ? Lookup.NONE : new Lookup(plan, 0);
    }

    /** True when a real (not {@code by_tag}) grinding recipe takes this input. */
    public static boolean hasRecipe(Level level, ItemStack input) {
        for (RecipeHolder<GrindingRecipe> holder : level.getRecipeManager()
                .getRecipesFor(ProcessingRegistry.GRINDING_TYPE.get(), new SingleRecipeInput(input), level)) {
            if (!holder.value().byTag()) return true;
        }
        return false;
    }

    /** The first recipe the Mk may run, else the lowest Mk that would run one. */
    public static Lookup pick(List<GrindingRecipe> recipes, int tier, boolean boostable) {
        int lowest = Integer.MAX_VALUE;
        for (GrindingRecipe r : recipes) {
            if (r.minTier() <= tier) {
                return new Lookup(new Plan(Kind.RECIPE, r.result().copy(), r.extras(), r.minTier(), r.time(), boostable), 0);
            }
            lowest = Math.min(lowest, r.minTier());
        }
        return lowest == Integer.MAX_VALUE ? Lookup.NONE : new Lookup(null, lowest);
    }

    public static boolean isBoostable(ItemStack input) {
        return input.is(ORES) || input.is(RAW_MATERIALS);
    }

    /** Plan from the c: tag rules, or null. Counts come from the config, so they follow config changes. */
    @Nullable
    public static Plan tagPlan(ItemStack input) {
        if (input.isEmpty() || input.is(BLACKLIST)) return null;
        TagPlan tp = TAG_CACHE.computeIfAbsent(input.getItem(), item -> Optional.ofNullable(computeTagPlan(item))).orElse(null);
        if (tp == null) return null;
        Item out = tp.output();
        return switch (tp.kind()) {
            case ORE -> new Plan(Kind.ORE, new ItemStack(out, ProcessingConfig.oreDustCount()), List.of(), 1, 0, true);
            case GEM_ORE -> ProcessingConfig.gemOreCount() <= 0 ? null
                    : new Plan(Kind.GEM_ORE, new ItemStack(out, ProcessingConfig.gemOreCount()), List.of(), 1, 0, true);
            case RAW -> {
                int count = ProcessingConfig.rawDustCount();
                float chance = ProcessingConfig.rawBonusChance();
                List<GrindingRecipe.Extra> extras = chance > 0 ? List.of(new GrindingRecipe.Extra(new ItemStack(out), chance)) : List.of();
                yield count <= 0 ? null : new Plan(Kind.RAW, new ItemStack(out, count), extras, 1, 0, true);
            }
            case INGOT -> ProcessingConfig.ingotDustCount() <= 0 ? null
                    : new Plan(Kind.INGOT, new ItemStack(out, ProcessingConfig.ingotDustCount()), List.of(), 1, 0, false);
            default -> null;
        };
    }

    /** The material a tag plan works on ("iron"), or null. For JEI grouping and tests. */
    @Nullable
    public static String tagMaterial(ItemStack input) {
        TagPlan tp = TAG_CACHE.computeIfAbsent(input.getItem(), item -> Optional.ofNullable(computeTagPlan(item))).orElse(null);
        return tp == null ? null : tp.material();
    }

    @Nullable
    private static TagPlan computeTagPlan(Item item) {
        String ore = null, raw = null, ingot = null;
        for (TagKey<Item> tag : item.builtInRegistryHolder().tags().sorted(Comparator.comparing(t -> t.location().toString())).toList()) {
            ResourceLocation id = tag.location();
            if (!"c".equals(id.getNamespace())) continue;
            String path = id.getPath();
            if (ore == null) ore = material(path, "ores/");
            if (raw == null) raw = material(path, "raw_materials/");
            if (ingot == null) ingot = material(path, "ingots/");
        }
        if (ore != null) {
            Item dust = preferred(cTag("dusts/" + ore));
            if (dust != null) return new TagPlan(Kind.ORE, dust, ore);
            Item gem = preferred(cTag("gems/" + ore));
            if (gem != null) return new TagPlan(Kind.GEM_ORE, gem, ore);
            return null;
        }
        if (raw != null) {
            Item dust = preferred(cTag("dusts/" + raw));
            return dust == null ? null : new TagPlan(Kind.RAW, dust, raw);
        }
        if (ingot != null) {
            Item dust = preferred(cTag("dusts/" + ingot));
            return dust == null ? null : new TagPlan(Kind.INGOT, dust, ingot);
        }
        return null;
    }

    @Nullable
    private static String material(String path, String prefix) {
        if (!path.startsWith(prefix)) return null;
        String rest = path.substring(prefix.length());
        return rest.isEmpty() || rest.contains("/") ? null : rest;
    }

    public static TagKey<Item> cTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    /** The item a tag stands for: a Robotica item, else Minecraft, else the alphabetically first id. Null if empty. */
    @Nullable
    public static Item preferred(TagKey<Item> tag) {
        Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.getTag(tag);
        if (set.isEmpty()) return null;
        Item best = null;
        String bestKey = null;
        for (Holder<Item> holder : set.get()) {
            Item item = holder.value();
            if (item == Items.AIR || item.builtInRegistryHolder().is(BLACKLIST)) continue;
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

    /** An item id or a {@code #tag} from data or config, resolved to an item; null when it does not exist. */
    @Nullable
    public static Item resolve(String ref) {
        if (ref == null || ref.isBlank()) return null;
        if (ref.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(ref.substring(1));
            return id == null ? null : preferred(TagKey.create(Registries.ITEM, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(ref);
        if (id == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? null : item;
    }

    // ------------------------------------------------------------------ rolls

    /** Whole part always, the fraction as a chance of one more. */
    public static int roll(double amount, RandomSource random) {
        int whole = (int) Math.floor(amount + 1e-6);
        double frac = amount - whole;
        return whole + (frac > 1e-6 && random.nextDouble() < frac ? 1 : 0);
    }

    /** The byproduct grinding media shakes out of this input: its data map entry, else a random fallback. */
    public static ItemStack byproduct(ItemStack input, RandomSource random) {
        GrindingByproducts entry = GrindingByproducts.of(input);
        if (entry != null) {
            for (String ref : entry.byproducts()) {
                Item item = resolve(ref);
                if (item != null) return new ItemStack(item);
            }
        }
        List<Item> options = new ArrayList<>();
        for (String ref : ProcessingConfig.fallbackByproducts()) {
            Item item = resolve(ref);
            if (item != null && !options.contains(item)) options.add(item);
        }
        return options.isEmpty() ? ItemStack.EMPTY : new ItemStack(options.get(random.nextInt(options.size())));
    }

    // ------------------------------------------------------------------ JEI / docs

    /** One generic tag rule, for recipe viewers: the inputs it covers and what one of them makes. */
    public record TagRule(ResourceLocation id, Kind kind, List<Item> inputs, ItemStack main, List<GrindingRecipe.Extra> extras) {}

    /**
     * Every tag rule that applies in this pack, grouped by kind and material. Inputs that a recipe already covers are
     * left out ({@code coveredByRecipe} answers that).
     */
    public static List<TagRule> tagRules(java.util.function.Predicate<ItemStack> coveredByRecipe) {
        Map<String, TagRule> rules = new java.util.TreeMap<>();
        for (String prefix : new String[]{"ores", "raw_materials", "ingots"}) {
            Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.getTag(cTag(prefix));
            if (set.isEmpty()) continue;
            for (Holder<Item> holder : set.get()) {
                ItemStack stack = new ItemStack(holder.value());
                Plan plan = tagPlan(stack);
                if (plan == null || coveredByRecipe.test(stack)) continue;
                String material = tagMaterial(stack);
                String key = plan.kind().name().toLowerCase(java.util.Locale.ROOT) + "/" + material;
                TagRule rule = rules.get(key);
                if (rule == null) {
                    rule = new TagRule(Robotica.id("grinding/tag/" + key), plan.kind(), new ArrayList<>(), plan.main(), plan.extras());
                    rules.put(key, rule);
                }
                if (!rule.inputs().contains(holder.value())) rule.inputs().add(holder.value());
            }
        }
        return new ArrayList<>(rules.values());
    }
}
