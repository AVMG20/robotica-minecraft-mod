package com.arno.robotica.architect;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance values of the architect module (server config, file robotica-architect-server.toml).
 * Read through the static helpers: they fall back to the defaults before the config is loaded.
 */
public final class ArchitectConfig {
    private ArchitectConfig() {}

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue BASE_INTERVAL;
    private static final ModConfigSpec.IntValue FE_PER_BLOCK;
    private static final ModConfigSpec.IntValue ENERGY_BUFFER;
    private static final ModConfigSpec.IntValue ENERGY_RECEIVE;
    private static final ModConfigSpec.IntValue MATTER_CAP;
    private static final ModConfigSpec.IntValue MAX_QUEUE;
    private static final ModConfigSpec.BooleanValue ALLOW_CLEAR;
    private static final ModConfigSpec.IntValue CLEAR_INTERVAL;
    private static final ModConfigSpec.BooleanValue DRONES;
    private static final ModConfigSpec.BooleanValue ALLOW_DEMOLISH;
    private static final ModConfigSpec.IntValue DEMOLISH_INTERVAL;
    private static final ModConfigSpec.IntValue DEMOLISH_ENERGY;
    private static final ModConfigSpec.DoubleValue DEMOLISH_REFUND;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("architect_table");
        BASE_INTERVAL = b.comment("Ticks per placed block at no speed upgrade (speed cards divide it).")
                .defineInRange("baseInterval", 4, 1, 200);
        FE_PER_BLOCK = b.comment("Base FE per placed block. Styles scale it: Clean Stone 50 %, Smooth Panel 100 %, Detailed Stone 200 %,",
                        "Tech Stone 400 %; speed and efficiency cards apply on top.")
                .defineInRange("fePerBlock", 20, 0, 1_000_000);
        ENERGY_BUFFER = b.comment("Internal FE buffer.")
                .defineInRange("energyBuffer", 200_000, 1_000, 100_000_000);
        ENERGY_RECEIVE = b.comment("Maximum FE/t accepted from any side.")
                .defineInRange("energyReceive", 20_000, 1, 100_000_000);
        MATTER_CAP = b.comment("Maximum stored amount of each matter grade.")
                .defineInRange("matterCap", 100_000, 1_000, 100_000_000);
        MAX_QUEUE = b.comment("Maximum number of plots queued at once per table (the grid has 25).")
                .defineInRange("maxQueue", 25, 1, 25);
        ALLOW_CLEAR = b.comment("Allow the clear terrain option (removes blocks inside a planned building).")
                .define("allowClearTerrain", true);
        CLEAR_INTERVAL = b.comment("Ticks per block removed by clear terrain. Speed cards do not change it, so the table is no quarry.")
                .defineInRange("clearInterval", 10, 1, 200);
        DRONES = b.comment("Spawn the cosmetic builder drone while a table is building.")
                .define("builderDrones", true);
        ALLOW_DEMOLISH = b.comment("Allow demolish (takes down every built plot of a table, everything inside included).")
                .define("allowDemolish", true);
        DEMOLISH_INTERVAL = b.comment("Ticks per block taken down by demolish at no speed upgrade (speed cards divide it). Building takes baseInterval.")
                .defineInRange("demolishInterval", 1, 1, 200);
        DEMOLISH_ENERGY = b.comment("FE per block taken down by demolish (global energy multiplier, speed and efficiency cards apply). Air and liquids are free.")
                .defineInRange("demolishEnergy", 5, 0, 1_000_000);
        DEMOLISH_REFUND = b.comment("Share of a Robotica building block's matter price that demolish puts back into the table.",
                        "0 gives the blocks back as items instead.")
                .defineInRange("demolishRefund", 0.75, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    public static int baseInterval() { return SPEC.isLoaded() ? BASE_INTERVAL.get() : BASE_INTERVAL.getDefault(); }
    public static int fePerBlock() { return SPEC.isLoaded() ? FE_PER_BLOCK.get() : FE_PER_BLOCK.getDefault(); }
    public static int energyBuffer() { return SPEC.isLoaded() ? ENERGY_BUFFER.get() : ENERGY_BUFFER.getDefault(); }
    public static int energyReceive() { return SPEC.isLoaded() ? ENERGY_RECEIVE.get() : ENERGY_RECEIVE.getDefault(); }
    public static int matterCap() { return SPEC.isLoaded() ? MATTER_CAP.get() : MATTER_CAP.getDefault(); }
    public static int maxQueue() { return SPEC.isLoaded() ? MAX_QUEUE.get() : MAX_QUEUE.getDefault(); }
    public static boolean allowClearTerrain() { return SPEC.isLoaded() ? ALLOW_CLEAR.get() : ALLOW_CLEAR.getDefault(); }
    public static int clearInterval() { return SPEC.isLoaded() ? CLEAR_INTERVAL.get() : CLEAR_INTERVAL.getDefault(); }
    public static boolean allowDemolish() { return SPEC.isLoaded() ? ALLOW_DEMOLISH.get() : ALLOW_DEMOLISH.getDefault(); }
    public static int demolishInterval() { return SPEC.isLoaded() ? DEMOLISH_INTERVAL.get() : DEMOLISH_INTERVAL.getDefault(); }
    public static int demolishEnergy() { return SPEC.isLoaded() ? DEMOLISH_ENERGY.get() : DEMOLISH_ENERGY.getDefault(); }
    public static double demolishRefund() { return SPEC.isLoaded() ? DEMOLISH_REFUND.get() : DEMOLISH_REFUND.getDefault(); }
    public static boolean builderDrones() { return SPEC.isLoaded() ? DRONES.get() : DRONES.getDefault(); }
}
