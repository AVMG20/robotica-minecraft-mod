package com.arno.robotica.processing;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Balance values of the ore processing module (server config, file robotica-processing-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 * Grinding media stats are data driven (data map {@code robotica:grinding_media}), not here.
 */
public final class ProcessingConfig {
    private ProcessingConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue[] UPGRADE_SLOTS = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.DoubleValue[] TIER_SPEED = new ModConfigSpec.DoubleValue[4];
    private static final ModConfigSpec.IntValue[] SPEED_CAP = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] EFFICIENCY_CAP = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] FORTUNE_CAP = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] RANGE_CAP = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] BUFFER = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] MAX_INPUT = new ModConfigSpec.IntValue[4];

    private static final ModConfigSpec.IntValue GRINDER_TICKS;
    private static final ModConfigSpec.IntValue GRINDER_POWER;
    private static final ModConfigSpec.IntValue ORE_DUST_COUNT;
    private static final ModConfigSpec.IntValue GEM_ORE_COUNT;
    private static final ModConfigSpec.IntValue RAW_DUST_COUNT;
    private static final ModConfigSpec.DoubleValue RAW_BONUS_CHANCE;
    private static final ModConfigSpec.IntValue INGOT_DUST_COUNT;
    private static final ModConfigSpec.DoubleValue FORTUNE_BONUS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FALLBACK_BYPRODUCTS;

    private static final ModConfigSpec.DoubleValue FURNACE_TIME_FACTOR;
    private static final ModConfigSpec.IntValue FURNACE_POWER;
    private static final ModConfigSpec.IntValue[] LANES = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.DoubleValue XP_PER_FORTUNE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Machine tiers (Mk1-Mk4), shared by the Grinder and the Electric Furnace.").push("processing_tiers");
        int[] slots = {2, 3, 4, 5};
        double[] speeds = {1.0, 1.5, 2.0, 3.0};
        int[] speedCaps = {2, 4, 6, 8};
        int[] effCaps = {2, 3, 4, 4};
        int[] fortuneCaps = {0, 1, 2, 3};
        int[] rangeCaps = {0, 1, 2, 3};
        int[] buffers = {20_000, 80_000, 320_000, 1_280_000};
        int[] inputs = {4_000, 16_000, 64_000, 256_000};
        for (int i = 0; i < 4; i++) {
            int mk = i + 1;
            UPGRADE_SLOTS[i] = b.comment("Mk" + mk + ": upgrade slots (one card kind per slot).")
                    .defineInRange("upgradeSlotsMk" + mk, slots[i], 1, 5);
            TIER_SPEED[i] = b.comment("Mk" + mk + ": work speed multiplier (FE/t rises with it, FE per item stays the same).")
                    .defineInRange("tierSpeedMk" + mk, speeds[i], 0.1, 100.0);
            SPEED_CAP[i] = b.comment("Mk" + mk + ": most Speed cards that count.")
                    .defineInRange("speedCapMk" + mk, speedCaps[i], 0, 8);
            EFFICIENCY_CAP[i] = b.comment("Mk" + mk + ": most Efficiency cards that count.")
                    .defineInRange("efficiencyCapMk" + mk, effCaps[i], 0, 4);
            FORTUNE_CAP[i] = b.comment("Mk" + mk + ": most Fortune cards that count (Grinder: more ore output, Electric Furnace: more experience).")
                    .defineInRange("fortuneCapMk" + mk, fortuneCaps[i], 0, 3);
            RANGE_CAP[i] = b.comment("Mk" + mk + ": most Range cards that count (Electric Furnace: +1 item per lane and cycle each).")
                    .defineInRange("rangeCapMk" + mk, rangeCaps[i], 0, 4);
            BUFFER[i] = b.comment("Mk" + mk + ": energy buffer (FE).")
                    .defineInRange("bufferMk" + mk, buffers[i], 1_000, Integer.MAX_VALUE / 2);
            MAX_INPUT[i] = b.comment("Mk" + mk + ": most FE/t the machine accepts from cables or its battery slot.")
                    .defineInRange("maxInputMk" + mk, inputs[i], 1, Integer.MAX_VALUE / 2);
        }
        b.pop();

        b.comment("Grinder: ores, raw ores and ingots to dust, by c: tags, plus robotica:grinding recipes.").push("grinder");
        GRINDER_TICKS = b.comment("Ticks per item at Mk1 without cards (a recipe may set its own time).")
                .defineInRange("grinderTicks", 200, 1, 72_000);
        GRINDER_POWER = b.comment("FE/t at Mk1 without cards. 200 ticks x 20 FE/t = 4,000 FE per ore, like Thermal's Pulverizer.")
                .defineInRange("grinderPower", 20, 0, 1_000_000);
        ORE_DUST_COUNT = b.comment("Dusts per ore block (c:ores/<metal> -> c:dusts/<metal>). 2 = classic ore doubling, 1 turns doubling off.")
                .defineInRange("oreDustCount", 2, 1, 16);
        GEM_ORE_COUNT = b.comment("Gems per ore block for ores without a dust but with a c:gems/<name> tag (diamond, emerald, quartz...).")
                .defineInRange("gemOreCount", 2, 0, 16);
        RAW_DUST_COUNT = b.comment("Dusts per raw ore (c:raw_materials/<metal>).")
                .defineInRange("rawDustCount", 1, 0, 16);
        RAW_BONUS_CHANCE = b.comment("Chance of one more dust per raw ore.")
                .defineInRange("rawBonusChance", 0.25, 0.0, 1.0);
        INGOT_DUST_COUNT = b.comment("Dusts per ingot (c:ingots/<metal>). 0 turns ingot grinding off.")
                .defineInRange("ingotDustCount", 1, 0, 16);
        FORTUNE_BONUS = b.comment("Extra main output per Fortune card on ores and raw ores (0.1 = +10%). Gem ores and ingots get none.")
                .defineInRange("fortuneBonus", 0.10, 0.0, 10.0);
        FALLBACK_BYPRODUCTS = b.comment("Byproducts of grinding media when a metal ore or raw ore (one with a c:ingots/<name> tag) has no entry "
                        + "in the robotica:grinding_byproducts data map. Item ids or #tags; one that exists is picked at random.")
                .defineListAllowEmpty("fallbackByproducts", List.of("#c:dusts/iron", "#c:dusts/copper", "#c:dusts/gold", "#c:dusts/redstone"),
                        () -> "#c:dusts/iron", o -> o instanceof String s && !s.isBlank());
        b.pop();

        b.comment("Electric Furnace: powered smelting of every smelting recipe, several lanes in parallel.").push("electric_furnace");
        FURNACE_TIME_FACTOR = b.comment("Recipe cooking time multiplier at Mk1 (0.5: a 200 tick furnace recipe takes 100 ticks).")
                .defineInRange("furnaceTimeFactor", 0.5, 0.01, 10.0);
        FURNACE_POWER = b.comment("FE/t per lane and item at Mk1 without cards (100 ticks x 20 FE/t = 2,000 FE per item).")
                .defineInRange("furnacePower", 20, 0, 1_000_000);
        int[] lanes = {1, 2, 4, 8};
        for (int i = 0; i < 4; i++) {
            LANES[i] = b.comment("Mk" + (i + 1) + ": lanes that smelt in parallel (each has its own input and output slot).")
                    .defineInRange("lanesMk" + (i + 1), lanes[i], 1, 8);
        }
        XP_PER_FORTUNE = b.comment("Extra experience per Fortune card (0.5 = +50%). Never for inputs in c:dusts, so ingot -> dust -> ingot farms no experience.")
                .defineInRange("xpPerFortune", 0.5, 0.0, 10.0);
        b.pop();
        SPEC = b.build();
    }

    private static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private static int mk(int tier) {
        return Math.max(1, Math.min(4, tier)) - 1;
    }

    public static int upgradeSlots(int tier) { return get(UPGRADE_SLOTS[mk(tier)]); }
    public static double tierSpeed(int tier) { return get(TIER_SPEED[mk(tier)]); }
    public static int speedCap(int tier) { return get(SPEED_CAP[mk(tier)]); }
    public static int efficiencyCap(int tier) { return get(EFFICIENCY_CAP[mk(tier)]); }
    public static int fortuneCap(int tier) { return get(FORTUNE_CAP[mk(tier)]); }
    public static int rangeCap(int tier) { return get(RANGE_CAP[mk(tier)]); }
    public static int buffer(int tier) { return get(BUFFER[mk(tier)]); }
    public static int maxInput(int tier) { return get(MAX_INPUT[mk(tier)]); }

    public static int grinderTicks() { return get(GRINDER_TICKS); }
    public static int grinderPower() { return get(GRINDER_POWER); }
    public static int oreDustCount() { return get(ORE_DUST_COUNT); }
    public static int gemOreCount() { return get(GEM_ORE_COUNT); }
    public static int rawDustCount() { return get(RAW_DUST_COUNT); }
    public static float rawBonusChance() { return (float) get(RAW_BONUS_CHANCE); }
    public static int ingotDustCount() { return get(INGOT_DUST_COUNT); }
    public static double fortuneBonus() { return get(FORTUNE_BONUS); }

    public static List<? extends String> fallbackByproducts() {
        return SPEC.isLoaded() ? FALLBACK_BYPRODUCTS.get() : FALLBACK_BYPRODUCTS.getDefault();
    }

    public static double furnaceTimeFactor() { return get(FURNACE_TIME_FACTOR); }
    public static int furnacePower() { return get(FURNACE_POWER); }
    public static int lanes(int tier) { return get(LANES[mk(tier)]); }
    public static double xpPerFortune() { return get(XP_PER_FORTUNE); }
}
