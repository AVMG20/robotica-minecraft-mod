package com.arno.robotica.automation;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Balance for the automation module (server config robotica-automation-server.toml).
 * Static getters fall back to the defaults while the config is not loaded (early init, game tests).
 */
public final class AutomationConfig {
    private AutomationConfig() {}

    public static final ModConfigSpec SPEC;

    private static final int[] DEF_RADIUS = {4, 6, 8, 12};
    private static final int[] DEF_INTERVAL = {40, 20, 5, 1};
    private static final double[] DEF_GROWTH = {1.5, 2.0, 3.0, 5.0};

    public static final ModConfigSpec.IntValue ENERGY_BUFFER;
    public static final ModConfigSpec.IntValue STUMPY_FE_PER_TICK;
    public static final ModConfigSpec.IntValue STUMPY_FE_PER_LOG;
    public static final ModConfigSpec.IntValue STUMPY_TICKS_PER_LOG;
    public static final ModConfigSpec.IntValue SPROUT_FE_PER_TICK;
    public static final ModConfigSpec.IntValue SPROUT_FE_PER_HARVEST;
    public static final ModConfigSpec.IntValue MAX_LOGS;
    private static final int[] DEF_EXC_SIZE = {8, 12, 16, 24};
    private static final int[] DEF_EXC_INTERVAL = {60, 40, 30, 20};
    private static final int[] DEF_SURVEY_SPEED = {100, 150, 200, 300};
    private static final int[] DEF_SURVEY_RARE = {0, 50, 100, 200};
    private static final ModConfigSpec.IntValue[] EXC_SIZE = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] EXC_INTERVAL = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue EXCAVATOR_RANGE_STEP;
    private static final ModConfigSpec.IntValue EXCAVATOR_MAX_SIZE;
    private static final ModConfigSpec.IntValue EXCAVATOR_INPUT;
    private static final ModConfigSpec.IntValue[] SURVEY_SPEED = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] SURVEY_RARE = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue SURVEY_RARE_WEIGHT;
    public static final ModConfigSpec.IntValue EXCAVATOR_FE_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue GROWTH_TICKS_PER_COLUMN;
    public static final ModConfigSpec.IntValue SURVEY_INTERVAL;
    public static final ModConfigSpec.IntValue SURVEY_FE_PER_TICK;
    public static final ModConfigSpec.IntValue SURVEY_ENERGY_BUFFER;
    public static final ModConfigSpec.IntValue SURVEY_MAX_INPUT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SURVEY_ORE_WEIGHTS;
    public static final ModConfigSpec.IntValue SURVEY_DEFAULT_WEIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SURVEY_CORE_ORES;

    /** Default ore weights of the Survey Rig: common metals high, gems low, ancient debris lowest. */
    public static final List<String> DEFAULT_ORE_WEIGHTS = List.of(
            "#c:ores/coal=100", "#c:ores/copper=90", "#c:ores/iron=80",
            "#c:ores/tin=60", "#c:ores/zinc=50", "#c:ores/aluminum=50", "#c:ores/thorium=50", "#c:ores/lead=45", "#c:ores/nickel=40",
            "#c:ores/redstone=35", "#c:ores/gold=30", "#c:ores/quartz=30", "#c:ores/lapis=25", "#c:ores/silver=25",
            "#c:ores/osmium=40", "#c:ores/uranium=12", "#c:ores/pyrolite=15", "#c:ores/fluorite=20",
            "#c:ores/diamond=6", "#c:ores/emerald=4", "#c:ores/resonite=2", "#c:ores/netherite_scrap=1");
    public static final List<String> DEFAULT_CORE_ORES = List.of("#c:ores/netherite_scrap", "#c:ores/resonite");
    private static final ModConfigSpec.IntValue[] FARM_RADIUS = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.IntValue[] FARM_INTERVAL = new ModConfigSpec.IntValue[4];
    private static final ModConfigSpec.DoubleValue[] FARM_GROWTH = new ModConfigSpec.DoubleValue[4];

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Area workers: Stumpy, Sprout, Excavator").push("automation");
        ENERGY_BUFFER = b.comment("Internal FE buffer of every area worker (the battery slot refills it).")
                .defineInRange("energyBuffer", 20_000, 1_000, 100_000_000);
        STUMPY_FE_PER_TICK = b.comment("FE per tick while Stumpy works (before upgrade multipliers).")
                .defineInRange("stumpyFePerTick", 4, 0, 10_000);
        STUMPY_FE_PER_LOG = b.comment("FE per log Stumpy fells, paid when the tree comes down (before efficiency cards). A tree never costs more than half the internal buffer.")
                .defineInRange("stumpyFePerLog", 250, 0, 100_000);
        STUMPY_TICKS_PER_LOG = b.comment("Rest after a tree, in ticks per log felled (speed cards shorten it). Stumpy's area is one block smaller on each side than Sprout's.")
                .defineInRange("stumpyTicksPerLog", 10, 0, 1_200);
        SPROUT_FE_PER_TICK = b.comment("FE per tick while Sprout works (before upgrade multipliers).")
                .defineInRange("sproutFePerTick", 3, 0, 10_000);
        SPROUT_FE_PER_HARVEST = b.comment("FE per crop Sprout harvests (before efficiency cards). Without it the crop waits.")
                .defineInRange("sproutFePerHarvest", 30, 0, 100_000);
        MAX_LOGS = b.comment("Maximum logs Stumpy fells in one action.")
                .defineInRange("maxLogsPerTree", 256, 1, 4096);
        for (int i = 0; i < 4; i++) {
            int mk = i + 1;
            FARM_RADIUS[i] = b.comment("Mk" + mk + " work radius in blocks (area side = 2 * radius + 1). Range cards add 2 per level.")
                    .defineInRange("farmRadiusMk" + mk, DEF_RADIUS[i], 1, 32);
            FARM_INTERVAL[i] = b.comment("Mk" + mk + " ticks between actions.")
                    .defineInRange("farmIntervalMk" + mk, DEF_INTERVAL[i], 1, 1200);
            FARM_GROWTH[i] = b.comment("Mk" + mk + " growth multiplier (growth cards add 0.5 per level).")
                    .defineInRange("farmGrowthMk" + mk, DEF_GROWTH[i], 1.0, 50.0);
        }
        GROWTH_TICKS_PER_COLUMN = b.comment("Extra random ticks per second per area column for each +1.0 of growth multiplier. Vanilla gives a block 3/4096 random ticks per game tick = 0.0146 per second, so the default makes a x1.5 multiplier really mean x1.5.")
                .defineInRange("growthTicksPerColumn", 0.0146, 0.0, 5.0);
        for (int i = 0; i < 4; i++) {
            int mk = i + 1;
            EXC_SIZE[i] = b.comment("Excavator Mk" + mk + ": square side of the hole without range cards.")
                    .defineInRange("excavatorSizeMk" + mk, DEF_EXC_SIZE[i], 1, 64);
            EXC_INTERVAL[i] = b.comment("Excavator Mk" + mk + ": ticks per block without speed cards (speed cards divide it, at a steep FE price).")
                    .defineInRange("excavatorIntervalMk" + mk, DEF_EXC_INTERVAL[i], 1, 1200);
        }
        EXCAVATOR_RANGE_STEP = b.comment("Blocks every range card adds to the Excavator's square side (Mk4 with 4 cards: 24 + 40 = 64).")
                .defineInRange("excavatorRangeStep", 10, 1, 32);
        EXCAVATOR_MAX_SIZE = b.comment("Largest square side an Excavator digs, whatever its Mk and range cards.")
                .defineInRange("excavatorMaxSize", 128, 8, 256);
        EXCAVATOR_INPUT = b.comment("FE/t an Excavator Mk1 accepts from cables and Tesla Coils (a MkN N times as much; its buffer is energyBuffer x N).")
                .defineInRange("excavatorInputPerTick", 1_000, 1, 100_000_000);
        EXCAVATOR_FE_PER_BLOCK = b.comment("Excavator FE per mined block (before upgrade multipliers).")
                .defineInRange("excavatorFePerBlock", 40, 0, 1_000_000);
        b.pop();
        b.comment("Survey Rig: placed once, it slowly turns a lot of power into random ores from the c:ores item tag.").push("survey_rig");
        SURVEY_INTERVAL = b.comment("Survey Rig ticks per ore without speed cards (400 = one ore every 20 seconds).")
                .defineInRange("surveyRigTicksPerOre", 400, 1, 72_000);
        SURVEY_FE_PER_TICK = b.comment("Survey Rig FE per tick while working, before upgrades. Speed cards multiply it by their speed and by the steep per-ore factor (8 cards: x20 speed, x420 FE/t).")
                .defineInRange("surveyRigFePerTick", 200, 0, 10_000_000);
        SURVEY_ENERGY_BUFFER = b.comment("Survey Rig internal FE buffer.")
                .defineInRange("surveyRigBuffer", 2_000_000, 10_000, 1_000_000_000);
        SURVEY_MAX_INPUT = b.comment("Survey Rig FE per tick it accepts from cables and its battery slot.")
                .defineInRange("surveyRigInputPerTick", 100_000, 100, 100_000_000);
        for (int i = 0; i < 4; i++) {
            int mk = i + 1;
            SURVEY_SPEED[i] = b.comment("Survey Rig Mk" + mk + ": work speed in percent of Mk1. Time per ore shrinks and FE/t grows by it, so FE per ore stays the same; buffer and input grow with the Mk.")
                    .defineInRange("surveyRigSpeedMk" + mk, DEF_SURVEY_SPEED[i], 10, 10_000);
            SURVEY_RARE[i] = b.comment("Survey Rig Mk" + mk + ": percent more weight for rare ore kinds (weight <= surveyRigRareWeight).")
                    .defineInRange("surveyRigRareBonusMk" + mk, DEF_SURVEY_RARE[i], 0, 10_000);
        }
        SURVEY_RARE_WEIGHT = b.comment("Ore kinds with at most this weight count as rare for the Survey Rig's Mk bonus (diamond 6, emerald 4, pyrolite 15).")
                .defineInRange("surveyRigRareWeight", 20, 0, 100_000);
        SURVEY_ORE_WEIGHTS = b.comment("Weight of each ore kind, as \"#tag=weight\" (an ores/<name> item tag) or \"namespace:item=weight\".",
                        "Every item in c:ores can come out. Variants of one kind (stone, deepslate, nether) share their kind's weight. 0 = never.")
                .defineListAllowEmpty("surveyRigOreWeights", DEFAULT_ORE_WEIGHTS, () -> "#c:ores/example=10", o -> o instanceof String str && str.contains("="));
        SURVEY_DEFAULT_WEIGHT = b.comment("Weight of ore kinds that are not listed above (other mods' ores).")
                .defineInRange("surveyRigDefaultOreWeight", 15, 0, 100_000);
        SURVEY_CORE_ORES = b.comment("Ore kinds (\"#tag\" or item id) the rig only makes with a Magma Core in its core slot.")
                .defineListAllowEmpty("surveyRigCoreOres", DEFAULT_CORE_ORES, () -> "#c:ores/example", o -> o instanceof String);
        b.pop();
        SPEC = b.build();
    }

    private static int tierIndex(int tier) {
        return Math.max(0, Math.min(3, tier - 1));
    }

    public static int energyBuffer() {
        return SPEC.isLoaded() ? ENERGY_BUFFER.get() : 20_000;
    }

    public static int stumpyTicksPerLog() {
        return SPEC.isLoaded() ? STUMPY_TICKS_PER_LOG.get() : 10;
    }

    public static int stumpyFePerLog() {
        return SPEC.isLoaded() ? STUMPY_FE_PER_LOG.get() : 250;
    }

    public static int stumpyFe() {
        return SPEC.isLoaded() ? STUMPY_FE_PER_TICK.get() : 4;
    }

    public static int sproutFe() {
        return SPEC.isLoaded() ? SPROUT_FE_PER_TICK.get() : 3;
    }

    public static int sproutFePerHarvest() {
        return SPEC.isLoaded() ? SPROUT_FE_PER_HARVEST.get() : 30;
    }

    public static int maxLogs() {
        return SPEC.isLoaded() ? MAX_LOGS.get() : 256;
    }

    public static int farmRadius(int tier) {
        int i = tierIndex(tier);
        return SPEC.isLoaded() ? FARM_RADIUS[i].get() : DEF_RADIUS[i];
    }

    public static int farmInterval(int tier) {
        int i = tierIndex(tier);
        return SPEC.isLoaded() ? FARM_INTERVAL[i].get() : DEF_INTERVAL[i];
    }

    public static double farmGrowth(int tier) {
        int i = tierIndex(tier);
        return SPEC.isLoaded() ? FARM_GROWTH[i].get() : DEF_GROWTH[i];
    }

    public static double growthTicksPerColumn() {
        return SPEC.isLoaded() ? GROWTH_TICKS_PER_COLUMN.get() : 0.0146;
    }

    private static int mk(ModConfigSpec.IntValue[] values, int[] defaults, int tier) {
        int i = tierIndex(tier);
        return SPEC.isLoaded() ? values[i].get() : defaults[i];
    }

    public static int excavatorSize(int tier) { return mk(EXC_SIZE, DEF_EXC_SIZE, tier); }
    public static int excavatorInterval(int tier) { return mk(EXC_INTERVAL, DEF_EXC_INTERVAL, tier); }
    public static int excavatorRangeStep() { return SPEC.isLoaded() ? EXCAVATOR_RANGE_STEP.get() : 10; }
    public static int excavatorMaxSize() { return SPEC.isLoaded() ? EXCAVATOR_MAX_SIZE.get() : 128; }
    public static int excavatorInput() { return SPEC.isLoaded() ? EXCAVATOR_INPUT.get() : 1_000; }
    /** Percent of Mk1 speed. */
    public static int surveySpeed(int tier) { return mk(SURVEY_SPEED, DEF_SURVEY_SPEED, tier); }
    public static int surveyRareBonus(int tier) { return mk(SURVEY_RARE, DEF_SURVEY_RARE, tier); }
    public static int surveyRareWeight() { return SPEC.isLoaded() ? SURVEY_RARE_WEIGHT.get() : 20; }

    public static int excavatorFe() {
        return SPEC.isLoaded() ? EXCAVATOR_FE_PER_BLOCK.get() : 40;
    }

    public static int surveyInterval() {
        return SPEC.isLoaded() ? SURVEY_INTERVAL.get() : 400;
    }

    public static int surveyFePerTick() {
        return SPEC.isLoaded() ? SURVEY_FE_PER_TICK.get() : 200;
    }

    public static int surveyEnergyBuffer() {
        return SPEC.isLoaded() ? SURVEY_ENERGY_BUFFER.get() : 2_000_000;
    }

    public static int surveyMaxInput() {
        return SPEC.isLoaded() ? SURVEY_MAX_INPUT.get() : 100_000;
    }

    public static List<? extends String> surveyOreWeights() {
        return SPEC.isLoaded() ? SURVEY_ORE_WEIGHTS.get() : DEFAULT_ORE_WEIGHTS;
    }

    public static int surveyDefaultWeight() {
        return SPEC.isLoaded() ? SURVEY_DEFAULT_WEIGHT.get() : 15;
    }

    public static List<? extends String> surveyCoreOres() {
        return SPEC.isLoaded() ? SURVEY_CORE_ORES.get() : DEFAULT_CORE_ORES;
    }
}
