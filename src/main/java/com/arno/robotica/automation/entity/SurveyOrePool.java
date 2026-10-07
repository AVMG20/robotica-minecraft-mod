package com.arno.robotica.automation.entity;

import com.arno.robotica.automation.AutomationConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ores a Survey Rig can make: every item of the {@code c:ores} tag (vanilla, Robotica's and other mods' ores),
 * grouped into kinds by their {@code c:ores/<name>} tag so stone, deepslate and nether variants share one weight.
 * Weights and Magma Core kinds come from the server config. Built lazily, rebuilt after a tag or config reload.
 */
public final class SurveyOrePool {
    /** One ore kind: its weight, whether it needs a Magma Core, and its variants (picked evenly). */
    public record Kind(String key, int weight, boolean needsCore, List<Item> items) {}

    private static volatile SurveyOrePool cached;

    private final List<Kind> kinds;

    private SurveyOrePool(List<Kind> kinds) {
        this.kinds = kinds;
    }

    public static SurveyOrePool get() {
        SurveyOrePool pool = cached;
        if (pool == null) {
            pool = build(AutomationConfig.surveyOreWeights(), AutomationConfig.surveyDefaultWeight(), AutomationConfig.surveyCoreOres());
            cached = pool;
        }
        return pool;
    }

    /** Drops the cached pool (tags reloaded, config changed). */
    public static void invalidate() {
        cached = null;
    }

    public List<Kind> kinds() {
        return kinds;
    }

    @Nullable
    public Kind kind(Item item) {
        for (Kind k : kinds) if (k.items().contains(item)) return k;
        return null;
    }

    /** Sum of the weights the rig can roll, with or without a Magma Core. */
    public int totalWeight(boolean core) {
        int total = 0;
        for (Kind k : kinds) if (core || !k.needsCore()) total += k.weight();
        return total;
    }

    /**
     * Weight of a kind in hundredths, with a higher Mk's rare bonus: kinds of at most {@code rareWeight} weigh
     * {@code rareBonus} percent more (Mk4 +200%: diamonds three times as often).
     */
    public static long weight(Kind kind, int rareBonus, int rareWeight) {
        long w = kind.weight() * 100L;
        return kind.weight() <= rareWeight ? w * (100L + Math.max(0, rareBonus)) / 100L : w;
    }

    private long total(boolean core, int rareBonus, int rareWeight) {
        long total = 0;
        for (Kind k : kinds) if (core || !k.needsCore()) total += weight(k, rareBonus, rareWeight);
        return total;
    }

    /** Chance in [0, 1] that one roll gives this kind. */
    public double chance(Kind kind, boolean core) {
        return chance(kind, core, 0, 0);
    }

    /** Same, with a Mk's rare bonus (see {@link #weight}). */
    public double chance(Kind kind, boolean core, int rareBonus, int rareWeight) {
        long total = total(core, rareBonus, rareWeight);
        if (total <= 0 || kind.weight() <= 0 || (kind.needsCore() && !core)) return 0.0;
        return (double) weight(kind, rareBonus, rareWeight) / total;
    }

    /** A random ore item by weight, or null when nothing can be rolled. */
    @Nullable
    public Item roll(RandomSource random, boolean core) {
        return roll(random, core, 0, 0);
    }

    /** Same, with a Mk's rare bonus (see {@link #weight}). */
    @Nullable
    public Item roll(RandomSource random, boolean core, int rareBonus, int rareWeight) {
        long total = total(core, rareBonus, rareWeight);
        if (total <= 0) return null;
        long r = (long) (random.nextDouble() * total);
        for (Kind k : kinds) {
            if (!core && k.needsCore()) continue;
            r -= weight(k, rareBonus, rareWeight);
            if (r < 0) return k.items().get(random.nextInt(k.items().size()));
        }
        return null;
    }

    // ---- building ----

    public static SurveyOrePool build(Collection<? extends String> weightEntries, int defaultWeight, Collection<? extends String> coreEntries) {
        Map<ResourceLocation, Integer> tagWeights = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> itemWeights = new LinkedHashMap<>();
        for (String entry : weightEntries) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0) continue;
            String id = entry.substring(0, eq).trim();
            int weight;
            try {
                weight = Math.max(0, Integer.parseInt(entry.substring(eq + 1).trim()));
            } catch (NumberFormatException e) {
                continue;
            }
            boolean tag = id.startsWith("#");
            ResourceLocation loc = ResourceLocation.tryParse(tag ? id.substring(1) : id);
            if (loc == null) continue;
            (tag ? tagWeights : itemWeights).put(loc, weight);
        }
        List<ResourceLocation> coreTags = new ArrayList<>();
        List<ResourceLocation> coreItems = new ArrayList<>();
        for (String entry : coreEntries) {
            String id = entry.trim();
            boolean tag = id.startsWith("#");
            ResourceLocation loc = ResourceLocation.tryParse(tag ? id.substring(1) : id);
            if (loc != null) (tag ? coreTags : coreItems).add(loc);
        }

        Map<String, List<Item>> groups = new LinkedHashMap<>();
        Map<String, Integer> weights = new LinkedHashMap<>();
        Map<String, Boolean> needsCore = new LinkedHashMap<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(Tags.Items.ORES)) {
            Item item = holder.value();
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String key;
            int weight;
            if (itemWeights.containsKey(itemId)) {
                key = itemId.toString();
                weight = itemWeights.get(itemId);
            } else {
                ResourceLocation kindTag = kindTag(holder, tagWeights);
                key = kindTag != null ? "#" + kindTag : itemId.toString();
                weight = kindTag != null && tagWeights.containsKey(kindTag) ? tagWeights.get(kindTag) : defaultWeight;
            }
            boolean core = coreItems.contains(itemId) || coreTags.stream().anyMatch(t -> holder.is(TagKey.create(Registries.ITEM, t)));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            weights.putIfAbsent(key, weight);
            needsCore.merge(key, core, Boolean::logicalOr);
        }
        List<Kind> kinds = new ArrayList<>();
        for (Map.Entry<String, List<Item>> e : groups.entrySet()) {
            int weight = weights.get(e.getKey());
            if (weight <= 0) continue;
            List<Item> items = e.getValue();
            items.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
            kinds.add(new Kind(e.getKey(), weight, needsCore.get(e.getKey()), List.copyOf(items)));
        }
        kinds.sort(Comparator.comparingInt(Kind::weight).reversed().thenComparing(Kind::key));
        return new SurveyOrePool(List.copyOf(kinds));
    }

    /** The {@code ores/<name>} tag that names the item's ore kind: a configured one first, else any, by id. */
    @Nullable
    private static ResourceLocation kindTag(Holder<Item> holder, Map<ResourceLocation, Integer> configured) {
        ResourceLocation best = null;
        for (TagKey<Item> tag : holder.tags().toList()) {
            ResourceLocation loc = tag.location();
            if (!loc.getPath().startsWith("ores/")) continue;
            boolean known = configured.containsKey(loc);
            boolean bestKnown = best != null && configured.containsKey(best);
            if (best == null || (known && !bestKnown) || (known == bestKnown && loc.toString().compareTo(best.toString()) < 0)) best = loc;
        }
        return best;
    }
}
