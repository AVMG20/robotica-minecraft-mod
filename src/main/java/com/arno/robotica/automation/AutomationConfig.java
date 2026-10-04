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
    public static final ModConfigSpec.IntValue SPROUT_FE_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_LOGS;
    public static final ModConfigSpec.IntValue EXCAVATOR_SIZE;
    public static final ModConfigSpec.IntValue EXCAVATOR_INTERVAL;
    public static final ModConfigSpec.IntValue EXCAVATOR_FE_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue GROWTH_TICKS_PER_COLUMN;
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
        SPROUT_FE_PER_TICK = b.comment("FE per tick while Sprout works (before upgrade multipliers).")
                .defineInRange("sproutFePerTick", 3, 0, 10_000);
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
        EXCAVATOR_INTERVAL = b.comment("Excavator ticks per block.")
                .defineInRange("excavatorInterval", 40, 1, 1200);
        EXCAVATOR_FE_PER_BLOCK = b.comment("Excavator FE per mined block (before upgrade multipliers).")
                .defineInRange("excavatorFePerBlock", 40, 0, 1_000_000);
        b.pop();
        SPEC = b.build();
    }

    private static int tierIndex(int tier) {
        return Math.max(0, Math.min(3, tier - 1));
    }

    public static int energyBuffer() {
        return SPEC.isLoaded() ? ENERGY_BUFFER.get() : 20_000;
    }

    public static int stumpyFe() {
        return SPEC.isLoaded() ? STUMPY_FE_PER_TICK.get() : 4;
    }

    public static int sproutFe() {
        return SPEC.isLoaded() ? SPROUT_FE_PER_TICK.get() : 3;
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
        return SPEC.isLoaded() ? EXCAVATOR_INTERVAL.get() : 40;
    }

    public static int excavatorFe() {
        return SPEC.isLoaded() ? EXCAVATOR_FE_PER_BLOCK.get() : 40;
    }
}
