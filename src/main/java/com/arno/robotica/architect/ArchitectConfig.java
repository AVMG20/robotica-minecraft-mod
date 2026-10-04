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
    private static final ModConfigSpec.BooleanValue DRONES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("architect_table");
        BASE_INTERVAL = b.comment("Ticks per placed block at no speed upgrade (speed cards divide it).")
                .defineInRange("baseInterval", 4, 1, 200);
        FE_PER_BLOCK = b.comment("FE per placed block before the speed and efficiency card modifiers.")
                .defineInRange("fePerBlock", 50, 0, 1_000_000);
        ENERGY_BUFFER = b.comment("Internal FE buffer.")
                .defineInRange("energyBuffer", 200_000, 1_000, 100_000_000);
        ENERGY_RECEIVE = b.comment("Maximum FE/t accepted from any side.")
                .defineInRange("energyReceive", 20_000, 1, 100_000_000);
        MATTER_CAP = b.comment("Maximum stored amount of each matter grade.")
                .defineInRange("matterCap", 100_000, 1_000, 100_000_000);
        MAX_QUEUE = b.comment("Maximum number of queued builds per table.")
                .defineInRange("maxQueue", 12, 1, 64);
        ALLOW_CLEAR = b.comment("Allow the clear terrain option (removes blocks inside a queued plot).")
                .define("allowClearTerrain", true);
        DRONES = b.comment("Spawn the cosmetic builder drone while a table is building.")
                .define("builderDrones", true);
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
    public static boolean builderDrones() { return SPEC.isLoaded() ? DRONES.get() : DRONES.getDefault(); }
}
