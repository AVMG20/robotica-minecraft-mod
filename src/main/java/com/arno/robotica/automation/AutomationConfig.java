package com.arno.robotica.automation;

import net.neoforged.neoforge.common.ModConfigSpec;

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
    public static final ModConfigSpec.IntValue EXCAVATOR_SIZE;
    public static final ModConfigSpec.IntValue EXCAVATOR_INTERVAL;
    public static final ModConfigSpec.IntValue EXCAVATOR_FE_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue GROWTH_TICKS_PER_COLUMN;
    public static final ModConfigSpec.IntValue SURVEY_INTERVAL;
    public static final ModConfigSpec.IntValue SURVEY_FE_PER_ORE;
    public static final ModConfigSpec.IntValue SURVEY_ENERGY_BUFFER;
    public static final ModConfigSpec.IntValue SURVEY_MAX_INPUT;
    public static final ModConfigSpec.IntValue SURVEY_SECTIONS_PER_TICK;
    public static final ModConfigSpec.BooleanValue STRIP_ORES;
    public static final ModConfigSpec.IntValue SURVEY_STRIP_PER_TICK;
    public static final ModConfigSpec.IntValue SURVEY_FILLER_PER_ORE;
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
        EXCAVATOR_SIZE = b.comment("Excavator base square side without range cards (range cards give 16/32/48/64).")
                .defineInRange("excavatorSize", 8, 1, 64);
        EXCAVATOR_INTERVAL = b.comment("Excavator ticks per block without speed cards. Slow on purpose: speed cards are the way up, at a steep FE price.")
                .defineInRange("excavatorInterval", 60, 1, 1200);
        EXCAVATOR_FE_PER_BLOCK = b.comment("Excavator FE per mined block (before upgrade multipliers).")
                .defineInRange("excavatorFePerBlock", 40, 0, 1_000_000);
        b.pop();
        b.comment("Survey Rig: a lag-free virtual quarry. It scans its own chunk once into an ore ledger, then mines the ledger without changing the world.").push("survey_rig");
        SURVEY_INTERVAL = b.comment("Survey Rig ticks per ore without speed cards (100 = one ore every 5 seconds).")
                .defineInRange("surveyRigInterval", 100, 1, 12_000);
        SURVEY_FE_PER_ORE = b.comment("Survey Rig FE per ore before upgrade multipliers. Speed cards raise it steeply, like the Excavator's.")
                .defineInRange("surveyRigFePerOre", 2_000, 0, 10_000_000);
        SURVEY_ENERGY_BUFFER = b.comment("Survey Rig internal FE buffer.")
                .defineInRange("surveyRigEnergyBuffer", 500_000, 10_000, 100_000_000);
        SURVEY_MAX_INPUT = b.comment("Survey Rig FE per tick it accepts from conduits and its battery slot.")
                .defineInRange("surveyRigMaxInput", 20_000, 100, 10_000_000);
        SURVEY_SECTIONS_PER_TICK = b.comment("Chunk sections (16x16x16) the scan reads per tick. The scan never loads other chunks.")
                .defineInRange("surveyRigSectionsPerTick", 2, 1, 24);
        STRIP_ORES = b.comment("true: ores the rig has put in its ledger are replaced by their stone, deepslate or netherrack host (a few per tick), so nobody can also mine them by hand. An ore that is gone or protected when its turn comes is taken off the ledger.",
                        "false: the ores stay in the world and the ledger is purely virtual (the chunk can be mined twice).")
                .define("stripOresFromWorld", true);
        SURVEY_STRIP_PER_TICK = b.comment("Ore blocks replaced by their host per tick when stripOresFromWorld is on.")
                .defineInRange("surveyRigStripPerTick", 4, 1, 256);
        SURVEY_FILLER_PER_ORE = b.comment("Host rock drops (cobblestone, cobbled deepslate, netherrack) added per mined ore. 0 = none, the default.")
                .defineInRange("surveyRigFillerPerOre", 0, 0, 16);
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

    public static int excavatorSize() {
        return SPEC.isLoaded() ? EXCAVATOR_SIZE.get() : 8;
    }

    public static int excavatorInterval() {
        return SPEC.isLoaded() ? EXCAVATOR_INTERVAL.get() : 60;
    }

    public static int excavatorFe() {
        return SPEC.isLoaded() ? EXCAVATOR_FE_PER_BLOCK.get() : 40;
    }

    public static int surveyInterval() {
        return SPEC.isLoaded() ? SURVEY_INTERVAL.get() : 100;
    }

    public static int surveyFePerOre() {
        return SPEC.isLoaded() ? SURVEY_FE_PER_ORE.get() : 2_000;
    }

    public static int surveyEnergyBuffer() {
        return SPEC.isLoaded() ? SURVEY_ENERGY_BUFFER.get() : 500_000;
    }

    public static int surveyMaxInput() {
        return SPEC.isLoaded() ? SURVEY_MAX_INPUT.get() : 20_000;
    }

    public static int surveySectionsPerTick() {
        return SPEC.isLoaded() ? SURVEY_SECTIONS_PER_TICK.get() : 2;
    }

    public static boolean stripOres() {
        return SPEC.isLoaded() ? STRIP_ORES.get() : true;
    }

    public static int surveyStripPerTick() {
        return SPEC.isLoaded() ? SURVEY_STRIP_PER_TICK.get() : 4;
    }

    public static int surveyFillerPerOre() {
        return SPEC.isLoaded() ? SURVEY_FILLER_PER_ORE.get() : 0;
    }
}
